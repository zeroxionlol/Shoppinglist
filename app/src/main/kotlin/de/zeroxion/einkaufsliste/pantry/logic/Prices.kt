package de.zeroxion.einkaufsliste.pantry.logic

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Supermarket chains whose prices are shown. */
enum class Chain(val label: String, private val match: Regex) {
    EDEKA("EDEKA", Regex("edeka|e[ -]?center|marktkauf|nah ?(&|und) ?gut")),
    REWE("REWE", Regex("rewe")),
    LIDL("LIDL", Regex("lidl")),
    ALDI("ALDI", Regex("aldi"));

    companion object {
        fun of(location: PriceLocation): Chain? {
            val s = "${location.brand.orEmpty()} ${location.name.orEmpty()}".lowercase()
            return entries.firstOrNull { it.match.containsMatchIn(s) }
        }

        fun fromName(name: String?): Chain? = entries.firstOrNull { it.name == name }
    }
}

enum class PriceRegion(
    val label: String,
    val lat: Double? = null,
    val lon: Double? = null,
    val radiusKm: Int = 0,
    private val city: Regex? = null,
    private val notCity: Regex? = null,
) {
    MUELHEIM(
        "Mülheim an der Ruhr", 51.4275, 6.8825, 9,
        Regex("m(ü|ue|u)lheim", RegexOption.IGNORE_CASE),
        Regex("k(ö|oe)ln|main|baden|kärlich", RegexOption.IGNORE_CASE),
    ),
    GERMANY("ganz Deutschland");

    /** Is the shop in this region? Checks the radius and – if known – the city name. */
    fun contains(location: PriceLocation): Boolean {
        val cityRegex = city ?: return true
        val lat = location.lat
        val lon = location.lon
        val hasPos = lat != null && lon != null
        if (hasPos && distanceKm(this.lat!!, this.lon!!, lat, lon) > radiusKm) return false
        val cityName = location.city.orEmpty()
        if (cityName.isNotEmpty()) {
            return cityRegex.containsMatchIn(cityName) && notCity?.containsMatchIn(cityName) != true
        }
        return hasPos // without a city only if the coordinate lies inside the radius
    }

    companion object {
        fun fromName(name: String?): PriceRegion = entries.firstOrNull { it.name == name } ?: MUELHEIM

        fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val toRad = Math.PI / 180
            val dLat = (lat2 - lat1) * toRad
            val dLon = (lon2 - lon1) * toRad
            val a = sin(dLat / 2).pow(2) + cos(lat1 * toRad) * cos(lat2 * toRad) * sin(dLon / 2).pow(2)
            return 2 * 6371 * asin(sqrt(a))
        }
    }
}

/** Shop of a price report (OpenStreetMap data from Open Prices). */
data class PriceLocation(
    val type: String? = "OSM",
    val brand: String? = null,
    val name: String? = null,
    val city: String? = null,
    val countryCode: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
)

/** One price report as delivered by Open Prices (newest first). */
data class PriceReport(
    val price: Double?,
    val date: String?,
    val discounted: Boolean = false,
    val priceWithoutDiscount: Double? = null,
    val location: PriceLocation = PriceLocation(),
)

data class ShopPrice(
    val price: Double,
    /** Regular price if the reported price was a special offer. */
    val old: Double?,
    val date: String,
    val city: String,
    val shop: String,
)

/** Condensed prices of one product: newest price per chain and the cheapest other shop. */
data class PriceSummary(
    val timestamp: Long,
    val region: PriceRegion,
    val chains: Map<Chain, ShopPrice>,
    val others: Int,
    val otherMin: ShopPrice?,
    val total: Int,
    val best: Chain?,
    /** True if the best chain is also not beaten by any other shop. */
    val bestOverall: Boolean,
) {
    val bestPrice: ShopPrice? get() = best?.let { chains[it] }

    fun isFresh(now: Long = System.currentTimeMillis()) = now - timestamp < TTL_MS

    companion object {
        const val TTL_MS = 12L * 3600 * 1000

        fun summarize(list: List<PriceReport>, region: PriceRegion, now: Long = System.currentTimeMillis()): PriceSummary {
            val chains = linkedMapOf<Chain, ShopPrice>()
            var others = 0
            var otherMin: ShopPrice? = null
            var total = 0
            for (p in list) {
                val loc = p.location
                val country = loc.countryCode.orEmpty().uppercase()
                if (if (country.isNotEmpty()) country != "DE" else loc.type != "OSM") continue
                if (!region.contains(loc)) continue
                val price = p.price ?: continue
                if (price.isNaN() || p.date.isNullOrBlank()) continue
                total++
                val old = if (p.discounted) p.priceWithoutDiscount else null
                val entry = ShopPrice(
                    price = price,
                    old = old?.takeIf { it > price },
                    date = p.date.take(10),
                    city = loc.city ?: if (region == PriceRegion.GERMANY) "" else region.label,
                    shop = loc.brand ?: loc.name ?: "",
                )
                val chain = Chain.of(loc)
                if (chain != null) {
                    if (chain !in chains) chains[chain] = entry // first hit = newest
                } else {
                    others++
                    if (otherMin == null || price < otherMin.price) otherMin = entry
                }
            }
            val best = chains.minByOrNull { it.value.price }?.key
            val bestOverall = best != null && (otherMin == null || chains.getValue(best).price <= otherMin.price)
            return PriceSummary(now, region, chains, others, otherMin, total, best, bestOverall)
        }
    }
}

/** "1,39 €" */
fun euro(value: Double): String = String.format(java.util.Locale.GERMANY, "%.2f €", value)

/** Readable product name from Open Food Facts fields: "Brand Name (quantity)". */
fun productLabel(nameDe: String?, name: String?, genericDe: String?, brands: String?, quantity: String?): String {
    var label = listOf(nameDe, name, genericDe).firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()
    val brand = brands.orEmpty().split(',').first().trim()
    if (brand.isNotEmpty() && !label.lowercase().contains(brand.lowercase())) label = "$brand $label"
    if (label.isNotBlank() && !quantity.isNullOrBlank()) label += " (${quantity.trim()})"
    return label.trim()
}

/** Only lets https image addresses through, otherwise "". */
fun safeImageUrl(url: String?): String {
    val s = url.orEmpty().trim()
    if (!Regex("^https://[a-z0-9.-]+/", RegexOption.IGNORE_CASE).containsMatchIn(s)) return ""
    if (Regex("""['"()\\\s<>]""").containsMatchIn(s) || s.length > 500) return ""
    return s
}

/**
 * Name for the shopping list: without a trailing pack size like "(500 g)", since the
 * shopping list has its own amount.
 */
fun shoppingName(productName: String): String =
    productName.replace(Regex("""\s*\([^()]*\)\s*$"""), "").trim().ifEmpty { productName.trim() }
