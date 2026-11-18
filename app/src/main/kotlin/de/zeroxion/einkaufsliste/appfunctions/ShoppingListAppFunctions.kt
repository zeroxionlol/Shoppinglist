package de.zeroxion.einkaufsliste.appfunctions

import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunctionDeclaration
import androidx.appfunctions.AppFunctionElementNotFoundException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import de.zeroxion.einkaufsliste.data.ShoppingItem
import de.zeroxion.einkaufsliste.data.ShoppingRepository
import de.zeroxion.einkaufsliste.parsing.ItemParser
import de.zeroxion.einkaufsliste.parsing.ParsedItem

/**
 * Lets Gemini (and other assistants with the EXECUTE_APP_FUNCTIONS permission) use the shopping list.
 * The KSP compiler generates the concrete `ShoppingListAppFunctionService` from this class.
 */
@RequiresApi(36)
@AppFunctionServiceEntryPoint(
    serviceName = "ShoppingListAppFunctionService",
    appFunctionXmlFileName = "shopping_list_app_function_service",
)
abstract class BaseShoppingListAppFunctionService : AppFunctionService() {

    private val repository: ShoppingRepository
        get() = ShoppingRepository.get(applicationContext)

    /**
     * Adds one or more products to the user's shopping list (Einkaufsliste).
     * Use this whenever the user wants to put something on the shopping list, e.g.
     * "Setz Milch und Brot auf die Einkaufsliste".
     * Products that are already on the list are not duplicated; they are marked as open again.
     *
     * @param products The products to add, exactly one entry per product. Keep amounts and units
     *   inside the entry and keep the user's language, e.g. "2 Liter Milch", "6 Eier", "Brot".
     * @return The entries of the shopping list that were added or updated.
     * @throws AppFunctionInvalidArgumentException If no product was given. Ask the user what to add.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun addItems(products: List<String>): List<ShoppingListEntry> {
        val parsed = products
            .filter { it.isNotBlank() }
            .map { ItemParser.parseFragment(it.trim()) ?: ParsedItem(it.trim(), null) }
        if (parsed.isEmpty()) {
            throw AppFunctionInvalidArgumentException("No products given. Ask the user what to add.")
        }
        return repository.add(parsed).all.map { it.toEntry() }
    }

    /**
     * Returns the products on the user's shopping list (Einkaufsliste), grouped by supermarket section.
     * Use this to answer questions like "Was steht auf meiner Einkaufsliste?".
     *
     * @param includeChecked Whether products that were already bought (checked off) should be included.
     * @return The products on the list. An empty list means the shopping list is empty.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun getShoppingList(includeChecked: Boolean = false): List<ShoppingListEntry> =
        repository.items.value
            .filter { includeChecked || !it.checked }
            .sortedWith(compareBy<ShoppingItem>({ it.checked }, { it.category.ordinal }, { it.createdAt }))
            .map { it.toEntry() }

    /**
     * Marks a product on the shopping list as bought (checks it off).
     *
     * @param productName Name of the product as the user said it, e.g. "Milch".
     * @return The updated entry.
     * @throws AppFunctionElementNotFoundException If the product is not on the list. Tell the user and
     *   offer to read the list with "getShoppingList".
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun checkOffItem(productName: String): ShoppingListEntry {
        val item = repository.findByName(productName, includeChecked = false)
            ?: throw AppFunctionElementNotFoundException("'$productName' is not on the shopping list.")
        repository.setChecked(item.id, true)
        return item.copy(checked = true).toEntry()
    }

    /**
     * Removes a product from the shopping list completely.
     *
     * @param productName Name of the product as the user said it, e.g. "Brot".
     * @return A short confirmation for the user.
     * @throws AppFunctionElementNotFoundException If the product is not on the list.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun removeItem(productName: String): String {
        val item = repository.findByName(productName)
            ?: throw AppFunctionElementNotFoundException("'$productName' is not on the shopping list.")
        repository.remove(item.id)
        return "'${item.name}' was removed from the shopping list."
    }

    /**
     * Removes all products that were already bought (checked off) from the shopping list.
     *
     * @return A short confirmation for the user, including how many products were removed.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun clearCheckedItems(): String {
        val removed = repository.clearChecked()
        return "${removed.size} bought products were removed from the shopping list."
    }

    private fun ShoppingItem.toEntry() = ShoppingListEntry(
        id = id,
        name = name,
        quantity = quantity,
        category = category.label,
        isChecked = checked,
    )
}

/** One product on the shopping list. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class ShoppingListEntry(
    /** Unique ID of the entry. */
    val id: String,
    /** Product name, e.g. "Milch". */
    val name: String,
    /** Amount with unit, e.g. "2 l" or "6". Null if no amount was given. */
    val quantity: String?,
    /** Supermarket section, e.g. "Obst & Gemüse". */
    val category: String,
    /** True if the product was already bought (checked off). */
    val isChecked: Boolean,
)
