# rtmp_stream 1.0.0

## 📖 Overview
`rtmp_stream` is a Flutter plugin designed to provide unified RTMP streaming and video recording capabilities for **Android** and **iOS**.  
It addresses the lack of suitable Flutter RTMP plugins on pub.dev: existing plugins are either no longer maintained or rely on outdated dependencies, making them unsuitable for modern mobile applications.

---

## ⚙️ Technical Foundation
- **Android**: Based on [`com.github.pedroSG94.RootEncoder:library:2.6.6`](https://github.com/pedroSG94/RootEncoder)  
- **iOS**: Based on [HaishinKit 2.2.2](https://github.com/shogo4405/HaishinKit.swift)  

By leveraging these mature libraries, `rtmp_stream` provides a consistent cross-platform API interface, reducing development complexity.

---

## ❓ Why This Plugin
- No suitable Flutter RTMP plugin exists on pub.dev.  
- Existing plugins suffer from:  
  - Long-term lack of maintenance.  
  - Outdated dependencies, incompatible with the latest Flutter and platform SDKs.  

Therefore, the goal of `rtmp_stream` is to deliver a **modern, stable, and maintainable** RTMP streaming solution.

---

## 🛠️ Supported Methods

### 🌍 Common Methods (Android & iOS)
- 📷 Get available cameras: `availableCameras`  
- ⚙️ Initialize plugin: `initialize`  
- 🎥 Start local video recording: `startVideoRecording`  
- ⏹️ Stop local video recording: `stopRecording`  
- 📡 Start recording and streaming: `startVideoRecordingAndStreaming`  
- ⏹️ Stop recording or streaming: `stopRecordingOrStreaming`  
- 📡 Start video streaming: `startVideoStreaming`  
- ⏹️ Stop video streaming: `stopStreaming`  
- 🔄 Switch camera: `switchCamera`  
- 🔊 Toggle audio on/off: `switchAudio`  
- 💡 Toggle flashlight on/off: `switchFlashLight`  
- 📊 Get stream statistics: `getStreamStatistics`  
- 🗑️ Dispose plugin: `dispose`  

---

### 🍎 iOS Exclusive Methods
Since HaishinKit supports not only streaming but also **RTMP playback**, iOS provides additional features:

- ⏸️ Pause stream playback: `pauseStream`  
  > Note: This pauses playback, not streaming.  
- ▶️ Resume stream playback: `resumeStream`  
  > Note: This resumes playback, not streaming.  
- 🎚️ Set audio bitrate: `setAudioSettings`  
- 🎞️ Set video settings: `setVideoSettings`  
- 🔊 Get temporary mute status: `getHasAudio`  
- 🔊 Set temporary mute: `setHasAudio`  
- 🎥 Get temporary video stop status: `getHasVideo`  
- 🎥 Set temporary video stop: `setHasVideo`  
- 🎬 Set streaming frame rate: `setFrameRate`  
- ⚙️ Set session preset: `setSessionPreset`  
- 🖼️ Set screen dimensions: `setScreenSettings`  

---

### 🤖 Android Exclusive Methods
Android provides additional features during live streaming:

- 📸 Take snapshot during streaming: `takePicture`  
- ⏸️ Pause recording: `pauseVideoRecording`  
- ▶️ Resume recording: `resumeVideoRecording`  
- 🎨 Apply filter: `setFilter`  
  > Filter `type` values correspond to filters defined in source code:  
  > [CameraNativeView.kt](https://github.com/whevether/flutter_rtmp_broadcaster/blob/main/android/src/main/kotlin/com/app/rtmp_stream/CameraNativeView.kt)  
- ❌ Remove filter: `removeFilter`  

---

## 🚀 Conclusion
`rtmp_stream 1.0.0` provides Flutter developers with a cross-platform, modern RTMP streaming and video recording plugin, addressing the shortcomings of the current ecosystem.  
It is built on Android’s RootEncoder and iOS’s HaishinKit, offering a unified API while extending playback and audio/video controls on iOS, and snapshot and filter features on Android—helping developers quickly build live streaming and recording applications.
