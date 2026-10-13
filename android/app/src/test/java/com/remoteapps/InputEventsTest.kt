package com.remoteapps

import com.remoteapps.core.protocol.InputPacket
import com.remoteapps.core.signaling.IceCandidatePayload
import com.remoteapps.core.signaling.SignalingMessage
import org.junit.Assert.*
import org.junit.Test

class InputEventsTest {

    @Test
    fun testTapPacketSerialization() {
        val original = InputPacket(
            action = "DOWN",
            xRatio = 0.4521f,
            yRatio = 0.8123f
        )
        val json = original.toJson()
        val parsed = InputPacket.fromJson(json)

        assertNotNull(parsed)
        assertEquals("DOWN", parsed?.action)
        assertEquals(0.4521f, parsed?.xRatio ?: 0f, 0.0001f)
        assertEquals(0.8123f, parsed?.yRatio ?: 0f, 0.0001f)
    }

    @Test
    fun testSwipePacketSerialization() {
        val original = InputPacket(
            action = "SWIPE",
            fromX = 0.1f,
            fromY = 0.2f,
            toX = 0.5f,
            toY = 0.8f,
            durationMs = 250L
        )
        val json = original.toJson()
        val parsed = InputPacket.fromJson(json)

        assertNotNull(parsed)
        assertEquals("SWIPE", parsed?.action)
        assertEquals(0.1f, parsed?.fromX ?: 0f, 0.0001f)
        assertEquals(0.8f, parsed?.toY ?: 0f, 0.0001f)
        assertEquals(250L, parsed?.durationMs)
    }

    @Test
    fun testCommandPacketSerialization() {
        val original = InputPacket(
            action = "COMMAND",
            command = "BACK"
        )
        val json = original.toJson()
        val parsed = InputPacket.fromJson(json)

        assertNotNull(parsed)
        assertEquals("COMMAND", parsed?.action)
        assertEquals("BACK", parsed?.command)
    }

    @Test
    fun testSignalingMessageSerialization() {
        val original = SignalingMessage(
            type = "candidate",
            deviceId = "123-456-789",
            candidate = IceCandidatePayload(
                sdpMid = "0",
                sdpMLineIndex = 0,
                candidate = "candidate:1 1 UDP 2122252543 192.168.1.1 54321 typ host"
            )
        )
        val json = original.toJson()
        val parsed = SignalingMessage.fromJson(json)

        assertNotNull(parsed)
        assertEquals("candidate", parsed?.type)
        assertEquals("123-456-789", parsed?.deviceId)
        assertEquals("0", parsed?.candidate?.sdpMid)
    }
}
