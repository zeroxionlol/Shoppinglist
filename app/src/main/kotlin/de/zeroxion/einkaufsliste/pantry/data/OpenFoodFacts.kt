package de.zeroxion.einkaufsliste.pantry.data

import android.content.Context
import de.zeroxion.einkaufsliste.pantry.logic.Chain
import de.zeroxion.einkaufsliste.pantry.logic.PriceLocation
import de.zeroxion.einkaufsliste.pantry.logic.PriceRegion
import de.zeroxion.einkaufsliste.pantry.logic.PriceReport
import de.zeroxion.einkaufsliste.pantry.logic.PriceSummary
import de.zeroxion.einkaufsliste.pantry.logic.ShopPrice
import de.zeroxion.einkaufsliste.pantry.logic.productLabel
import de.zeroxion.einkaufsliste.pantry.logic.safeImageUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Open Food Facts: product name for a barcode and user-reported prices (Open Prices). */
object OpenFoodFacts {
    private const val USER_AGENT = "Einkaufsliste-Android/1.0 (github.com/zeroxionlol/Shoppinglist)"

    data class Product(val name: String, val image: String)

    /** @return null if the barcode is unknown. @throws IOException without a connection. */
    suspend fun lookup(code: String): Product? = withContext(Dispatchers.IO) {
        val json = getJson(
            "https://world.openfoodfacts.org/api/v2/product/${enc(code)}.json" +
                "?fields=product_name,product_name_de,generic_name_de,brands,quantity,image_front_small_url,image_small_url",
            9_000,
        )
        val p = json.optJSONObject("product")
        if (json.optInt("status") != 1 || p == null) return@withContext null
        Product(
            name = productLabel(
                p.str("product_name_de"), p.str("product_name"), p.str("generic_name_de"),
                p.str("brands"), p.str("quantity"),
            ),
            image = safeImageUrl(p.str("image_front_small_url") ?: p.str("image_small_url")),
        )
    }

    /** Newest prices per chain for [code] in [region]. @throws IOException without a connection. */
    suspend fun prices(code: String, region: PriceRegion): PriceSummary = withContext(Dispatchers.IO) {
        val area = if (region.lat != null) "&lat=${region.lat}&lon=${region.lon}&radius_km=${region.radiusKm}" else ""
        val json = getJson(
            "https://prices.openfoodfacts.org/api/v1/prices?product_code=${enc(code)}" +
                "&currency=EUR&order_by=-date&size=100$area",
            10_000,
        )
        val items = json.optJSONArray("items")
        val reports = (0 until (items?.length() ?: 0)).mapNotNull { i ->
            val p = items!!.optJSONObject(i) ?: return@mapNotNull null
            val loc = p.optJSONObject("location") ?: JSONObject()
            PriceReport(
                price = p.num("price"),
                date = p.str("date"),
                discounted = p.optBoolean("price_is_discounted", false),
                priceWithoutDiscount = p.num("price_without_discount"),
                location = PriceLocation(
                    type = loc.str("type"),
                    brand = loc.str("osm_brand"),
                    name = loc.str("osm_name"),
                    city = loc.str("osm_address_city"),
                    countryCode = loc.str("osm_address_country_code"),
                    lat = loc.num("osm_lat"),
                    lon = loc.num("osm_lon"),
                ),
            )
        }
        PriceSummary.summarize(reports, region)
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun JSONObject.str(key: String): String? =
        if (isNull(key)) null else optString(key).trim().ifEmpty { null }

    private fun JSONObject.num(key: String): Double? =
        if (isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }

    private fun getJson(url: String, timeoutMs: Int): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }
}

/** Stores price summaries per barcode (also offline), so the list can show them right away. */
class PriceCache private constructor(private val file: File) {
    private val cache: MutableMap<String, PriceSummary> = load()

    @Synchronized
    fun get(code: String, region: PriceRegion): PriceSummary? = cache[code]?.takeIf { it.region == region }

    @Synchronized
    fun put(code: String, summary: PriceSummary) {
        cache[code] = summary
        save()
    }

    /** Removes outdated prices of products that are no longer in the pantry. */
    @Synchronized
    fun prune(codesInUse: Set<String>) {
        val removed = cache.keys.removeAll { it !in codesInUse && !cache.getValue(it).isFresh() }
        if (removed) save()
    }

    private fun load(): MutableMap<String, PriceSummary> = try {
        val json = JSONObject(file.readText())
        json.keys().asSequence().mapNotNull { code -> fromJson(json.optJSONObject(code))?.let { code to it } }
            .toMap(mutableMapOf())
    } catch (e: Exception) {
        mutableMapOf()
    }

    private fun save() {
        val json = JSONObject()
        cache.forEach { (code, s) -> json.put(code, toJson(s)) }
        try {
            file.writeText(json.toString())
        } catch (e: IOException) {
            // cache only – nothing lost
        }
    }

    private fun shopToJson(p: ShopPrice) = JSONObject()
        .put("price", p.price).put("old", p.old ?: JSONObject.NULL)
        .put("date", p.date).put("city", p.city).put("shop", p.shop)

    private fun shopFromJson(j: JSONObject?): ShopPrice? = j?.let {
        ShopPrice(
            price = it.optDouble("price").takeIf { v -> !v.isNaN() } ?: return null,
            old = if (it.isNull("old")) null else it.optDouble("old").takeIf { v -> !v.isNaN() },
            date = it.optString("date"),
            city = it.optString("city"),
            shop = it.optString("shop"),
        )
    }

    private fun toJson(s: PriceSummary): JSONObject {
        val chains = JSONObject()
        s.chains.forEach { (chain, p) -> chains.put(chain.name, shopToJson(p)) }
        return JSONObject()
            .put("ts", s.timestamp).put("region", s.region.name).put("chains", chains)
            .put("others", s.others).put("otherMin", s.otherMin?.let(::shopToJson) ?: JSONObject.NULL)
            .put("total", s.total).put("best", s.best?.name ?: JSONObject.NULL).put("bestOverall", s.bestOverall)
    }

    private fun fromJson(j: JSONObject?): PriceSummary? {
        if (j == null) return null
        val chainsJson = j.optJSONObject("chains") ?: JSONObject()
        val chains = linkedMapOf<Chain, ShopPrice>()
        for (key in chainsJson.keys()) {
            val chain = Chain.fromName(key) ?: continue
            shopFromJson(chainsJson.optJSONObject(key))?.let { chains[chain] = it }
        }
        return PriceSummary(
            timestamp = j.optLong("ts"),
            region = PriceRegion.fromName(j.optString("region")),
            chains = chains,
            others = j.optInt("others"),
            otherMin = shopFromJson(j.optJSONObject("otherMin")),
            total = j.optInt("total"),
            best = Chain.fromName(if (j.isNull("best")) null else j.optString("best")),
            bestOverall = j.optBoolean("bestOverall"),
        )
    }

    companion object {
        @Volatile
        private var instance: PriceCache? = null

        fun get(context: Context): PriceCache =
            instance ?: synchronized(this) {
                instance ?: PriceCache(File(context.applicationContext.filesDir, "preise.json")).also { instance = it }
            }
    }
}
