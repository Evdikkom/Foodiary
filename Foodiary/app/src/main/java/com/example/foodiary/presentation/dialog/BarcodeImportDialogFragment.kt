package com.example.foodiary.presentation.dialog

import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import androidx.fragment.app.DialogFragment
import com.example.foodiary.R

class BarcodeImportDialogFragment : DialogFragment(R.layout.dialog_barcode_import) {

    companion object {
        private const val ARG_TARGET_REQUEST_KEY = "arg_target_request_key"

        const val REQUEST_KEY = "barcode_import_request"
        const val RESULT_BARCODE = "result_barcode"

        fun newInstance(targetRequestKey: String = REQUEST_KEY): BarcodeImportDialogFragment {
            return BarcodeImportDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TARGET_REQUEST_KEY, targetRequestKey)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            setBackgroundDrawableResource(android.R.color.transparent)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.45f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                attributes = attributes.apply { blurBehindRadius = 18 }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageView>(R.id.buttonClose).setOnClickListener {
            dismissAllowingStateLoss()
        }

        view.findViewById<View>(R.id.buttonImport).setOnClickListener {
            val input = view.findViewById<EditText>(R.id.editBarcode)
            val barcode = input.text?.toString().orEmpty().trim()
            if (barcode.isBlank()) {
                input.error = "Введите штрихкод"
                input.requestFocus()
                return@setOnClickListener
            }
            parentFragmentManager.setFragmentResult(
                arguments?.getString(ARG_TARGET_REQUEST_KEY).orEmpty().ifBlank { REQUEST_KEY },
                Bundle().apply { putString(RESULT_BARCODE, barcode) }
            )
            dismissAllowingStateLoss()
        }
    }
}
