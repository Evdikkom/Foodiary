package com.example.foodiary.presentation.fragment

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.foodiary.R
import com.example.foodiary.databinding.FragmentFoodPhotoCaptureBinding
import com.example.foodiary.domain.model.MealType
import com.example.foodiary.presentation.util.FoodiaryMotionPattern
import com.example.foodiary.presentation.util.popBackStackSafely
import com.example.foodiary.presentation.util.replaceFragmentSafely
import com.example.foodiary.presentation.util.setDebouncedClickListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class FoodPhotoCameraFragment : Fragment(R.layout.fragment_food_photo_capture) {

    companion object {
        private const val ARG_MEAL_TYPE = "arg_meal_type"
        private const val ARG_TARGET_DAY_START = "arg_target_day_start"

        fun newInstance(
            mealType: MealType,
            targetDayStart: Long
        ): FoodPhotoCameraFragment {
            return FoodPhotoCameraFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_MEAL_TYPE, mealType.name)
                    putLong(ARG_TARGET_DAY_START, targetDayStart)
                }
            }
        }
    }

    private val mealType: MealType by lazy {
        arguments?.getString(ARG_MEAL_TYPE)
            ?.let { runCatching { MealType.valueOf(it) }.getOrNull() }
            ?: MealType.BREAKFAST
    }

    private val targetDayStart: Long by lazy {
        arguments?.getLong(ARG_TARGET_DAY_START) ?: System.currentTimeMillis()
    }

    private var _binding: FragmentFoodPhotoCaptureBinding? = null
    private val binding get() = _binding!!

    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var currentCamera: Camera? = null
    private var cameraExecutor: ExecutorService? = null

    private var isTorchEnabled = false
    private var isCapturing = false
    private var isCameraReady = false

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCameraFlow()
        } else {
            renderPermissionState()
        }
    }

    private val galleryImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            importFromGallery(uri)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentFoodPhotoCaptureBinding.bind(view)
        cameraExecutor = Executors.newSingleThreadExecutor()

        setupUi()
        startCameraFlow()
    }

    override fun onResume() {
        super.onResume()
        if (!isCapturing && hasCameraPermission() && currentCamera == null) {
            startCameraFlow()
        }
    }

    override fun onDestroyView() {
        stopCamera()
        _binding = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor?.shutdown()
        cameraExecutor = null
    }

    private fun setupUi() {
        (binding.topBar.getChildAt(1) as? TextView)?.text = "Фото блюда"
        binding.scanSubtitle.text = "Старайтесь, чтобы блюдо целиком попало в кадр"

        binding.buttonBack.setDebouncedClickListener {
            popBackStackSafely()
        }

        binding.buttonManualEntry.text = "Выбрать из галереи"
        binding.buttonManualEntry.isEnabled = true
        binding.buttonManualEntry.alpha = 1f
        binding.buttonManualEntry.setDebouncedClickListener {
            galleryImageLauncher.launch("image/*")
        }

        bindCaptureAction()

        binding.buttonTorch.setDebouncedClickListener {
            toggleTorch()
        }
    }

    private fun startCameraFlow() {
        if (!hasCameraPermission()) {
            renderPermissionState()
            return
        }

        bindCaptureAction()
        isCameraReady = false
        renderScannerState(
            status = "Подготавливаем камеру",
            hint = "Сейчас откроем камеру. После этого можно сделать снимок или выбрать фото из галереи.",
            showPrimaryAction = true,
            primaryActionText = "Сделать снимок"
        )
        binding.progressScanner.isVisible = true
        binding.buttonPrimaryAction.isEnabled = false
        bindCameraUseCases()
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

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                try {
                    provider.unbindAll()
                    currentCamera = provider.bindToLifecycle(
                        viewLifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture
                    )
                    isCameraReady = true
                    binding.progressScanner.isVisible = false
                    binding.buttonPrimaryAction.isEnabled = true
                    binding.textStatus.text = "Сфотографируйте блюдо"
                    binding.textHint.text =
                        "Старайтесь, чтобы тарелка целиком была в кадре. После снимка мы отправим фото на серверный анализ."
                    updateTorchAvailability()
                } catch (_: Exception) {
                    isCameraReady = false
                    binding.progressScanner.isVisible = false
                    renderScannerState(
                        status = "Не удалось запустить камеру",
                        hint = "Проверьте доступ к камере и попробуйте ещё раз. При желании можно выбрать фото из галереи.",
                        showPrimaryAction = true,
                        primaryActionText = "Повторить"
                    )
                    binding.buttonPrimaryAction.isEnabled = true
                    binding.buttonPrimaryAction.setOnClickListener {
                        startCameraFlow()
                    }
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    private fun capturePhoto() {
        val imageCapture = imageCapture ?: run {
            startCameraFlow()
            return
        }
        val context = context ?: return
        if (isCapturing) return

        isCapturing = true
        binding.progressScanner.isVisible = true
        binding.textStatus.text = "Сохраняем снимок..."
        binding.buttonPrimaryAction.isEnabled = false
        binding.buttonManualEntry.isEnabled = false

        val photoFile = File(
            context.cacheDir,
            "food_photo_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())}.jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    isCapturing = false
                    binding.progressScanner.isVisible = false
                    binding.buttonPrimaryAction.isEnabled = true
                    binding.buttonManualEntry.isEnabled = true
                    openAnalysis(photoFile.absolutePath)
                }

                override fun onError(exception: ImageCaptureException) {
                    isCapturing = false
                    binding.progressScanner.isVisible = false
                    binding.buttonPrimaryAction.isEnabled = true
                    binding.buttonManualEntry.isEnabled = true
                    binding.textStatus.text =
                        exception.message ?: "Не удалось сделать снимок"
                }
            }
        )
    }

    private fun importFromGallery(uri: Uri) {
        binding.progressScanner.isVisible = true
        binding.textStatus.text = "Подготавливаем фото из галереи..."
        binding.buttonPrimaryAction.isEnabled = false
        binding.buttonManualEntry.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val imagePath = withContext(Dispatchers.IO) {
                    copyGalleryImageToCache(uri)
                }
                binding.progressScanner.isVisible = false
                binding.buttonPrimaryAction.isEnabled = true
                binding.buttonManualEntry.isEnabled = true
                openAnalysis(imagePath)
            } catch (_: Exception) {
                binding.progressScanner.isVisible = false
                binding.buttonPrimaryAction.isEnabled = true
                binding.buttonManualEntry.isEnabled = true
                binding.textStatus.text = "Не удалось подготовить фото из галереи"
                binding.textHint.text = "Попробуйте выбрать другой снимок или сфотографировать блюдо прямо сейчас."
            }
        }
    }

    private fun copyGalleryImageToCache(uri: Uri): String {
        val resolver = requireContext().contentResolver
        val extension = when (resolver.getType(uri)?.lowercase(Locale.US)) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }

        val target = File(
            requireContext().cacheDir,
            "food_gallery_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())}.$extension"
        )

        resolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: error("Unable to open image from gallery")

        return target.absolutePath
    }

    private fun openAnalysis(imagePath: String) {
        stopCamera()
        replaceFragmentSafely(
            FoodPhotoSelectionFragment.newInstance(
                mealType = mealType,
                imagePath = imagePath,
                targetDayStart = targetDayStart
            ),
            motionPattern = FoodiaryMotionPattern.MODAL_AXIS_Y
        )
    }

    private fun renderPermissionState() {
        stopCamera()
        isCameraReady = false
        renderScannerState(
            status = "Нужен доступ к камере",
            hint = "Разрешите доступ к камере, чтобы сфотографировать блюдо прямо в приложении. Либо выберите уже готовое фото из галереи.",
            showPrimaryAction = true,
            primaryActionText = "Разрешить доступ",
            showTorch = false
        )
        binding.progressScanner.isVisible = false
        binding.buttonPrimaryAction.isEnabled = true
        binding.buttonPrimaryAction.setOnClickListener {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun renderScannerState(
        status: String,
        hint: String,
        showPrimaryAction: Boolean = true,
        primaryActionText: String = "Сделать снимок",
        showTorch: Boolean = true
    ) {
        binding.textStatus.text = status
        binding.textHint.text = hint
        binding.buttonPrimaryAction.text = primaryActionText
        binding.buttonPrimaryAction.isVisible = showPrimaryAction
        binding.buttonTorch.isVisible = showTorch
    }

    private fun toggleTorch() {
        val camera = currentCamera ?: return
        if (!camera.cameraInfo.hasFlashUnit()) return

        isTorchEnabled = !isTorchEnabled
        camera.cameraControl.enableTorch(isTorchEnabled)
        binding.buttonTorch.text = if (isTorchEnabled) "Вспышка: вкл" else "Вспышка"
    }

    private fun updateTorchAvailability() {
        val hasFlash = currentCamera?.cameraInfo?.hasFlashUnit() == true
        binding.buttonTorch.isEnabled = hasFlash
        binding.buttonTorch.alpha = if (hasFlash) 1f else 0.5f
        binding.buttonTorch.text = when {
            !hasFlash -> "Без вспышки"
            isTorchEnabled -> "Вспышка: вкл"
            else -> "Вспышка"
        }
    }

    private fun stopCamera() {
        runCatching { cameraProvider?.unbindAll() }
        currentCamera = null
        preview = null
        imageCapture = null
        isTorchEnabled = false
        isCameraReady = false
    }

    private fun bindCaptureAction() {
        binding.buttonPrimaryAction.setDebouncedClickListener {
            if (!hasCameraPermission()) {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                return@setDebouncedClickListener
            }

            if (!isCameraReady || imageCapture == null) {
                startCameraFlow()
                return@setDebouncedClickListener
            }

            capturePhoto()
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }
}
