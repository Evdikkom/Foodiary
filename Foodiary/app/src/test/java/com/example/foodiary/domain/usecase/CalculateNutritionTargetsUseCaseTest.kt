package com.example.foodiary.domain.usecase

import com.example.foodiary.data.model.ActivityLevel
import com.example.foodiary.data.model.BiologicalSex
import com.example.foodiary.data.model.UserGoal
import com.example.foodiary.domain.model.ProteinGoalBasis
import com.example.foodiary.domain.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculateNutritionTargetsUseCaseTest {

    private val useCase = CalculateNutritionTargetsUseCase()

    @Test
    fun `muscle gain with body fat uses lean body mass and higher protein`() {
        val user = baseUser(
            biologicalSex = BiologicalSex.MALE,
            age = 30,
            weightKg = 80.0,
            heightCm = 180,
            bodyFatPercent = 20.0,
            goal = UserGoal.MUSCLE_GAIN_TRAINING,
            activityLevel = ActivityLevel.ACTIVE
        )

        val result = useCase(user)

        assertEquals(ProteinGoalBasis.LEAN_BODY_MASS, result.proteinGoalBasis)
        assertEquals(64.0, result.proteinReferenceWeightKg, 0.01)
        assertEquals(128, result.proteinGrams)
        assertTrue(result.targetCalories > result.maintenanceCalories)
    }

    @Test
    fun `obesity without body fat uses adjusted body weight`() {
        val user = baseUser(
            biologicalSex = BiologicalSex.FEMALE,
            age = 35,
            weightKg = 110.0,
            heightCm = 160,
            bodyFatPercent = null,
            goal = UserGoal.MAINTAIN_WEIGHT,
            activityLevel = ActivityLevel.LOW_ACTIVE
        )

        val result = useCase(user)

        assertEquals(ProteinGoalBasis.ADJUSTED_BODY_WEIGHT, result.proteinGoalBasis)
        assertTrue(result.adjustedBodyWeightKg != null)
        assertTrue(result.adjustedBodyWeightKg!! < user.weightKg)
    }

    @Test
    fun `weight loss respects lower calorie floor`() {
        val user = baseUser(
            biologicalSex = BiologicalSex.FEMALE,
            age = 60,
            weightKg = 50.0,
            heightCm = 150,
            bodyFatPercent = null,
            goal = UserGoal.WEIGHT_LOSS,
            activityLevel = ActivityLevel.INACTIVE
        )

        val result = useCase(user)

        assertEquals(1200, result.targetCalories)
    }

    @Test
    fun `weight gain stays above maintenance while weight loss stays below`() {
        val maintainUser = baseUser(goal = UserGoal.MAINTAIN_WEIGHT)
        val gainUser = maintainUser.copy(goal = UserGoal.WEIGHT_GAIN)
        val lossUser = maintainUser.copy(goal = UserGoal.WEIGHT_LOSS)

        val maintain = useCase(maintainUser)
        val gain = useCase(gainUser)
        val loss = useCase(lossUser)

        assertEquals(maintain.maintenanceCalories, maintain.targetCalories)
        assertTrue(gain.targetCalories > maintain.targetCalories)
        assertTrue(loss.targetCalories < maintain.targetCalories)
    }

    @Test
    fun `invalid body fat percent is ignored for protein basis`() {
        val user = baseUser(
            weightKg = 80.0,
            heightCm = 180,
            bodyFatPercent = 99.0,
            goal = UserGoal.MUSCLE_GAIN_TRAINING
        )

        val result = useCase(user)

        assertEquals(ProteinGoalBasis.TOTAL_BODY_WEIGHT, result.proteinGoalBasis)
        assertEquals(80.0, result.proteinReferenceWeightKg, 0.01)
        assertEquals(128, result.proteinGrams)
    }

    @Test
    fun `body fat percent changes macro split through lean body mass but not calories`() {
        val withoutBodyFat = baseUser(
            weightKg = 70.0,
            heightCm = 175,
            bodyFatPercent = null,
            goal = UserGoal.MAINTAIN_WEIGHT
        )
        val withBodyFat = withoutBodyFat.copy(bodyFatPercent = 20.0)

        val totalWeightResult = useCase(withoutBodyFat)
        val leanMassResult = useCase(withBodyFat)

        assertEquals(totalWeightResult.maintenanceCalories, leanMassResult.maintenanceCalories)
        assertEquals(totalWeightResult.targetCalories, leanMassResult.targetCalories)
        assertEquals(ProteinGoalBasis.TOTAL_BODY_WEIGHT, totalWeightResult.proteinGoalBasis)
        assertEquals(ProteinGoalBasis.LEAN_BODY_MASS, leanMassResult.proteinGoalBasis)
        assertTrue(leanMassResult.proteinGrams < totalWeightResult.proteinGrams)
        assertTrue(leanMassResult.carbsGrams > totalWeightResult.carbsGrams)
    }

    @Test
    fun `muscle gain surplus grows with body mass`() {
        val lighter = baseUser(weightKg = 60.0, bodyFatPercent = null, goal = UserGoal.MUSCLE_GAIN_TRAINING)
        val heavier = baseUser(weightKg = 100.0, bodyFatPercent = null, goal = UserGoal.MUSCLE_GAIN_TRAINING)

        val lighterResult = useCase(lighter)
        val heavierResult = useCase(heavier)

        assertTrue(
            heavierResult.calorieDeltaFromMaintenance > lighterResult.calorieDeltaFromMaintenance
        )
    }

    private fun baseUser(
        biologicalSex: BiologicalSex = BiologicalSex.MALE,
        age: Int = 28,
        weightKg: Double = 78.0,
        heightCm: Int = 178,
        bodyFatPercent: Double? = 18.0,
        goal: UserGoal = UserGoal.MAINTAIN_WEIGHT,
        activityLevel: ActivityLevel = ActivityLevel.ACTIVE
    ): User {
        return User(
            biologicalSex = biologicalSex,
            age = age,
            weightKg = weightKg,
            heightCm = heightCm,
            bodyFatPercent = bodyFatPercent,
            goal = goal,
            activityLevel = activityLevel
        )
    }
}
