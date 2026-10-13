package com.remoteapps.controller.ui

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.remoteapps.core.protocol.InputPacket
import com.remoteapps.core.webrtc.WebRtcManager
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@Composable
fun RemoteViewerScreen(
    targetDeviceId: String,
    webRtcManager: WebRtcManager,
    remoteVideoTrack: VideoTrack?,
    onDisconnect: () -> Unit
) {
    var viewSize by remember { mutableStateOf(IntSize(1, 1)) }
    var surfaceRenderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }

    // Intercept hardware back button to send BACK command to remote device
    BackHandler {
        webRtcManager.sendInputPacket(InputPacket(action = "COMMAND", command = "BACK"))
    }

    DisposableEffect(remoteVideoTrack) {
        surfaceRenderer?.let { renderer ->
            remoteVideoTrack?.addSink(renderer)
        }
        onDispose {
            surfaceRenderer?.let { renderer ->
                remoteVideoTrack?.removeSink(renderer)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { viewSize = it }
    ) {
        // WebRTC SurfaceViewRenderer
        AndroidView(
            factory = { context ->
                SurfaceViewRenderer(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    init(webRtcManager.eglBase.eglBaseContext, null)
                    setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                    setEnableHardwareScaler(true)
                    remoteVideoTrack?.addSink(this)
                    surfaceRenderer = this
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            val xRatio = (offset.x / viewSize.width.toFloat()).coerceIn(0f, 1f)
                            val yRatio = (offset.y / viewSize.height.toFloat()).coerceIn(0f, 1f)
                            webRtcManager.sendInputPacket(
                                InputPacket(action = "DOWN", xRatio = xRatio, yRatio = yRatio)
                            )
                        }
                    )
                }
                .pointerInput(Unit) {
                    var startX = 0f
                    var startY = 0f
                    detectDragGestures(
                        onDragStart = { offset ->
                            startX = (offset.x / viewSize.width.toFloat()).coerceIn(0f, 1f)
                            startY = (offset.y / viewSize.height.toFloat()).coerceIn(0f, 1f)
                        },
                        onDragEnd = {},
                        onDrag = { change, _ ->
                            change.consume()
                            val curX = (change.position.x / viewSize.width.toFloat()).coerceIn(0f, 1f)
                            val curY = (change.position.y / viewSize.height.toFloat()).coerceIn(0f, 1f)

                            webRtcManager.sendInputPacket(
                                InputPacket(
                                    action = "SWIPE",
                                    fromX = startX,
                                    fromY = startY,
                                    toX = curX,
                                    toY = curY,
                                    durationMs = 80L
                                )
                            )
                            startX = curX
                            startY = curY
                        }
                    )
                }
        )

        // Floating Header Status Tag
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xCC1E293B),
            contentColor = Color.White
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color(0xFF10B981), shape = RoundedCornerShape(4.dp))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Meremote: $targetDeviceId",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Bottom Navigation Bar
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp, start = 16.dp, end = 16.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xEE1E293B),
            contentColor = Color.White,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    webRtcManager.sendInputPacket(InputPacket(action = "COMMAND", command = "BACK"))
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }

                IconButton(onClick = {
                    webRtcManager.sendInputPacket(InputPacket(action = "COMMAND", command = "HOME"))
                }) {
                    Icon(Icons.Default.Home, contentDescription = "Home")
                }

                IconButton(onClick = {
                    webRtcManager.sendInputPacket(InputPacket(action = "COMMAND", command = "RECENTS"))
                }) {
                    Icon(Icons.Default.List, contentDescription = "Recents")
                }

                VerticalDivider(modifier = Modifier.height(24.dp), color = Color.Gray)

                IconButton(
                    onClick = onDisconnect,
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color(0xFFEF4444))
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Disconnect")
                }
            }
        }
    }
}
