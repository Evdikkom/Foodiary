package com.example.foodiary.data.local.seed

import com.example.foodiary.data.local.entity.AllergenEntity
import com.example.foodiary.data.model.AllergenPresenceType

object AllergenCatalog {

    const val MILK = "milk"
    const val EGGS = "eggs"
    const val FISH = "fish"
    const val SHELLFISH = "shellfish"
    const val MOLLUSCS = "molluscs"
    const val PEANUTS = "peanuts"
    const val TREE_NUTS = "tree_nuts"
    const val SOY = "soy"
    const val GLUTEN = "gluten"
    const val SESAME = "sesame"
    const val MUSTARD = "mustard"
    const val CELERY = "celery"
    const val LUPIN = "lupin"
    const val SULPHITES = "sulphites"
    const val CITRUS = "citrus"
    const val SWEETS = "sweets"
    const val HONEY = "honey"
    const val COCOA = "cocoa"
    const val STRAWBERRIES = "strawberries"
    const val NIGHTSHADES = "nightshades"
    const val CORN = "corn"

    data class InferenceHit(
        val allergenId: String,
        val confidence: Double,
    )

    private data class NameProfile(
        val allergenId: String,
        val confidence: Double,
        val keywords: List<String>,
    )

    val allergens: List<AllergenEntity> = listOf(
        AllergenEntity(MILK, "MILK", "Молоко", "Молочные продукты и белки молока", 10),
        AllergenEntity(EGGS, "EGGS", "Яйца", "Яичные продукты и блюда на их основе", 20),
        AllergenEntity(FISH, "FISH", "Рыба", "Рыба и рыбные продукты", 30),
        AllergenEntity(SHELLFISH, "CRUSTACEANS", "Ракообразные", "Креветки, крабы, лобстеры и другие ракообразные", 40),
        AllergenEntity(MOLLUSCS, "MOLLUSCS", "Моллюски", "Мидии, устрицы, кальмары и другие моллюски", 50),
        AllergenEntity(PEANUTS, "PEANUTS", "Арахис", "Арахис и продукты на его основе", 60),
        AllergenEntity(TREE_NUTS, "TREE_NUTS", "Орехи", "Миндаль, фундук, кешью и другие древесные орехи", 70),
        AllergenEntity(SOY, "SOY", "Соя", "Соя и продукты на её основе", 80),
        AllergenEntity(GLUTEN, "GLUTEN", "Глютен", "Злаки с глютеном: пшеница, рожь, ячмень, овёс и продукты на их основе", 90),
        AllergenEntity(SESAME, "SESAME", "Кунжут", "Кунжут и продукты на его основе", 100),
        AllergenEntity(MUSTARD, "MUSTARD", "Горчица", "Горчица и блюда с горчицей", 110),
        AllergenEntity(CELERY, "CELERY", "Сельдерей", "Сельдерей и продукты на его основе", 120),
        AllergenEntity(LUPIN, "LUPIN", "Люпин", "Люпин и продукты на его основе", 130),
        AllergenEntity(SULPHITES, "SULPHITES", "Сульфиты", "Диоксид серы и сульфиты как частая причина непереносимости у чувствительных людей", 140),
        AllergenEntity(CITRUS, "CITRUS", "Цитрусовые", "Дополнительное пользовательское ограничение для цитрусовых фруктов", 150),
        AllergenEntity(
            SWEETS,
            "SWEETS",
            "Сладости и сахар",
            "Персональное ограничение для сладостей, сахара и десертов. Это не классический аллерген, но помогает Foodiary предупреждать о продуктах, которые пользователь плохо переносит.",
            160
        ),
        AllergenEntity(
            HONEY,
            "HONEY",
            "Мёд",
            "Персональное ограничение для мёда и продуктов на его основе.",
            170
        ),
        AllergenEntity(
            COCOA,
            "COCOA",
            "Какао и шоколад",
            "Персональное ограничение для какао, шоколада и продуктов с какао.",
            180
        ),
        AllergenEntity(
            STRAWBERRIES,
            "STRAWBERRIES",
            "Клубника",
            "Персональное ограничение для клубники и продуктов с выраженным клубничным составом.",
            190
        ),
        AllergenEntity(
            NIGHTSHADES,
            "NIGHTSHADES",
            "Паслёновые",
            "Персональное ограничение для томатов, картофеля, баклажанов и сладкого перца.",
            200
        ),
        AllergenEntity(
            CORN,
            "CORN",
            "Кукуруза",
            "Персональное ограничение для кукурузы и продуктов на её основе.",
            210
        )
    )

