package com.example.foodiary.presentation.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.foodiary.R
import com.example.foodiary.data.local.database.AppDatabase
import com.example.foodiary.data.local.seed.AllergenCatalog
import com.example.foodiary.data.mapper.toDomain
import com.example.foodiary.data.model.ActivityLevel
import com.example.foodiary.data.model.BiologicalSex
import com.example.foodiary.data.model.UserGoal
import com.example.foodiary.data.model.UserRestrictionKind
import com.example.foodiary.data.repository.AllergenRepositoryImpl
import com.example.foodiary.data.repository.UserRepositoryImpl
import com.example.foodiary.databinding.FragmentProfileSettingsBinding
import com.example.foodiary.domain.model.Allergen
import com.example.foodiary.domain.model.NutritionTargets
import com.example.foodiary.domain.model.ProteinGoalBasis
import com.example.foodiary.domain.model.User
import com.example.foodiary.domain.model.UserRestriction
import com.example.foodiary.domain.repository.AllergenRepository
import com.example.foodiary.domain.repository.UserRepository
import com.example.foodiary.domain.usecase.CalculateNutritionTargetsUseCase
import com.example.foodiary.presentation.util.FoodiaryMotionPattern
import com.example.foodiary.presentation.util.popBackStackSafely
import com.example.foodiary.presentation.util.replaceFragmentSafely
import com.example.foodiary.presentation.util.setDebouncedClickListener
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class ProfileSettingsFragment : Fragment() {

    private data class RestrictionRowViews(
        val allergyChip: TextView,
        val intoleranceChip: TextView,
    )

    companion object {
        private const val ARG_RETURN_DAY_START = "arg_return_day_start"

        fun newInstance(returnDayStart: Long = System.currentTimeMillis()): ProfileSettingsFragment {
            return ProfileSettingsFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_RETURN_DAY_START, returnDayStart)
                }
            }
        }
    }

    private var _binding: FragmentProfileSettingsBinding? = null
    private val binding: FragmentProfileSettingsBinding
        get() = _binding ?: error("FragmentProfileSettingsBinding is null")

    private lateinit var userRepository: UserRepository
    private lateinit var allergenRepository: AllergenRepository
    private val calculateNutritionTargets = CalculateNutritionTargetsUseCase()

    private var availableAllergens: List<Allergen> = emptyList()
    private val restrictionSelections = linkedMapOf<String, UserRestrictionKind?>()
    private val restrictionRows = linkedMapOf<String, RestrictionRowViews>()
    private var restrictionsExpanded = false
    private var currentGoal: UserGoal = UserGoal.MAINTAIN_WEIGHT

    private val returnDayStart: Long by lazy {
        arguments?.getLong(ARG_RETURN_DAY_START) ?: System.currentTimeMillis()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.textScreenTitle.text = "Параметры тела"
        binding.textScreenSubtitle.text =
            "Изменяйте параметры тела, активность и ограничения. Цель питания редактируется отдельно."
        binding.cardNutritionGoalSection.visibility = View.GONE

        val database = AppDatabase.getInstance(requireContext())
        userRepository = UserRepositoryImpl(
            userDao = database.userDao(),
            allergenDao = database.allergenDao(),
            userRestrictionDao = database.userRestrictionDao()
        )
        allergenRepository = AllergenRepositoryImpl(
            allergenDao = database.allergenDao(),
            foodAllergenDao = database.foodAllergenDao(),
            userRestrictionDao = database.userRestrictionDao()
        )

        setupDefaultSelections()
        setupListeners()
        loadAllergens()
        loadExistingProfile()
        refreshPreview()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupDefaultSelections() {
        binding.radioLowActive.isChecked = true
    }

    private fun setupListeners() {
        listOf(
            binding.editAge,
            binding.editHeight,
            binding.editWeight,
            binding.editBodyFat
        ).forEach { editText ->
            editText.doAfterTextChanged { refreshPreview() }
        }

        listOf(binding.groupSex, binding.groupActivity).forEach { group ->
            group.setOnCheckedChangeListener { _, _ -> refreshPreview() }
        }

        binding.buttonClose.setDebouncedClickListener {
            closeScreen()
        }

        binding.layoutRestrictionHeader.setDebouncedClickListener {
            restrictionsExpanded = !restrictionsExpanded
            renderRestrictionSectionState()
        }

        binding.buttonSaveProfile.setDebouncedClickListener {
            saveProfile()
        }
    }

    private fun loadAllergens() {
        viewLifecycleOwner.lifecycleScope.launch {
            availableAllergens = allergenRepository.getAllergens()
                .ifEmpty { AllergenCatalog.allergens.map { it.toDomain() } }
            renderRestrictionRows()
            refreshRestrictionSummary()
        }
    }

    private fun renderRestrictionRows() {
        val container = binding.layoutRestrictionRows
        container.removeAllViews()
        restrictionRows.clear()

        availableAllergens.forEach { allergen ->
            val row = layoutInflater.inflate(
                R.layout.item_user_restriction_row,
                container,
                false
            )
            row.findViewById<TextView>(R.id.textAllergenName).text = allergen.displayName
            val allergyChip = row.findViewById<TextView>(R.id.chipAllergy)
            val intoleranceChip = row.findViewById<TextView>(R.id.chipIntolerance)

            allergyChip.setOnClickListener {
                toggleRestriction(allergen.id, UserRestrictionKind.ALLERGY)
            }
            intoleranceChip.setOnClickListener {
                toggleRestriction(allergen.id, UserRestrictionKind.INTOLERANCE)
            }

            restrictionRows[allergen.id] = RestrictionRowViews(
                allergyChip = allergyChip,
                intoleranceChip = intoleranceChip
            )
            container.addView(row)
        }

        applyRestrictionSelectionUi()
        renderRestrictionSectionState()
    }

    private fun toggleRestriction(allergenId: String, kind: UserRestrictionKind) {
        val current = restrictionSelections[allergenId]
        restrictionSelections[allergenId] = if (current == kind) null else kind
        applyRestrictionSelectionUi()
        refreshRestrictionSummary()
    }

    private fun applyRestrictionSelectionUi() {
        restrictionRows.forEach { (allergenId, row) ->
            val selected = restrictionSelections[allergenId]
            row.allergyChip.setBackgroundResource(
                if (selected == UserRestrictionKind.ALLERGY) {
                    R.drawable.bg_product_config_portion_chip_selected
                } else {
                    R.drawable.bg_product_config_portion_chip
                }
            )
            row.intoleranceChip.setBackgroundResource(
                if (selected == UserRestrictionKind.INTOLERANCE) {
                    R.drawable.bg_product_config_portion_chip_selected
                } else {
                    R.drawable.bg_product_config_portion_chip
                }
            )
        }
    }

    private fun refreshRestrictionSummary() {
        val allergies = restrictionSelections.values.count { it == UserRestrictionKind.ALLERGY }
        val intolerances = restrictionSelections.values.count { it == UserRestrictionKind.INTOLERANCE }
        binding.textRestrictionSummaryCompact.text = when {
            allergies == 0 && intolerances == 0 ->
                "Можно пропустить, если ограничений нет"
            allergies > 0 && intolerances > 0 ->
                "Выбрано: аллергии — $allergies, непереносимости — $intolerances"
            allergies > 0 ->
                "Выбрано: аллергии — $allergies"
            else ->
                "Выбрано: непереносимости — $intolerances"
        }
        binding.textRestrictionHint.text = when {
            allergies == 0 && intolerances == 0 ->
                "Если ничего не отмечено, рекомендации и продукты не будут дополнительно фильтроваться по аллергенам."
            else ->
                "Аллергии будут давать строгие предупреждения, непереносимости — более мягкие. Эти ограничения также учитываются в рекомендациях."
        }
    }

    private fun renderRestrictionSectionState() {
        binding.layoutRestrictionContent.isVisible = restrictionsExpanded
        binding.textRestrictionToggle.text = if (restrictionsExpanded) {
            "Свернуть"
        } else {
            "Развернуть"
        }
    }

    private fun loadExistingProfile() {
        viewLifecycleOwner.lifecycleScope.launch {
            val existingUser = userRepository.getCurrentUser()
            if (existingUser == null) {
                binding.textScreenSubtitle.text =
                    "Заполните профиль, чтобы приложение пересчитало калории, белок, жиры, углеводы и могло точнее подбирать рекомендации."
                refreshPreview()
                return@launch
            }

            _binding ?: return@launch
            applyUser(existingUser)
            binding.textScreenSubtitle.text =
                "Изменяйте параметры тела, активность и ограничения. После сохранения дневные нормы и рекомендации сразу обновятся."
            refreshPreview()
        }
    }

    private fun applyUser(user: User) {
        when (user.biologicalSex) {
            BiologicalSex.FEMALE -> binding.radioFemale.isChecked = true
            BiologicalSex.MALE -> binding.radioMale.isChecked = true
        }

        binding.editAge.setText(user.age.toString())
        binding.editHeight.setText(user.heightCm.toString())
        binding.editWeight.setText(formatDecimal(user.weightKg))
        binding.editBodyFat.setText(user.bodyFatPercent?.let(::formatDecimal).orEmpty())

        when (user.activityLevel) {
            ActivityLevel.INACTIVE -> binding.radioInactive.isChecked = true
            ActivityLevel.LOW_ACTIVE -> binding.radioLowActive.isChecked = true
            ActivityLevel.ACTIVE -> binding.radioActive.isChecked = true
            ActivityLevel.VERY_ACTIVE -> binding.radioVeryActive.isChecked = true
        }

        currentGoal = user.goal

        restrictionSelections.clear()
        user.restrictions.forEach { restriction ->
            restrictionSelections[restriction.allergen.id] = restriction.restrictionKind
        }
        restrictionsExpanded = false
        applyRestrictionSelectionUi()
        refreshRestrictionSummary()
        renderRestrictionSectionState()
    }

    private fun refreshPreview() {
        val draft = buildDraftOrNull()
        if (draft == null) {
            binding.cardTargetsPreview.visibility = View.GONE
            binding.textCalculationHint.visibility = View.VISIBLE
            binding.textCalculationHint.text =
                "Заполните биологический пол, возраст, рост и массу тела. Тогда приложение покажет, как именно будут рассчитаны калории и макронутриенты."
            return
        }

        val targets = calculateNutritionTargets(draft)
        binding.cardTargetsPreview.visibility = View.VISIBLE
        binding.textCalculationHint.visibility = View.GONE
        bindTargets(targets, draft)
    }

    private fun bindTargets(targets: NutritionTargets, user: User) {
        binding.textMaintenanceCaloriesValue.text = "${targets.maintenanceCalories} ккал"
        binding.textTargetCaloriesValue.text = "${targets.targetCalories} ккал"
        binding.textProteinValue.text = "${targets.proteinGrams} г"
        binding.textFatValue.text = "${targets.fatGrams} г"
        binding.textCarbsValue.text = "${targets.carbsGrams} г"

        binding.textEnergyExplanation.text =
            "Калории поддержания рассчитаны по официальным формулам Dietary Reference Intakes for Energy 2023. Формула учитывает биологический пол, возраст, рост, массу тела и уровень физической активности."

        binding.textProteinExplanation.text = when (targets.proteinGoalBasis) {
            ProteinGoalBasis.LEAN_BODY_MASS -> {
                val leanMass = targets.leanBodyMassKg?.roundToInt() ?: 0
                val bodyFat = targets.bodyFatPercentUsed?.let(::formatDecimal).orEmpty()
                "Белок рассчитан по безжировой массе тела. В расчёте использованы масса тела ${formatDecimal(user.weightKg)} кг и процент жира $bodyFat%. Полученная безжировая масса тела: $leanMass кг."
            }

            ProteinGoalBasis.ADJUSTED_BODY_WEIGHT -> {
                val adjustedWeight = targets.adjustedBodyWeightKg?.let(::formatDecimal).orEmpty()
                "Процент жира не указан, поэтому при индексе массы тела 30 и выше белок считается по скорректированной массе тела. В этом профиле использована скорректированная масса $adjustedWeight кг."
            }

            ProteinGoalBasis.TOTAL_BODY_WEIGHT -> {
                "Процент жира не указан, а индекс массы тела не относится к ожирению. Поэтому белок рассчитан по общей массе тела ${formatDecimal(user.weightKg)} кг."
            }
        }

        binding.textGoalExplanation.text = when (currentGoal) {
            UserGoal.WEIGHT_LOSS ->
                "Сейчас выбрана цель снижения массы тела, поэтому приложение использует дефицит 500 килокалорий в день, но не опускается ниже практических минимальных рамок 1200 килокалорий для женщин и 1500 килокалорий для мужчин."

            UserGoal.MAINTAIN_WEIGHT ->
                "Сейчас выбрана цель поддержания массы тела, поэтому целевые калории равны расчётной потребности поддержания."

            UserGoal.WEIGHT_GAIN ->
                "Сейчас выбрана цель набора массы, поэтому приложение добавляет к калориям поддержания 250 килокалорий в день как осторожный стартовый профицит."

            UserGoal.MUSCLE_GAIN_TRAINING ->
                "Сейчас выбрана цель набора мышечной массы при силовых тренировках, поэтому приложение использует осторожный динамический профицит и повышенный белок."
        }
    }

    private fun saveProfile() {
        val user = buildDraftOrNull()
        if (user == null) {
            Toast.makeText(
                requireContext(),
                "Проверьте, что биологический пол выбран, а возраст, рост и масса тела заполнены корректно.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        binding.progressSave.visibility = View.VISIBLE
        binding.buttonSaveProfile.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                userRepository.saveCurrentUser(user)
                Toast.makeText(
                    requireContext(),
                    "Профиль обновлён",
                    Toast.LENGTH_SHORT
                ).show()
                closeScreen()
            } finally {
                _binding?.progressSave?.visibility = View.GONE
                _binding?.buttonSaveProfile?.isEnabled = true
            }
        }
    }

    private fun closeScreen() {
        if (parentFragmentManager.backStackEntryCount > 0) {
            popBackStackSafely()
            return
        }
        replaceFragmentSafely(
            DailyNutritionFragment.newInstance(returnDayStart),
            addToBackStack = false,
            motionPattern = FoodiaryMotionPattern.ROOT_FADE_THROUGH
        )
    }

    private fun buildDraftOrNull(): User? {
        val biologicalSex = when (binding.groupSex.checkedRadioButtonId) {
            R.id.radioFemale -> BiologicalSex.FEMALE
            R.id.radioMale -> BiologicalSex.MALE
            else -> null
        } ?: return null

        val age = binding.editAge.text.toString().trim().toIntOrNull()
            ?.takeIf { it in 19..120 }
            ?: return null

        val heightCm = binding.editHeight.text.toString().trim().toIntOrNull()
            ?.takeIf { it in 120..230 }
            ?: return null

        val weightKg = binding.editWeight.text.toString()
            .trim()
            .replace(",", ".")
            .toDoubleOrNull()
            ?.takeIf { it in 30.0..350.0 }
            ?: return null

        val bodyFatPercent = binding.editBodyFat.text.toString()
            .trim()
            .replace(",", ".")
            .takeIf { it.isNotBlank() }
            ?.toDoubleOrNull()
            ?.takeIf { it in 3.0..75.0 }

        val activityLevel = when (binding.groupActivity.checkedRadioButtonId) {
            R.id.radioInactive -> ActivityLevel.INACTIVE
            R.id.radioLowActive -> ActivityLevel.LOW_ACTIVE
            R.id.radioActive -> ActivityLevel.ACTIVE
            R.id.radioVeryActive -> ActivityLevel.VERY_ACTIVE
            else -> return null
        }

        val allergenMap = availableAllergens.associateBy { it.id }
        val restrictions = restrictionSelections.mapNotNull { (allergenId, kind) ->
            val safeKind = kind ?: return@mapNotNull null
            val allergen = allergenMap[allergenId] ?: return@mapNotNull null
            UserRestriction(
                allergen = allergen,
                restrictionKind = safeKind
            )
        }.sortedBy { it.allergen.displayName }

        return User(
            biologicalSex = biologicalSex,
            age = age,
            weightKg = weightKg,
            heightCm = heightCm,
            bodyFatPercent = bodyFatPercent,
            goal = currentGoal,
            activityLevel = activityLevel,
            restrictions = restrictions
        )
    }

    private fun formatDecimal(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.roundToInt().toString()
        } else {
            String.format("%.1f", value)
        }
    }
}
