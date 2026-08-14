package com.example.foodiary.presentation.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

object ImageCompressionUtils {

    private const val DEFAULT_MAX_BYTES = 950 * 1024
    private const val DEFAULT_MAX_DIMENSION = 1280
    private const val MIN_JPEG_QUALITY = 55
    private const val INITIAL_JPEG_QUALITY = 82

    fun compressForUpload(
        sourceFile: File,
        outputDir: File,
        maxBytes: Int = DEFAULT_MAX_BYTES,
        maxDimension: Int = DEFAULT_MAX_DIMENSION,
    ): File {
        require(sourceFile.exists()) { "Файл изображения не найден: ${sourceFile.absolutePath}" }

        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        val bitmap = decodeScaledBitmap(sourceFile, maxDimension)
            ?: throw IllegalStateException("Не удалось прочитать изображение для отправки")

        val rotatedBitmap = applyExifRotationIfNeeded(sourceFile, bitmap)
        val outputFile = File(outputDir, "upload_${System.currentTimeMillis()}.jpg")

        var quality = INITIAL_JPEG_QUALITY
        writeBitmap(rotatedBitmap, outputFile, quality)

        while (outputFile.length() > maxBytes && quality > MIN_JPEG_QUALITY) {
            quality -= 7
            writeBitmap(rotatedBitmap, outputFile, quality)
        }

        if (rotatedBitmap !== bitmap) {
            bitmap.recycle()
        }
        rotatedBitmap.recycle()

        return outputFile
    }

    private fun decodeScaledBitmap(sourceFile: File, maxDimension: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(sourceFile.absolutePath, bounds)

        val maxSourceDimension = max(bounds.outWidth, bounds.outHeight)
        val sampleSize = calculateInSampleSize(maxSourceDimension, maxDimension)

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(sourceFile.absolutePath, options)
    }

    private fun calculateInSampleSize(sourceMaxDimension: Int, targetMaxDimension: Int): Int {
        var sampleSize = 1
        while (sourceMaxDimension / sampleSize > targetMaxDimension * 2) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun applyExifRotationIfNeeded(sourceFile: File, bitmap: Bitmap): Bitmap {
        val exif = ExifInterface(sourceFile.absolutePath)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )

        val rotationDegrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }

        if (rotationDegrees == 0f) {
            return bitmap
        }

        val matrix = Matrix().apply {
            postRotate(rotationDegrees)
        }

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun writeBitmap(bitmap: Bitmap, outputFile: File, quality: Int) {
        FileOutputStream(outputFile).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            stream.flush()
        }
    }
}
