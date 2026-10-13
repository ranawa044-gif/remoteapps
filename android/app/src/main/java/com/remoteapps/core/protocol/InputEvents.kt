package com.remoteapps.core.protocol

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

enum class TouchAction {
    DOWN,
    MOVE,
    UP,
    SWIPE,
    COMMAND,
    TEXT,
    KEY
}

data class InputPacket(
    @SerializedName("action") val action: String,
    @SerializedName("xRatio") val xRatio: Float? = null,
    @SerializedName("yRatio") val yRatio: Float? = null,
    @SerializedName("fromX") val fromX: Float? = null,
    @SerializedName("fromY") val fromY: Float? = null,
    @SerializedName("toX") val toX: Float? = null,
    @SerializedName("toY") val toY: Float? = null,
    @SerializedName("durationMs") val durationMs: Long? = null,
    @SerializedName("command") val command: String? = null,
    @SerializedName("text") val text: String? = null,
    @SerializedName("key") val key: String? = null
) {
    fun toJson(): String = gson.toJson(this)

    companion object {
        private val gson = Gson()
        fun fromJson(json: String): InputPacket? = try {
            gson.fromJson(json, InputPacket::class.java)
        } catch (e: Exception) {
            null
        }
    }
}
