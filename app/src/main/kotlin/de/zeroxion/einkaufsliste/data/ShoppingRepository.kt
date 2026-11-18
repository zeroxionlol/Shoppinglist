package de.zeroxion.einkaufsliste.data

import android.content.Context
import de.zeroxion.einkaufsliste.parsing.ParsedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.util.Locale
import java.util.UUID

/**
 * Single source of truth for the list. Shared by the UI and the Gemini AppFunction service
 * (same process), so changes made by Gemini show up live in the open app.
 */
class ShoppingRepository private constructor(private val file: File) {

    private val mutex = Mutex()
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<ShoppingItem>> = _items.asStateFlow()

    /** Result of adding products: what is new and what already was on the list. */
    data class AddResult(val added: List<ShoppingItem>, val updated: List<ShoppingItem>) {
        val all: List<ShoppingItem> get() = added + updated
        val count: Int get() = added.size + updated.size
    }

    suspend fun add(parsed: List<ParsedItem>): AddResult = mutate { list ->
        val added = mutableListOf<ShoppingItem>()
        val updated = mutableListOf<ShoppingItem>()
        val now = System.currentTimeMillis()
        for (p in parsed) {
            val index = list.indexOfFirst { it.name.equals(p.name, ignoreCase = true) }
            if (index >= 0) {
                // Already known: put it back on the open list and take the new quantity.
                val existing = list[index]
                val merged = existing.copy(
                    checked = false,
                    checkedAt = null,
                    quantity = p.quantity ?: existing.quantity,
                )
                list[index] = merged
                updated += merged
            } else {
                val item = ShoppingItem(
                    id = UUID.randomUUID().toString(),
                    name = p.name,
                    quantity = p.quantity,
                    category = Category.guess(p.name),
                    checked = false,
                    createdAt = now + added.size,
                )
                list += item
                added += item
            }
        }
        AddResult(added, updated)
    }

    suspend fun setChecked(id: String, checked: Boolean) = mutate { list ->
        val i = list.indexOfFirst { it.id == id }
        if (i >= 0) {
            list[i] = list[i].copy(checked = checked, checkedAt = if (checked) System.currentTimeMillis() else null)
        }
    }

    suspend fun update(id: String, name: String, quantity: String?, category: Category) = mutate { list ->
        val i = list.indexOfFirst { it.id == id }
        if (i >= 0 && name.isNotBlank()) {
            list[i] = list[i].copy(name = name.trim(), quantity = quantity?.trim()?.ifBlank { null }, category = category)
        }
    }

    suspend fun remove(id: String): ShoppingItem? = mutate { list ->
        val i = list.indexOfFirst { it.id == id }
        if (i >= 0) list.removeAt(i) else null
    }

    suspend fun restore(items: List<ShoppingItem>) = mutate { list ->
        val ids = list.map { it.id }.toSet()
        list += items.filter { it.id !in ids }
    }

    suspend fun clearChecked(): List<ShoppingItem> = mutate { list ->
        val removed = list.filter { it.checked }
        list.removeAll { it.checked }
        removed
    }

    suspend fun clearAll(): List<ShoppingItem> = mutate { list ->
        val removed = list.toList()
        list.clear()
        removed
    }

    /** Finds the best match for a spoken product name ("milch" finds "Vollmilch"). */
    fun findByName(name: String, includeChecked: Boolean = true): ShoppingItem? {
        val query = name.trim().lowercase(Locale.GERMAN)
        if (query.isEmpty()) return null
        val candidates = _items.value.filter { includeChecked || !it.checked }
            .sortedBy { it.checked } // prefer open items
        return candidates.firstOrNull { it.name.lowercase(Locale.GERMAN) == query }
            ?: candidates.firstOrNull { it.name.lowercase(Locale.GERMAN).contains(query) }
            ?: candidates.firstOrNull { query.contains(it.name.lowercase(Locale.GERMAN)) }
    }

    private suspend fun <T> mutate(block: (MutableList<ShoppingItem>) -> T): T = mutex.withLock {
        val list = _items.value.toMutableList()
        val result = block(list)
        _items.value = list.toList()
        withContext(Dispatchers.IO) { save(list) }
        result
    }

    private fun load(): List<ShoppingItem> = try {
        if (!file.exists()) {
            emptyList()
        } else {
            val array = JSONArray(file.readText())
            (0 until array.length()).map { ShoppingItem.fromJson(array.getJSONObject(it)) }
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun save(list: List<ShoppingItem>) {
        val array = JSONArray()
        list.forEach { array.put(it.toJson()) }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(array.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(array.toString())
            tmp.delete()
        }
    }

    companion object {
        @Volatile
        private var instance: ShoppingRepository? = null

        fun get(context: Context): ShoppingRepository =
            instance ?: synchronized(this) {
                instance ?: ShoppingRepository(File(context.applicationContext.filesDir, "einkaufsliste.json"))
                    .also { instance = it }
            }
    }
}
