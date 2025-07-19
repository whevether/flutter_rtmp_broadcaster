// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// ignore_for_file: public_member_api_docs

import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:path_provider/path_provider.dart';
import 'package:rtmp_streaming/camera.dart';
import 'package:wakelock_plus/wakelock_plus.dart';

class CameraExampleHome extends StatefulWidget {
  @override
  _CameraExampleHomeState createState() {
    return _CameraExampleHomeState();
  }
}

/// Returns a suitable camera icon for [direction].
IconData getCameraLensIcon(CameraLensDirection? direction) {
  switch (direction) {
    case CameraLensDirection.back:
      return Icons.camera_rear;
    case CameraLensDirection.front:
      return Icons.camera_front;
    case CameraLensDirection.external:
    default:
      return Icons.camera;
  }
}

void logError(String code, String message) =>
    print('Error: $code\nError Message: $message');

class _CameraExampleHomeState extends State<CameraExampleHome>
    with WidgetsBindingObserver {
  CameraController? controller;
  String? imagePath;
  String? videoPath;
  String? url;
  VoidCallback? videoPlayerListener;
  bool enableAudio = true; // 是否启用音频
  bool useOpenGL = true;
  bool switchCamera = false; // 为false 表示使用前置摄像头，为true表示使用后置摄像头
  bool isFlashLight = false; // false表示关闭闪光灯，true表示打开闪光灯
  TextEditingController _textFieldController =
      TextEditingController(text: "rtmp://192.168.1.20/live/show1");

  bool get isStreaming => controller?.value.isStreamingVideoRtmp ?? false;
  bool isVisible = true;

  bool get isControllerInitialized => controller?.value.isInitialized ?? false;
  bool get isRecordingVideo => controller?.value.isRecordingVideo ?? false;
  bool get isRecordingPaused => controller?.value.isRecordingPaused ?? false;
  bool get isStreamingPaused => controller?.value.isStreamingPaused ?? false;
  bool get isTakingPicture => controller?.value.isTakingPicture ?? false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    WakelockPlus.disable();
    super.dispose();
  }

  @override
  Future<void> didChangeAppLifecycleState(AppLifecycleState state) async {
    // App state changed before we got the chance to initialize.
    if (controller == null || !isControllerInitialized) {
      return;
    }
    if (state == AppLifecycleState.paused) {
      isVisible = false;
      await pauseVideoRecording();
    } else if (state == AppLifecycleState.resumed) {
      isVisible = true;
      await resumeVideoRecording();
    }
  }

  final GlobalKey<ScaffoldState> _scaffoldKey = GlobalKey<ScaffoldState>();

  @override
  Widget build(BuildContext context) {
    Color color = Colors.grey;

    if (controller != null) {
      if (isRecordingVideo) {
        color = Colors.redAccent;
      } else if (isStreaming) {
        color = Colors.blueAccent;
      }
    }

    return Scaffold(
      key: _scaffoldKey,
      appBar: AppBar(
        title: const Text('Camera example'),
      ),
      body: Column(
        children: <Widget>[
          Expanded(
            flex: 1,
            child: Container(
              child: Padding(
                padding: const EdgeInsets.all(1.0),
                child: Center(
                  child: _cameraPreviewWidget(),
                ),
              ),
              decoration: BoxDecoration(
                color: Colors.black,
                border: Border.all(
                  color: color,
                  width: 3.0,
                ),
              ),
            ),
          ),
          _captureControlRowWidget(),
          _toggleAudioWidget(),
          Padding(
            padding: const EdgeInsets.all(5.0),
            child: Wrap(
              children: <Widget>[
                _cameraTogglesRowWidget(),
                _thumbnailWidget(),
              ],
            ),
          ),
        ],
      ),
    );
  }

  /// Display camera preview (or a message if the preview is not available).
  Widget _cameraPreviewWidget() {
    if (controller == null || !isControllerInitialized) {
      return const Text(
        'Tap a camera',
        style: TextStyle(
          color: Colors.white,
          fontSize: 24.0,
          fontWeight: FontWeight.w900,
        ),
      );
    }

    return AspectRatio(
      aspectRatio: controller!.value.aspectRatio,
      child: CameraPreview(controller!),
    );
  }

  /// Toggle recording audio
  Widget _toggleAudioWidget() {
    return Wrap(
      children: <Widget>[
        // const Text('Enable Audio:'),
        // Switch(
        //   value: enableAudio,
        //   onChanged: (bool value) {
        //     enableAudio = value;
        //     setState(() {

        //     });
        //     if (controller != null) {
        //       onNewCameraSelected(controller!.description);
        //     }
        //   },
        // ),
        const SizedBox(
          width: 5,
        ),
        Text('switch ${switchCamera ? 'back' : 'font'} Camera'),
        Switch(
          value: switchCamera,
          onChanged: (bool value) async {
            if (controller != null) {
              switchCamera = value;
              setState(() {});
              String cameraId = switchCamera ? "0" : "1";
              await controller!.switchCamera(cameraId);
            } else {
              showInSnackBar('Please select a camera first.');
            }
          },
        ),
        const SizedBox(
          width: 5,
        ),
        Text('${enableAudio ? 'Enable' : 'Disable'} Audio'),
        Switch(
          value: enableAudio,
          onChanged: (bool value) async {
            if (controller != null) {
              enableAudio = value;
              setState(() {});
              enableAudio
                  ? await controller!.onEnableAudio()
                  : await controller!.onDisableAudio();
            } else {
              showInSnackBar('Please select a camera first.');
            }
          },
        ),
        const SizedBox(
          width: 5,
        ),
        Text('${isFlashLight ? 'Enable' : 'Disable'} FlashLight'),
        Switch(
          value: isFlashLight,
          onChanged: (bool value) async {
            if (controller != null) {
              isFlashLight = value;
              setState(() {});
              isFlashLight
                  ? await controller!.onFlashLight()
                  : await controller!.offFlashLight();
            } else {
              showInSnackBar('Please select a camera first.');
            }
          },
        ),
      ],
    );
  }

  /// Display the thumbnail of the captured image or video.
  Widget _thumbnailWidget() {
    return Expanded(
      child: Align(
        alignment: Alignment.centerRight,
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            imagePath == null
                ? Container()
                : SizedBox(
                    child: Image.file(File(imagePath!)),
                    width: 64.0,
                    height: 64.0,
                  ),
          ],
        ),
      ),
    );
  }

  /// Display the control bar with buttons to take pictures and record videos.
  Widget _captureControlRowWidget() {
    if (controller == null) return Container();

    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceEvenly,
      mainAxisSize: MainAxisSize.max,
      children: <Widget>[
        IconButton(
          icon: const Icon(Icons.camera_alt),
          color: Colors.blue,
          onPressed: controller != null && isControllerInitialized
              ? onTakePictureButtonPressed
              : null,
        ),
        IconButton(
          icon: const Icon(Icons.videocam),
          color: Colors.blue,
          onPressed: () {
            if (controller != null &&
                isControllerInitialized &&
                !isRecordingVideo) {
              onVideoRecordButtonPressed();
            }
          },
        ),
        IconButton(
          icon: const Icon(Icons.watch),
          color: Colors.blue,
          onPressed:
              controller != null && isControllerInitialized && !isStreaming
                  ? onVideoStreamingButtonPressed
                  : null,
        ),
        IconButton(
          icon: controller != null && (isRecordingPaused || isStreamingPaused)
              ? Icon(Icons.play_arrow)
              : Icon(Icons.pause),
          color: Colors.blue,
          onPressed: () {
            if (controller != null &&
                isControllerInitialized &&
                (isRecordingVideo || isStreaming)) {
              if (controller != null &&
                  (isRecordingPaused || isStreamingPaused)) {
                onResumeButtonPressed();
              } else {
                onPauseButtonPressed();
              }
            } else {
              return null;
            }
          },
        ),
        IconButton(
          icon: const Icon(Icons.stop),
          color: Colors.red,
          onPressed: controller != null &&
                  isControllerInitialized &&
                  (isRecordingVideo || isStreaming)
              ? onStopButtonPressed
              : null,
        )
      ],
    );
  }

  /// Display a row of toggles to select the camera (or a message if no camera is available).
  Widget _cameraTogglesRowWidget() {
    final List<Widget> toggles = <Widget>[];

    if (cameras.isEmpty) {
      return const Text('No camera found');
    } else {
      for (CameraDescription cameraDescription in cameras) {
        toggles.add(
          SizedBox(
            width: 90.0,
            child: RadioListTile<CameraDescription>(
              title: Icon(getCameraLensIcon(cameraDescription.lensDirection)),
              groupValue: controller?.description,
              value: cameraDescription,
              onChanged: (CameraDescription? cld) =>
                  isRecordingVideo ? null : onNewCameraSelected(cld),
            ),
          ),
        );
      }
    }

    return Wrap(children: toggles);
  }

  String timestamp() => DateTime.now().millisecondsSinceEpoch.toString();

  void showInSnackBar(String message) {
    ScaffoldMessenger.of(context)
        .showSnackBar(SnackBar(content: Text(message)));
  }

  void onNewCameraSelected(CameraDescription? cameraDescription) async {
    if (cameraDescription == null) return;

    if (controller != null) {
      await stopVideoStreaming();
      await controller?.dispose();
    }
    controller = CameraController(
      cameraDescription,
      ResolutionPreset.medium,
      enableAudio: enableAudio,
      androidUseOpenGL: useOpenGL,
    );

    // If the controller is updated then update the UI.
    controller!.addListener(() async {
      if (mounted) setState(() {});

      if (controller != null) {
        if (controller!.value.hasError) {
          showInSnackBar('Camera error ${controller!.value.errorDescription}');
          await stopVideoStreaming();
        } else {
          try {
            final Map<dynamic, dynamic> event =
                controller!.value.event as Map<dynamic, dynamic>;
            print('Event $event');
            final String eventType = event['eventType'] as String;
            if (isVisible && isStreaming && eventType == 'rtmp_retry') {
              showInSnackBar('BadName received, endpoint in use.');
              await stopVideoStreaming();
            }
          } catch (e) {
            print(e);
          }
        }
      }
    });

    try {
      await controller!.initialize();
    } on CameraException catch (e) {
      _showCameraException(e);
    }

    if (mounted) {
      final number = int.tryParse(cameraDescription.name!);
      switchCamera = number?.isEven ?? false;
      setState(() {});
    }
  }

  void onTakePictureButtonPressed() {
    takePicture().then((String? filePath) {
      if (mounted) {
        setState(() {
          imagePath = filePath;
        });
        showInSnackBar('Picture saved to $filePath');
      }
    });
  }

  void onVideoRecordButtonPressed() {
    startVideoRecording().then((String? filePath) {
      if (mounted) setState(() {});
      showInSnackBar('Saving video to $filePath');
      WakelockPlus.enable();
    });
  }

  void onVideoStreamingButtonPressed() {
    startVideoStreaming().then((String? url) {
      if (mounted) setState(() {});
      showInSnackBar('Streaming video to $url');
      WakelockPlus.enable();
    });
  }

  void onRecordingAndVideoStreamingButtonPressed() {
    startRecordingAndVideoStreaming().then((String? url) {
      if (mounted) setState(() {});
      showInSnackBar('Recording streaming video to $url');
      WakelockPlus.enable();
    });
  }

  void onStopButtonPressed() {
    if (this.isStreaming) {
      stopVideoStreaming().then((_) {
        if (mounted) setState(() {});
        showInSnackBar('Video streamed to: $url');
      });
    } else {
      stopVideoRecording().then((_) {
        if (mounted) setState(() {});
        showInSnackBar('Video recorded to: $videoPath');
      });
    }
    WakelockPlus.disable();
  }

  void onPauseButtonPressed() {
    pauseVideoRecording().then((_) {
      if (mounted) setState(() {});
      showInSnackBar('Video recording paused');
    });
  }

  void onResumeButtonPressed() {
    resumeVideoRecording().then((_) {
      if (mounted) setState(() {});
      showInSnackBar('Video recording resumed');
    });
  }

  void onStopStreamingButtonPressed() {
    stopVideoStreaming().then((_) {
      if (mounted) setState(() {});
      showInSnackBar('Video not streaming to: $url');
    });
  }

  void onPauseStreamingButtonPressed() {
    pauseVideoRecording().then((_) {
      if (mounted) setState(() {});
      showInSnackBar('Video streaming paused');
    });
  }

  void onResumeStreamingButtonPressed() {
    resumeVideoRecording().then((_) {
      if (mounted) setState(() {});
      showInSnackBar('Video streaming resumed');
    });
  }

  Future<String?> startVideoRecording() async {
    if (!isControllerInitialized) {
      showInSnackBar('Error: select a camera first.');
      return null;
    }

    final Directory? extDir = await getExternalStorageDirectory();
    if (extDir == null) return null;

    final String dirPath = '${extDir.path}/Movies/flutter_test';
    await Directory(dirPath).create(recursive: true);
    final String filePath = '$dirPath/${timestamp()}.mp4';

    if (isRecordingVideo) {
      // A recording is already started, do nothing.
      return null;
    }

    try {
      videoPath = filePath;
      await controller!.startVideoRecording(filePath);
    } on CameraException catch (e) {
      _showCameraException(e);
      return null;
    }
    return filePath;
  }

  Future<void> stopVideoRecording() async {
    if (!isRecordingVideo) {
      return null;
    }

    try {
      await controller!.stopVideoRecording();
    } on CameraException catch (e) {
      _showCameraException(e);
      return null;
    }
  }

  Future<void> pauseVideoRecording() async {
    try {
      if (isRecordingVideo) {
        await controller!.pauseVideoRecording();
      }
      if (isStreaming && Platform.isIOS) {
        await controller!.pauseVideoStreaming();
      }
    } on CameraException catch (e) {
      _showCameraException(e);
      rethrow;
    } catch (e) {
      print(e);
    }
  }

  Future<void> resumeVideoRecording() async {
    try {
      if (isRecordingVideo) {
        await controller!.resumeVideoRecording();
      }
      if (isStreaming && Platform.isIOS) {
        await controller!.resumeVideoStreaming();
      }
    } on CameraException catch (e) {
      _showCameraException(e);
      rethrow;
    } catch (e) {
      print(e);
    }
  }

  Future<String> _getUrl() async {
    // Open up a dialog for the url
    String result = _textFieldController.text;

    return await showDialog(
        context: context,
        builder: (context) {
          return AlertDialog(
            title: Text('Url to Stream to'),
            content: TextField(
              controller: _textFieldController,
              decoration: InputDecoration(hintText: "Url to Stream to"),
              onChanged: (String str) => result = str,
            ),
            actions: <Widget>[
              TextButton(
                child: new Text(
                    MaterialLocalizations.of(context).cancelButtonLabel),
                onPressed: () {
                  Navigator.of(context).pop();
                },
              ),
              TextButton(
                child: Text(MaterialLocalizations.of(context).okButtonLabel),
                onPressed: () {
                  Navigator.pop(context, result);
                },
              )
            ],
          );
        });
  }

  Future<String?> startRecordingAndVideoStreaming() async {
    if (!isControllerInitialized) {
      showInSnackBar('Error: select a camera first.');
      return null;
    }

    if (isStreaming) {
      return null;
    }

    String myUrl = await _getUrl();

    final Directory extDir = await getApplicationDocumentsDirectory();
    final String dirPath = '${extDir.path}/Movies/flutter_test';
    await Directory(dirPath).create(recursive: true);
    final String filePath = '$dirPath/${timestamp()}.mp4';

    try {
      url = myUrl;
      videoPath = filePath;
      await controller!.startVideoRecordingAndStreaming(videoPath!, url!);
    } on CameraException catch (e) {
      _showCameraException(e);
      return null;
    }
    return url;
  }

  Future<String?> startVideoStreaming() async {
    await stopVideoStreaming();
    if (controller == null) {
      return null;
    }
    if (!isControllerInitialized) {
      showInSnackBar('Error: select a camera first.');
      return null;
    }

    if (isStreaming) {
      return null;
    }

    // Open up a dialog for the url
    String myUrl = await _getUrl();

    try {
      url = myUrl;
      await controller!.startVideoStreaming(url!);
    } on CameraException catch (e) {
      _showCameraException(e);
      return null;
    }
    return url;
  }

  Future<void> stopVideoStreaming() async {
    if (controller == null || !isControllerInitialized) {
      return;
    }
    if (!isStreaming) {
      return;
    }

    try {
      await controller!.stopVideoStreaming();
    } on CameraException catch (e) {
      _showCameraException(e);
      return null;
    }
  }

  Future<String?> takePicture() async {
    if (!isControllerInitialized) {
      showInSnackBar('Error: select a camera first.');
      return null;
    }
    final Directory? extDir = await getExternalStorageDirectory();
    final String dirPath = '${extDir?.path}/Pictures/flutter_test';
    await Directory(dirPath).create(recursive: true);
    final String filePath = '$dirPath/${timestamp()}.jpg';

    if (isTakingPicture) {
      // A capture is already pending, do nothing.
      return null;
    }

    try {
      await controller!.takePicture(filePath);
    } on CameraException catch (e) {
      _showCameraException(e);
      return null;
    }
    return filePath;
  }

  void _showCameraException(CameraException e) {
    logError(e.code, e.description ?? "No description found");
    showInSnackBar(
        'Error: ${e.code}\n${e.description ?? "No description found"}');
  }
}

class CameraApp extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: CameraExampleHome(),
    );
  }
}

List<CameraDescription> cameras = [];

Future<void> main() async {
  // Fetch the available cameras before initializing the app.
  try {
    WidgetsFlutterBinding.ensureInitialized();
    cameras = await availableCameras();
  } on CameraException catch (e) {
    logError(e.code, e.description ?? "No description found");
  }
  runApp(CameraApp());
}
