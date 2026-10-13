package com.remoteapps.core.webrtc

import android.content.Context
import android.util.Log
import com.remoteapps.core.protocol.InputPacket
import org.webrtc.*
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

interface WebRtcEventListener {
    fun onIceCandidate(candidate: IceCandidate)
    fun onDataChannelReceived(channel: DataChannel)
    fun onDataChannelMessage(packet: InputPacket)
    fun onRemoteVideoTrackReceived(videoTrack: VideoTrack)
    fun onConnectionStateChange(state: PeerConnection.PeerConnectionState)
}

class WebRtcManager(
    private val context: Context,
    val eglBase: EglBase = EglBase.create()
) {
    private val tag = "WebRtcManager"

    var peerConnection: PeerConnection? = null
        private set
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var dataChannel: DataChannel? = null
    var eventListener: WebRtcEventListener? = null

    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
    )

    init {
        initializePeerConnectionFactory()
    }

    private fun initializePeerConnectionFactory() {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val defaultVideoEncoderFactory = DefaultVideoEncoderFactory(
            eglBase.eglBaseContext,
            /* enableIntelVp8Encoder = */ true,
            /* enableH264HighProfile = */ true
        )
        val defaultVideoDecoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(defaultVideoEncoderFactory)
            .setVideoDecoderFactory(defaultVideoDecoderFactory)
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()
    }

    fun createPeerConnection(isHost: Boolean): PeerConnection? {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}

            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let { eventListener?.onIceCandidate(it) }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}

            override fun onDataChannel(channel: DataChannel?) {
                channel?.let {
                    Log.d(tag, "Host received DataChannel: ${it.label()}")
                    setupDataChannelListener(it)
                    eventListener?.onDataChannelReceived(it)
                }
            }

            override fun onRenegotiationNeeded() {}

            override fun onTrack(transceiver: RtpTransceiver?) {
                val track = transceiver?.receiver?.track()
                if (track is VideoTrack) {
                    Log.d(tag, "Controller received remote VideoTrack")
                    eventListener?.onRemoteVideoTrackReceived(track)
                }
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                newState?.let {
                    Log.d(tag, "Connection state changed: $it")
                    eventListener?.onConnectionStateChange(it)
                }
            }
        })

        if (!isHost) {
            // Controller membuat DataChannel
            val dcInit = DataChannel.Init().apply {
                ordered = true
            }
            dataChannel = peerConnection?.createDataChannel("input_channel", dcInit)
            dataChannel?.let { setupDataChannelListener(it) }
        }

        return peerConnection
    }

    fun setupDataChannelListener(channel: DataChannel) {
        dataChannel = channel
        channel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}

            override fun onStateChange() {
                Log.d(tag, "DataChannel state: ${channel.state()}")
            }

            override fun onMessage(buffer: DataChannel.Buffer?) {
                buffer?.let {
                    val bytes = ByteArray(it.data.remaining())
                    it.data.get(bytes)
                    val json = String(bytes, StandardCharsets.UTF_8)
                    val packet = InputPacket.fromJson(json)
                    packet?.let { p -> eventListener?.onDataChannelMessage(p) }
                }
            }
        })
    }

    fun sendInputPacket(packet: InputPacket) {
        if (dataChannel?.state() == DataChannel.State.OPEN) {
            val json = packet.toJson()
            val buffer = ByteBuffer.wrap(json.toByteArray(StandardCharsets.UTF_8))
            dataChannel?.send(DataChannel.Buffer(buffer, false))
        }
    }

    fun createVideoSource(isScreencast: Boolean): VideoSource? {
        return peerConnectionFactory?.createVideoSource(isScreencast)
    }

    fun createVideoTrack(id: String, source: VideoSource): VideoTrack? {
        return peerConnectionFactory?.createVideoTrack(id, source)
    }

    fun close() {
        try {
            dataChannel?.close()
            dataChannel = null
            peerConnection?.close()
            peerConnection = null
        } catch (e: Exception) {
            Log.e(tag, "Error closing WebRTC", e)
        }
    }
}
