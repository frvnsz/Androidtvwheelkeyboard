package com.example.wheelkeyboard.settings

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import com.example.wheelkeyboard.BuildConfig
import com.example.wheelkeyboard.R
import java.net.HttpURLConnection
import java.net.URL

class SetupActivity : Activity() {
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        findViewById<android.view.View>(R.id.enableKeyboardButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        findViewById<android.view.View>(R.id.chooseKeyboardButton).setOnClickListener {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }
        findViewById<android.view.View>(R.id.voicePermissionButton).setOnClickListener {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
        }
        checkForUpdates()
    }

    private fun checkForUpdates() {
        val updateCheckUrl = BuildConfig.UPDATE_CHECK_URL.takeIf { it.isNotBlank() } ?: return
        Thread {
            val update = runCatching { fetchUpdate(updateCheckUrl) }.getOrNull() ?: return@Thread
            if (update.versionCode <= BuildConfig.VERSION_CODE) return@Thread
            mainHandler.post { showUpdateDialog(update) }
        }.start()
    }

    private fun fetchUpdate(updateCheckUrl: String): UpdateInfo? {
        val connection = URL(updateCheckUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = UPDATE_TIMEOUT_MILLIS
        connection.readTimeout = UPDATE_TIMEOUT_MILLIS
        connection.setRequestProperty("Accept", "application/json")
        return try {
            if (connection.responseCode !in 200..299) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val versionCode = VERSION_CODE_REGEX.find(body)?.groupValues?.get(1)?.toIntOrNull() ?: return null
            val versionName = VERSION_NAME_REGEX.find(body)?.groupValues?.get(1)?.unescapeJson() ?: versionCode.toString()
            val apkUrl = APK_URL_REGEX.find(body)?.groupValues?.get(1)?.unescapeJson()
                ?: BuildConfig.UPDATE_APK_URL.takeIf { it.isNotBlank() }
                ?: return null
            val notes = NOTES_REGEX.find(body)?.groupValues?.get(1)?.unescapeJson().orEmpty()
            UpdateInfo(versionCode = versionCode, versionName = versionName, apkUrl = apkUrl, notes = notes)
        } finally {
            connection.disconnect()
        }
    }

    private fun showUpdateDialog(update: UpdateInfo) {
        if (isFinishing || isDestroyed) return
        val message = buildString {
            append(getString(R.string.update_available_body, update.versionName, BuildConfig.VERSION_NAME))
            if (update.notes.isNotBlank()) {
                append("\n\n")
                append(update.notes)
            }
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.update_available_title)
            .setMessage(message)
            .setPositiveButton(R.string.update_install) { _, _ -> openUpdate(update.apkUrl) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openUpdate(apkUrl: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl)))
    }

    private fun String.unescapeJson(): String =
        replace("\\/", "/")
            .replace("\\n", "\n")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")

    private data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String,
        val notes: String
    )

    private companion object {
        const val REQUEST_RECORD_AUDIO = 1001
        const val UPDATE_TIMEOUT_MILLIS = 5_000
        val VERSION_CODE_REGEX = Regex("\\\"versionCode\\\"\\s*:\\s*(\\d+)")
        val VERSION_NAME_REGEX = Regex("\\\"versionName\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"")
        val APK_URL_REGEX = Regex("\\\"apkUrl\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"")
        val NOTES_REGEX = Regex("\\\"notes\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"")
    }
}
