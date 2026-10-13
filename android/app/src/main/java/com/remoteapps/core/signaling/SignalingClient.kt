package com.remoteapps.core.signaling

import android.util.Log
import okhttp3.*
import java.util.concurrent.TimeUnit

interface SignalingListener {
    fun onConnected()
    fun onDisconnected(reason: String)
    fun onMessageReceived(message: SignalingMessage)
    fun onError(error: String)
}

class SignalingClient(
    private val serverUrl: String,
    private val listener: SignalingListener
) {
    private val tag = "SignalingClient"
    private val okHttpClient = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var isConnected = false

    fun connect() {
        try {
            val request = Request.Builder().url(serverUrl).build()
            webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: Response) {
                    Log.d(tag, "WebSocket Connected to $serverUrl")
                    isConnected = true
                    listener.onConnected()
                }

                override fun onMessage(ws: WebSocket, text: String) {
                    Log.d(tag, "Received message: $text")
                    val msg = SignalingMessage.fromJson(text)
                    if (msg != null) {
                        listener.onMessageReceived(msg)
                    }
                }

                override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                    Log.d(tag, "WebSocket Closing: $reason")
                    isConnected = false
                    listener.onDisconnected(reason)
                }

                override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                    Log.e(tag, "WebSocket Error: ${t.message}", t)
                    isConnected = false
                    listener.onError(t.message ?: "Koneksi WebSocket gagal")
                }
            })
        } catch (e: Exception) {
            listener.onError(e.message ?: "Gagal inisialisasi WebSocket")
        }
    }

    fun send(message: SignalingMessage) {
        val json = message.toJson()
        val success = webSocket?.send(json) ?: false
        if (!success) {
            Log.w(tag, "Gagal mengirim pesan: $json")
        }
    }

    fun disconnect() {
        try {
            webSocket?.close(1000, "Normal closure")
            webSocket = null
            isConnected = false
        } catch (e: Exception) {
            Log.e(tag, "Error closing websocket", e)
        }
    }
}
