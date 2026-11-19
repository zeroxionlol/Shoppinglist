package de.zeroxion.einkaufsliste.data

import org.json.JSONObject

data class ShoppingItem(
    val id: String,
    val name: String,
    val amount: Double = 1.0,
    val unit: String? = null,
    val category: Category,
    val checked: Boolean,
    val createdAt: Long,
    val checkedAt: Long? = null,
) {
    /** True if nothing beyond "1 piece" was specified. */
    val isDefaultAmount: Boolean
        get() = amount == 1.0 && unit.isNullOrBlank()

    /** "500 g", "3 Packungen", "2" – or null for a plain single product. */
    val quantity: String?
        get() = if (isDefaultAmount) null else Quantity.format(amount, unit)

    /** "2× Milch", "500 g Hackfleisch" – used for sharing. */
    val displayText: String
        get() = when {
            isDefaultAmount -> name
            unit.isNullOrBlank() -> "${Quantity.formatNumber(amount)}× $name"
            else -> "${Quantity.format(amount, unit)} $name"
        }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("amount", amount)
        put("unit", unit ?: JSONObject.NULL)
        put("category", category.name)
        put("checked", checked)
        put("createdAt", createdAt)
        put("checkedAt", checkedAt ?: JSONObject.NULL)
    }

    companion object {
        fun fromJson(json: JSONObject): ShoppingItem {
            var amount = 1.0
            var unit: String? = null
            if (json.has("amount")) {
                amount = json.optDouble("amount", 1.0).takeIf { !it.isNaN() && it > 0 } ?: 1.0
                unit = if (json.isNull("unit")) null else json.optString("unit").ifBlank { null }
            } else if (json.has("quantity") && !json.isNull("quantity")) {
                // Data from version 1.0.1: free text like "2 l" or "6".
                Quantity.parse(json.optString("quantity"))?.let { (a, u) ->
                    amount = a
                    unit = u
                }
            }
            return ShoppingItem(
                id = json.getString("id"),
                name = json.getString("name"),
                amount = amount,
                unit = unit,
                category = Category.fromName(json.optString("category")),
                checked = json.optBoolean("checked", false),
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                checkedAt = if (json.isNull("checkedAt")) null else json.optLong("checkedAt"),
            )
        }
    }
}
