package com.example.foodiary.presentation.fragment

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.foodiary.BuildConfig
import com.example.foodiary.R
import com.example.foodiary.data.local.preferences.LocalAccountPreferences
import com.example.foodiary.data.remote.off.OffNetworkDebugLogger
import com.example.foodiary.presentation.util.popBackStackSafely
import com.example.foodiary.presentation.util.setDebouncedClickListener

class SupportFragment : Fragment(R.layout.fragment_support) {

    companion object {
        private const val SUPPORT_EMAIL = "evdikkom2004@mail.ru"

        fun newInstance(): SupportFragment = SupportFragment()
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageView>(R.id.buttonBack).setDebouncedClickListener {
            popBackStackSafely()
        }
        view.findViewById<TextView>(R.id.textSupportEmail).text = SUPPORT_EMAIL

        val faqContainer = view.findViewById<LinearLayout>(R.id.layoutFaqCards)
        faqContainer.removeAllViews()
        addFaqCard(
            faqContainer,
            "Если не совпадает КБЖУ",
            "Проверьте массу продукта, источник данных и то, не использовалась ли ручная настройка норм поверх автоматической базы."
        )
        addFaqCard(
            faqContainer,
            "Если не работают фото и камера",
            "Откройте “Настройки сервисов”, проверьте разрешение камеры и убедитесь, что backend и интернет доступны."
        )
        addFaqCard(
            faqContainer,
            "Если рекомендации кажутся странными",
            "На рекомендации влияют история питания, цель, текущий дефицит по КБЖУ и ограничения пользователя."
        )

        view.findViewById<Button>(R.id.buttonSendEmail).setDebouncedClickListener {
            val diagnostics = buildOffDiagnostics()
            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = android.net.Uri.parse("mailto:$SUPPORT_EMAIL")
                putExtra(Intent.EXTRA_SUBJECT, "Foodiary support")
                putExtra(Intent.EXTRA_TEXT, diagnostics)
            }
            if (emailIntent.resolveActivity(requireContext().packageManager) == null) {
                Toast.makeText(requireContext(), "На устройстве не найден почтовый клиент", Toast.LENGTH_SHORT).show()
                return@setDebouncedClickListener
            }
            startActivity(emailIntent)
        }

        view.findViewById<TextView>(R.id.buttonShareDiagnostics).setDebouncedClickListener {
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, buildOffDiagnostics())
                    },
                    "Поделиться диагностикой"
                )
            )
        }
    }

    private fun addFaqCard(container: LinearLayout, title: String, note: String) {
        val card = layoutInflater.inflate(R.layout.item_profile_info_card, container, false)
        card.findViewById<TextView>(R.id.textCardTitle).text = title
        card.findViewById<TextView>(R.id.textCardValue).text = "Краткая подсказка"
        card.findViewById<TextView>(R.id.textCardNote).apply {
            text = note
            visibility = android.view.View.VISIBLE
        }
        container.addView(card)
    }

    private fun buildOffDiagnostics(): String {
        val account = LocalAccountPreferences(requireContext()).getAccount()
        return buildString {
            appendLine(OffNetworkDebugLogger.buildDeviceHeader())
            appendLine("Email: ${account?.email ?: "not_set"}")
            appendLine("Name: ${account?.displayName ?: "not_set"}")
            appendLine("OFF log path: ${OffNetworkDebugLogger.readablePath(requireContext())}")
            appendLine()
            appendLine("Describe the problem below:")
            appendLine("- What you did")
            appendLine("- What you expected")
            appendLine("- What actually happened")
            appendLine()
            appendLine("OFF network log:")
            append(OffNetworkDebugLogger.read(requireContext()))
        }
    }

    private fun buildDiagnostics(): String {
        val account = LocalAccountPreferences(requireContext()).getAccount()
        return buildString {
            appendLine("Foodiary support request")
            appendLine("Version: ${BuildConfig.VERSION_NAME}")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Email: ${account?.email ?: "not_set"}")
            appendLine("Name: ${account?.displayName ?: "not_set"}")
            appendLine()
            appendLine("Опишите проблему ниже:")
            appendLine("- Что вы делали")
            appendLine("- Что ожидали увидеть")
            appendLine("- Что произошло фактически")
        }
    }
}
