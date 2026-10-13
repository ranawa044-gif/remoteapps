package com.remoteapps.core.signaling

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

data class IceCandidatePayload(
    @SerializedName("sdpMid") val sdpMid: String?,
    @SerializedName("sdpMLineIndex") val sdpMLineIndex: Int,
    @SerializedName("candidate") val candidate: String
)

data class SignalingMessage(
    @SerializedName("type") val type: String,
    @SerializedName("deviceId") val deviceId: String? = null,
    @SerializedName("targetDeviceId") val targetDeviceId: String? = null,
    @SerializedName("pin") val pin: String? = null,
    @SerializedName("sdp") val sdp: String? = null,
    @SerializedName("candidate") val candidate: IceCandidatePayload? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("code") val code: String? = null
) {
    fun toJson(): String = gson.toJson(this)

    companion object {
        private val gson = Gson()
        fun fromJson(json: String): SignalingMessage? = try {
            gson.fromJson(json, SignalingMessage::class.java)
        } catch (e: Exception) {
            null
        }
    }
}