    val offTagToAllergenId: Map<String, String> = mapOf(
        "en:milk" to MILK,
        "en:lactose" to MILK,
        "en:eggs" to EGGS,
        "en:fish" to FISH,
        "en:crustaceans" to SHELLFISH,
        "en:molluscs" to MOLLUSCS,
        "en:mollusks" to MOLLUSCS,
        "en:peanuts" to PEANUTS,
        "en:nuts" to TREE_NUTS,
        "en:almonds" to TREE_NUTS,
        "en:hazelnuts" to TREE_NUTS,
        "en:walnuts" to TREE_NUTS,
        "en:cashew-nuts" to TREE_NUTS,
        "en:pistachio-nuts" to TREE_NUTS,
        "en:soybeans" to SOY,
        "en:soy" to SOY,
        "en:gluten" to GLUTEN,
        "en:wheat" to GLUTEN,
        "en:barley" to GLUTEN,
        "en:rye" to GLUTEN,
        "en:oats" to GLUTEN,
        "en:sesame-seeds" to SESAME,
        "en:sesame" to SESAME,
        "en:mustard" to MUSTARD,
        "en:celery" to CELERY,
        "en:lupin" to LUPIN,
        "en:sulphur-dioxide-and-sulphites" to SULPHITES,
        "en:sulphites" to SULPHITES,
        "en:sulfites" to SULPHITES,
        "en:cocoa" to COCOA,
        "en:chocolate" to COCOA,
        "en:corn" to CORN,
        "en:maize" to CORN
    )

