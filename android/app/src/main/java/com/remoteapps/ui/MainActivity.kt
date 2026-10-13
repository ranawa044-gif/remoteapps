package com.remoteapps.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.remoteapps.core.protocol.InputPacket
import com.remoteapps.core.security.PreferenceManager
import com.remoteapps.core.signaling.*
import com.remoteapps.core.webrtc.SdpObserverAdapter
import com.remoteapps.core.webrtc.WebRtcEventListener
import com.remoteapps.core.webrtc.WebRtcManager
import com.remoteapps.controller.ui.RemoteViewerScreen
import com.remoteapps.host.service.HostForegroundService
import com.remoteapps.ui.theme.RemoteAppsTheme
import org.webrtc.*

class MainActivity : ComponentActivity(), SignalingListener, WebRtcEventListener {

    private lateinit var prefs: PreferenceManager
    private lateinit var mediaProjectionManager: MediaProjectionManager

    // Controller Mode State
    private var controllerSignaling: SignalingClient? = null
    private var controllerWebRtc: WebRtcManager? = null
    private var pendingTargetId: String = ""

    // Compose State
    private var isHostActive by mutableStateOf(false)
    private var isConnectedAsController by mutableStateOf(false)
    private var remoteVideoTrackState by mutableStateOf<VideoTrack?>(null)

    // Activity Launchers
    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startHostService(result.data!!)
        } else {
            Toast.makeText(this, "Izin rekam layar ditolak.", Toast.LENGTH_SHORT).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PreferenceManager(this)
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        requestNotificationPermission()
        requestIgnoreBatteryOptimizations()

        setContent {
            RemoteAppsTheme {
                if (isConnectedAsController && controllerWebRtc != null) {
                    RemoteViewerScreen(
                        targetDeviceId = pendingTargetId,
                        webRtcManager = controllerWebRtc!!,
                        remoteVideoTrack = remoteVideoTrackState,
                        onDisconnect = { disconnectController() }
                    )
                } else {
                    HomeScreen(
                        prefs = prefs,
                        isHostServiceActive = isHostActive,
                        onStartHostService = { launchMediaProjection() },
                        onStopHostService = { stopHostService() },
                        onConnectToTarget = { targetId, pin ->
                            connectToTargetHost(targetId, pin)
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isHostActive = HostForegroundService.isRunning
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    // Abaikan jika tidak didukung OEM
                }
            }
        }
    }

    // Host Functions
    private fun launchMediaProjection() {
        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
        mediaProjectionLauncher.launch(captureIntent)
    }

    private fun startHostService(resultData: Intent) {
        val serviceIntent = Intent(this, HostForegroundService::class.java).apply {
            action = HostForegroundService.ACTION_START
            putExtra(HostForegroundService.EXTRA_RESULT_DATA, resultData)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        isHostActive = true
        Toast.makeText(this, "Layanan Host aktif di latar belakang", Toast.LENGTH_SHORT).show()
    }

    private fun stopHostService() {
        val serviceIntent = Intent(this, HostForegroundService::class.java).apply {
            action = HostForegroundService.ACTION_STOP
        }
        startService(serviceIntent)
        isHostActive = false
        Toast.makeText(this, "Layanan Host dihentikan", Toast.LENGTH_SHORT).show()
    }

    // Controller Functions
    private fun connectToTargetHost(targetId: String, pin: String) {
        pendingTargetId = targetId
        controllerSignaling = SignalingClient(prefs.serverUrl, this)
        controllerSignaling?.connect()

        // Kirim request connect saat connected
    }

    private fun disconnectController() {
        controllerWebRtc?.close()
        controllerWebRtc = null
        controllerSignaling?.disconnect()
        controllerSignaling = null
        remoteVideoTrackState = null
        isConnectedAsController = false
    }

    // Signaling Listener
    override fun onConnected() {
        runOnUiThread {
            Toast.makeText(this, "Signaling tersambung. Mengirim autentikasi...", Toast.LENGTH_SHORT).show()
            controllerSignaling?.send(
                SignalingMessage(
                    type = "connect_request",
                    targetDeviceId = pendingTargetId,
                    pin = prefs.unattendedPin
                )
            )
        }
    }

    override fun onDisconnected(reason: String) {
        runOnUiThread {
            if (isConnectedAsController) {
                Toast.makeText(this, "Koneksi terputus: $reason", Toast.LENGTH_SHORT).show()
                disconnectController()
            }
        }
    }

    override fun onError(error: String) {
        runOnUiThread {
            Toast.makeText(this, "Error: $error", Toast.LENGTH_SHORT).show()
            disconnectController()
        }
    }

    override fun onMessageReceived(message: SignalingMessage) {
        runOnUiThread {
            when (message.type) {
                "connect_accepted" -> {
                    Toast.makeText(this, "Autentikasi Unattended Berhasil! Menghubungkan WebRTC...", Toast.LENGTH_SHORT).show()
                    initControllerWebRtc()
                }

                "answer" -> {
                    val sdp = message.sdp ?: return@runOnUiThread
                    val sessionDescription = SessionDescription(SessionDescription.Type.ANSWER, sdp)
                    controllerWebRtc?.peerConnection?.setRemoteDescription(object : SdpObserverAdapter() {}, sessionDescription)
                }

                "candidate" -> {
                    message.candidate?.let {
                        val ice = IceCandidate(it.sdpMid, it.sdpMLineIndex, it.candidate)
                        controllerWebRtc?.peerConnection?.addIceCandidate(ice)
                    }
                }

                "error" -> {
                    Toast.makeText(this, message.message ?: "Koneksi gagal", Toast.LENGTH_LONG).show()
                    disconnectController()
                }
            }
        }
    }

    private fun initControllerWebRtc() {
        controllerWebRtc = WebRtcManager(this)
        controllerWebRtc?.eventListener = this
        val pc = controllerWebRtc?.createPeerConnection(isHost = false)

        // Buat Offer
        val mediaConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
        }

        pc?.createOffer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let {
                    pc.setLocalDescription(object : SdpObserverAdapter() {
                        override fun onSetSuccess() {
                            controllerSignaling?.send(
                                SignalingMessage(
                                    type = "offer",
                                    targetDeviceId = pendingTargetId,
                                    sdp = it.description
                                )
                            )
                        }
                    }, it)
                }
            }
        }, mediaConstraints)
    }

    // WebRTC Listener
    override fun onIceCandidate(candidate: IceCandidate) {
        controllerSignaling?.send(
            SignalingMessage(
                type = "candidate",
                targetDeviceId = pendingTargetId,
                candidate = IceCandidatePayload(
                    sdpMid = candidate.sdpMid,
                    sdpMLineIndex = candidate.sdpMLineIndex,
                    candidate = candidate.sdp
                )
            )
        )
    }

    override fun onDataChannelReceived(channel: DataChannel) {}
    override fun onDataChannelMessage(packet: InputPacket) {}

    override fun onRemoteVideoTrackReceived(videoTrack: VideoTrack) {
        runOnUiThread {
            remoteVideoTrackState = videoTrack
            isConnectedAsController = true
        }
    }

    override fun onConnectionStateChange(state: PeerConnection.PeerConnectionState) {
        runOnUiThread {
            if (state == PeerConnection.PeerConnectionState.FAILED || state == PeerConnection.PeerConnectionState.DISCONNECTED) {
                disconnectController()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnectController()
    }
}
