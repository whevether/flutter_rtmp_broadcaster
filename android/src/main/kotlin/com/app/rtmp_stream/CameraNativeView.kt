package com.app.rtmp_stream

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.media.MediaPlayer
import android.os.Build
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import android.view.View
import androidx.annotation.RequiresApi
import com.app.rtmp_stream.CameraPermissions.ResolutionPreset
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.gl.SpriteGestureController
import com.pedro.encoder.input.gl.render.filters.BasicDeformationFilterRender
import com.pedro.encoder.input.gl.render.filters.BeautyFilterRender
import com.pedro.encoder.input.gl.render.filters.BlackFilterRender
import com.pedro.encoder.input.gl.render.filters.BlurFilterRender
import com.pedro.encoder.input.gl.render.filters.BrightnessFilterRender
import com.pedro.encoder.input.gl.render.filters.CartoonFilterRender
import com.pedro.encoder.input.gl.render.filters.ChromaFilterRender
import com.pedro.encoder.input.gl.render.filters.ChromaticAberrationFilterRender
import com.pedro.encoder.input.gl.render.filters.CircleFilterRender
import com.pedro.encoder.input.gl.render.filters.ColorFilterRender
import com.pedro.encoder.input.gl.render.filters.ContrastFilterRender
import com.pedro.encoder.input.gl.render.filters.CropFilterRender
import com.pedro.encoder.input.gl.render.filters.DistortedTvFilterRender
import com.pedro.encoder.input.gl.render.filters.DuotoneFilterRender
import com.pedro.encoder.input.gl.render.filters.EarlyBirdFilterRender
import com.pedro.encoder.input.gl.render.filters.EdgeDetectionFilterRender
import com.pedro.encoder.input.gl.render.filters.ExposureFilterRender
import com.pedro.encoder.input.gl.render.filters.FireFilterRender
import com.pedro.encoder.input.gl.render.filters.GammaFilterRender
import com.pedro.encoder.input.gl.render.filters.GlitchFilterRender
import com.pedro.encoder.input.gl.render.filters.GreyScaleFilterRender
import com.pedro.encoder.input.gl.render.filters.HalftoneLinesFilterRender
import com.pedro.encoder.input.gl.render.filters.Image70sFilterRender
import com.pedro.encoder.input.gl.render.filters.LamoishFilterRender
import com.pedro.encoder.input.gl.render.filters.MoneyFilterRender
import com.pedro.encoder.input.gl.render.filters.NegativeFilterRender
import com.pedro.encoder.input.gl.render.filters.NoiseFilterRender
import com.pedro.encoder.input.gl.render.filters.PixelatedFilterRender
import com.pedro.encoder.input.gl.render.filters.PolygonizationFilterRender
import com.pedro.encoder.input.gl.render.filters.RGBSaturationFilterRender
import com.pedro.encoder.input.gl.render.filters.RainbowFilterRender
import com.pedro.encoder.input.gl.render.filters.RippleFilterRender
import com.pedro.encoder.input.gl.render.filters.RotationFilterRender
import com.pedro.encoder.input.gl.render.filters.SaturationFilterRender
import com.pedro.encoder.input.gl.render.filters.SepiaFilterRender
import com.pedro.encoder.input.gl.render.filters.SharpnessFilterRender
import com.pedro.encoder.input.gl.render.filters.SnowFilterRender
import com.pedro.encoder.input.gl.render.filters.TemperatureFilterRender
import com.pedro.encoder.input.gl.render.filters.ZebraFilterRender
import com.pedro.encoder.input.gl.render.filters.`object`.GifObjectFilterRender
import com.pedro.encoder.input.gl.render.filters.`object`.ImageObjectFilterRender
import com.pedro.encoder.input.gl.render.filters.`object`.SurfaceFilterRender
import com.pedro.encoder.input.gl.render.filters.`object`.TextObjectFilterRender
import com.pedro.encoder.input.video.CameraHelper.Facing.BACK
import com.pedro.encoder.input.video.CameraHelper.Facing.FRONT
import com.pedro.encoder.utils.gl.AspectRatioMode
import com.pedro.encoder.utils.gl.TranslateTo
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
    ConnectChecker {
    private val glView = OpenGlView(activity)
    private val rtmpCamera: RtmpCamera2
    private var isSurfaceCreated = false
    private var fps = 0
    private val aBitrate = 128 * 1000
    private val vBitrate = 1200 * 1000
    private val bitrateAdapter: BitrateAdapter
  val spriteGestureController = SpriteGestureController()
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
        if (!rtmpCamera.isOnPreview) {
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
        activity?.runOnUiThread {
            dartMessenger?.send(DartMessenger.EventType.WAIT, "connection wait")
        }
    }

    override fun onConnectionSuccess() {
        activity?.runOnUiThread {
            dartMessenger?.send(DartMessenger.EventType.SUCCESS, "connection success")
        }
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
            dartMessenger?.sendCameraClosingEvent()
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
            result.error(
                "fileExists",
                "File at path '$filePath' already exists. Cannot overwrite.",
                null
            )
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
            result.error(
                "fileExists",
                "File at path '$filePath' already exists. Cannot overwrite.",
                null
            )
            return
        }
        Log.d("CameraNativeView", "startVideoRecording filePath: $filePath result: $result")


        /*if (rtmpCamera.isRecording || rtmpCamera.prepareAudio() && rtmpCamera.prepareVideo(
                streamingSize.videoFrameWidth,
                streamingSize.videoFrameHeight,
                streamingSize.videoBitRate
            )*/
        //判断如果不是视频流的话并且其用了音频
        try {
            if (!rtmpCamera.isStreaming) {
                val streamingSize = CameraUtils.computeBestPreviewSize(activity, cameraName, preset)
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
                val streamingSize = CameraUtils.computeBestPreviewSize(activity, cameraName, preset)
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
                    result.error(
                        "videoStreamingFailed",
                        "Error preparing stream, This device cant do it",
                        null
                    )
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

    fun startVideoRecordingAndStreaming(
        filePath: String?,
        url: String?,
        bitrate: Int?,
        result: MethodChannel.Result
    ) {
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
          result.success(null)
        } catch (e: CameraAccessException) {
            result.error("videoRecordingFailed", e.message, null)
        } catch (e: IOException) {
            result.error("videoRecordingFailed", e.message, null)
        }
    }


    //开/关闪光灯
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun switchFlashLight(isEnable: Boolean?, result: MethodChannel.Result) {
        try {
            if(rtmpCamera.cameraFacing != BACK){
                result.error("switchFlashLightFailed", "camera is Not BACK", null)
                return
            }
             if (isEnable == null) {
                result.error("switchFlashLightFailed", "isEnable not empty.", null)
                return
            }
            if(isEnable == true){
                 rtmpCamera.enableLantern()
            }else{
                rtmpCamera.disableLantern()
            }
          result.success(null)
        } catch (e: CameraAccessException) {
            result.error("switchFlashLightFailed", e.message, null)
        } catch (e: IOException) {
            result.error("switchFlashLightFailed", e.message, null)
        }
    }

    //切换相机式
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun switchCamera(cameraId: String?, result: MethodChannel.Result) {

        try {
          if (cameraId == null) {
            result.error("cameraIdExist", "empty cameraId!", null)
            return
          }
          rtmpCamera.switchCamera(cameraId)
          result.success(null)
        } catch (e: CameraAccessException) {
            result.error("switchCameraFailed", e.message, null)
        } catch (e: IOException) {
            result.error("switchCameraFailed", e.message, null)
        }


    }

    //开/关声音
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun switchAudio(isEnable: Boolean?,result: MethodChannel.Result) {
        try {
            if (isEnable == null) {
                result.error("switchAudioFailed", "empty isEnable!", null)
                return
            }
            if(isEnable == true){
                rtmpCamera.enableAudio()
            }else{
                rtmpCamera.disableAudio()
            }
          result.success(null)
        } catch (e: CameraAccessException) {
            result.error("switchAudioFailed", e.message, null)
        } catch (e: IOException) {
            result.error("switchAudioFailed", e.message, null)
        }
    }

    //设置滤镜
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun setFilter(type: Int?,filePath: String?, result: MethodChannel.Result) {
        try {
          if(type == null){
            result.error("setFilter", "type is empty", null)
            return
          }
            spriteGestureController.stopListener()
          when (type) {
            0 -> {
              rtmpCamera.glInterface?.setFilter(BasicDeformationFilterRender())
              result.success(null)
            }
            1 -> {
              rtmpCamera.glInterface?.setFilter(BeautyFilterRender())
              result.success(null)
            }
            2 -> {
              rtmpCamera.glInterface?.setFilter(BlackFilterRender())
              result.success(null)
            }
            3 -> {
              rtmpCamera.glInterface?.setFilter(BlurFilterRender())
              result.success(null)
            }
            4 -> {
              rtmpCamera.glInterface?.setFilter(BrightnessFilterRender())
              result.success(null)
            }
            5 -> {
              rtmpCamera.glInterface?.setFilter(CartoonFilterRender())
              result.success(null)
            }
            6 -> {
              if (filePath == null) {
                result.error("setFilter", "filePath Not Empty", null)
                return
              }
              val chromaFilterRender = ChromaFilterRender()
              rtmpCamera.glInterface?.setFilter(chromaFilterRender)
              chromaFilterRender.setImage(
                BitmapFactory.decodeFile(filePath)
              )
              result.success(null)
            }
            7 -> {
              rtmpCamera.glInterface?.setFilter(ChromaticAberrationFilterRender())
              result.success(null)
            }
            8 -> {
              rtmpCamera.glInterface?.setFilter(CircleFilterRender())
              result.success(null)
            }
            9 -> {
              rtmpCamera.glInterface?.setFilter(ColorFilterRender())
              result.success(null)
            }
            10 -> {
              rtmpCamera.glInterface?.setFilter(ContrastFilterRender())
              result.success(null)
            }
            11 -> {
              rtmpCamera.glInterface?.setFilter(CropFilterRender().apply {
                //crop center of the image with 40% of width and 40% of height
                setCropArea(30f, 30f, 40f, 40f)
              })
              result.success(null)
            }
            12 -> {
              rtmpCamera.glInterface?.setFilter(DistortedTvFilterRender())
              result.success(null)
            }
            13 -> {
              rtmpCamera.glInterface?.setFilter(DuotoneFilterRender())
              result.success(null)
            }
            14 -> {
              rtmpCamera.glInterface?.setFilter(EarlyBirdFilterRender())
              result.success(null)
            }
            15 -> {
              rtmpCamera.glInterface?.setFilter(EdgeDetectionFilterRender())
              result.success(null)
            }
            16 -> {
              rtmpCamera.glInterface?.setFilter(ExposureFilterRender())
              result.success(null)
            }
            17 -> {
              rtmpCamera.glInterface?.setFilter(FireFilterRender())
              result.success(null)
            }
            18 -> {
              rtmpCamera.glInterface?.setFilter(GammaFilterRender())
              result.success(null)
            }
            19 -> {
              rtmpCamera.glInterface?.setFilter(GlitchFilterRender())
              result.success(null)
            }
            20 -> {
              if (filePath == null) {
                result.error("setFilter", "filePath Not Empty", null)
                return
              }
              val file = File(filePath)
              val inputStream = FileInputStream(file)
              val gifObjectFilterRender = GifObjectFilterRender()
              gifObjectFilterRender.setGif(inputStream)
              rtmpCamera.glInterface?.setFilter(gifObjectFilterRender)
              gifObjectFilterRender.setScale(50f, 50f)
              gifObjectFilterRender.setPosition(TranslateTo.BOTTOM)
              spriteGestureController.setBaseObjectFilterRender(gifObjectFilterRender)
              result.success(null)
            }
            21 -> {
              rtmpCamera.glInterface?.setFilter(GreyScaleFilterRender())
              result.success(null)
            }
            22 -> {
              rtmpCamera.glInterface?.setFilter(HalftoneLinesFilterRender())
              result.success(null)
            }
            23 -> {
              if (filePath == null) {
                result.error("setFilter", "filePath Not Empty", null)
                return
              }
              val imageObjectFilterRender = ImageObjectFilterRender()
              rtmpCamera.glInterface?.setFilter(imageObjectFilterRender)
              imageObjectFilterRender.setImage(
                BitmapFactory.decodeFile(filePath)
              )
              imageObjectFilterRender.setScale(50f, 50f)
              imageObjectFilterRender.setPosition(TranslateTo.RIGHT)
              spriteGestureController.setBaseObjectFilterRender(imageObjectFilterRender) //Optional
              spriteGestureController.setPreventMoveOutside(false)
              result.success(null)
            }
            24 -> {
              rtmpCamera.glInterface?.setFilter(Image70sFilterRender())
              result.success(null)
            }
            25 -> {
              rtmpCamera.glInterface?.setFilter(LamoishFilterRender())
              result.success(null)
            }
            26 -> {
              rtmpCamera.glInterface?.setFilter(MoneyFilterRender())
              result.success(null)
            }
            27 -> {
              rtmpCamera.glInterface?.setFilter(NegativeFilterRender())
              result.success(null)
            }
            28 -> {
              rtmpCamera.glInterface?.setFilter(NoiseFilterRender())
              result.success(null)
            }
            29 -> {
              rtmpCamera.glInterface?.setFilter(PixelatedFilterRender())
              result.success(null)
            }
            30 -> {
              rtmpCamera.glInterface?.setFilter(PolygonizationFilterRender())
              result.success(null)
            }
            31 -> {
              rtmpCamera.glInterface?.setFilter(RainbowFilterRender())
              result.success(null)
            }
            32 -> {
              val rgbSaturationFilterRender = RGBSaturationFilterRender()
              rtmpCamera.glInterface?.setFilter(rgbSaturationFilterRender)
              rgbSaturationFilterRender.setRGBSaturation(1f, 0.8f, 0.8f)
              result.success(null)
            }
            33 -> {
              rtmpCamera.glInterface?.setFilter(RippleFilterRender())
              result.success(null)
            }
            34 -> {
              val rotationFilterRender = RotationFilterRender()
              rtmpCamera.glInterface?.setFilter(rotationFilterRender)
              rotationFilterRender.rotation = 90
              result.success(null)
            }
            35 -> {
              rtmpCamera.glInterface?.setFilter(SaturationFilterRender())
              result.success(null)
            }
            36 -> {
              rtmpCamera.glInterface?.setFilter(SepiaFilterRender())
              result.success(null)
            }
            37 -> {
              rtmpCamera.glInterface?.setFilter(SharpnessFilterRender())
              result.success(null)
            }
            38-> {
              rtmpCamera.glInterface?.setFilter(SnowFilterRender())
              result.success(null)
            }
            39-> {
              if (filePath == null) {
                result.error("setFilter", "filePath Not Empty", null)
                return
              }
              val surfaceFilterRender =
                SurfaceFilterRender { surfaceTexture -> //You can render this filter with other api that draw in a surface. for example you can use VLC
                  val mediaPlayer = MediaPlayer()
                  mediaPlayer.setDataSource(filePath)
                  mediaPlayer.setSurface(Surface(surfaceTexture))
                  mediaPlayer.start()
                }
              rtmpCamera.glInterface?.setFilter(surfaceFilterRender)
              surfaceFilterRender.setScale(50f, 33.3f)
              spriteGestureController.setBaseObjectFilterRender(surfaceFilterRender)
              result.success(null)
            }
            40 -> {
              rtmpCamera.glInterface?.setFilter(TemperatureFilterRender())
              result.success(null)
            }
            41 -> {
              val textObjectFilterRender = TextObjectFilterRender()
              rtmpCamera.glInterface?.setFilter(textObjectFilterRender)
              textObjectFilterRender.setText("Hello world", 22f, Color.RED)
              textObjectFilterRender.setScale(50f, 50f)
              textObjectFilterRender.setPosition(TranslateTo.CENTER)
              spriteGestureController.setBaseObjectFilterRender(textObjectFilterRender) //Optional
              result.success(null)
            }
            42 -> {
              rtmpCamera.glInterface?.setFilter(ZebraFilterRender())
              result.success(null)
            }
            else -> {
              result.success(null)
            }
          }

        } catch (e: CameraAccessException) {
          result.error("setFilter", e.message, null)
        } catch (e: IOException) {
          result.error("setFilter", e.message, null)
        }
    }

    //移除滤镜
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun removeFilter(type: Int?, result: MethodChannel.Result) {
        try {
          if(type == null){
            result.error("removeFilter", "type is empty", null)
            return
          }
          spriteGestureController.stopListener()
          when (type) {
            0 -> {
              rtmpCamera.glInterface?.removeFilter(BasicDeformationFilterRender())
              result.success(null)
            }
            1 -> {
              rtmpCamera.glInterface?.removeFilter(BeautyFilterRender())
              result.success(null)
            }
            2 -> {
              rtmpCamera.glInterface?.removeFilter(BlackFilterRender())
              result.success(null)
            }
            3 -> {
              rtmpCamera.glInterface?.removeFilter(BlurFilterRender())
              result.success(null)
            }
            4 -> {
              rtmpCamera.glInterface?.removeFilter(BrightnessFilterRender())
              result.success(null)
            }
            5 -> {
              rtmpCamera.glInterface?.removeFilter(CartoonFilterRender())
              result.success(null)
            }
            6 -> {
              val chromaFilterRender = ChromaFilterRender()
              rtmpCamera.glInterface?.removeFilter(chromaFilterRender)
              result.success(null)
            }
            7 -> {
              rtmpCamera.glInterface?.removeFilter(ChromaticAberrationFilterRender())
              result.success(null)
            }
            8 -> {
              rtmpCamera.glInterface?.removeFilter(CircleFilterRender())
              result.success(null)
            }
            9 -> {
              rtmpCamera.glInterface?.removeFilter(ColorFilterRender())
              result.success(null)
            }
            10 -> {
              rtmpCamera.glInterface?.removeFilter(ContrastFilterRender())
              result.success(null)
            }
            11 -> {
              rtmpCamera.glInterface?.removeFilter(CropFilterRender())
              result.success(null)
            }
            12 -> {
              rtmpCamera.glInterface?.removeFilter(DistortedTvFilterRender())
              result.success(null)
            }
            13 -> {
              rtmpCamera.glInterface?.removeFilter(DuotoneFilterRender())
              result.success(null)
            }
            14 -> {
              rtmpCamera.glInterface?.removeFilter(EarlyBirdFilterRender())
              result.success(null)
            }
            15 -> {
              rtmpCamera.glInterface?.removeFilter(EdgeDetectionFilterRender())
              result.success(null)
            }
            16 -> {
              rtmpCamera.glInterface?.removeFilter(ExposureFilterRender())
              result.success(null)
            }
            17 -> {
              rtmpCamera.glInterface?.removeFilter(FireFilterRender())
              result.success(null)
            }
            18 -> {
              rtmpCamera.glInterface?.removeFilter(GammaFilterRender())
              result.success(null)
            }
            19 -> {
              rtmpCamera.glInterface?.removeFilter(GlitchFilterRender())
              result.success(null)
            }
            20 -> {
              val gifObjectFilterRender = GifObjectFilterRender()
              rtmpCamera.glInterface?.removeFilter(gifObjectFilterRender)
              result.success(null)
            }
            21 -> {
              rtmpCamera.glInterface?.removeFilter(GreyScaleFilterRender())
              result.success(null)
            }
            22 -> {
              rtmpCamera.glInterface?.removeFilter(HalftoneLinesFilterRender())
              result.success(null)
            }
            23 -> {
              val imageObjectFilterRender = ImageObjectFilterRender()
              rtmpCamera.glInterface?.removeFilter(imageObjectFilterRender)
              result.success(null)
            }
            24 -> {
              rtmpCamera.glInterface?.removeFilter(Image70sFilterRender())
              result.success(null)
            }
            25 -> {
              rtmpCamera.glInterface?.removeFilter(LamoishFilterRender())
              result.success(null)
            }
            26 -> {
              rtmpCamera.glInterface?.removeFilter(MoneyFilterRender())
              result.success(null)
            }
            27 -> {
              rtmpCamera.glInterface?.removeFilter(NegativeFilterRender())
              result.success(null)
            }
            28 -> {
              rtmpCamera.glInterface?.removeFilter(NoiseFilterRender())
              result.success(null)
            }
            29 -> {
              rtmpCamera.glInterface?.removeFilter(PixelatedFilterRender())
              result.success(null)
            }
            30 -> {
              rtmpCamera.glInterface?.removeFilter(PolygonizationFilterRender())
              result.success(null)
            }
            31 -> {
              rtmpCamera.glInterface?.removeFilter(RainbowFilterRender())
              result.success(null)
            }
            32 -> {
              val rgbSaturationFilterRender = RGBSaturationFilterRender()
              rtmpCamera.glInterface?.removeFilter(rgbSaturationFilterRender)
              result.success(null)
            }
            33 -> {
              rtmpCamera.glInterface?.removeFilter(RippleFilterRender())
              result.success(null)
            }
            34 -> {
              val rotationFilterRender = RotationFilterRender()
              rtmpCamera.glInterface?.removeFilter(rotationFilterRender)
              result.success(null)
            }
            35 -> {
              rtmpCamera.glInterface?.removeFilter(SaturationFilterRender())
              result.success(null)
            }
            36 -> {
              rtmpCamera.glInterface?.removeFilter(SepiaFilterRender())
              result.success(null)
            }
            37 -> {
              rtmpCamera.glInterface?.removeFilter(SharpnessFilterRender())
              result.success(null)
            }
            38-> {
              rtmpCamera.glInterface?.removeFilter(SnowFilterRender())
              result.success(null)
            }
            39-> {
              rtmpCamera.glInterface?.removeFilter(SurfaceFilterRender())
              result.success(null)
            }
            40 -> {
              rtmpCamera.glInterface?.removeFilter(TemperatureFilterRender())
              result.success(null)
            }
            41 -> {
              val textObjectFilterRender = TextObjectFilterRender()
              rtmpCamera.glInterface?.removeFilter(textObjectFilterRender)
              result.success(null)
            }
            42 -> {
              rtmpCamera.glInterface?.removeFilter(ZebraFilterRender())
              result.success(null)
            }
            else -> {
              result.success(null)
            }
          }
        } catch (e: CameraAccessException) {
          result.error("removeFilter", e.message, null)
        } catch (e: IOException) {
          result.error("removeFilter", e.message, null)
        }
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
        try {
            if (!rtmpCamera.isRecording) {
                result.error("pauseVideoRecording", "没有正在录制的视频", null)
                return
            }
            rtmpCamera.pauseRecord();
          result.success(null)
        } catch (e: CameraAccessException) {
            result.error("pauseVideoRecording", e.message, null)
            return
        } catch (e: IllegalStateException) {
            result.error("pauseVideoRecording", e.message, null)
            return
        }

    }

    fun resumeVideoRecording(result: MethodChannel.Result) {
        try {
            if (!rtmpCamera.isRecording) {
                result.error("resumeVideoRecording", "没有正在录制的视频", null)
                return
            }
            rtmpCamera.resumeRecord()
          result.success(null)
        } catch (e: CameraAccessException) {
            result.error("resumeVideoRecording", e.message, null)
            return
        } catch (e: IllegalStateException) {
            result.error("resumeVideoRecording", e.message, null)
            return
        }

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
                val previewSize = CameraUtils.computeBestPreviewSize(activity, cameraName, preset)
                val size = previewSize["size"] as Size
                rtmpCamera.startPreview(
                    if (isFrontFacing(targetCamera)) FRONT else BACK,
                    size.width,
                    size.height
                )
            } catch (e: CameraAccessException) {
                close()
                activity?.runOnUiThread {
                    dartMessenger?.send(
                        DartMessenger.EventType.ERROR,
                        "CameraAccessException"
                    )
                }
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
        if (cameraManager == null) {
            throw Exception("相机是空的")
        }
        val characteristics = cameraManager.getCameraCharacteristics(cameraName)
        return characteristics.get(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_FRONT
    }
}
