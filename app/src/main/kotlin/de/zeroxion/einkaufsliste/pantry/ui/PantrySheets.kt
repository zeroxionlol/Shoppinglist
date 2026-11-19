package de.zeroxion.einkaufsliste.pantry.ui

import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.zeroxion.einkaufsliste.R
import de.zeroxion.einkaufsliste.pantry.data.PantrySettings
import de.zeroxion.einkaufsliste.pantry.logic.Chain
import de.zeroxion.einkaufsliste.pantry.logic.PriceRegion
import de.zeroxion.einkaufsliste.pantry.logic.PriceSummary
import de.zeroxion.einkaufsliste.pantry.logic.ShopPrice
import de.zeroxion.einkaufsliste.pantry.logic.euro
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val DAY_MS = 86_400_000L

// ---------------------------------------------------------------------------------------------
// Product editor
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PantryEditorSheet(
    state: EditorState,
    viewModel: PantryViewModel,
    scanBarcodeOnOpen: Boolean,
    onScanOnOpenHandled: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var scanning by remember { mutableStateOf<ScanMode?>(null) }
    var pickingDate by remember { mutableStateOf(false) }

    LaunchedEffect(scanBarcodeOnOpen) {
        if (scanBarcodeOnOpen) {
            onScanOnOpenHandled()
            scanning = ScanMode.BARCODE
        }
    }

    ModalBottomSheet(onDismissRequest = viewModel::closeEditor, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                if (state.isNew) "Produkt hinzufügen" else "Produkt bearbeiten",
                style = MaterialTheme.typography.headlineSmall,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                ProductThumb(state.name, state.image, Modifier.size(56.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (state.barcode.isNotEmpty()) "Barcode ${state.barcode}" else "Noch kein Barcode",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.lookupStatus.isNotEmpty()) {
                        Text(state.lookupStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                FilledTonalButton(onClick = { scanning = ScanMode.BARCODE }) {
                    Icon(painterResource(R.drawable.ic_barcode), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Scannen")
                }
            }

            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text("Produkt") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Mindestens haltbar bis", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { pickingDate = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            state.date?.let { longDate(it) } ?: "Datum wählen",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (state.date == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                FilledTonalButton(onClick = { scanning = ScanMode.DATE }, modifier = Modifier.height(52.dp)) {
                    Icon(painterResource(R.drawable.ic_calendar), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("MHD scannen")
                }
            }
            if (state.ocrStatus.isNotEmpty()) {
                Text(state.ocrStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf("+3 Tage" to 3L, "+1 Woche" to 7L, "+2 Wochen" to 14L, "+1 Monat" to 30L, "+3 Monate" to 91L)
                    .forEach { (label, days) ->
                        AssistChip(
                            onClick = { viewModel.setDate(LocalDate.now().plusDays(days)) },
                            label = { Text(label) },
                            shape = CircleShape,
                        )
                    }
            }

            if (state.barcode.isNotEmpty()) {
                PriceCard(state.prices, onRefresh = viewModel::refreshEditorPrices)
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!state.isNew) {
                    OutlinedButton(
                        onClick = viewModel::deleteFromEditor,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.height(52.dp),
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Löschen")
                    }
                }
                Button(
                    onClick = viewModel::saveEditor,
                    enabled = state.date != null,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    Text("Speichern")
                }
            }
        }
    }

    scanning?.let { mode ->
        ScannerDialog(
            mode = mode,
            onBarcode = {
                viewModel.applyBarcode(it)
                // Without a date yet, go straight on to the best-before date.
                scanning = if (state.date == null) ScanMode.DATE else null
            },
            onDate = {
                scanning = null
                viewModel.applyDateHit(it)
            },
            onDismiss = { scanning = null },
        )
    }

    if (pickingDate) {
        val initial = (state.date ?: LocalDate.now()).toEpochDay() * DAY_MS
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { viewModel.setDate(LocalDate.ofEpochDay(Math.floorDiv(it, DAY_MS))) }
                    pickingDate = false
                }) { Text("Übernehmen") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Abbrechen") } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

private val LONG_DATE = DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy", Locale.GERMAN)

private fun longDate(date: LocalDate): String {
    val today = LocalDate.now()
    val days = java.time.temporal.ChronoUnit.DAYS.between(today, date)
    val suffix = when {
        days == 0L -> " (heute)"
        days == 1L -> " (morgen)"
        days < 0 -> " (abgelaufen)"
        else -> ""
    }
    return date.format(LONG_DATE) + suffix
}

@Composable
private fun PriceCard(prices: PriceState, onRefresh: () -> Unit) {
    val summary = prices.summary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Preise", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onRefresh, enabled = !prices.loading) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Preise aktualisieren")
            }
        }
        when {
            summary == null && prices.loading -> Text("Lade Preise …", style = MaterialTheme.typography.bodyMedium)
            summary == null -> Text(
                "Keine Verbindung – Preise konnten nicht geladen werden.",
                style = MaterialTheme.typography.bodyMedium,
            )
            else -> {
                val several = summary.chains.size > 1
                Chain.entries.forEach { chain ->
                    PriceRow(chain.label, summary.chains[chain], several && summary.bestOverall && summary.best == chain)
                }
                summary.otherMin?.let { other ->
                    PriceRow(
                        "ANDERE",
                        other,
                        false,
                        detail = if (summary.others == 1) "weiterer Laden" else "günstigster von ${summary.others} weiteren Meldungen",
                    )
                }
                Text(priceNote(summary, prices), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun priceNote(summary: PriceSummary, prices: PriceState): String {
    val where = summary.region.label
    val ageMin = ((System.currentTimeMillis() - summary.timestamp) / 60_000).toInt()
    return buildString {
        append(
            if (summary.total > 0) "Zuletzt gemeldete Preise in $where aus Open Prices (von Nutzern gemeldet)."
            else "Für dieses Produkt wurde in $where noch kein Preis gemeldet.",
        )
        if (ageMin > 5) append(" Stand: vor " + if (ageMin < 120) "$ageMin Min." else "${ageMin / 60} Std.")
        if (prices.offline) append(" (offline, gespeicherter Stand)")
        if (prices.loading) append(" Wird aktualisiert …")
    }
}

private val SHORT_DATE = DateTimeFormatter.ofPattern("dd.MM.", Locale.GERMAN)

@Composable
private fun PriceRow(label: String, price: ShopPrice?, best: Boolean, detail: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .widthIn(min = 72.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (best) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (best) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (price == null) {
                Text("noch kein Preis gemeldet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(
                    listOf(price.shop, price.city).filter { it.isNotBlank() }.distinct().joinToString(", ").ifEmpty { "–" },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
                Text(
                    detail ?: ("gemeldet " + shortReportDate(price.date)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (price != null) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    euro(price.price),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (best) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (best) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                price.old?.let {
                    Text(
                        euro(it),
                        style = MaterialTheme.typography.labelSmall,
                        textDecoration = TextDecoration.LineThrough,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            Text("–", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun shortReportDate(iso: String): String = try {
    val d = LocalDate.parse(iso.take(10))
    if (d.year == LocalDate.now().year) d.format(SHORT_DATE) else d.format(DateTimeFormatter.ofPattern("dd.MM.yy"))
} catch (e: Exception) {
    iso
}

// ---------------------------------------------------------------------------------------------
// Settings
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantrySettingsSheet(
    viewModel: PantryViewModel,
    onDismiss: () -> Unit,
    onRequestNotifications: () -> Unit,
    onShareBackup: () -> Unit,
) {
    val context = LocalContext.current
    val settings by viewModel.reminderSettings.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pickingTime by remember { mutableStateOf(false) }

    // Coming back from the system settings may have changed the permission.
    LifecycleResumeEffect(Unit) {
        viewModel.onNotificationPermissionChanged()
        onPauseOrDispose { }
    }

    val openAppSettings: () -> Unit = {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)),
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Vorrat-Einstellungen", style = MaterialTheme.typography.headlineSmall)

            SettingsCard("🔔", "Erinnerung") {
                Text(
                    "Vor Ablauf des Mindesthaltbarkeitsdatums bekommst du eine Benachrichtigung.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Wie früh?", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    (1..PantrySettings.MAX_LEAD_DAYS).forEach { days ->
                        SegmentedButton(
                            selected = settings.leadDays == days,
                            onClick = { viewModel.setLeadDays(days) },
                            shape = SegmentedButtonDefaults.itemShape(days - 1, PantrySettings.MAX_LEAD_DAYS),
                        ) { Text(if (days == 1) "1 Tag" else "$days Tage") }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Uhrzeit", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    FilledTonalButton(onClick = { pickingTime = true }) {
                        Text(String.format(Locale.GERMAN, "%02d:%02d Uhr", settings.hour, settings.minute))
                    }
                }
                if (settings.notificationsEnabled) {
                    Text("✓ Benachrichtigungen sind aktiv", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                } else {
                    Hint("Benachrichtigungen sind ausgeschaltet – so kommt keine Erinnerung an.") {
                        Button(onClick = onRequestNotifications) { Text("Benachrichtigungen erlauben") }
                    }
                }
                if (settings.batteryOptimized) {
                    Hint(
                        "Die Akku-Optimierung ist für diese App aktiv. Samsung kann Erinnerungen dadurch verzögern " +
                            "oder die App „schlafen legen“. In der App-Info unter „Akku“ auf „Nicht eingeschränkt“ stellen.",
                    ) {
                        OutlinedButton(onClick = openAppSettings) { Text("App-Info öffnen") }
                    }
                }
                TextButton(onClick = viewModel::testNotification) { Text("Test-Benachrichtigung senden") }
            }

            SettingsCard("🏷️", "Preise") {
                Text(
                    "Von Nutzern gemeldete Preise bei EDEKA, REWE, LIDL und ALDI (Open Prices).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    PriceRegion.entries.forEachIndexed { index, region ->
                        SegmentedButton(
                            selected = settings.region == region,
                            onClick = { viewModel.setRegion(region) },
                            shape = SegmentedButtonDefaults.itemShape(index, PriceRegion.entries.size),
                        ) { Text(if (region == PriceRegion.MUELHEIM) "Mülheim" else "Deutschland", maxLines = 1) }
                    }
                }
            }

            SettingsCard("💾", "Sicherung") {
                Text(
                    "Exportiert den Vorrat als Text (z. B. an dich selbst schicken). Zum Wiederherstellen den Text " +
                        "kopieren und hier importieren – oder aus der alten MHD-App über „Teilen“ an die Einkaufsliste schicken.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = onShareBackup, modifier = Modifier.weight(1f)) { Text("Exportieren") }
                    OutlinedButton(
                        onClick = {
                            val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
                            val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                            if (text.isNullOrBlank()) viewModel.showMessage("Die Zwischenablage ist leer")
                            else viewModel.importBackup(text)
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Aus Zwischenablage") }
                }
            }
        }
    }

    if (pickingTime) {
        val timeState = rememberTimePickerState(settings.hour, settings.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            title = { Text("Uhrzeit der Erinnerung") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setReminderTime(timeState.hour, timeState.minute)
                    pickingTime = false
                }) { Text("Übernehmen") }
            },
            dismissButton = { TextButton(onClick = { pickingTime = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun SettingsCard(emoji: String, title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        content()
    }
}

@Composable
private fun Hint(text: String, action: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SoonColor.copy(alpha = 0.14f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        action()
    }
}

