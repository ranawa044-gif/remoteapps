package com.remoteapps

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class RemoteApplication : Application() {

    companion object {
        const val CHANNEL_ID = "remoteapps_host_service_channel"
        const val CHANNEL_NAME = "Remote Host Service"
        lateinit var instance: RemoteApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifikasi status layanan remote control HP"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
