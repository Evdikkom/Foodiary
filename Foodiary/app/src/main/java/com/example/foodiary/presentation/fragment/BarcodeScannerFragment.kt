package com.example.foodiary.presentation.fragment

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.example.foodiary.R
import com.example.foodiary.databinding.FragmentBarcodeScannerBinding
import com.example.foodiary.presentation.dialog.BarcodeImportDialogFragment
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class BarcodeScannerFragment : Fragment(R.layout.fragment_barcode_scanner) {

    companion object {
        private const val MANUAL_REQUEST_KEY = "barcode_scanner_manual_request"

        fun newInstance(): BarcodeScannerFragment = BarcodeScannerFragment()
    }

    private var _binding: FragmentBarcodeScannerBinding? = null
    private val binding get() = _binding!!

    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var currentCamera: Camera? = null
    private var cameraExecutor: ExecutorService? = null

    private var isAnalyzingFrame = false
    private var isBarcodeHandled = false
    private var isTorchEnabled = false

    private val barcodeScanner by lazy {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_CODE_39,
                Barcode.FORMAT_ITF,
                Barcode.FORMAT_CODABAR
            )
            .build()

        BarcodeScanning.getClient(options)
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startScannerFlow()
        } else {
            renderPermissionState()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentBarcodeScannerBinding.bind(view)
        cameraExecutor = Executors.newSingleThreadExecutor()

        setupUi()
        setupManualImportResultListener()
        startScannerFlow()
    }

    override fun onResume() {
        super.onResume()
        if (!isBarcodeHandled && hasCameraPermission() && currentCamera == null) {
            bindCameraUseCases()
        }
    }

    override fun onDestroyView() {
        stopCamera()
        _binding = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { barcodeScanner.close() }
        cameraExecutor?.shutdown()
        cameraExecutor = null
    }

    private fun setupUi() {
        binding.buttonBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.buttonManualEntry.setOnClickListener {
            BarcodeImportDialogFragment.newInstance(MANUAL_REQUEST_KEY)
                .show(parentFragmentManager, "barcode_import_manual")
        }

        binding.buttonPrimaryAction.setOnClickListener {
            if (hasCameraPermission()) {
                isBarcodeHandled = false
                bindCameraUseCases()
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        binding.buttonTorch.setOnClickListener {
            toggleTorch()
        }
    }

    private fun setupManualImportResultListener() {
        parentFragmentManager.setFragmentResultListener(
            MANUAL_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val barcode = bundle.getString(BarcodeImportDialogFragment.RESULT_BARCODE).orEmpty().trim()
            if (barcode.isNotBlank()) {
                dispatchBarcodeResultAndExit(barcode)
            }
        }
    }

    private fun startScannerFlow() {
        if (hasCameraPermission()) {
            renderScannerState(
                status = "Наведите камеру на штрихкод продукта",
                hint = "Штрихкод будет считан автоматически, когда попадёт в рамку."
            )
            bindCameraUseCases()
        } else {
            renderPermissionState()
        }
    }

    private fun bindCameraUseCases() {
        val context = context ?: return
        val providerFuture = ProcessCameraProvider.getInstance(context)

        providerFuture.addListener(
            {
                val provider = providerFuture.get()
                cameraProvider = provider

                preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

                imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        val executor = cameraExecutor ?: return@also
                        analysis.setAnalyzer(executor) { imageProxy ->
                            analyzeFrame(imageProxy)
                        }
                    }

                try {
                    provider.unbindAll()
                    currentCamera = provider.bindToLifecycle(
                        viewLifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                    updateTorchAvailability()
                    binding.progressScanner.isVisible = false
                } catch (_: Exception) {
                    renderScannerState(
                        status = "Не удалось запустить камеру",
                        hint = "Проверьте, доступна ли камера на устройстве, и попробуйте ещё раз.",
                        showPrimaryAction = true,
                        primaryActionText = "Повторить"
                    )
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    @androidx.annotation.OptIn(markerClass = [ExperimentalGetImage::class])
    private fun analyzeFrame(imageProxy: androidx.camera.core.ImageProxy) {
        if (isBarcodeHandled || isAnalyzingFrame) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        isAnalyzingFrame = true
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        barcodeScanner.process(image)
            .addOnSuccessListener { barcodes ->
                val rawValue = barcodes
                    .firstOrNull { !it.rawValue.isNullOrBlank() }
                    ?.rawValue
                    ?.trim()
                    .orEmpty()

                if (rawValue.isNotBlank()) {
                    binding.textStatus.text = "Штрихкод считан: $rawValue"
                    binding.progressScanner.isVisible = true
                    dispatchBarcodeResultAndExit(rawValue)
                }
            }
            .addOnFailureListener {
                // В этом месте намеренно не показываем пользователю ошибку на каждый кадр,
                // чтобы не было визуального шума при кратковременных сбоях анализа.
            }
            .addOnCompleteListener {
                isAnalyzingFrame = false
                imageProxy.close()
            }
    }

    private fun dispatchBarcodeResultAndExit(barcode: String) {
        if (isBarcodeHandled) return

        isBarcodeHandled = true
        stopCamera()

        parentFragmentManager.setFragmentResult(
            BarcodeImportDialogFragment.REQUEST_KEY,
            Bundle().apply {
                putString(BarcodeImportDialogFragment.RESULT_BARCODE, barcode)
            }
        )
        parentFragmentManager.popBackStack()
    }

    private fun renderPermissionState() {
        stopCamera()
        renderScannerState(
            status = "Нужен доступ к камере",
            hint = "Разрешите доступ к камере, чтобы сканировать штрихкод прямо в приложении.",
            showPrimaryAction = true,
            primaryActionText = "Разрешить доступ",
            showTorch = false
        )
    }

    private fun renderScannerState(
        status: String,
        hint: String,
        showPrimaryAction: Boolean = false,
        primaryActionText: String = "Повторить",
        showTorch: Boolean = true
    ) {
        binding.textStatus.text = status
        binding.textHint.text = hint
        binding.buttonPrimaryAction.isVisible = showPrimaryAction
        binding.buttonPrimaryAction.text = primaryActionText
        binding.progressScanner.isVisible = false
        binding.buttonTorch.isVisible = showTorch && (currentCamera?.cameraInfo?.hasFlashUnit() == true)
        binding.scanFrame.isVisible = hasCameraPermission()
        binding.scanSubtitle.isVisible = hasCameraPermission()
    }

    private fun updateTorchAvailability() {
        val hasFlash = currentCamera?.cameraInfo?.hasFlashUnit() == true
        binding.buttonTorch.isVisible = hasFlash
        if (!hasFlash) {
            isTorchEnabled = false
            binding.buttonTorch.text = "Фонарик"
        }
    }

    private fun toggleTorch() {
        val camera = currentCamera ?: return
        val hasFlash = camera.cameraInfo.hasFlashUnit()
        if (!hasFlash) return

        isTorchEnabled = !isTorchEnabled
        camera.cameraControl.enableTorch(isTorchEnabled)
        binding.buttonTorch.text = if (isTorchEnabled) "Фонарик: вкл" else "Фонарик"
    }

    private fun stopCamera() {
        runCatching {
            imageAnalysis?.clearAnalyzer()
            cameraProvider?.unbindAll()
        }
        imageAnalysis = null
        preview = null
        currentCamera = null
        isTorchEnabled = false
    }

    private fun hasCameraPermission(): Boolean {
        val context = context ?: return false
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }
}