    val seedFoodAllergens: Map<String, List<Pair<String, AllergenPresenceType>>> = mapOf(
        "milk_2_5" to contains(MILK),
        "kefir_2_5" to contains(MILK),
        "greek_yogurt" to contains(MILK),
        "natural_yogurt" to contains(MILK),
        "skyr" to contains(MILK),
        "cottage_cheese" to contains(MILK),
        "cottage_cheese_lowfat" to contains(MILK),
        "ryazhenka" to contains(MILK),
        "ricotta" to contains(MILK),
        "mozzarella" to contains(MILK),
        "feta" to contains(MILK),
        "cheese" to contains(MILK),
        "sour_cream_15" to contains(MILK),
        "butter" to contains(MILK),
        "ice_cream" to contains(MILK, SWEETS),

        "egg" to contains(EGGS),
        "egg_white" to contains(EGGS),
        "mayonnaise" to contains(EGGS).mayContain(MUSTARD),
        "cookie" to contains(GLUTEN, SWEETS).mayContain(MILK, EGGS, TREE_NUTS, COCOA),

        "salmon" to contains(FISH),
        "trout" to contains(FISH),
        "tuna" to contains(FISH),
        "cod" to contains(FISH),
        "herring" to contains(FISH),
        "mackerel" to contains(FISH),
        "sardines" to contains(FISH),
        "shrimp" to contains(SHELLFISH),
        "squid" to contains(MOLLUSCS),

        "tofu" to contains(SOY),
        "tempeh" to contains(SOY),
        "edamame" to contains(SOY),

        "peanuts" to contains(PEANUTS),
        "peanut_butter" to contains(PEANUTS),
        "almonds" to contains(TREE_NUTS),
        "walnuts" to contains(TREE_NUTS),
        "cashews" to contains(TREE_NUTS),
        "pistachios" to contains(TREE_NUTS),
        "sunflower_seeds" to mayContain(TREE_NUTS, PEANUTS),
        "pumpkin_seeds" to mayContain(TREE_NUTS, PEANUTS),
        "chia_seeds" to mayContain(SESAME, TREE_NUTS),
        "flaxseed" to mayContain(SESAME, TREE_NUTS),
        "sesame_seeds" to contains(SESAME),
        "hummus" to contains(SESAME).mayContain(SOY),

        "oatmeal" to contains(GLUTEN),
        "oat_porridge" to contains(GLUTEN),
        "wholegrain_bread" to contains(GLUTEN),
        "rye_bread" to contains(GLUTEN),
        "lavash" to contains(GLUTEN),
        "pasta" to contains(GLUTEN),
        "whole_wheat_pasta" to contains(GLUTEN),
        "bulgur" to contains(GLUTEN),
        "couscous" to contains(GLUTEN),
        "barley" to contains(GLUTEN),

        "orange" to contains(CITRUS),
        "mandarin" to contains(CITRUS),
        "grapefruit" to contains(CITRUS),
        "lemon" to contains(CITRUS),
        "orange_juice" to contains(CITRUS),

        "strawberries" to contains(STRAWBERRIES),
        "berries_mix" to mayContain(STRAWBERRIES),
        "dried_apricots" to mayContain(SULPHITES, SWEETS),
        "raisins" to mayContain(SULPHITES, SWEETS),
        "dates" to contains(SWEETS).mayContain(SULPHITES),

        "tomato" to contains(NIGHTSHADES),
        "potato" to contains(NIGHTSHADES),
        "bell_pepper" to contains(NIGHTSHADES),
        "eggplant" to contains(NIGHTSHADES),
        "ketchup" to contains(NIGHTSHADES).mayContain(MUSTARD),
        "tomato_juice" to contains(NIGHTSHADES),
        "chips" to contains(NIGHTSHADES).mayContain(GLUTEN, MILK),
        "corn" to contains(CORN),

        "dark_chocolate" to contains(SWEETS, COCOA).mayContain(MILK, TREE_NUTS, PEANUTS, SOY),
        "honey" to contains(SWEETS, HONEY),
        "jam" to contains(SWEETS),
        "sugar" to contains(SWEETS),
        "sausage" to mayContain(MUSTARD, MILK, SOY, GLUTEN, SULPHITES)
    )

