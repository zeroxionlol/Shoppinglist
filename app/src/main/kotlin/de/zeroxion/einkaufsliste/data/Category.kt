package de.zeroxion.einkaufsliste.data

import java.util.Locale

/**
 * Supermarket sections. Order = order on the list (roughly the walk through a German supermarket).
 */
enum class Category(
    val label: String,
    val emoji: String,
    val color: Long,
    keywords: List<String>,
) {
    PRODUCE(
        "Obst & Gemüse", "🥦", 0xFF2E9E5B,
        listOf(
            "obst", "gemüse", "apfel", "äpfel", "banane", "birne", "tomate", "tomaten", "gurke", "salat",
            "paprika", "zwiebel", "kartoffel", "karotte", "möhre", "möhren", "zucchini", "brokkoli",
            "blumenkohl", "spinat", "beeren", "erdbeere", "erdbeeren", "himbeere", "himbeeren",
            "heidelbeere", "heidelbeeren", "trauben", "zitrone", "zitronen", "limette", "orange", "orangen",
            "mandarine", "mandarinen", "kiwi", "mango", "ananas", "avocado", "knoblauch", "pilze",
            "champignons", "lauch", "porree", "sellerie", "kohl", "radieschen", "ingwer", "kräuter",
            "petersilie", "schnittlauch", "basilikum", "melone", "pfirsich", "kirschen", "aubergine",
            "kürbis", "rucola", "fenchel", "spargel", "frühlingszwiebeln", "mais", "nektarine", "pflaume",
        ),
    ),
    BAKERY(
        "Brot & Gebäck", "🥐", 0xFFC98A2B,
        listOf(
            "brot", "brötchen", "semmel", "semmeln", "toast", "baguette", "croissant", "brezel", "brezeln",
            "laugen", "kuchen", "wraps", "tortilla", "tortillas", "knäckebrot", "zwieback", "muffins",
        ),
    ),
    DAIRY(
        "Milch, Käse & Eier", "🧀", 0xFFE0B020,
        listOf(
            "milch", "käse", "joghurt", "jogurt", "quark", "butter", "sahne", "schmand", "frischkäse",
            "mozzarella", "gouda", "parmesan", "feta", "eier", "ei", "margarine", "skyr", "kefir",
            "buttermilch", "pudding", "creme fraiche", "crème fraîche", "hafermilch", "mascarpone",
        ),
    ),
    MEAT(
        "Fleisch & Fisch", "🥩", 0xFFD0503C,
        listOf(
            "fleisch", "hack", "hackfleisch", "wurst", "würstchen", "schinken", "salami", "hähnchen",
            "huhn", "hühnchen", "pute", "steak", "schnitzel", "speck", "bacon", "lachs", "thunfisch",
            "fisch", "garnelen", "rind", "schwein", "aufschnitt", "frikadellen", "bratwurst", "chicken",
            "filet", "gulasch", "leberkäse", "geflügel",
        ),
    ),
    FROZEN(
        "Tiefkühl", "🧊", 0xFF3D8FD6,
        listOf(
            "tiefkühl", "tk", "pizza", "eis", "pommes", "fischstäbchen", "eiswürfel", "tiefkühlpizza",
            "kroketten", "rahmspinat",
        ),
    ),
    PANTRY(
        "Vorrat & Konserven", "🥫", 0xFF9C6B3F,
        listOf(
            "nudeln", "pasta", "spaghetti", "penne", "reis", "mehl", "zucker", "salz", "pfeffer", "öl",
            "olivenöl", "essig", "ketchup", "senf", "mayo", "mayonnaise", "soße", "sauce", "gewürz",
            "gewürze", "brühe", "konserve", "bohnen", "linsen", "kichererbsen", "tomatenmark",
            "passierte", "haferflocken", "müsli", "cornflakes", "honig", "marmelade", "nutella",
            "aufstrich", "hefe", "backpulver", "vanillezucker", "grieß", "couscous", "dosentomaten",
            "pesto", "erdnussbutter", "lasagneplatten", "puderzucker", "stärke",
        ),
    ),
    SNACKS(
        "Süßes & Snacks", "🍫", 0xFFB3478F,
        listOf(
            "schokolade", "schoko", "chips", "kekse", "keks", "gummibärchen", "bonbons", "nüsse", "nuss",
            "popcorn", "riegel", "süßigkeiten", "cracker", "salzstangen", "flips", "kaugummi", "waffeln",
            "lakritz",
        ),
    ),
    DRINKS(
        "Getränke", "🥤", 0xFF1E9BB0,
        listOf(
            "wasser", "saft", "cola", "limo", "limonade", "bier", "wein", "sekt", "kaffee", "tee",
            "sprudel", "schorle", "fanta", "sprite", "energy", "smoothie", "kakao", "eistee", "mineralwasser",
            "kaffeebohnen", "kaffeepads", "kapseln",
        ),
    ),
    DRUGSTORE(
        "Drogerie", "🧴", 0xFF7D5BD6,
        listOf(
            "shampoo", "duschgel", "seife", "zahnpasta", "zahncreme", "zahnbürste", "deo", "creme",
            "windeln", "feuchttücher", "taschentücher", "klopapier", "toilettenpapier", "rasierer",
            "tampons", "binden", "pflaster", "sonnencreme", "spülung", "wattepads", "bodylotion",
            "handcreme", "zahnseide", "haargel",
        ),
    ),
    HOUSEHOLD(
        "Haushalt", "🧽", 0xFF5C7A8A,
        listOf(
            "spülmittel", "waschmittel", "müllbeutel", "müllsäcke", "alufolie", "frischhaltefolie",
            "backpapier", "schwamm", "schwämme", "küchenrolle", "reiniger", "putzmittel", "batterien",
            "glühbirne", "kerzen", "servietten", "spülmaschinentabs", "tabs", "weichspüler", "entkalker",
            "gefrierbeutel", "geschirrspülmittel",
        ),
    ),
    OTHER("Sonstiges", "🛒", 0xFF6F7B74, emptyList());

    private val keywordSet: List<String> = keywords.map { it.lowercase(Locale.GERMAN) }

    companion object {
        private val tokenSplit = Regex("[^\\p{L}]+")

        /**
         * Guesses the supermarket section from a German product name.
         * German compounds carry their meaning at the end ("Vollmilch", "Hackfleisch"),
         * so a match at the end of a word scores higher than one at the beginning.
         */
        fun guess(name: String): Category {
            val lower = name.lowercase(Locale.GERMAN).trim()
            if (lower.isEmpty()) return OTHER
            val tokens = lower.split(tokenSplit).filter { it.isNotEmpty() }
            var best = OTHER
            var bestScore = 0
            for (category in entries) {
                for (kw in category.keywordSet) {
                    val score = when {
                        kw.contains(' ') -> if (lower.contains(kw)) 4 else 0
                        tokens.any { it == kw } -> 4
                        kw.length < 4 -> 0
                        tokens.any { it.endsWith(kw) } -> 3
                        tokens.any { it.startsWith(kw) } -> 1
                        else -> 0
                    }
                    if (score > bestScore) {
                        bestScore = score
                        best = category
                    }
                }
            }
            return best
        }

        fun fromName(name: String?): Category =
            entries.firstOrNull { it.name == name } ?: OTHER
    }
}
