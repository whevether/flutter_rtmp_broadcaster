# rtmp_stream 1.0.0

## 📖 概述
`rtmp_stream` 是一个 Flutter 插件，旨在为 **Android** 和 **iOS** 提供统一的 RTMP 推流与视频录制能力。  
它解决了 pub.dev 上缺乏合适 Flutter RTMP 插件的问题：现有插件要么长期无人维护，要么依赖包过时，无法满足现代移动应用的需求。

---

## ⚙️ 技术基础
- **Android**：基于 [`com.github.pedroSG94.RootEncoder:library:2.6.6`](https://github.com/pedroSG94/RootEncoder)  
- **iOS**：基于 [HaishinKit 2.2.2](https://github.com/shogo4405/HaishinKit.swift)  

通过这两个成熟的底层库，`rtmp_stream` 提供了跨平台一致的 API 接口，简化了开发者的使用成本。

---

## ❓ 为什么要做这个插件
- pub.dev 上没有合适的 Flutter RTMP 插件。  
- 现有插件存在以下问题：  
  - 长期无人维护。  
  - 依赖包过时，无法兼容最新的 Flutter 与平台 SDK。  

因此，`rtmp_stream` 的目标是提供一个 **现代、稳定、可维护** 的 RTMP 推流解决方案。

---

## 🛠️ 支持的方法

### 🌍 Android 与 iOS 通用方法
- 📷 获取可用摄像头：`availableCameras`  
- ⚙️ 初始化插件：`initialize`  
- 🎥 开始本地视频录制：`startVideoRecording`  
- ⏹️ 停止本地视频录制：`stopRecording`  
- 📡 开始录制并推送直播流：`startVideoRecordingAndStreaming`  
- ⏹️ 停止录制或推送直播流：`stopRecordingOrStreaming`  
- 📡 开始推送直播流：`startVideoStreaming`  
- ⏹️ 停止推送直播流：`stopStreaming`  
- 🔄 切换摄像头：`switchCamera`  
- 🔊 切换开启/关闭声音：`switchAudio`  
- 💡 切换开启/关闭闪光灯：`switchFlashLight`  
- 📊 获取流信息：`getStreamStatistics`  
- 🗑️ 销毁插件：`dispose`  

---

### 🍎 iOS 平台独有方法
由于 HaishinKit 不仅支持推流，还支持 **RTMP 播放**，因此 iOS 平台提供了额外的功能：

- ⏸️ 暂停直播流播放：`pauseStream`  
  > 注意：这不是暂停推流，而是暂停播放 RTMP 流。  
- ▶️ 恢复直播流播放：`resumeStream`  
  > 注意：这不是恢复推流，而是恢复播放 RTMP 流。  
- 🎚️ 音频比特率设置：`setAudioSettings`  
- 🎞️ 视频设置：`setVideoSettings`  
- 🔊 获取是否暂时静音：`getHasAudio`  
- 🔊 设置暂时静音：`setHasAudio`  
- 🎥 获取是否暂时停止视频：`getHasVideo`  
- 🎥 设置暂时停止视频：`setHasVideo`  
- 🎬 设置直播帧率：`setFrameRate`  
- ⚙️ 设置直播预设配置：`setSessionPreset`  
- 🖼️ 设置直播屏幕宽高：`setScreenSettings`  

---

## 🚀 总结
`rtmp_stream 1.0.0` 正式版为 Flutter 开发者提供了一个跨平台、现代化的 RTMP 推流与视频录制插件，解决了现有生态的不足。  
它基于 Android 的 RootEncoder 与 iOS 的 HaishinKit，提供一致的 API，同时在 iOS 平台扩展了播放、音视频控制等独有功能，帮助开发者快速构建直播与视频录制应用。
