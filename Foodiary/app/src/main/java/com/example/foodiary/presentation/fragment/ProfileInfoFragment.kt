package com.example.foodiary.presentation.fragment

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.foodiary.BuildConfig
import com.example.foodiary.R
import com.example.foodiary.data.local.database.AppDatabase
import com.example.foodiary.data.model.UserGoal
import com.example.foodiary.data.model.UserRestrictionKind
import com.example.foodiary.data.repository.UserRepositoryImpl
import com.example.foodiary.domain.model.NutritionTargets
import com.example.foodiary.domain.model.User
import com.example.foodiary.domain.repository.UserRepository
import com.example.foodiary.domain.usecase.CalculateNutritionTargetsUseCase
import com.example.foodiary.presentation.util.popBackStackSafely
import com.example.foodiary.presentation.util.replaceFragmentSafely
import com.example.foodiary.presentation.util.setDebouncedClickListener
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class ProfileInfoFragment : Fragment(R.layout.fragment_profile_info) {

    enum class SectionType {
        ACCOUNT,
        DAILY_TARGETS,
        MEAL_TYPES,
        DEVICES,
        APPS,
        SUPPORT
    }

    companion object {
        private const val ARG_SECTION = "arg_section"
        private const val ARG_RETURN_DAY_START = "arg_return_day_start"

        fun newInstance(
            section: SectionType,
            returnDayStart: Long = System.currentTimeMillis()
        ): ProfileInfoFragment {
            return ProfileInfoFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SECTION, section.name)
                    putLong(ARG_RETURN_DAY_START, returnDayStart)
                }
            }
        }
    }

    private val section: SectionType by lazy {
        arguments?.getString(ARG_SECTION)
            ?.let { runCatching { SectionType.valueOf(it) }.getOrNull() }
            ?: SectionType.ACCOUNT
    }
    private val returnDayStart: Long by lazy {
        arguments?.getLong(ARG_RETURN_DAY_START) ?: System.currentTimeMillis()
    }

    private lateinit var userRepository: UserRepository
    private val calculateNutritionTargets = CalculateNutritionTargetsUseCase()

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        userRepository = UserRepositoryImpl(
            userDao = AppDatabase.getInstance(requireContext()).userDao(),
            allergenDao = AppDatabase.getInstance(requireContext()).allergenDao(),
            userRestrictionDao = AppDatabase.getInstance(requireContext()).userRestrictionDao()
        )

        view.findViewById<ImageView>(R.id.buttonBack).setDebouncedClickListener {
            popBackStackSafely()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val user = userRepository.getCurrentUser()
            val targets = user?.let(calculateNutritionTargets::invoke)
            renderSection(view, user, targets)
        }
    }

    private fun renderSection(root: android.view.View, user: User?, targets: NutritionTargets?) {
        val title = root.findViewById<TextView>(R.id.textScreenTitle)
        val subtitle = root.findViewById<TextView>(R.id.textScreenSubtitle)
        val sectionTitle = root.findViewById<TextView>(R.id.textSectionTitle)
        val sectionDescription = root.findViewById<TextView>(R.id.textSectionDescription)
        val cards = root.findViewById<LinearLayout>(R.id.layoutInfoCards)
        val primaryButton = root.findViewById<Button>(R.id.buttonPrimaryAction)
        val secondaryButton = root.findViewById<Button>(R.id.buttonSecondaryAction)

        cards.removeAllViews()
        primaryButton.visibility = android.view.View.GONE
        secondaryButton.visibility = android.view.View.GONE

        when (section) {
            SectionType.ACCOUNT -> {
                title.text = "Учетная запись"
                subtitle.text = "Здесь собрана локальная персональная информация Foodiary."
                sectionTitle.text = "Личный профиль"
                sectionDescription.text = if (user == null) {
                    "Профиль еще не заполнен. После настройки приложение начнет считать нормы и персональные рекомендации."
                } else {
                    "Профиль уже используется для расчета калорий, КБЖУ и аллергенной совместимости."
                }

                addInfoCard(cards, "Статус профиля", if (user == null) "Не заполнен" else "Активен")
                addInfoCard(
                    cards,
                    "Базовые данные",
                    user?.let { "${it.age} лет, ${it.heightCm} см, ${formatNumber(it.weightKg)} кг" } ?: "Нет данных"
                )
                addInfoCard(
                    cards,
                    "Ограничения",
                    user?.restrictions?.size?.let { "$it отмечено" } ?: "Не заданы",
                    if (user?.restrictions.isNullOrEmpty()) {
                        "Аллергии и непереносимости можно не указывать, если их нет."
                    } else {
                        val allergies = user?.restrictions?.count { it.restrictionKind == UserRestrictionKind.ALLERGY } ?: 0
                        val intolerances = user?.restrictions?.count { it.restrictionKind == UserRestrictionKind.INTOLERANCE } ?: 0
                        "Аллергии: $allergies, непереносимости: $intolerances."
                    }
                )
                addInfoCard(
                    cards,
                    "Хранение данных",
                    "Локально на устройстве",
                    "Сейчас Foodiary работает без облачной учетной записи и использует локальный профиль."
                )

                bindPrimaryButton(primaryButton, "Открыть параметры профиля") {
                    replaceFragmentSafely(ProfileSettingsFragment.newInstance(returnDayStart))
                }
            }

            SectionType.DAILY_TARGETS -> {
                title.text = "Суточные нормы"
                subtitle.text = "Сводка по вашим текущим расчетным калориям и макронутриентам."
                sectionTitle.text = "Дневные цели"
                sectionDescription.text = if (user == null || targets == null) {
                    "Чтобы получить точные нормы, сначала заполните параметры тела и цель."
                } else {
                    "Здесь показаны текущие расчетные цели, на которые опирается дневник."
                }

                addInfoCard(cards, "Поддержание", targets?.maintenanceCalories?.let { "$it ккал" } ?: "Нет расчета")
                addInfoCard(cards, "Цель на день", targets?.targetCalories?.let { "$it ккал" } ?: "Нет расчета")
                addInfoCard(
                    cards,
                    "Белки, жиры, углеводы",
                    if (targets == null) "Нет расчета" else {
                        "${targets.proteinGrams} г, ${targets.fatGrams} г, ${targets.carbsGrams} г"
                    }
                )
                addInfoCard(
                    cards,
                    "Текущая цель",
                    user?.goal?.let(::goalLabel) ?: "Не задана",
                    "Расчет строится на основе профиля, активности и выбранной цели питания."
                )

                bindPrimaryButton(primaryButton, "Изменить расчет норм") {
                    replaceFragmentSafely(ProfileSettingsFragment.newInstance(returnDayStart))
                }
            }

            SectionType.MEAL_TYPES -> {
                title.text = "Приёмы пищи"
                subtitle.text = "Текущая структура дневника и распределение калорий по приемам пищи."
                sectionTitle.text = "Структура дня"
                sectionDescription.text = "Сейчас Foodiary использует 4 базовых приема пищи. Они уже работают в дневнике и аналитике."

                val targetCalories = targets?.targetCalories ?: 0
                addInfoCard(cards, "Завтрак", if (targetCalories == 0) "30%" else "30% · ${(targetCalories * 0.30).roundToInt()} ккал")
                addInfoCard(cards, "Обед", if (targetCalories == 0) "40%" else "40% · ${(targetCalories * 0.40).roundToInt()} ккал")
                addInfoCard(cards, "Ужин", if (targetCalories == 0) "20%" else "20% · ${(targetCalories * 0.20).roundToInt()} ккал")
                addInfoCard(cards, "Перекус", if (targetCalories == 0) "10%" else "10% · ${(targetCalories * 0.10).roundToInt()} ккал")
                addInfoCard(
                    cards,
                    "Что дальше",
                    "Следующий шаг — гибкая настройка долей",
                    "При необходимости это можно вынести в отдельный конфигуратор режимов питания."
                )
            }

            SectionType.DEVICES -> {
                title.text = "Приложения и устройства"
                subtitle.text = "Системные возможности, на которые опираются фото-анализ и сканирование."
                sectionTitle.text = "Доступные интеграции"
                sectionDescription.text = "Раздел помогает понять, какие возможности уже есть на устройстве и для чего они нужны."

                val hasCamera = requireContext().packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
                addInfoCard(cards, "Камера", if (hasCamera) "Доступна" else "Недоступна", "Используется для штрихкода и фото-анализа блюд.")
                addInfoCard(cards, "Галерея", "Системный выбор фото", "Позволяет загружать снимки блюд из памяти устройства.")
                addInfoCard(cards, "Сетевые сервисы", "LogMeal и Open Food Facts", "Используются для анализа по фото и части внешнего каталога продуктов.")
                addInfoCard(cards, "Локальная база", "Работает офлайн", "Собственные продукты, блюда и история хранятся локально.")
            }

            SectionType.APPS -> {
                title.text = "Наши приложения"
                subtitle.text = "Текущее место Foodiary в экосистеме приложения и дипломного проекта."
                sectionTitle.text = "Экосистема"
                sectionDescription.text = "Сейчас вся продуктовая логика собрана внутри одного приложения Foodiary."

                addInfoCard(cards, "Foodiary", "Дневник, статистика, рекомендации, рецепты", "Основное приложение уже объединяет питание, фото-анализ и персонализацию.")
                addInfoCard(cards, "Будущее расширение", "Раздел зарезервирован", "Если у проекта появятся дополнительные клиентские приложения или сервисы, этот блок станет их точкой входа.")
            }

            SectionType.SUPPORT -> {
                title.text = "Поддержка"
                subtitle.text = "Быстрые ответы и диагностическая информация по текущей сборке."
                sectionTitle.text = "Помощь по приложению"
                sectionDescription.text = "Если что-то работает неожиданно, здесь можно быстро понять, откуда это могло появиться."

                addInfoCard(cards, "Калории и КБЖУ", "Считаются из профиля", "Нормы зависят от пола, возраста, роста, массы тела, активности и цели.")
                addInfoCard(cards, "Фото-анализ", "Использует LogMeal", "Вес и состав блюда приходят из анализа изображения и затем нормализуются внутри Foodiary.")
                addInfoCard(cards, "Диагностика", "Можно поделиться сводкой", "Это удобно для воспроизведения состояния приложения на устройстве.")

                bindPrimaryButton(primaryButton, "Поделиться диагностикой") {
                    shareDiagnostics(user, targets)
                }
                bindSecondaryButton(secondaryButton, "Открыть параметры профиля") {
                    replaceFragmentSafely(ProfileSettingsFragment.newInstance(returnDayStart))
                }
            }
        }
    }

    private fun addInfoCard(
        container: LinearLayout,
        title: String,
        value: String,
        note: String? = null
    ) {
        val card = layoutInflater.inflate(R.layout.item_profile_info_card, container, false)
        card.findViewById<TextView>(R.id.textCardTitle).text = title
        card.findViewById<TextView>(R.id.textCardValue).text = value
        card.findViewById<TextView>(R.id.textCardNote).apply {
            if (note.isNullOrBlank()) {
                visibility = android.view.View.GONE
            } else {
                text = note
                visibility = android.view.View.VISIBLE
            }
        }
        container.addView(card)
    }

    private fun bindPrimaryButton(button: Button, title: String, onClick: () -> Unit) {
        button.text = title
        button.visibility = android.view.View.VISIBLE
        button.setDebouncedClickListener { onClick() }
    }

    private fun bindSecondaryButton(button: Button, title: String, onClick: () -> Unit) {
        button.text = title
        button.visibility = android.view.View.VISIBLE
        button.setDebouncedClickListener { onClick() }
    }

    private fun goalLabel(goal: UserGoal): String {
        return when (goal) {
            UserGoal.WEIGHT_LOSS -> "Снижение массы тела"
            UserGoal.MAINTAIN_WEIGHT -> "Поддержание массы тела"
            UserGoal.WEIGHT_GAIN -> "Набор массы тела"
            UserGoal.MUSCLE_GAIN_TRAINING -> "Набор мышечной массы"
        }
    }

    private fun formatNumber(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.roundToInt().toString()
        } else {
            String.format("%.1f", value)
        }
    }

    private fun shareDiagnostics(user: User?, targets: NutritionTargets?) {
        val payload = buildString {
            appendLine("Foodiary diagnostics")
            appendLine("Version: ${BuildConfig.VERSION_NAME}")
            appendLine("Profile configured: ${user != null}")
            appendLine("Goal: ${user?.goal?.let(::goalLabel) ?: "not_set"}")
            appendLine("Target calories: ${targets?.targetCalories ?: "not_set"}")
            appendLine("Protein/Fat/Carbs: ${targets?.proteinGrams ?: "-"} / ${targets?.fatGrams ?: "-"} / ${targets?.carbsGrams ?: "-"}")
            appendLine("Restrictions: ${user?.restrictions?.size ?: 0}")
        }

        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, payload)
                },
                "Поделиться диагностикой"
            )
        )
    }
}
