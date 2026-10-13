package com.remoteapps.host.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.os.PowerManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.remoteapps.core.protocol.InputPacket

class HostAccessibilityService : AccessibilityService() {

    private val tag = "HostAccessibility"
    private var screenWidth = 1080
    private var screenHeight = 2400

    companion object {
        var instance: HostAccessibilityService? = null
            private set
        val isServiceRunning: Boolean
            get() = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        updateScreenDimensions()
        Log.d(tag, "HostAccessibilityService terhubung. Resolusi: ${screenWidth}x${screenHeight}")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Tidak perlu memproses event UI lokal
    }

    override fun onInterrupt() {
        Log.w(tag, "HostAccessibilityService diinterupsi")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
        Log.d(tag, "HostAccessibilityService dihancurkan")
    }

    fun updateScreenDimensions() {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager?.currentWindowMetrics?.bounds
            if (bounds != null) {
                screenWidth = bounds.width()
                screenHeight = bounds.height()
                return
            }
        }
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager?.defaultDisplay?.getRealMetrics(dm)
        if (dm.widthPixels > 0 && dm.heightPixels > 0) {
            screenWidth = dm.widthPixels
            screenHeight = dm.heightPixels
        } else {
            val resDm = resources.displayMetrics
            screenWidth = resDm.widthPixels
            screenHeight = resDm.heightPixels
        }
    }

    /**
     * Mengeksekusi sentuhan (Tap) pada koordinat fisik tertentu
     */
    fun performTap(x: Float, y: Float, durationMs: Long = 40L) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.v(tag, "Tap berhasil di ($x, $y)")
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(tag, "Tap dibatalkan di ($x, $y)")
                }
            }, null)
        }
    }

    /**
     * Mengeksekusi geseran (Swipe / Drag) dari satu titik ke titik lain
     */
    fun performSwipe(fromX: Float, fromY: Float, toX: Float, toY: Float, durationMs: Long = 200L) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path().apply {
                moveTo(fromX, fromY)
                lineTo(toX, toY)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchGesture(gesture, null, null)
        }
    }

    /**
     * Mengeksekusi tombol sistem global
     */
    fun executeGlobalCommand(command: String) {
        when (command.uppercase()) {
            "BACK" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "HOME" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "RECENTS" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            "NOTIFICATIONS" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            "LOCK" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                }
            }
            "UNLOCK", "SWIPE_UP" -> {
                wakeUpScreen()
                updateScreenDimensions()
                // Geser ke atas dari 85% tinggi layar ke 15% tinggi layar
                performSwipe(
                    screenWidth * 0.5f,
                    screenHeight * 0.85f,
                    screenWidth * 0.5f,
                    screenHeight * 0.15f,
                    300L
                )
            }
        }
    }

    fun wakeUpScreen() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            @Suppress("DEPRECATION")
            val wakeLock = pm?.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "RemoteApps:WakeLock"
            )
            wakeLock?.acquire(3000)
            wakeLock?.release()
        } catch (e: Exception) {
            Log.w(tag, "WakeLock error", e)
        }
    }

    /**
     * Memproses paket input yang diterima dari WebRTC DataChannel
     */
    fun handleInputPacket(packet: InputPacket) {
        updateScreenDimensions()

        when (packet.action.uppercase()) {
            "DOWN", "CLICK" -> {
                val rx = packet.xRatio ?: 0.5f
                val ry = packet.yRatio ?: 0.5f
                performTap(rx * screenWidth, ry * screenHeight, 50L)
            }
            "SWIPE" -> {
                val fx = (packet.fromX ?: 0.5f) * screenWidth
                val fy = (packet.fromY ?: 0.5f) * screenHeight
                val tx = (packet.toX ?: 0.5f) * screenWidth
                val ty = (packet.toY ?: 0.5f) * screenHeight
                val dur = packet.durationMs ?: 200L
                performSwipe(fx, fy, tx, ty, dur)
            }
            "COMMAND" -> {
                packet.command?.let { executeGlobalCommand(it) }
            }
        }
    }
}
