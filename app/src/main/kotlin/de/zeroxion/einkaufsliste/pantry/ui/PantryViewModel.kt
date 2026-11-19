package de.zeroxion.einkaufsliste.pantry.ui

import android.app.Application
import android.content.Context
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.zeroxion.einkaufsliste.data.ShoppingRepository
import de.zeroxion.einkaufsliste.pantry.data.OpenFoodFacts
import de.zeroxion.einkaufsliste.pantry.data.PantryItem
import de.zeroxion.einkaufsliste.pantry.data.PantryJson
import de.zeroxion.einkaufsliste.pantry.data.PantryRepository
import de.zeroxion.einkaufsliste.pantry.data.PantrySettings
import de.zeroxion.einkaufsliste.pantry.data.PriceCache
import de.zeroxion.einkaufsliste.pantry.logic.DateHit
import de.zeroxion.einkaufsliste.pantry.logic.PriceRegion
import de.zeroxion.einkaufsliste.pantry.logic.PriceSummary
import de.zeroxion.einkaufsliste.pantry.logic.shoppingName
import de.zeroxion.einkaufsliste.pantry.reminders.Reminders
import de.zeroxion.einkaufsliste.parsing.ParsedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate
import java.util.Locale

/** Up to this many days a product counts as "soon due". */
const val SOON_DAYS = 3

/** From this many products on, the search field is shown. */
const val SEARCH_MIN_ITEMS = 6

enum class ExpiryStatus(val title: String) {
    EXPIRED("Abgelaufen"),
    SOON("Bald fällig"),
    LATER("Später"),
}

fun statusOf(days: Int): ExpiryStatus = when {
    days < 0 -> ExpiryStatus.EXPIRED
    days <= SOON_DAYS -> ExpiryStatus.SOON
    else -> ExpiryStatus.LATER
}

data class PantryRow(
    val item: PantryItem,
    val days: Int,
    val price: PriceSummary?,
    /** The product is on the open shopping list. */
    val onShoppingList: Boolean,
) {
    val status: ExpiryStatus get() = statusOf(days)
}

data class PantryGroup(val status: ExpiryStatus, val rows: List<PantryRow>)

data class PantryUiState(
    val groups: List<PantryGroup> = emptyList(),
    val expired: Int = 0,
    val soon: Int = 0,
    val total: Int = 0,
    val query: String = "",
    val today: LocalDate = LocalDate.now(),
) {
    val showSearch: Boolean get() = total >= SEARCH_MIN_ITEMS || query.isNotEmpty()
    val noHits: Boolean get() = total > 0 && groups.isEmpty()
    /** Number shown on the tab: expired and soon due products. */
    val attention: Int get() = expired + soon
}

data class PriceState(
    val summary: PriceSummary? = null,
    val loading: Boolean = false,
    val offline: Boolean = false,
)

/** The product currently open in the editor. */
data class EditorState(
    val id: String,
    val isNew: Boolean,
    val name: String = "",
    val date: LocalDate? = null,
    val barcode: String = "",
    val image: String = "",
    val lookupStatus: String = "",
    val ocrStatus: String = "",
    val prices: PriceState = PriceState(),
)

data class ReminderSettings(
    val hour: Int,
    val minute: Int,
    val leadDays: Int,
    val region: PriceRegion,
    val notificationsEnabled: Boolean,
    val batteryOptimized: Boolean,
)

/** Snackbar message, optionally with an action (usually "Rückgängig"). */
data class PantryMessage(val text: String, val actionLabel: String? = null, val action: (() -> Unit)? = null)

