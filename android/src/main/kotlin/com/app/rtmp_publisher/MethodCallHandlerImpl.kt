package com.app.rtmp_publisher

import android.app.Activity
import android.hardware.camera2.CameraAccessException
import android.os.Build
import android.util.Log
import android.util.LongSparseArray
import androidx.annotation.RequiresApi
import com.app.rtmp_publisher.CameraPermissions.ResultCallback
import io.flutter.plugin.common.*
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.view.TextureRegistry

internal class MethodCallHandlerImpl(
        private val activity: Activity,
        private val messenger: BinaryMessenger,
        private val cameraPermissions: CameraPermissions,
        private val permissionsRegistry: PermissionStuff,
        private val textureRegistry: TextureRegistry) : MethodCallHandler {
    private val methodChannel: MethodChannel
    private val imageStreamChannel: EventChannel
//    private var camera: CameraWrapper? = null
    private var camera: Camera? = null

    init {
        methodChannel = MethodChannel(messenger, "plugins.flutter.io/rtmp_publisher")
        imageStreamChannel = EventChannel(messenger, "plugins.flutter.io/rtmp_publisher/imageStream")
        methodChannel.setMethodCallHandler(this)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "availableCameras" -> try {
                Log.i("Stuff", "availableCameras")
                result.success(CameraUtils.getAvailableCameras(activity))
            } catch (e: Exception) {
                handleException(e, result)
            }
            "initialize" -> {
                Log.i("Stuff", "initialize")
                camera?.close()
                cameraPermissions.requestPermissions(
                        activity,
                        permissionsRegistry,
                        call.argument("enableAudio")!!,
                        object : ResultCallback {
                            override fun onResult(errorCode: String?, errorDescription: String?) {
                                if (errorCode == null) {
                                    try {
                                        instantiateCamera(call, result)
                                    } catch (e: Exception) {
                                        handleException(e, result)
                                    }
                                } else {
                                    result.error(errorCode, errorDescription, null)
                                }
                            }
                        })
            }
            "takePicture" -> {
//                camera?.takePicture(call.argument("path")!!, result)
                Log.i("Stuff", "takePicture")
                result.success(null)
            }
            "prepareForVideoRecording" -> {
                Log.i("Stuff", "prepareForVideoRecording")
                // This optimization is not required for Android.
                result.success(null)
            }
            "startVideoRecording" -> {
                Log.i("Stuff", "startVideoRecording")
                camera?.startVideoRecording(call.argument("filePath")!!, result)
            }
            "startVideoStreaming" -> {
                Log.i("Stuff", "startVideoStreaming ${call.arguments.toString()}")
                var bitrate: Int? = null
                if (call.hasArgument("bitrate")) {
                    bitrate = call.argument("bitrate")
                }

                camera?.startVideoStreaming(
                        call.argument("url"),
                        bitrate,
                        result)
            }
            "startVideoRecordingAndStreaming" -> {
                Log.i("Stuff", "startVideoRecordingAndStreaming ${call.arguments.toString()}")
                var bitrate: Int? = null
                if (call.hasArgument("bitrate")) {
                    bitrate = call.argument("bitrate")
                }
                camera?.startVideoRecordingAndStreaming(
                        call.argument("filePath")!!,
                        call.argument("url"),
                        bitrate,
                        result)
            }
            "pauseVideoStreaming" -> {
                Log.i("Stuff", "pauseVideoStreaming")
                camera?.pauseVideoStreaming(result)
            }
            "resumeVideoStreaming" -> {
                Log.i("Stuff", "resumeVideoStreaming")
                camera?.resumeVideoStreaming(result)
            }
            "stopRecordingOrStreaming" -> {
                Log.i("Stuff", "stopRecordingOrStreaming")
                camera?.stopVideoRecordingOrStreaming(result)
            }
            "stopRecording" -> {
                Log.i("Stuff", "stopRecording")
                camera?.stopVideoRecording(result)
            }
            "stopStreaming" -> {
                Log.i("Stuff", "stopStreaming")
                camera?.stopVideoStreaming(result)
            }
            "pauseVideoRecording" -> {
                Log.i("Stuff", "pauseVideoRecording")
                camera?.pauseVideoRecording(result)
            }
            "resumeVideoRecording" -> {
                Log.i("Stuff", "resumeVideoRecording")
                camera?.resumeVideoRecording(result)
            }
            "startImageStream" -> {
                Log.i("Stuff", "startImageStream")
                try {
                    camera?.startPreviewWithImageStream(imageStreamChannel)
                    result.success(null)
                } catch (e: Exception) {
                    handleException(e, result)
                }
            }
            "stopImageStream" -> {
                Log.i("Stuff", "startImageStream")
                try {
                    camera?.startPreview()
                    result.success(null)
                } catch (e: Exception) {
                    handleException(e, result)
                }
            }
            "getStreamStatistics" -> {
                Log.i("Stuff", "getStreamStatistics")
                try {
                    camera?.getStreamStatistics(result)
                } catch (e: Exception) {
                    handleException(e, result)
                }
            }
            "dispose" -> {
                Log.i("Stuff", "dispose")
                if (camera != null) {
                    camera?.dispose()
                }
                result.success(null)
            }
            else -> result.notImplemented()
        }
    }

    fun stopListening() {
        methodChannel.setMethodCallHandler(null)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    @Throws(CameraAccessException::class)
    private fun instantiateCamera(call: MethodCall, result: MethodChannel.Result) {
        val cameraName = call.argument<String>("cameraName")
        val resolutionPreset = call.argument<String>("resolutionPreset")
        val streamingPreset = call.argument<String>("streamingPreset")
        val enableAudio = call.argument<Boolean>("enableAudio")!!
        var enableOpenGL = false
        if (call.hasArgument("enableAndroidOpenGL")) {
            enableOpenGL = call.argument<Boolean>("enableAndroidOpenGL")!!
        }
        val flutterSurfaceTexture = textureRegistry.createSurfaceTexture()
        val textureId: Long = flutterSurfaceTexture.id()
        val dartMessenger = DartMessenger(messenger, textureId)
//        camera = CameraWrapper(
//                activity = activity,
//                flutterTexture = flutterSurfaceTexture,
//                dartMessenger = dartMessenger,
//                cameraName = cameraName!!,
//                resolutionPreset = resolutionPreset,
//                streamingPreset = streamingPreset,
//                enableAudio = enableAudio,
//                useOpenGL = enableOpenGL)
        camera = Camera(
                activity = activity,
                flutterTexture = flutterSurfaceTexture,
                dartMessenger = dartMessenger,
                cameraName = cameraName!!,
                resolutionPreset = resolutionPreset,
                streamingPreset = streamingPreset,
                enableAudio = enableAudio,
                useOpenGL = enableOpenGL)
        camera?.apply { open(result) }
    }

    // We move catching CameraAccessException out of onMethodCall because it causes a crash
    // on plugin registration for sdks incompatible with Camera2 (< 21). We want this plugin to
    // to be able to compile with <21 sdks for apps that want the camera and support earlier version.
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun handleException(exception: Exception, result: MethodChannel.Result) {
        if (exception is CameraAccessException) {
            result.error("CameraAccess", exception.message, null)
        }
        throw (exception as RuntimeException)
    }

}