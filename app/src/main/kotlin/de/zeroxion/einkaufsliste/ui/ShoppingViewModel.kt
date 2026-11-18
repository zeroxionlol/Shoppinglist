package de.zeroxion.einkaufsliste.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.zeroxion.einkaufsliste.data.Category
import de.zeroxion.einkaufsliste.data.ShoppingItem
import de.zeroxion.einkaufsliste.data.ShoppingRepository
import de.zeroxion.einkaufsliste.parsing.ItemParser
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A group of open products in one supermarket section. */
data class CategoryGroup(val category: Category, val items: List<ShoppingItem>)

data class ShoppingUiState(
    val groups: List<CategoryGroup> = emptyList(),
    val checked: List<ShoppingItem> = emptyList(),
    val openCount: Int = 0,
    val totalCount: Int = 0,
    val showChecked: Boolean = true,
) {
    val isEmpty: Boolean get() = totalCount == 0
    val checkedCount: Int get() = totalCount - openCount
}

/** One-off messages for the snackbar, optionally with an undo action. */
data class UiMessage(val text: String, val undo: List<ShoppingItem> = emptyList())

class ShoppingViewModel(private val repository: ShoppingRepository) : ViewModel() {

    private val showChecked = MutableStateFlow(true)

    val uiState: StateFlow<ShoppingUiState> = combine(repository.items, showChecked) { items, show ->
        val open = items.filter { !it.checked }
        val groups = open.groupBy { it.category }
            .toSortedMap()
            .map { (category, list) -> CategoryGroup(category, list.sortedBy { it.createdAt }) }
        ShoppingUiState(
            groups = groups,
            checked = items.filter { it.checked }.sortedByDescending { it.checkedAt ?: 0L },
            openCount = open.size,
            totalCount = items.size,
            showChecked = show,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShoppingUiState())

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    /** Set when the app was started from the "Per Sprache hinzufügen" shortcut. */
    private val _voiceRequested = MutableStateFlow(false)
    val voiceRequested: StateFlow<Boolean> = _voiceRequested.asStateFlow()

    fun addFromText(text: String, fromSpeech: Boolean = false, source: String? = null) {
        val parsed = ItemParser.parse(text, fromSpeech)
        if (parsed.isEmpty()) {
            if (fromSpeech || source != null) _messages.trySend(UiMessage("Kein Produkt erkannt"))
            return
        }
        viewModelScope.launch {
            val result = repository.add(parsed)
            val msg = when {
                result.count == 1 && result.added.size == 1 -> "„${result.added.first().name}“ hinzugefügt"
                result.count == 1 -> "„${result.updated.first().name}“ ist wieder auf der Liste"
                else -> "${result.count} Produkte hinzugefügt"
            }
            val prefix = if (source != null) "$source: " else ""
            if (fromSpeech || source != null || result.count > 1 || result.updated.isNotEmpty()) {
                _messages.send(UiMessage(prefix + msg))
            }
        }
    }

    fun toggle(item: ShoppingItem) {
        viewModelScope.launch { repository.setChecked(item.id, !item.checked) }
    }

    fun delete(item: ShoppingItem) {
        viewModelScope.launch {
            repository.remove(item.id)?.let {
                _messages.send(UiMessage("„${it.name}“ gelöscht", undo = listOf(it)))
            }
        }
    }

    fun update(item: ShoppingItem, name: String, quantity: String?, category: Category) {
        viewModelScope.launch { repository.update(item.id, name, quantity, category) }
    }

    fun clearChecked() {
        viewModelScope.launch {
            val removed = repository.clearChecked()
            if (removed.isNotEmpty()) {
                _messages.send(UiMessage("${removed.size} erledigte Produkte entfernt", undo = removed))
            }
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            val removed = repository.clearAll()
            if (removed.isNotEmpty()) {
                _messages.send(UiMessage("Liste geleert", undo = removed))
            }
        }
    }

    fun undo(items: List<ShoppingItem>) {
        viewModelScope.launch { repository.restore(items) }
    }

    fun toggleShowChecked() {
        showChecked.value = !showChecked.value
    }

    fun requestVoice() {
        _voiceRequested.value = true
    }

    fun consumeVoiceRequest() {
        _voiceRequested.value = false
    }

    fun showMessage(text: String) {
        _messages.trySend(UiMessage(text))
    }

    /** Plain text version of the open list for sharing via WhatsApp & Co. */
    fun shareText(): String {
        val state = uiState.value
        val sb = StringBuilder("🛒 Einkaufsliste\n")
        state.groups.forEach { group ->
            sb.append("\n").append(group.category.emoji).append(' ').append(group.category.label).append('\n')
            group.items.forEach { sb.append("• ").append(it.displayText).append('\n') }
        }
        return sb.toString().trim()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY]!!
                ShoppingViewModel(ShoppingRepository.get(app))
            }
        }
    }
}