class PantryViewModel(
    private val app: Application,
    private val repository: PantryRepository,
    private val shopping: ShoppingRepository,
) : AndroidViewModel(app) {

    private val settings = PantrySettings.get(app)
    private val priceCache = PriceCache.get(app)

    private val query = MutableStateFlow("")
    private val today = MutableStateFlow(LocalDate.now())
    private val prices = MutableStateFlow<Map<String, PriceSummary>>(emptyMap())

    val uiState: StateFlow<PantryUiState> = combine(
        repository.items, shopping.items, query, today, prices,
    ) { items, shoppingItems, q, day, priceMap ->
        val openShopping = shoppingItems.filter { !it.checked }.map { it.name.lowercase(Locale.GERMAN) }.toSet()
        val rows = items
            .sortedWith(compareBy<PantryItem> { it.date }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            .map { item ->
                PantryRow(
                    item = item,
                    days = item.daysLeft(day),
                    price = priceMap[item.barcode],
                    onShoppingList = shoppingName(item.name).lowercase(Locale.GERMAN) in openShopping,
                )
            }
        val needle = q.trim().lowercase(Locale.GERMAN)
        val visible = if (needle.isEmpty()) rows else rows.filter {
            it.item.name.lowercase(Locale.GERMAN).contains(needle) || it.item.barcode == needle
        }
        PantryUiState(
            groups = ExpiryStatus.entries.mapNotNull { status ->
                visible.filter { it.status == status }.takeIf { it.isNotEmpty() }?.let { PantryGroup(status, it) }
            },
            expired = rows.count { it.status == ExpiryStatus.EXPIRED },
            soon = rows.count { it.status == ExpiryStatus.SOON },
            total = rows.size,
            query = q,
            today = day,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PantryUiState())

    private val _editor = MutableStateFlow<EditorState?>(null)
    val editor: StateFlow<EditorState?> = _editor.asStateFlow()

    private val _settings = MutableStateFlow(readSettings())
    val reminderSettings: StateFlow<ReminderSettings> = _settings.asStateFlow()

    private val _messages = Channel<PantryMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    /** Asks the screen to request the notification permission (once, after the first product). */
    private val _askNotifications = Channel<Unit>(Channel.CONFLATED)
    val askNotifications = _askNotifications.receiveAsFlow()

    private var lookupJob: Job? = null
    private var priceJob: Job? = null

    init {
        loadCachedPrices()
        viewModelScope.launch {
            delay(1500)
            refreshAllPrices()
        }
    }

    // ---------- List ----------

    fun setQuery(value: String) {
        query.value = value
    }

    /** Called when the app comes back to the foreground: a new day changes all countdowns. */
    fun onResume() {
        today.value = LocalDate.now()
        _settings.value = readSettings()
    }

    fun markUsed(item: PantryItem) {
        viewModelScope.launch {
            val removed = repository.remove(item.id) ?: return@launch
            afterChange()
            _messages.send(
                PantryMessage("„${removed.name}“ verbraucht", "Rückgängig") {
                    viewModelScope.launch {
                        repository.upsert(removed)
                        afterChange()
                    }
                },
            )
        }
    }

    /** Puts the product (without its pack size) on the shopping list. */
    fun addToShoppingList(item: PantryItem) {
        viewModelScope.launch {
            val result = shopping.add(listOf(ParsedItem(shoppingName(item.name))))
            val name = result.all.firstOrNull()?.name ?: item.name
            _messages.send(
                PantryMessage("„$name“ steht auf der Einkaufsliste", "Rückgängig") {
                    viewModelScope.launch { shopping.restore(result.previous, result.added.map { it.id }) }
                },
            )
        }
    }

    // ---------- Editor ----------

    fun openEditor(item: PantryItem?) {
        lookupJob?.cancel()
        _editor.value = if (item == null) {
            EditorState(id = PantryJson.newId(), isNew = true)
        } else {
            EditorState(
                id = item.id, isNew = false, name = item.name, date = item.date,
                barcode = item.barcode, image = item.image,
            )
        }
        item?.barcode?.takeIf { it.isNotEmpty() }?.let { showPrices(it, force = false) }
    }

    fun closeEditor() {
        lookupJob?.cancel()
        priceJob?.cancel()
        _editor.value = null
    }

    fun setName(name: String) = _editor.update { it?.copy(name = name) }

    fun setDate(date: LocalDate) = _editor.update { it?.copy(date = date, ocrStatus = "") }

    fun applyDateHit(hit: DateHit) = _editor.update {
        it?.copy(
            date = hit.date,
            ocrStatus = "Erkannt: „${hit.raw}“ → ${hit.date.format(GERMAN_DATE)} – bitte kurz prüfen.",
        )
    }

    /** After the barcode scan: take the name from memory or look it up at Open Food Facts. */
    fun applyBarcode(code: String) {
        val draftId = _editor.value?.id ?: return
        _editor.update { it?.copy(barcode = code, lookupStatus = "") }
        showPrices(code, force = false)
        val known = settings.knownProduct(code)
        if (known != null) {
            _editor.update { it?.copy(name = known.name, image = known.image, lookupStatus = "Bekanntes Produkt") }
            return
        }
        _editor.update { it?.copy(lookupStatus = "Suche Produkt …") }
        lookupJob?.cancel()
        lookupJob = viewModelScope.launch {
            val status: String
            var name: String? = null
            var image = ""
            try {
                val product = OpenFoodFacts.lookup(code)
                status = when {
                    product == null -> "Produkt nicht gefunden – bitte Namen eingeben."
                    product.name.isBlank() -> "Kein Name hinterlegt – bitte eingeben."
                    else -> "Gefunden bei Open Food Facts"
                }
                name = product?.name?.takeIf { it.isNotBlank() }
                image = product?.image.orEmpty()
            } catch (e: IOException) {
                _editor.update { if (it != null && it.id == draftId) it.copy(lookupStatus = "Keine Verbindung – bitte Namen eingeben.") else it }
                return@launch
            } catch (e: Exception) {
                _editor.update { if (it != null && it.id == draftId) it.copy(lookupStatus = "Produktsuche fehlgeschlagen – bitte Namen eingeben.") else it }
                return@launch
            }
            _editor.update { draft ->
                if (draft == null || draft.id != draftId) return@update draft
                draft.copy(name = name ?: draft.name, image = image.ifEmpty { draft.image }, lookupStatus = status)
            }
        }
    }

    fun refreshEditorPrices() {
        _editor.value?.barcode?.takeIf { it.isNotEmpty() }?.let { showPrices(it, force = true) }
    }

    private fun showPrices(code: String, force: Boolean) {
        val region = settings.region
        val cached = priceCache.get(code, region)
        _editor.update { it?.copy(prices = PriceState(summary = cached, loading = cached == null || force)) }
        if (!force && cached?.isFresh() == true) return
        priceJob?.cancel()
        priceJob = viewModelScope.launch {
            try {
                val summary = OpenFoodFacts.prices(code, region)
                priceCache.put(code, summary)
                prices.update { it + (code to summary) }
                _editor.update { if (it != null && it.barcode == code) it.copy(prices = PriceState(summary)) else it }
            } catch (e: Exception) {
                _editor.update {
                    if (it != null && it.barcode == code) it.copy(prices = PriceState(summary = cached, offline = true)) else it
                }
            }
        }
    }

    fun saveEditor() {
        val draft = _editor.value ?: return
        val date = draft.date
        if (date == null) {
            _messages.trySend(PantryMessage("Bitte ein Mindesthaltbarkeitsdatum eingeben"))
            return
        }
        val name = draft.name.trim().ifEmpty { "Produkt" }
        viewModelScope.launch {
            val existing = repository.items.value.firstOrNull { it.id == draft.id }
            val item = PantryItem(
                id = draft.id,
                name = name,
                date = date,
                barcode = draft.barcode,
                image = draft.image,
                added = existing?.added ?: LocalDate.now(),
            )
            settings.rememberProduct(item.barcode, item.name, item.image)
            repository.upsert(item)
            closeEditor()
            afterChange()

            val days = item.daysLeft(LocalDate.now())
            val lead = settings.leadDays
            val text = when {
                !draft.isNew -> "Änderungen gespeichert"
                days < 0 -> "Gespeichert – bereits abgelaufen"
                days == 0 -> "Gespeichert – läuft heute ab"
                lead == 1 -> "Gespeichert – Erinnerung am Vortag"
                else -> "Gespeichert – Erinnerung $lead Tage vorher"
            }
            _messages.send(PantryMessage(text))
            if (draft.isNew && !Reminders.notificationsEnabled(app) && !settings.askedForNotifications) {
                settings.askedForNotifications = true
                _askNotifications.send(Unit)
            }
        }
    }

    fun deleteFromEditor() {
        val draft = _editor.value ?: return
        closeEditor()
        val item = repository.items.value.firstOrNull { it.id == draft.id } ?: return
        viewModelScope.launch {
            repository.remove(item.id)
            afterChange()
            _messages.send(
                PantryMessage("„${item.name}“ gelöscht", "Rückgängig") {
                    viewModelScope.launch {
                        repository.upsert(item)
                        afterChange()
                    }
                },
            )
        }
    }

    // ---------- Settings ----------

    private fun readSettings(): ReminderSettings {
        val power = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return ReminderSettings(
            hour = settings.hour,
            minute = settings.minute,
            leadDays = settings.leadDays,
            region = settings.region,
            notificationsEnabled = Reminders.notificationsEnabled(app),
            batteryOptimized = power != null && !power.isIgnoringBatteryOptimizations(app.packageName),
        )
    }

    fun refreshSettings() {
        _settings.value = readSettings()
    }

    fun setReminderTime(hour: Int, minute: Int) {
        settings.hour = hour
        settings.minute = minute
        refreshSettings()
        viewModelScope.launch(Dispatchers.Default) { Reminders.schedule(app) }
        _messages.trySend(PantryMessage(String.format(Locale.GERMAN, "Erinnerung täglich um %02d:%02d Uhr", hour, minute)))
    }

    fun setLeadDays(days: Int) {
        settings.leadDays = days
        refreshSettings()
        afterChange() // a longer lead time can make something due right away
        _messages.trySend(
            PantryMessage(if (days == 1) "Erinnerung einen Tag vor Ablauf" else "Erinnerung $days Tage vor Ablauf"),
        )
    }

    fun setRegion(region: PriceRegion) {
        settings.region = region
        refreshSettings()
        loadCachedPrices()
        viewModelScope.launch { refreshAllPrices() }
        _messages.trySend(PantryMessage("Preise aus ${region.label}"))
    }

    fun testNotification() {
        if (!Reminders.notificationsEnabled(app)) {
            _messages.trySend(PantryMessage("Benachrichtigungen sind für die App ausgeschaltet"))
            return
        }
        Reminders.testNotification(app)
        _messages.trySend(PantryMessage("Test-Benachrichtigung gesendet"))
    }

    /** Called after the permission dialog or when coming back from the system settings. */
    fun onNotificationPermissionChanged() {
        refreshSettings()
        if (Reminders.notificationsEnabled(app)) {
            viewModelScope.launch(Dispatchers.Default) { Reminders.checkIfTimePassed(app, includeToday = true) }
        }
    }

    fun exportBackup(): String = PantryJson.exportBackup(repository.items.value)

    /** Imports a backup of this app or of the former MHD-Tracker app. */
    fun importBackup(text: String) {
        viewModelScope.launch {
            val added = try {
                PantryJson.parseBackup(text, repository.items.value.map { it.id }.toSet())
            } catch (e: Exception) {
                _messages.send(PantryMessage("Import fehlgeschlagen – ungültige Daten"))
                return@launch
            }
            if (added.isEmpty()) {
                _messages.send(PantryMessage("Die Sicherung enthält keine Produkte"))
                return@launch
            }
            repository.addAll(added)
            added.forEach { settings.rememberProduct(it.barcode, it.name, it.image) }
            afterChange()
            _messages.send(
                PantryMessage(
                    if (added.size == 1) "1 Produkt in den Vorrat übernommen" else "${added.size} Produkte in den Vorrat übernommen",
                    "Rückgängig",
                ) {
                    viewModelScope.launch {
                        added.forEach { repository.remove(it.id) }
                        afterChange()
                    }
                },
            )
            refreshAllPrices()
        }
    }

    fun showMessage(text: String) {
        _messages.trySend(PantryMessage(text))
    }

    // ---------- Internals ----------

    private fun afterChange() {
        viewModelScope.launch(Dispatchers.Default) { Reminders.afterItemsChanged(app) }
    }

    private fun loadCachedPrices() {
        val region = settings.region
        prices.value = repository.items.value
            .mapNotNull { item -> priceCache.get(item.barcode, region)?.let { item.barcode to it } }
            .toMap()
    }

    /** Loads missing or outdated prices of the list one after another in the background. */
    private suspend fun refreshAllPrices() {
        val region = settings.region
        val codes = repository.items.value.map { it.barcode }.filter { it.isNotEmpty() }.toSet()
        withContext(Dispatchers.IO) { priceCache.prune(codes) }
        val stale = codes.filter { priceCache.get(it, region)?.isFresh() != true }.take(25)
        for (code in stale) {
            try {
                val summary = OpenFoodFacts.prices(code, region)
                priceCache.put(code, summary)
                prices.update { it + (code to summary) }
            } catch (e: Exception) {
                return // offline: try again next time
            }
            delay(300)
        }
    }

    companion object {
        val GERMAN_DATE: java.time.format.DateTimeFormatter =
            java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN)

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY]!!
                PantryViewModel(app, PantryRepository.get(app), ShoppingRepository.get(app))
            }
        }
    }
}
