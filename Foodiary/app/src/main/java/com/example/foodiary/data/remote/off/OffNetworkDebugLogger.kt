package com.example.foodiary.data.remote.off

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.foodiary.BuildConfig
import com.example.foodiary.data.remote.network.FoodiaryNetworkContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object OffNetworkDebugLogger {

    private const val TAG = "FoodiaryOFF"
    private const val DIRECTORY_NAME = "off-debug"
    private const val FILE_NAME = "off_network_debug.log"
    private const val PREFERENCES_NAME = "off_network_debug_logger"
    private const val KEY_LAST_CLEANUP_DAY = "last_cleanup_day"
    private const val MAX_FILE_BYTES = 768 * 1024
    private val lock = Any()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun log(message: String, error: Throwable? = null) {
        if (!BuildConfig.DEBUG) return

        val entry = buildEntry(message, error)
        runCatching {
            Log.d(TAG, entry)
        }

        val context = FoodiaryNetworkContext.context() ?: return
        synchronized(lock) {
            cleanupIfNewDayLocked(context)
            writeTo(internalLogFile(context), entry)
            externalLogFile(context)?.let { writeTo(it, entry) }
        }
    }

    fun cleanupIfNeeded(context: Context) {
        if (!BuildConfig.DEBUG) return

        synchronized(lock) {
            cleanupIfNewDayLocked(context.applicationContext)
        }
    }

    fun read(context: Context, maxChars: Int = 96_000): String {
        if (!BuildConfig.DEBUG) {
            return "Диагностика Open Food Facts доступна только в debug-сборке."
        }

        val appContext = context.applicationContext
        val text = synchronized(lock) {
            cleanupIfNewDayLocked(appContext)
            val file = externalLogFile(appContext)?.takeIf { it.exists() }
                ?: internalLogFile(appContext)

            if (file.exists()) {
                file.readText()
            } else {
                "OFF debug log is empty"
            }
        }

        return if (text.length <= maxChars) {
            text
        } else {
            "... trimmed first ${text.length - maxChars} chars ...\n" + text.takeLast(maxChars)
        }
    }

    fun readablePath(context: Context): String {
        if (!BuildConfig.DEBUG) {
            return "Только debug-сборка"
        }

        val appContext = context.applicationContext
        synchronized(lock) {
            cleanupIfNewDayLocked(appContext)
        }
        return externalLogFile(appContext)?.absolutePath ?: internalLogFile(appContext).absolutePath
    }

    private fun buildEntry(message: String, error: Throwable?): String {
        return buildString {
            append('[')
            append(dateFormat.format(Date()))
            append("] ")
            append(message)
            if (error != null) {
                appendLine()
                append(formatThrowable(error))
            }
            appendLine()
        }
    }

    private fun formatThrowable(error: Throwable): String {
        return buildString {
            append("exception=")
            append(error.javaClass.name)
            append(": ")
            append(error.message.orEmpty())

            var cause = error.cause
            var depth = 0
            while (cause != null && depth < 8) {
                appendLine()
                append("caused_by=")
                append(cause.javaClass.name)
                append(": ")
                append(cause.message.orEmpty())
                cause = cause.cause
                depth++
            }
        }
    }

    private fun writeTo(file: File, entry: String) {
        file.parentFile?.mkdirs()
        if (file.exists() && file.length() > MAX_FILE_BYTES) {
            file.delete()
        }
        file.appendText(entry)
    }

    private fun cleanupIfNewDayLocked(context: Context) {
        val today = currentDayKey()
        val prefs = preferences(context)
        if (prefs.getString(KEY_LAST_CLEANUP_DAY, null) == today) return

        deleteLogFiles(context)
        prefs.edit()
            .putString(KEY_LAST_CLEANUP_DAY, today)
            .apply()
        runCatching {
            Log.d(TAG, "OFF debug log auto-cleaned for day=$today")
        }
    }

    private fun deleteLogFiles(context: Context) {
        internalLogFile(context).delete()
        externalLogFile(context)?.delete()
    }

    private fun internalLogFile(context: Context): File {
        return File(File(context.filesDir, DIRECTORY_NAME), FILE_NAME)
    }

    private fun externalLogFile(context: Context): File? {
        val root = context.getExternalFilesDir(DIRECTORY_NAME) ?: return null
        return File(root, FILE_NAME)
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private fun currentDayKey(): String = dayFormat.format(Date())

    fun buildDeviceHeader(): String {
        return buildString {
            appendLine("Foodiary OFF diagnostics")
            appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Android: ${Build.VERSION.RELEASE} SDK ${Build.VERSION.SDK_INT}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        }
    }
}
