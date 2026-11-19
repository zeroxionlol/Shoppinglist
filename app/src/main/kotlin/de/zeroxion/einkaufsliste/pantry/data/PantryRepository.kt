package de.zeroxion.einkaufsliste.pantry.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Pantry products with best-before dates. Shared by the UI and the reminder receiver. */
class PantryRepository private constructor(private val file: File) {

    private val mutex = Mutex()
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<PantryItem>> = _items.asStateFlow()

    /** Adds the item or replaces the one with the same id. */
    suspend fun upsert(item: PantryItem) = mutate { list ->
        val i = list.indexOfFirst { it.id == item.id }
        if (i >= 0) list[i] = item else list += item
    }

    suspend fun remove(id: String): PantryItem? = mutate { list ->
        val i = list.indexOfFirst { it.id == id }
        if (i >= 0) list.removeAt(i) else null
    }

    suspend fun addAll(items: List<PantryItem>) = mutate { list -> list += items }

    private suspend fun <T> mutate(block: (MutableList<PantryItem>) -> T): T = mutex.withLock {
        val list = _items.value.toMutableList()
        val result = block(list)
        _items.value = list.toList()
        withContext(Dispatchers.IO) { save(list) }
        result
    }

    private fun load(): List<PantryItem> = try {
        if (file.exists()) PantryJson.parseList(file.readText()) else emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    private fun save(list: List<PantryItem>) {
        val json = PantryJson.toJsonArray(list)
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json)
        if (!tmp.renameTo(file)) {
            file.writeText(json)
            tmp.delete()
        }
    }

    companion object {
        @Volatile
        private var instance: PantryRepository? = null

        fun get(context: Context): PantryRepository =
            instance ?: synchronized(this) {
                instance ?: PantryRepository(File(context.applicationContext.filesDir, "vorrat.json"))
                    .also { instance = it }
            }
    }
}
