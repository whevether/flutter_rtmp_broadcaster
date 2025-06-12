# Flutter RTMP streaming

Extend the functionality of the Flutter camera plugin with this plugin. Seamlessly integrate RTMP streaming into your Android and iOS applications, while excluding web platforms.

Utilize a consistent API structure similar to the camera plugin, preserving existing installation requirements. A distinctive feature is the introduction of the `startStreaming(url)` API, enabling developers to initiate real-time streaming to a designated RTMP URL.

This plugin employs established tools:
- Android leverages [rtmp-rtsp-stream-client-java](https://github.com/pedroSG94/rtmp-rtsp-stream-client-java).
- iOS integration involves [HaishinKit.swift](https://github.com/shogo4405/HaishinKit.swift).

## Features:

- Seamlessly embed live camera previews within widgets
- Capture snapshots, conveniently saving them to files
- Enable video recording capabilities
- Access image streams directly from Dart

## Support Development ☕

Developing and maintaining this plugin takes time and effort. If you find this plugin useful and would like to show your appreciation, consider making a donation. Your contributions help ensure the continued development and improvement of the plugin. 🚀

You can make a donation and buy me a cup of coffee to keep the momentum going:

[!["Buy Me A Coffee"](https://www.buymeacoffee.com/assets/img/custom_images/orange_img.png)](https://www.buymeacoffee.com/emiliodallatorre)

Your support is invaluable, and every donation is deeply appreciated. Thank you for helping to make this plugin even better! 🙏

## Usage

Using this plugin is as easy as using the original [camera plugin](https://pub.dev/packages/camera), but it extends the controller with more functionalities. You can find here a list of the supported new APIs.

| Function | Description |
|----------|-------------|
| `startVideoStreaming(String url, {int bitrate = 1200 * 1024, bool? androidUseOpenGL})` | Initiates video streaming to an RTMP endpoint. |
| `startVideoRecordingAndStreaming(String filePath, String url, {int bitrate = 1200 * 1024, bool? androidUseOpenGL})` | Initiates video streaming to an RTMP endpoint while simultaneously saving a high-quality version to a local file. |
| `pauseVideoStreaming()` | Pauses an ongoing video stream. Android is not implemented. Because the Android library does not have a pause method, Android needs to stop streaming before restarting it|
| `resumeVideoStreaming()` | Resumes a paused video stream. Android is not implemented. Because the Android library does not have a pause method, Android needs to stop streaming before restarting it|
| `stopEverything()` | Halts ongoing video streaming and recording processes. |
| ... | ... |

## Getting started

To quickly integrate this plugin into your Flutter project, follow the platform-specific instructions below.

### iOS

To get started on iOS, follow these steps:

1. Open the `ios/Runner/Info.plist` file.
2. Add the following two rows to the `Info.plist` file:
   - Key: `Privacy - Camera Usage Description`
     Value: A description of why your app needs access to the camera.
   - Key: `Privacy - Microphone Usage Description`
     Value: A description of why your app needs access to the microphone.

Or in text format add the keys:

```xml
    <key>NSCameraUsageDescription</key>
    <string>App requires access to the camera for live streaming feature.</string>
    <key>NSMicrophoneUsageDescription</key>
    <string>App requires access to the microphone for live streaming feature.</string>
```

### Android

To get started with the **Flutter RTMP Streaming Plugin** on Android, follow these steps:

1. Open your `android/app/build.gradle` file.
2. Change the minimum Android SDK version to 21 or higher by modifying the `minSdkVersion` value:
   ```groovy
   minSdkVersion 21
   ```
3. Next, open your `android/app/build.gradle` file.
4. Inside the `android` block, add the following code to the `packagingOptions` section to prevent packaging issues:
   ```groovy
   android {
       // ... other configurations

       packagingOptions {
           exclude 'project.clj'
       }
   }
    ```

These adjustments will ensure compatibility and address packaging concerns for your Android implementation.

## Example

For an illustrative implementation of this plugin, you can explore the [example code](https://github.com/whevether/flutter_rtmp_broadcastertree/master/example). This provides a practical showcase of utilizing the `rtmp_publisher` plugin to facilitate real-time video streaming to MUX. Additionally, the example demonstrates snapshot capturing and video recording. To explore further, clone the repository and execute the app on either an Android or iOS device.

## Troubleshooting & issues

If you encounter any issues while using this plugin, don't hesitate to seek assistance. To report problems or unexpected behavior, please open an issue on the [GitHub repository](https://github.com/whevether/flutter_rtmp_broadcaster). I will do my best to address the issues and provide solutions. However, please understand that my availability for addressing issues is limited due to time constraints.

For those in need of expedited and prioritized support, I offer paid fast support services. If you require immediate assistance, personalized guidance, or customized solutions, feel free to reach out to me at [info@emiliodallatorre.it](mailto:info@emiliodallatorre.it) to discuss potential support options.

Your feedback is crucial in improving this plugin's functionality and ensuring its reliability. Thank you for contributing to the ongoing development of this plugin!

## Contributing

Contributions to this plugin are highly encouraged and greatly appreciated. If you have ideas for enhancements, bug fixes, or new features, please feel free to contribute. To get started, follow these steps:

1. Fork the repository and create a new branch for your contribution.
2. Make your desired changes or additions.
3. Ensure your code is properly formatted and tested.
4. Submit a pull request with a detailed explanation of your changes.

Your contributions play a vital role in improving the plugin and benefiting the entire community. Thank you for taking the time to enhance this plugin!
