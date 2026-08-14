package com.example.foodiary.presentation.fragment

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.foodiary.R
import com.example.foodiary.data.local.database.AppDatabase
import com.example.foodiary.data.local.preferences.LocalAccountPreferences
import com.example.foodiary.data.repository.UserRepositoryImpl
import com.example.foodiary.domain.validation.EmailAddressValidator
import com.example.foodiary.presentation.util.popBackStackSafely
import com.example.foodiary.presentation.util.setDebouncedClickListener
import kotlinx.coroutines.launch

class AccountProfileFragment : Fragment(R.layout.fragment_account_profile) {

    companion object {
        private const val ARG_SETUP_MODE = "arg_setup_mode"
        private const val ARG_RETURN_DAY_START = "arg_return_day_start"

        fun newSetupInstance(): AccountProfileFragment {
            return AccountProfileFragment().apply {
                arguments = Bundle().apply { putBoolean(ARG_SETUP_MODE, true) }
            }
        }

        fun newInstance(returnDayStart: Long = System.currentTimeMillis()): AccountProfileFragment {
            return AccountProfileFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_SETUP_MODE, false)
                    putLong(ARG_RETURN_DAY_START, returnDayStart)
                }
            }
        }
    }

    private val setupMode: Boolean by lazy {
        arguments?.getBoolean(ARG_SETUP_MODE, false) == true
    }

    private val returnDayStart: Long by lazy {
        arguments?.getLong(ARG_RETURN_DAY_START) ?: System.currentTimeMillis()
    }

    fun isSetupMode(): Boolean = setupMode

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val accountPreferences = LocalAccountPreferences(requireContext())
        val database = AppDatabase.getInstance(requireContext())
        val userRepository = UserRepositoryImpl(
            userDao = database.userDao(),
            allergenDao = database.allergenDao(),
            userRestrictionDao = database.userRestrictionDao()
        )

        val back = view.findViewById<ImageView>(R.id.buttonBack)
        val title = view.findViewById<TextView>(R.id.textScreenTitle)
        val subtitle = view.findViewById<TextView>(R.id.textScreenSubtitle)
        val accountDescription = view.findViewById<TextView>(R.id.textAccountDescription)
        val localOnlyNote = view.findViewById<TextView>(R.id.textLocalOnlyNote)
        val nameInput = view.findViewById<EditText>(R.id.editDisplayName)
        val emailLabel = view.findViewById<TextView>(R.id.textEmailLabel)
        val emailInput = view.findViewById<EditText>(R.id.editEmail)
        val saveButton = view.findViewById<Button>(R.id.buttonSave)

        val account = accountPreferences.getAccount()
        nameInput.setText(account?.displayName.orEmpty())
        emailInput.setText(account?.email.orEmpty())

        back.isVisible = !setupMode
        back.setDebouncedClickListener { popBackStackSafely() }

        emailLabel.text = "\u041f\u043e\u0447\u0442\u0430 (\u043d\u0435\u043e\u0431\u044f\u0437\u0430\u0442\u0435\u043b\u044c\u043d\u043e)"
        emailInput.hint = "example@mail.com"
        accountDescription.text =
            "\u0418\u043c\u044f \u0438 \u043f\u043e\u0447\u0442\u0430 \u043d\u0435\u043e\u0431\u044f\u0437\u0430\u0442\u0435\u043b\u044c\u043d\u044b. \u041e\u043d\u0438 \u0445\u0440\u0430\u043d\u044f\u0442\u0441\u044f \u043b\u043e\u043a\u0430\u043b\u044c\u043d\u043e \u0438 \u043c\u043e\u0433\u0443\u0442 \u043f\u043e\u043c\u043e\u0447\u044c \u0441 \u043f\u0435\u0440\u0441\u043e\u043d\u0430\u043b\u0438\u0437\u0430\u0446\u0438\u0435\u0439 \u0438 \u043f\u043e\u0434\u0434\u0435\u0440\u0436\u043a\u043e\u0439."

        if (setupMode) {
            title.text = "\u0412\u0445\u043e\u0434 \u0432 Foodiary"
            subtitle.text =
                "\u0421\u043e\u0437\u0434\u0430\u0439\u0442\u0435 \u043b\u043e\u043a\u0430\u043b\u044c\u043d\u044b\u0439 \u043f\u0440\u043e\u0444\u0438\u043b\u044c. \u0418\u043c\u044f \u0438 \u043f\u043e\u0447\u0442\u0443 \u043c\u043e\u0436\u043d\u043e \u0443\u043a\u0430\u0437\u0430\u0442\u044c \u0441\u0435\u0439\u0447\u0430\u0441 \u0438\u043b\u0438 \u043f\u043e\u0437\u0436\u0435."
            localOnlyNote.text =
                "\u041c\u043e\u0436\u043d\u043e \u043f\u0440\u043e\u0434\u043e\u043b\u0436\u0438\u0442\u044c \u0431\u0435\u0437 \u043f\u043e\u0447\u0442\u044b. \u0412\u0441\u0435 \u0434\u0430\u043d\u043d\u044b\u0435 \u044d\u0442\u043e\u0433\u043e \u0431\u043b\u043e\u043a\u0430 \u043e\u0441\u0442\u0430\u044e\u0442\u0441\u044f \u0432 \u043b\u043e\u043a\u0430\u043b\u044c\u043d\u043e\u043c \u043f\u0440\u043e\u0444\u0438\u043b\u0435 Foodiary."
            saveButton.text = "\u041f\u0440\u043e\u0434\u043e\u043b\u0436\u0438\u0442\u044c"
        } else {
            title.text = "\u0423\u0447\u0435\u0442\u043d\u0430\u044f \u0437\u0430\u043f\u0438\u0441\u044c"
            subtitle.text =
                "\u0420\u0435\u0434\u0430\u043a\u0442\u0438\u0440\u0443\u0439\u0442\u0435 \u043b\u043e\u043a\u0430\u043b\u044c\u043d\u044b\u0435 \u043a\u043e\u043d\u0442\u0430\u043a\u0442\u043d\u044b\u0435 \u0434\u0430\u043d\u043d\u044b\u0435 Foodiary."
            localOnlyNote.text =
                "\u041f\u043e\u0447\u0442\u0430 \u0438 \u0438\u043c\u044f \u043d\u0435\u043e\u0431\u044f\u0437\u0430\u0442\u0435\u043b\u044c\u043d\u044b. \u0418\u0445 \u043c\u043e\u0436\u043d\u043e \u0438\u0437\u043c\u0435\u043d\u0438\u0442\u044c \u0438\u043b\u0438 \u043e\u0447\u0438\u0441\u0442\u0438\u0442\u044c \u0432 \u043b\u044e\u0431\u043e\u0439 \u043c\u043e\u043c\u0435\u043d\u0442."
            saveButton.text = "\u0421\u043e\u0445\u0440\u0430\u043d\u0438\u0442\u044c"
        }

        saveButton.setDebouncedClickListener {
            val email = emailInput.text?.toString()?.trim().orEmpty()
            val displayName = nameInput.text?.toString()?.trim().orEmpty()

            if (email.isNotBlank() && !EmailAddressValidator.isValid(email)) {
                emailInput.error = "\u041f\u0440\u043e\u0432\u0435\u0440\u044c\u0442\u0435 \u0444\u043e\u0440\u043c\u0430\u0442 \u043f\u043e\u0447\u0442\u044b"
                Toast.makeText(
                    requireContext(),
                    "\u0412\u0432\u0435\u0434\u0438\u0442\u0435 \u043a\u043e\u0440\u0440\u0435\u043a\u0442\u043d\u0443\u044e \u043f\u043e\u0447\u0442\u0443",
                    Toast.LENGTH_SHORT
                ).show()
                return@setDebouncedClickListener
            }

            emailInput.error = null
            val normalizedEmail = EmailAddressValidator.normalizeOrNull(email).orEmpty()
            accountPreferences.saveAccount(
                email = normalizedEmail,
                displayName = displayName
            )

            if (!setupMode) {
                Toast.makeText(
                    requireContext(),
                    "\u0414\u0430\u043d\u043d\u044b\u0435 \u0443\u0447\u0435\u0442\u043d\u043e\u0439 \u0437\u0430\u043f\u0438\u0441\u0438 \u0441\u043e\u0445\u0440\u0430\u043d\u0435\u043d\u044b",
                    Toast.LENGTH_SHORT
                ).show()
                popBackStackSafely()
                return@setDebouncedClickListener
            }

            viewLifecycleOwner.lifecycleScope.launch {
                val nextFragment = if (userRepository.getCurrentUser() == null) {
                    OnboardingFragment.newInstance()
                } else {
                    DailyNutritionFragment.newInstance(returnDayStart)
                }

                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, nextFragment)
                    .commit()
            }
        }
    }
}