    private val nameProfiles = listOf(
        NameProfile(MILK, 0.95, listOf("milk", "lactose", "yogurt", "yoghurt", "kefir", "skyr", "cheese", "cream", "butter", "ricotta", "mozzarella", "feta", "ice cream", "молок", "лактоз", "кефир", "йогурт", "скир", "сыр", "творог", "сливк", "сметан", "масло слив", "морожен")),
        NameProfile(EGGS, 0.94, listOf("egg", "eggs", "yolk", "omelet", "omelette", "mayonnaise", "яйцо", "яйца", "яич", "желт", "омлет", "майонез")),
        NameProfile(FISH, 0.95, listOf("fish", "salmon", "tuna", "cod", "trout", "sardine", "herring", "mackerel", "рыба", "лосос", "тунец", "треск", "форел", "сардин", "сельд", "скумбр")),
        NameProfile(SHELLFISH, 0.92, listOf("shrimp", "prawn", "crab", "lobster", "кревет", "краб", "лобстер", "рак", "ракообраз")),
        NameProfile(MOLLUSCS, 0.9, listOf("mussel", "oyster", "squid", "octopus", "clam", "scallop", "мидии", "устриц", "кальмар", "осьминог", "моллюск")),
        NameProfile(PEANUTS, 0.96, listOf("peanut", "арахис")),
        NameProfile(TREE_NUTS, 0.93, listOf("almond", "hazelnut", "walnut", "cashew", "pistachio", "nut", "орех", "миндаль", "фундук", "кешью", "фисташ", "грецк")),
        NameProfile(SOY, 0.94, listOf("soy", "soya", "tofu", "tempeh", "edamame", "соев", "соя", "тофу", "темпе", "эдамаме", "эдэмаме")),
        NameProfile(GLUTEN, 0.9, listOf("bread", "pasta", "bun", "toast", "bagel", "croissant", "spaghetti", "noodle", "wheat", "barley", "rye", "oats", "bulgur", "couscous", "lavash", "хлеб", "паста", "булка", "тост", "лапша", "макарон", "пшениц", "ячмен", "рож", "ржан", "овёс", "овсян", "булгур", "кускус", "лаваш")),
        NameProfile(SESAME, 0.9, listOf("sesame", "tahini", "кунжут", "тахини")),
        NameProfile(MUSTARD, 0.9, listOf("mustard", "горчиц")),
        NameProfile(CELERY, 0.9, listOf("celery", "celeriac", "сельдер", "селдер")),
        NameProfile(LUPIN, 0.88, listOf("lupin", "люпин")),
        NameProfile(SULPHITES, 0.82, listOf("sulphite", "sulfite", "sulfur dioxide", "dried fruit", "курага", "изюм", "сухофрукт", "сульфит", "диоксид серы")),
        NameProfile(CITRUS, 0.86, listOf("citrus", "orange", "lemon", "lime", "grapefruit", "mandarin", "tangerine", "апельсин", "лимон", "лайм", "грейпфрут", "мандарин", "цитрус")),
        NameProfile(SWEETS, 0.82, listOf("sweet", "sweets", "sugar", "candy", "chocolate", "dessert", "cake", "cookie", "ice cream", "jam", "honey", "слад", "сахар", "конфет", "шоколад", "десерт", "торт", "пирож", "печень", "морожен", "варень", "джем", "мёд", "мед")),
        NameProfile(HONEY, 0.9, listOf("honey", "мёд", "медовый", "мед")),
        NameProfile(COCOA, 0.88, listOf("cocoa", "chocolate", "какао", "шоколад")),
        NameProfile(STRAWBERRIES, 0.88, listOf("strawberry", "strawberries", "клубник")),
        NameProfile(NIGHTSHADES, 0.82, listOf("tomato", "potato", "pepper", "eggplant", "помидор", "томат", "картоф", "перец", "баклажан", "кетчуп")),
        NameProfile(CORN, 0.88, listOf("corn", "maize", "кукуруз", "маис"))
    )

    fun inferFromNames(
        names: List<String>,
        excludedAllergenIds: Set<String> = emptySet()
    ): List<InferenceHit> {
        if (names.isEmpty()) return emptyList()
        val normalized = names
            .map { normalize(it) }
            .filter { it.isNotBlank() }

        if (normalized.isEmpty()) return emptyList()

        return nameProfiles
            .asSequence()
            .filter { it.allergenId !in excludedAllergenIds }
            .filter { profile ->
                normalized.any { value -> profile.keywords.any { keyword -> value.contains(keyword) } }
            }
            .map { InferenceHit(it.allergenId, it.confidence) }
            .distinctBy { it.allergenId }
            .toList()
    }

    fun normalizeOpenFoodFactsTags(tags: List<String>): Set<String> {
        return tags.mapNotNull { raw ->
            offTagToAllergenId[raw.trim().lowercase()]
        }.toSet()
    }

    private fun contains(vararg allergenIds: String): List<Pair<String, AllergenPresenceType>> {
        return allergenIds.map { it to AllergenPresenceType.CONTAINS }
    }

    private fun mayContain(vararg allergenIds: String): List<Pair<String, AllergenPresenceType>> {
        return allergenIds.map { it to AllergenPresenceType.MAY_CONTAIN }
    }

    private fun List<Pair<String, AllergenPresenceType>>.mayContain(
        vararg allergenIds: String
    ): List<Pair<String, AllergenPresenceType>> {
        return this + AllergenCatalog.mayContain(*allergenIds)
    }

    private fun normalize(raw: String): String {
        return raw
            .trim()
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\s]+"), " ")
            .replace(Regex("\\s+"), " ")
    }
}
