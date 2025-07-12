package com.app.rtmp_publisher

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.os.Build
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender
import android.util.Log
import android.util.Size
import android.view.SurfaceHolder
import android.view.View
import androidx.annotation.RequiresApi
import com.app.rtmp_publisher.CameraPermissions.ResolutionPreset
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.video.CameraHelper.Facing.BACK
import com.pedro.encoder.input.video.CameraHelper.Facing.FRONT
import com.pedro.encoder.utils.gl.AspectRatioMode
import com.pedro.library.rtmp.RtmpCamera2
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.platform.PlatformView
import com.pedro.library.view.OpenGlView
import com.pedro.library.util.BitrateAdapter
import java.io.*


class CameraNativeView(
    private var activity: Activity? = null,
    private var enableAudio: Boolean = false,
    private val preset: ResolutionPreset,
    private var cameraName: String,
    private var dartMessenger: DartMessenger? = null
) :
    PlatformView,
    SurfaceHolder.Callback,
    ConnectChecker{
    private val glView = OpenGlView(activity)
    private val rtmpCamera: RtmpCamera2
    private var isSurfaceCreated = false
    private var fps = 0
    private val aBitrate = 128 * 1000
    private val vBitrate = 1200 * 1000
    private val bitrateAdapter: BitrateAdapter
    init {
//        glView.isKeepAspectRatio = true
        glView.setAspectRatioMode(AspectRatioMode.Adjust)
        glView.holder.addCallback(this)
        rtmpCamera = RtmpCamera2(glView, this)
        rtmpCamera.streamClient.setReTries(10)
        rtmpCamera.setFpsListener { fps = it }
        bitrateAdapter = BitrateAdapter {
            rtmpCamera.setVideoBitrateOnFly(it)
        }.apply {
            setMaxBitrate(vBitrate + aBitrate)
        }
    }
    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.d("CameraNativeView", "surfaceCreated")
        isSurfaceCreated = true
        if(!rtmpCamera.isOnPreview){
            startPreview(cameraName)
        }
    }

    override fun surfaceChanged(p0: SurfaceHolder, p1: Int, p2: Int, p3: Int) {
        // TODO("Not yet implemented")
    }

    override fun surfaceDestroyed(p0: SurfaceHolder) {
        // TODO("Not yet implemented")
        if (rtmpCamera.isOnPreview) {
            rtmpCamera.stopPreview()
        }
        isSurfaceCreated = false
        activity = null
    }
     override fun onConnectionStarted(url: String) {
        }

        override fun onConnectionSuccess() {
        }

        override fun onNewBitrate(bitrate: Long) {
            bitrateAdapter.adaptBitrate(bitrate, rtmpCamera.getStreamClient().hasCongestion())
        }

        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        override fun onConnectionFailed(reason: String) {
            activity?.runOnUiThread { //Wait 5s and retry connect stream
                if (rtmpCamera.streamClient.reTry(5000, reason)) {
                    dartMessenger?.send(DartMessenger.EventType.RTMP_RETRY, reason)
                } else {
                    dartMessenger?.send(DartMessenger.EventType.RTMP_STOPPED, "Failed retry")
                    rtmpCamera.stopStream()
                }
            }
        }

        override fun onDisconnect() {
            activity?.runOnUiThread {
                dartMessenger?.send(DartMessenger.EventType.RTMP_STOPPED, "Disconnected")
            }
        }

        override fun onAuthError() {
            activity?.runOnUiThread {
                dartMessenger?.send(DartMessenger.EventType.ERROR, "Auth error")
            }
        }
        override fun onAuthSuccess() {
        }
    fun close() {
        Log.d("CameraNativeView", "close")
    }

    fun takePicture(filePath: String, result: MethodChannel.Result) {
        Log.d("CameraNativeView", "takePicture filePath: $filePath result: $result")
        val file: File = File(filePath)
        if (file.exists()) {
            result.error("fileExists", "File at path '$filePath' already exists. Cannot overwrite.", null)
            return
        }
        glView.takePhoto {
            try {
                val outputStream: OutputStream = BufferedOutputStream(FileOutputStream(file))
                it.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                outputStream.close()
                view.post { result.success(null) }
            } catch (e: IOException) {
                result.error("IOError", "Failed saving image", null)
            }
        }
    }

    fun startVideoRecording(filePath: String?, result: MethodChannel.Result) {
        if (filePath == null) {
            result.error("fileExists", "Must specify a filePath.", null)
            return
        }

        val file = File(filePath)
        if (file.exists()) {
            result.error("fileExists", "File at path '$filePath' already exists. Cannot overwrite.", null)
            return
        }
        Log.d("CameraNativeView", "startVideoRecording filePath: $filePath result: $result")


        /*if (rtmpCamera.isRecording || rtmpCamera.prepareAudio() && rtmpCamera.prepareVideo(
                streamingSize.videoFrameWidth,
                streamingSize.videoFrameHeight,
                streamingSize.videoBitRate
            )*/
        //判断如果不是视频流的话并且其用了音频
        try{
            if (!rtmpCamera.isStreaming) {
                val streamingSize = CameraUtils.computeBestPreviewSize(activity,cameraName, preset)
                val size = streamingSize["size"] as Size
                val bitrateRes = streamingSize["bitrate"] as Int
                if ((enableAudio && rtmpCamera.prepareAudio()) && rtmpCamera.prepareVideo(
                        size.width,
                        size.height,
                        bitrateRes
                    )
                ) {
                    rtmpCamera.startRecord(filePath)
                }
            
            } else {
                rtmpCamera.startRecord(filePath)
            }
            result.success(null)
        } catch (e: CameraAccessException) {
            result.error("videoRecordingFailed", e.message, null)
        } catch (e: IOException) {
            result.error("videoRecordingFailed", e.message, null)
        }
        
    }


    fun startVideoStreaming(url: String?, bitrate: Int?, result: MethodChannel.Result) {
        Log.d("CameraNativeView", "startVideoStreaming url: $url")
        if (url == null) {
            result.error("startVideoStreaming", "Must specify a url.", null)
            return
        }

        try {
            if (!rtmpCamera.isStreaming) {
                val streamingSize = CameraUtils.computeBestPreviewSize(activity,cameraName, preset)
                val size = streamingSize["size"] as Size
                val bitrateRes = streamingSize["bitrate"] as Int
                if (rtmpCamera.isRecording || rtmpCamera.prepareAudio() && rtmpCamera.prepareVideo(
                        size.width,
                        size.height,
                        bitrate ?: bitrateRes
                    )
                ) {
                    // ready to start streaming
                    rtmpCamera.startStream(url)
                } else {
                    result.error("videoStreamingFailed", "Error preparing stream, This device cant do it", null)
                    return
                }
            } else {
                rtmpCamera.stopStream()
            }
            result.success(null)
        } catch (e: CameraAccessException) {
            result.error("videoStreamingFailed", e.message, null)
        } catch (e: IOException) {
            result.error("videoStreamingFailed", e.message, null)
        }
    }

    fun startVideoRecordingAndStreaming(filePath: String?, url: String?, bitrate: Int?, result: MethodChannel.Result) {
        if (filePath == null) {
            result.error("fileExists", "Must specify a filePath.", null)
            return
        }
        if (File(filePath).exists()) {
            result.error("fileExists", "File at path '$filePath' already exists.", null)
            return
        }
        if (url == null) {
            result.error("fileExists", "Must specify a url.", null)
            return
        }
        try {
            startVideoRecording(filePath, result)
            startVideoStreaming(url, bitrate, result)
        } catch (e: CameraAccessException) {
            result.error("videoRecordingFailed", e.message, null)
        } catch (e: IOException) {
            result.error("videoRecordingFailed", e.message, null)
        }
    }

    fun pauseVideoStreaming(result: MethodChannel.Result) {
        // TODO: Implement pause video streaming
        result.error("pauseVideoStreaming", "安卓暂时不支持暂停播放", null)
    }

    fun resumeVideoStreaming(result: MethodChannel.Result) {
        // TODO: Implement resume video streaming
        result.error("resumeVideoStreaming", "安卓暂时不支持恢复播放", null)
    }
    //启用低光模式
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun onFlashLight(result: MethodChannel.Result){
        try {
            rtmpCamera.enableLantern()
        } catch (e: CameraAccessException) {
            result.error("enableLanternFailed", e.message, null)
        } catch (e: IOException) {
            result.error("enableLanternFailed", e.message, null)
        }
    }
    //关闭低光模式
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun offFlashLight(result: MethodChannel.Result){
        try {
            rtmpCamera.disableLantern()
        } catch (e: CameraAccessException) {
            result.error("disableLanternFailed", e.message, null)
        } catch (e: IOException) {
            result.error("disableLanternFailed", e.message, null)
        }
    }
    //切换相机式
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun switchCamera(cameraId: String?,result: MethodChannel.Result){
        if (cameraId == null) {
            result.error("cameraIdExist", "empty cameraId!", null)
            return
        }
        try {
            rtmpCamera.switchCamera(cameraId)
        } catch (e: CameraAccessException) {
            result.error("switchCameraFailed", e.message, null)
        } catch (e: IOException) {
            result.error("switchCameraFailed", e.message, null)
        }


    }
    //启用声音
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun onEnableAudio(result: MethodChannel.Result){
        try {
            rtmpCamera.enableAudio()
        } catch (e: CameraAccessException) {
            result.error("enableAudioFailed", e.message, null)
        } catch (e: IOException) {
            result.error("enableAudioFailed", e.message, null)
        }
    }
    //关闭声音
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun onDisableAudio(result: MethodChannel.Result){
        try {
            rtmpCamera.disableAudio()
        } catch (e: CameraAccessException) {
            result.error("disableAudioFailed", e.message, null)
        } catch (e: IOException) {
            result.error("disableAudioFailed", e.message, null)
        }
    }
    //设置滤镜
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun setFilter(filter: BaseFilterRender){
        rtmpCamera.glInterface?.setFilter(filter)
    }
     //移除滤镜
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun removeFilter(filter: BaseFilterRender){
        rtmpCamera.glInterface?.removeFilter(filter)
    }
    fun stopVideoRecordingOrStreaming(result: MethodChannel.Result) {
        try {
            rtmpCamera.apply {
                if (isStreaming) stopStream()
                if (isRecording) stopRecord()
            }
            result.success(null)
        } catch (e: CameraAccessException) {
            result.error("videoRecordingFailed", e.message, null)
        } catch (e: IllegalStateException) {
            result.error("videoRecordingFailed", e.message, null)
        }
    }

    fun stopVideoRecording(result: MethodChannel.Result) {
        try {
            rtmpCamera.apply {
                if (isRecording) stopRecord()
            }
            result.success(null)
        } catch (e: CameraAccessException) {
            result.error("stopVideoRecordingFailed", e.message, null)
        } catch (e: IllegalStateException) {
            result.error("stopVideoRecordingFailed", e.message, null)
        }
    }

    fun stopVideoStreaming(result: MethodChannel.Result) {
        try {
            rtmpCamera.apply {
                if (isStreaming) stopStream()
            }
            result.success(null)
        } catch (e: CameraAccessException) {
            result.error("stopVideoStreamingFailed", e.message, null)
        } catch (e: IllegalStateException) {
            result.error("stopVideoStreamingFailed", e.message, null)
        }
    }

    fun pauseVideoRecording(result: MethodChannel.Result) {
        // TODO: Implement pause Video Recording
        result.error("pauseVideoRecording", "安卓暂时不支持暂停录制", null)
    }

    fun resumeVideoRecording(result: MethodChannel.Result) {
        result.error("resumeVideoRecording", "安卓暂时不支持恢复录制", null)
        // TODO: Implement resume video recording
    }

    fun startPreviewWithImageStream(result: MethodChannel.Result) {
        // TODO: Implement start preview with image stream
        result.error("startPreviewWithImageStream", "安卓暂时不支持使用图像流开始预览", null)
    }

    fun startPreview(cameraNameArg: String? = null) {
        val targetCamera = if (cameraNameArg.isNullOrEmpty()) {
            cameraName
        } else {
            cameraNameArg
        }
        cameraName = targetCamera

        Log.d("CameraNativeView", "startPreview: $preset")
        if (isSurfaceCreated) {
            try {
//                Log.d("error", targetCamera)
                val previewSize = CameraUtils.computeBestPreviewSize(activity,cameraName, preset)
                val size = previewSize["size"] as Size
                rtmpCamera.startPreview(if (isFrontFacing(targetCamera))  FRONT else BACK, size.width, size.height)
            } catch (e: CameraAccessException) {
//                close()
                activity?.runOnUiThread { dartMessenger?.send(DartMessenger.EventType.ERROR, "CameraAccessException") }
                return
            }
        }
        
    }

    fun getStreamStatistics(result: MethodChannel.Result) {
        val ret = hashMapOf<String, Any>()
        ret["cacheSize"] = rtmpCamera.streamClient.getCacheSize()
        ret["sentAudioFrames"] = rtmpCamera.streamClient.getSentAudioFrames()
        ret["sentVideoFrames"] = rtmpCamera.streamClient.getSentVideoFrames()
        ret["droppedAudioFrames"] = rtmpCamera.streamClient.getDroppedAudioFrames()
        ret["droppedVideoFrames"] = rtmpCamera.streamClient.getDroppedVideoFrames()
        ret["isAudioMuted"] = rtmpCamera.isAudioMuted
        ret["bitrate"] = rtmpCamera.bitrate
        ret["width"] = rtmpCamera.streamWidth
        ret["height"] = rtmpCamera.streamHeight
        ret["fps"] = fps
        result.success(ret)
    }

    override fun getView(): View {
        return glView
    }

    override fun dispose() {
        isSurfaceCreated = false
        activity = null
    }

     private fun isFrontFacing(cameraName: String): Boolean {
         val cameraManager = activity?.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
         if(cameraManager == null){
             throw Exception("相机是空的")
         }
         val characteristics = cameraManager.getCameraCharacteristics(cameraName)
         return characteristics.get(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_FRONT
     }
}
