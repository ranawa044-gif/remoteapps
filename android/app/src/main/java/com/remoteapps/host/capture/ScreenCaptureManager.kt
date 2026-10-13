package com.remoteapps.host.capture

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.util.DisplayMetrics
import android.util.Log
import com.remoteapps.core.webrtc.WebRtcManager
import org.webrtc.*

class ScreenCaptureManager(
    private val context: Context,
    private val webRtcManager: WebRtcManager
) {
    private val tag = "ScreenCaptureManager"

    private var videoCapturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var videoSource: VideoSource? = null
    var videoTrack: VideoTrack? = null
        private set
    val isCapturing: Boolean
        get() = videoTrack != null

    fun startCapture(mediaProjectionData: Intent, displayMetrics: DisplayMetrics) {
        if (isCapturing) {
            Log.d(tag, "ScreenCapture sudah berjalan, tidak perlu inisialisasi ulang")
            return
        }

        try {
            videoCapturer = ScreenCapturerAndroid(mediaProjectionData, object : MediaProjection.Callback() {
                override fun onStop() {
                    super.onStop()
                    Log.w(tag, "MediaProjection dihentikan oleh pengguna atau sistem")
                }
            })

            surfaceTextureHelper = SurfaceTextureHelper.create("ScreenCaptureThread", webRtcManager.eglBase.eglBaseContext)
            videoSource = webRtcManager.createVideoSource(true)

            videoCapturer?.initialize(surfaceTextureHelper, context, videoSource?.capturerObserver)

            // Pastikan resolusi bernilai genap (multiple of 2) agar MediaCodec H.264/VP8 tidak gagal
            var width = minOf(displayMetrics.widthPixels, 720)
            var height = (width * (displayMetrics.heightPixels.toFloat() / displayMetrics.widthPixels.toFloat())).toInt()
            if (width % 2 != 0) width -= 1
            if (height % 2 != 0) height -= 1
            val fps = 30

            videoSource?.adaptOutputFormat(width, height, fps)

            Log.d(tag, "Memulai ScreenCapturer: ${width}x${height} @ ${fps}fps")
            videoCapturer?.startCapture(width, height, fps)

            videoSource?.let {
                videoTrack = webRtcManager.createVideoTrack("ARDAMSv0", it)
                videoTrack?.setEnabled(true)
            }
        } catch (e: Exception) {
            Log.e(tag, "Gagal memulai ScreenCapture", e)
        }
    }

    fun stopCapture() {
        try {
            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
            videoCapturer = null

            surfaceTextureHelper?.dispose()
            surfaceTextureHelper = null

            videoTrack?.dispose()
            videoTrack = null

            videoSource?.dispose()
            videoSource = null
        } catch (e: Exception) {
            Log.e(tag, "Error saat menghentikan capture", e)
        }
    }
}
