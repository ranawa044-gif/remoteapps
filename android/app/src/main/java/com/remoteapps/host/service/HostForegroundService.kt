package com.remoteapps.host.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.remoteapps.R
import com.remoteapps.RemoteApplication
import com.remoteapps.core.protocol.InputPacket
import com.remoteapps.core.security.PreferenceManager
import com.remoteapps.core.signaling.*
import com.remoteapps.core.webrtc.SdpObserverAdapter
import com.remoteapps.core.webrtc.WebRtcEventListener
import com.remoteapps.core.webrtc.WebRtcManager
import com.remoteapps.host.capture.ScreenCaptureManager
import com.remoteapps.ui.MainActivity
import org.webrtc.*

class HostForegroundService : Service(), SignalingListener, WebRtcEventListener {

    private val tag = "HostForegroundService"

    private lateinit var prefs: PreferenceManager
    private var signalingClient: SignalingClient? = null
    private var webRtcManager: WebRtcManager? = null
    private var screenCaptureManager: ScreenCaptureManager? = null

    private var mediaProjectionData: Intent? = null
    private var isRegistered = false

    companion object {
        const val ACTION_START = "com.remoteapps.ACTION_START_HOST"
        const val ACTION_STOP = "com.remoteapps.ACTION_STOP_HOST"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val NOTIFICATION_ID = 1001

        var isRunning = false
            private set
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = PreferenceManager(this)
        webRtcManager = WebRtcManager(this)
        webRtcManager?.eventListener = this
        screenCaptureManager = ScreenCaptureManager(this, webRtcManager!!)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                mediaProjectionData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }
                startForegroundServiceWithNotification()
                mediaProjectionData?.let { data ->
                    screenCaptureManager?.startCapture(data, resources.displayMetrics)
                }
                connectSignaling()
                isRunning = true
            }
            ACTION_STOP -> {
                stopHostService()
            }
        }
        return START_STICKY
    }

    private fun startForegroundServiceWithNotification() {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, HostForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, RemoteApplication.CHANNEL_ID)
            .setContentTitle("Layanan Remote Host Aktif")
            .setContentText("Device ID: ${prefs.deviceId} | Siap menerima koneksi (Unattended)")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Hentikan Layanan", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun connectSignaling() {
        val url = prefs.serverUrl
        Log.d(tag, "Menghubungkan ke Signaling Server: $url")
        signalingClient = SignalingClient(url, this)
        signalingClient?.connect()
    }

    override fun onConnected() {
        Log.d(tag, "Signaling terhubung. Mendaftarkan host Device ID: ${prefs.deviceId}")
        signalingClient?.send(
            SignalingMessage(
                type = "register_host",
                deviceId = prefs.deviceId,
                pin = prefs.unattendedPin
            )
        )
    }

    override fun onDisconnected(reason: String) {
        Log.w(tag, "Signaling terputus: $reason")
        isRegistered = false
    }

    override fun onError(error: String) {
        Log.e(tag, "Signaling error: $error")
    }

    override fun onMessageReceived(message: SignalingMessage) {
        when (message.type) {
            "host_registered" -> {
                isRegistered = true
                Log.d(tag, "Host terdaftar sukses di server. Siap unattended access!")
            }

            "incoming_connection" -> {
                Log.d(tag, "Koneksi masuk terverifikasi PIN! Mempersiapkan WebRTC...")
                setupHostWebRtcSession()
            }

            "offer" -> {
                Log.d(tag, "Menerima SDP Offer dari controller")
                handleOffer(message.sdp ?: "")
            }

            "candidate" -> {
                message.candidate?.let {
                    val ice = IceCandidate(it.sdpMid, it.sdpMLineIndex, it.candidate)
                    webRtcManager?.peerConnection?.addIceCandidate(ice)
                }
            }

            "peer_disconnected" -> {
                Log.d(tag, "Pengendali memutuskan koneksi")
                stopStreaming()
            }
        }
    }

    private fun setupHostWebRtcSession() {
        val pc = webRtcManager?.createPeerConnection(isHost = true)

        screenCaptureManager?.videoTrack?.let { track ->
            pc?.addTrack(track, listOf("ARDAMS"))
            Log.d(tag, "Screen VideoTrack ditambahkan ke PeerConnection")
        }
    }

    private fun handleOffer(sdpDescription: String) {
        val pc = webRtcManager?.peerConnection ?: return
        val sessionDescription = SessionDescription(SessionDescription.Type.OFFER, sdpDescription)

        pc.setRemoteDescription(object : SdpObserverAdapter() {
            override fun onSetSuccess() {
                // Pastikan transceiver video diatur ke SEND_ONLY dan terikat dengan VideoTrack
                for (transceiver in pc.transceivers) {
                    if (transceiver.mediaType == MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO) {
                        transceiver.direction = RtpTransceiver.RtpTransceiverDirection.SEND_ONLY
                        screenCaptureManager?.videoTrack?.let { track ->
                            transceiver.sender.setTrack(track, true)
                        }
                    }
                }

                // Buat SDP Answer
                pc.createAnswer(object : SdpObserverAdapter() {
                    override fun onCreateSuccess(answer: SessionDescription?) {
                        answer?.let {
                            pc.setLocalDescription(object : SdpObserverAdapter() {
                                override fun onSetSuccess() {
                                    // Kirim Answer balik ke controller via signaling
                                    signalingClient?.send(
                                        SignalingMessage(
                                            type = "answer",
                                            deviceId = prefs.deviceId,
                                            sdp = it.description
                                        )
                                    )
                                }
                            }, it)
                        }
                    }
                }, MediaConstraints())
            }
        }, sessionDescription)
    }

    // WebRtcEventListener Implementation
    override fun onIceCandidate(candidate: IceCandidate) {
        signalingClient?.send(
            SignalingMessage(
                type = "candidate",
                deviceId = prefs.deviceId,
                candidate = IceCandidatePayload(
                    sdpMid = candidate.sdpMid,
                    sdpMLineIndex = candidate.sdpMLineIndex,
                    candidate = candidate.sdp
                )
            )
        )
    }

    override fun onDataChannelReceived(channel: DataChannel) {
        Log.d(tag, "DataChannel diterima di Host")
    }

    override fun onDataChannelMessage(packet: InputPacket) {
        // Teruskan event sentuhan/navigasi ke Accessibility Service
        val accessibility = HostAccessibilityService.instance
        if (accessibility != null) {
            accessibility.handleInputPacket(packet)
        } else {
            Log.w(tag, "HostAccessibilityService tidak aktif! Tidak dapat mengeksekusi sentuhan.")
        }
    }

    override fun onRemoteVideoTrackReceived(videoTrack: VideoTrack) {}

    override fun onConnectionStateChange(state: PeerConnection.PeerConnectionState) {
        Log.d(tag, "WebRTC Connection State: $state")
        if (state == PeerConnection.PeerConnectionState.DISCONNECTED || state == PeerConnection.PeerConnectionState.FAILED) {
            stopStreaming()
        }
    }

    private fun stopStreaming() {
        // Jangan panggil screenCaptureManager.stopCapture() agar token tidak hangus untuk koneksi berikutnya (Unattended)
        webRtcManager?.peerConnection?.close()
    }

    private fun stopHostService() {
        screenCaptureManager?.stopCapture()
        webRtcManager?.close()
        signalingClient?.disconnect()
        isRunning = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopHostService()
    }
}
