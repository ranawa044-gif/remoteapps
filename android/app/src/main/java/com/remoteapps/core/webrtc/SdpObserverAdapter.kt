package com.remoteapps.core.webrtc

import android.util.Log
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

open class SdpObserverAdapter : SdpObserver {
    override fun onCreateSuccess(desc: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String?) {
        Log.e("SdpObserver", "onCreateFailure: $error")
    }
    override fun onSetFailure(error: String?) {
        Log.e("SdpObserver", "onSetFailure: $error")
    }
}
