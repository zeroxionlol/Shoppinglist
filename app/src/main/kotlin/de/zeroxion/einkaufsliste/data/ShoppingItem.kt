package de.zeroxion.einkaufsliste.data

import org.json.JSONObject

data class ShoppingItem(
    val id: String,
    val name: String,
    val quantity: String?,
    val category: Category,
    val checked: Boolean,
    val createdAt: Long,
    val checkedAt: Long? = null,
) {
    /** "2 l Milch" – used for sharing and for Gemini answers. */
    val displayText: String
        get() = if (quantity.isNullOrBlank()) name else "$quantity $name"

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("quantity", quantity ?: JSONObject.NULL)
        put("category", category.name)
        put("checked", checked)
        put("createdAt", createdAt)
        put("checkedAt", checkedAt ?: JSONObject.NULL)
    }

    companion object {
        fun fromJson(json: JSONObject): ShoppingItem = ShoppingItem(
            id = json.getString("id"),
            name = json.getString("name"),
            quantity = json.optString("quantity").takeUnless { json.isNull("quantity") || it.isBlank() },
            category = Category.fromName(json.optString("category")),
            checked = json.optBoolean("checked", false),
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            checkedAt = if (json.isNull("checkedAt")) null else json.optLong("checkedAt"),
        )
    }
}
