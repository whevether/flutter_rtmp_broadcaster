# rtmp_stream

## 📖 概述
`rtmp_stream` 是一个 Flutter 插件，旨在为 **Android** 和 **iOS** 提供统一的 RTMP 推流与视频录制能力。  
它解决了 pub.dev 上缺乏合适 Flutter RTMP 插件的问题：现有插件要么长期无人维护，要么依赖包过时，无法满足现代移动应用的需求。

---

## ⚙️ 技术基础
- **Android**：基于 [`com.github.pedroSG94.RootEncoder:library:2.7.1`](https://github.com/pedroSG94/RootEncoder)  
- **iOS**：基于 [HaishinKit 2.2.5](https://github.com/HaishinKit/HaishinKit.swift)  

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
- 📸 直播时截图：`takePicture` 

---

### 🍎 iOS 平台独有方法
由于 HaishinKit 不仅支持推流，还支持 **RTMP 播放**，因此 iOS 平台提供了额外的功能：

- ⏸️ 暂停直播流播放：`pauseStream`  
  > 注意：这不是暂停推流，而是暂停播放 RTMP 流。  
- ▶️ 恢复直播流播放：`resumeStream`  
  > 注意：这不是恢复推流，而是恢复播放 RTMP 流。  
- 🎚️ 音频比特率设置：`setAudioSettings`  
- 🎞️ 视频设置：`setVideoSettings`（可选 `expectedFrameRate`、`bitRateMode` — HaishinKit 2.2.1+ / 2.2.2+）  
- 📱 多任务相机：`setMultitaskingCameraAccessEnabled`（HaishinKit 2.2.5+，iOS 17+ 且设备支持时）  
- 🔊 获取是否暂时静音：`getHasAudio`  
- 🔊 设置暂时静音：`setHasAudio`  
- 🎥 获取是否暂时停止视频：`getHasVideo`  
- 🎥 设置暂时停止视频：`setHasVideo`  
- 🎬 设置直播帧率：`setFrameRate`  
- ⚙️ 设置直播预设配置：`setSessionPreset`  
- 🖼️ 设置直播屏幕宽高：`setScreenSettings`  

---
### 🤖 Android 平台独有方法
Android 平台在直播推流时提供了额外的功能：

- ⏸️ 暂停录制：`pauseVideoRecording`  
- ▶️ 恢复录制：`resumeVideoRecording`  
- 🎨 设置滤镜：`setFilter`  
  > 滤镜 `type` 值对应的滤镜请查看源码：  
  > [CameraNativeView.kt](https://github.com/whevether/flutter_rtmp_broadcaster/blob/main/android/src/main/kotlin/com/app/rtmp_stream/CameraNativeView.kt)  
- ❌ 移除滤镜：`removeFilter`  
- 🎨 BT.709 编码：`setForceBt709Color`（RootEncoder 2.7.0+）  
- 📶 RTMP Ping / RTT：`setRtmpShouldSendPings`（RootEncoder 2.7.0+）  

---

## 📘 扩展 API 使用说明（分平台）

### Android：`setForceBt709Color(bool enabled)`
- **作用**：让视频编码使用 BT.709 色彩矩阵，便于与期望 BT.709 的播放器或服务端对齐。
- **调用时机**：在 `initialize` 之后，在开始录制或推流之前设置（插件在准备编码时生效）。
- **示例**：
```dart
await controller.setForceBt709Color(true);
await controller.startVideoStreaming(url);
```

### Android：`setRtmpShouldSendPings(bool enabled)`
- **作用**：开启 RTMP 周期 ping，服务端 pong 后客户端可计算往返时延（RTT）。
- **调用时机**：在 `initialize` 之后、**`startVideoStreaming` 之前**（连接建立前生效）。
- **关联**：推流过程中可调用 `getStreamStatistics()`；开启 ping 且服务端支持时，返回结果中含 `rttMicros`（微秒）、`bytesSend` 等字段。
- **示例**：
```dart
await controller.setRtmpShouldSendPings(true);
await controller.startVideoStreaming(url);
final stats = await controller.getStreamStatistics();
```

### iOS：`setVideoSettings({ ... })`
原有参数：`bitrate`、`width`、`height`、`frameInterval`、`profileLevel`（仅 iOS）。

**HaishinKit 2.2.1+ / 2.2.2+ 扩展参数**（可选命名参数）：

| 参数 | 类型 | 说明 |
|------|------|------|
| `expectedFrameRate` | `double?` | 编码期望帧率；并写入 RTMP **onMetaData** 的 `framerate`（2.2.2+）。 |
| `bitRateMode` | `String?` | `"average"`（默认类行为）、`"constant"`（iOS 16+）、`"variable"`（iOS 26+，VideoToolbox 可变码率）。 |

- **调用时机**：`initialize` 之后，一般在开始推流前或推流早期；直播中改参请遵循 HaishinKit 对热更新限制。
- **示例**：
```dart
await controller.setVideoSettings(
  expectedFrameRate: 30,
  bitRateMode: 'average',
);
```

### iOS：`setMultitaskingCameraAccessEnabled(bool enabled)`
- **作用**：在系统支持时启用 `AVCaptureSession` 的多任务相机访问，便于分屏、画中画等场景下保持采集（HaishinKit 2.2.5+）。
- **要求**：需 **iOS 17+**（插件侧配置 API），且设备 `isMultitaskingCameraAccessSupported` 为真。
- **调用时机**：`initialize` 之后、开始推流之前。
- **示例**：
```dart
await controller.setMultitaskingCameraAccessEnabled(true);
await controller.startVideoStreaming(url);
```

---

## 🚀 总结
`rtmp_stream` 为 Flutter 开发者提供跨平台、现代化的 RTMP 推流与视频录制能力。  
它基于 Android 的 RootEncoder 与 iOS 的 HaishinKit，在 iOS 上扩展播放与音视频控制，在 Android 上扩展截图与滤镜等能力，帮助快速构建直播与录制应用。
