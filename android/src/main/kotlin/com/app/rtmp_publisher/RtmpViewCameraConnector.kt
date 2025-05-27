package com.app.rtmp_publisher

import android.app.Activity
import android.util.Size
import net.ossrs.rtmp.ConnectCheckerRtmp
import net.ossrs.rtmp.SrsFlvMuxer

class RtmpViewCameraConnector(val activity: Activity,
                              val connectChecker: ConnectCheckerRtmp) {

    private val srsFlvMuxer: SrsFlvMuxer = SrsFlvMuxer(connectChecker)

    var isStreaming = false
        private set
    var isRecording = false
        private set

    companion object {
        private val TAG: String? = "RtmpCameraConnector"
    }


}