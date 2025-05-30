package com.app.rtmp_publisher

import android.app.Activity
import androidx.core.app.ActivityCompat
import android.Manifest.permission
import io.flutter.plugin.common.PluginRegistry.RequestPermissionsResultListener
import android.content.pm.PackageManager
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat

interface ResultCallback {
    fun onResult(errorCode: String?, errorDescription: String?)
}

class CameraPermissions {
    // Mirrors camera.dart
    enum class ResolutionPreset {
        low, medium, high, veryHigh, ultraHigh, max
    }
    private var ongoing = false
    fun requestPermissions(
        activity: Activity,
        permissionsRegistry: PermissionStuff,
        enableAudio: Boolean,
        callback: ResultCallback) {
        if (ongoing) {
            callback.onResult("cameraPermission", "Camera permission request ongoing")
        }
        if (!hasCameraPermission(activity) || enableAudio && !hasAudioPermission(activity) || !hasWriteExternalStoragePermission(activity) || !hasWakeLockPermission(activity)) {
            permissionsRegistry.adddListener(
                CameraRequestPermissionsListener(
                    object : ResultCallback {
                        override fun onResult(errorCode: String?, errorDescription: String?) {
                            ongoing = false
                            callback.onResult(errorCode, errorDescription)
                        }
                    }))
            ongoing = true
            ActivityCompat.requestPermissions(
                activity,
                if (enableAudio) arrayOf(permission.CAMERA, permission.RECORD_AUDIO, permission.WRITE_EXTERNAL_STORAGE, permission.WAKE_LOCK) else arrayOf(permission.CAMERA, permission.WRITE_EXTERNAL_STORAGE, permission.WAKE_LOCK),
                CAMERA_REQUEST_ID)
        } else {
            // Permissions already exist. Call the callback with success.
            callback.onResult(null, null)
        }
    }
    //检查相机权限
    private fun hasCameraPermission(activity: Activity): Boolean {
        return (ContextCompat.checkSelfPermission(activity, permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED)
    }
    //检查音频权限
    private fun hasAudioPermission(activity: Activity): Boolean {
        return (ContextCompat.checkSelfPermission(activity, permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED)
    }
    //检查存储写入权限
    private fun hasWriteExternalStoragePermission(activity: Activity): Boolean {
        return (ContextCompat.checkSelfPermission(activity, permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED)
    }
    //检查屏幕唤醒权限
    private fun hasWakeLockPermission(activity: Activity): Boolean {
        return (ContextCompat.checkSelfPermission(activity, permission.WAKE_LOCK)
                == PackageManager.PERMISSION_GRANTED)
    }
    //权限请求监听事件
    @VisibleForTesting
    internal class CameraRequestPermissionsListener @VisibleForTesting constructor(val callback: ResultCallback) : RequestPermissionsResultListener {
        // There's no way to unregister permission listeners in the v1 embedding, so we'll be called
        // duplicate times in cases where the user denies and then grants a permission. Keep track of if
        // we've responded before and bail out of handling the callback manually if this is a repeat
        // call.
        var alreadyCalled = false
        override fun onRequestPermissionsResult(id: Int, permissions: Array<String>, grantResults: IntArray): Boolean {
            if (alreadyCalled || id != CAMERA_REQUEST_ID) {
                return false
            }
            alreadyCalled = true
            if (grantResults.size == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                callback.onResult("cameraPermission", "MediaRecorderCamera permission not granted")
            } else if (grantResults.size > 1 && grantResults[1] != PackageManager.PERMISSION_GRANTED) {
                callback.onResult("cameraPermission", "MediaRecorderAudio permission not granted")
            } else {
                callback.onResult(null, null)
            }
            return true
        }

    }
    companion object {
        private const val CAMERA_REQUEST_ID = 9796
    }
}