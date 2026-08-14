package com.example.foodiary.domain.model

enum class SmartCoachFocus {
    CALORIES_DEFICIT,
    CALORIES_EXCESS,
    PROTEIN_DEFICIT,
    FAT_DEFICIT,
    CARBS_DEFICIT,
    BALANCED
}

data class SmartCoachForecast(
    val projectedCalories: Int,
    val projectedProtein: Int,
    val projectedFat: Int,
    val projectedCarbs: Int,
    val title: String,
    val message: String
)

data class SmartCoachReplacement(
    val originalFood: Food?,
    val replacement: Food,
    val title: String,
    val reason: String,
    val calorieDeltaPer100g: Int,
    val proteinDeltaPer100g: Int,
    val semanticMatch: String,
    val semanticScore: Int
)

data class SmartCoachWeatherContext(
    val title: String,
    val message: String,
    val suggestedFood: Food?
)

enum class SmartCoachScoreSection {
    DAY_STATE,
    RECOMMENDATION,
    CONTEXT
}

data class SmartCoachScoreDetail(
    val section: SmartCoachScoreSection,
    val label: String,
    val value: Int,
    val description: String
)

data class SmartCoachMealPlanItem(
    val food: Food,
    val quantityInGrams: Int
)

data class SmartCoachMealPlanOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val items: List<SmartCoachMealPlanItem>,
    val calories: Int,
    val protein: Int,
    val fat: Int,
    val carbs: Int,
    val score: Int,
    val reason: String
)

data class SmartCoachMealPlanSection(
    val mealType: MealType,
    val title: String,
    val subtitle: String,
    val targetCalories: Int,
    val targetProtein: Int,
    val targetFat: Int,
    val targetCarbs: Int,
    val options: List<SmartCoachMealPlanOption>
)

data class SmartCoachMealPlan(
    val title: String,
    val subtitle: String,
    val sections: List<SmartCoachMealPlanSection>
)

data class SmartCoachInsight(
    val balanceScore: Int,
    val balanceTitle: String,
    val balanceMessage: String,
    val focus: SmartCoachFocus,
    val forecast: SmartCoachForecast,
    val correctionTitle: String,
    val correctionMessage: String,
    val suggestedFood: Food?,
    val suggestedMealType: MealType,
    val replacement: SmartCoachReplacement?,
    val scoreDetails: List<SmartCoachScoreDetail>,
    val explanationBullets: List<String>,
    val weatherContext: SmartCoachWeatherContext?,
    val mealPlan: SmartCoachMealPlan? = null
)
