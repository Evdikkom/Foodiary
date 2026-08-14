package com.example.foodiary.presentation.dialog

import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.example.foodiary.R

class DailyScreenMenuDialogFragment : DialogFragment(R.layout.dialog_daily_screen_menu) {

    companion object {
        const val REQUEST_KEY = "daily_screen_menu_request"
        const val RESULT_ACTION = "result_action"
        const val ACTION_COPY_DAY = "action_copy_day"
        const val ACTION_WEATHER = "action_weather"

        private const val ARG_SELECTED_DAY_LABEL = "arg_selected_day_label"

        fun newInstance(selectedDayLabel: String): DailyScreenMenuDialogFragment {
            return DailyScreenMenuDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SELECTED_DAY_LABEL, selectedDayLabel)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            setGravity(Gravity.TOP)
            setBackgroundDrawableResource(android.R.color.transparent)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.32f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                attributes = attributes.apply { blurBehindRadius = 22 }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val selectedDayLabel = arguments?.getString(ARG_SELECTED_DAY_LABEL).orEmpty()

        view.findViewById<TextView>(R.id.textCopyDaySubtitle).text =
            "Скопировать рацион в $selectedDayLabel"

        view.findViewById<ImageView>(R.id.buttonClose).setOnClickListener {
            dismissAllowingStateLoss()
        }
        view.findViewById<View>(R.id.optionCopyDay).setOnClickListener {
            submitAction(ACTION_COPY_DAY)
        }
        view.findViewById<View>(R.id.optionWeather).setOnClickListener {
            submitAction(ACTION_WEATHER)
        }
    }

    private fun submitAction(action: String) {
        parentFragmentManager.setFragmentResult(
            REQUEST_KEY,
            bundleOf(RESULT_ACTION to action)
        )
        dismissAllowingStateLoss()
    }
}
