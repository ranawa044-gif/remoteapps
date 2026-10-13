package com.remoteapps.core.security

import android.content.Context
import android.content.SharedPreferences
import kotlin.random.Random

class PreferenceManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var deviceId: String
        get() {
            var id = prefs.getString(KEY_DEVICE_ID, null)
            if (id.isNullOrEmpty()) {
                id = generateRandomDeviceId()
                prefs.edit().putString(KEY_DEVICE_ID, id).apply()
            }
            return id
        }
        set(value) = prefs.edit().putString(KEY_DEVICE_ID, value).apply()

    var unattendedPin: String
        get() = prefs.getString(KEY_UNATTENDED_PIN, "123456") ?: "123456"
        set(value) = prefs.edit().putString(KEY_UNATTENDED_PIN, value).apply()

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, "ws://10.0.2.2:3000") ?: "ws://10.0.2.2:3000"
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()

    var lastTargetId: String
        get() = prefs.getString(KEY_LAST_TARGET_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_TARGET_ID, value).apply()

    private fun generateRandomDeviceId(): String {
        val p1 = Random.nextInt(100, 1000)
        val p2 = Random.nextInt(100, 1000)
        val p3 = Random.nextInt(100, 1000)
        return "$p1-$p2-$p3"
    }

    companion object {
        private const val PREFS_NAME = "remoteapps_prefs"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_UNATTENDED_PIN = "unattended_pin"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_LAST_TARGET_ID = "last_target_id"
    }
}
