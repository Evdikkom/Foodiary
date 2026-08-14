package com.example.foodiary.presentation.fragment

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.foodiary.R
import com.example.foodiary.data.local.preferences.ServiceConfigPreferences
import com.example.foodiary.data.remote.off.OffNetworkDebugLogger
import com.example.foodiary.presentation.location.DeviceLocationProvider
import com.example.foodiary.presentation.util.popBackStackSafely
import com.example.foodiary.presentation.util.setDebouncedClickListener

class ServiceSettingsFragment : Fragment(R.layout.fragment_service_settings) {

    companion object {
        fun newInstance(): ServiceSettingsFragment = ServiceSettingsFragment()
    }

    private lateinit var weatherLocationProvider: DeviceLocationProvider
    private lateinit var serviceConfigPreferences: ServiceConfigPreferences

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        view?.let(::renderServiceCards)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        weatherLocationProvider = DeviceLocationProvider(requireContext())
        serviceConfigPreferences = ServiceConfigPreferences(requireContext())

        view.findViewById<ImageView>(R.id.buttonBack).setDebouncedClickListener {
            popBackStackSafely()
        }
        view.findViewById<TextView>(R.id.buttonOpenSystemSettings).setDebouncedClickListener {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", requireContext().packageName, null)
                }
            )
        }

        renderServiceCards(view)
    }

    private fun renderServiceCards(view: View) {
        val permissionContainer = view.findViewById<LinearLayout>(R.id.layoutPermissionCards)
        val serviceContainer = view.findViewById<LinearLayout>(R.id.layoutServiceCards)
        permissionContainer.removeAllViews()
        serviceContainer.removeAllViews()

        val cameraStatus = if (
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            "Камера разрешена"
        } else {
            "Камера пока не разрешена"
        }
        addCard(
            permissionContainer,
            title = "Камера",
            value = cameraStatus,
            note = "Нужна для сканирования штрихкодов и фото-анализа блюд."
        )

        val locationAllowed = weatherLocationProvider.hasLocationPermission()
        addCard(
            permissionContainer,
            title = "Геолокация для погоды",
            value = if (locationAllowed) "Разрешена" else "Не разрешена",
            note = if (locationAllowed) {
                "Используется только для определения погоды рядом с вами и подбора продуктовых погодных рекомендаций."
            } else {
                "Нужна для погодного облачка и раздела «Погода». Нажмите на карточку, чтобы запросить доступ."
            },
            onClick = if (locationAllowed) {
                null
            } else {
                {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            }
        )
        addCard(
            serviceContainer,
            title = "Галерея",
            value = "Системный выбор фото",
            note = "Foodiary использует безопасный системный выбор изображения и не требует отдельного постоянного доступа к памяти."
        )
        addCard(
            serviceContainer,
            title = "Погодный сервис",
            value = "Open-Meteo",
            note = "Погода берётся по координатам устройства: температура, ощущаемая температура, дождь, ветер, солнце и UV-индекс."
        )

        addSectionTitle(serviceContainer, "Фотоанализ")
        addEditableConfigCard(
            serviceContainer,
            title = "IP или адрес backend",
            value = serviceConfigPreferences.getBackendHost(),
            note = "Если ноутбук получил другой IP, измените адрес здесь без пересборки приложения.",
            inputType = InputType.TYPE_CLASS_TEXT
        ) { host ->
            saveBackendConfig(host = host)
        }
        addEditableConfigCard(
            serviceContainer,
            title = "Порт backend",
            value = serviceConfigPreferences.getBackendPort().toString(),
            note = "По умолчанию Foodiary обращается к серверу фотоанализа на порту 8080.",
            inputType = InputType.TYPE_CLASS_NUMBER
        ) { rawPort ->
            val port = rawPort.toIntOrNull()
            if (port == null || port !in 1..65_535) {
                Toast.makeText(requireContext(), "Введите порт от 1 до 65535", Toast.LENGTH_SHORT).show()
                return@addEditableConfigCard
            }
            saveBackendConfig(port = port)
        }
        addEditableConfigCard(
            serviceContainer,
            title = "API-ключ backend",
            value = serviceConfigPreferences.getBackendApiKey(),
            note = "Ключ отправляется только в заголовке X-API-Key при анализе фото.",
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        ) { apiKey ->
            if (apiKey.isBlank()) {
                Toast.makeText(requireContext(), "API-ключ не должен быть пустым", Toast.LENGTH_SHORT).show()
                return@addEditableConfigCard
            }
            saveBackendConfig(apiKey = apiKey)
        }
        addCard(
            serviceContainer,
            title = "Текущий backend URL",
            value = serviceConfigPreferences.getBackendBaseUrl(),
            note = "Этот адрес используется для отправки фото на локальный backend LogMeal."
        )

        addSectionTitle(serviceContainer, "Диагностика")
        addCard(
            serviceContainer,
            title = "Локальная база",
            value = "Включена",
            note = "Дневник, свои продукты, рецепты и персональные настройки хранятся на устройстве."
        )
        addCard(
            serviceContainer,
            title = "Open Food Facts",
            value = "Каталог продуктов и штрихкоды",
            note = "Диагностический журнал доступен только в debug-сборке и очищается автоматически.",
            onClick = { shareOffDebugLog() }
        )
    }

    private fun saveBackendConfig(
        host: String = serviceConfigPreferences.getBackendHost(),
        port: Int = serviceConfigPreferences.getBackendPort(),
        apiKey: String = serviceConfigPreferences.getBackendApiKey()
    ) {
        serviceConfigPreferences.saveBackendConfig(host, port, apiKey)
        Toast.makeText(requireContext(), "Настройки backend сохранены", Toast.LENGTH_SHORT).show()
        view?.let(::renderServiceCards)
    }

    private fun shareOffDebugLog() {
        val body = buildString {
            append(OffNetworkDebugLogger.buildDeviceHeader())
            appendLine("OFF diagnostics: ${OffNetworkDebugLogger.readablePath(requireContext())}")
            appendLine()
            append(OffNetworkDebugLogger.read(requireContext()))
        }

        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Foodiary OFF debug log")
                    putExtra(Intent.EXTRA_TEXT, body)
                },
                "OFF debug log"
            )
        )
    }

    private fun addCard(
        container: LinearLayout,
        title: String,
        value: String,
        note: String,
        onClick: (() -> Unit)? = null
    ) {
        val card = layoutInflater.inflate(R.layout.item_profile_info_card, container, false)
        card.findViewById<TextView>(R.id.textCardTitle).text = title
        card.findViewById<TextView>(R.id.textCardValue).text = value
        card.findViewById<TextView>(R.id.textCardNote).apply {
            text = note
            visibility = View.VISIBLE
        }
        if (onClick != null) {
            card.isClickable = true
            card.isFocusable = true
            card.setDebouncedClickListener { onClick() }
        }
        container.addView(card)
    }

    private fun addEditableConfigCard(
        container: LinearLayout,
        title: String,
        value: String,
        note: String,
        inputType: Int,
        onSave: (String) -> Unit
    ) {
        val card = layoutInflater.inflate(R.layout.item_profile_info_card, container, false) as LinearLayout
        card.findViewById<TextView>(R.id.textCardTitle).text = title
        card.findViewById<TextView>(R.id.textCardValue).visibility = View.GONE
        card.findViewById<TextView>(R.id.textCardNote).apply {
            text = note
            visibility = View.VISIBLE
        }
        val input = EditText(requireContext()).apply {
            setText(value)
            this.inputType = inputType
            isSingleLine = true
            setSelectAllOnFocus(true)
            background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_addmeal_input)
            setPadding(14.dp(), 10.dp(), 14.dp(), 10.dp())
            textSize = 14f
            setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 10.dp()
            }
        }
        val save = TextView(requireContext()).apply {
            text = "Сохранить"
            setTextColor(ContextCompat.getColor(requireContext(), R.color.fi_primary_start))
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 10.dp(), 0, 0)
            setDebouncedClickListener {
                onSave(input.text?.toString().orEmpty().trim())
            }
        }
        card.addView(input, 1)
        card.addView(save)
        container.addView(card)
    }

    private fun addSectionTitle(container: LinearLayout, title: String) {
        container.addView(
            TextView(requireContext()).apply {
                text = title
                setTextColor(ContextCompat.getColor(requireContext(), R.color.fi_primary_start))
                textSize = 14f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 18.dp()
                    bottomMargin = 2.dp()
                }
            }
        )
    }

    private fun Int.dp(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
}
