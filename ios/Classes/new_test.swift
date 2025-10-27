import Flutter
import UIKit
import HaishinKit
import AVFoundation

public class SwiftHaishinPlugin: NSObject, FlutterPlugin {
    var connection: RTMPConnection?
    var stream: RTMPStream?
    private var currentPosition: AVCaptureDevice.Position = .back
    private var previewView: MTHKView?
    private var isRecordingPaused = false
    private var recordFileName: String?

    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(name: "haishin_plugin", binaryMessenger: registrar.messenger())
        let instance = SwiftHaishinPlugin()
        registrar.addMethodCallDelegate(instance, channel: channel)
        registrar.register(PreviewFactory(previewView: instance.previewView), withId: "haishin_preview")
    }

    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        switch call.method {
        case "instantiate": instantiate(); result("initialized")
        case "startVideoStreaming":
            if let args = call.arguments as? [String: Any],
               let url = args["url"] as? String,
               let streamName = args["streamName"] as? String {
                startVideoStreaming(url: url, streamName: streamName)
                result("started")
            }
        case "pauseVideoStreaming": pauseVideoStreaming(); result("paused")
        case "resumeVideoStreaming": resumeVideoStreaming(); result("resumed")
        case "stopStreaming": stopStreaming(); result("stopped")
        case "getStreamStatistics": result(getStreamStatistics())
        case "takePicture": takePicture(result: result)
        case "switchCamera": switchCamera(); result("camera switched")
        case "onEnableAudio": onEnableAudio(); result("audio enabled")
        case "onDisableAudio": onDisableAudio(); result("audio disabled")
        case "onFlashLight": onFlashLight(); result("flashlight on")
        case "offFlashLight": offFlashLight(); result("flashlight off")
        case "startVideoRecording":
            if let args = call.arguments as? [String: Any],
               let fileName = args["fileName"] as? String {
                startVideoRecording(fileName: fileName)
                result("recording started")
            }
        case "pauseVideoRecording": pauseVideoRecording(); result("recording paused")
        case "resumeVideoRecording": resumeVideoRecording(); result("recording resumed")
        case "stopRecording": stopRecording(); result("recording stopped")
        case "dispose": dispose(); result("disposed")
        case "startPreview": startPreview(); result("preview started")
        case "availableCameras": result(availableCameras())
        default: result(FlutterMethodNotImplemented)
        }
    }

    // MARK: - Core
    private func instantiate() {
        connection = RTMPConnection()
        stream = RTMPStream(connection: connection!)
        stream?.attachAudio(AVCaptureDevice.default(for: .audio))
        stream?.attachCamera(DeviceUtil.device(withPosition: currentPosition))
    }
    private func startVideoStreaming(url: String, streamName: String) {
        connection?.connect(url)
        stream?.publish(streamName, type: .live)
    }
    private func pauseVideoStreaming() { stream?.pause() }
    private func resumeVideoStreaming() { stream?.resume() }
    private func stopStreaming() { stream?.close(); connection?.close() }

    // MARK: - Extra
    private func getStreamStatistics() -> [String: Any] {
        guard let stream = stream else { return [:] }
        return [
            "fps": stream.currentFPS,
            "bitrate": stream.currentBitrate,
            "resolution": "\(stream.videoSettings[.width] ?? 0)x\(stream.videoSettings[.height] ?? 0)"
        ]
    }
    private func takePicture(result: @escaping FlutterResult) {
        guard let view = stream?.drawable as? UIView else {
            result(FlutterError(code: "NO_VIEW", message: "No drawable view", details: nil)); return
        }
        UIGraphicsBeginImageContextWithOptions(view.bounds.size, false, UIScreen.main.scale)
        view.drawHierarchy(in: view.bounds, afterScreenUpdates: true)
        let image = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()
        if let data = image?.jpegData(compressionQuality: 0.9) {
            let path = NSTemporaryDirectory() + "snapshot.jpg"
            try? data.write(to: URL(fileURLWithPath: path))
            result(path)
        } else {
            result(FlutterError(code: "CAPTURE_FAILED", message: "Failed to capture image", details: nil))
        }
    }
    private func switchCamera() {
        currentPosition = (currentPosition == .back) ? .front : .back
        let device = DeviceUtil.device(withPosition: currentPosition)
        stream?.attachCamera(device)
    }
    private func onEnableAudio() { stream?.attachAudio(AVCaptureDevice.default(for: .audio)) }
    private func onDisableAudio() { stream?.attachAudio(nil) }
    private func onFlashLight() {
        guard let device = DeviceUtil.device(withPosition: currentPosition), device.hasTorch else { return }
        do {
            try device.lockForConfiguration()
            try device.setTorchModeOn(level: AVCaptureDevice.maxAvailableTorchLevel)
            device.unlockForConfiguration()
        } catch { print("Flashlight on error: \(error)") }
    }
    private func offFlashLight() {
        guard let device = DeviceUtil.device(withPosition: currentPosition), device.hasTorch else { return }
        do {
            try device.lockForConfiguration()
            device.torchMode = .off
            device.unlockForConfiguration()
        } catch { print("Flashlight off error: \(error)") }
    }
    private func dispose() {
        stream?.close(); connection?.close()
        stream = nil; connection = nil; previewView = nil
    }
    private func startPreview() {
        if previewView == nil { previewView = MTHKView(frame: UIScreen.main.bounds) }
        previewView?.videoGravity = .resizeAspectFill
        stream?.drawable = previewView
    }

    // MARK: - Recording
    private func startVideoRecording(fileName: String) {
        recordFileName = fileName
        stream?.publish(fileName, type: .localRecord)
        isRecordingPaused = false
    }
    private func pauseVideoRecording() {
        if !isRecordingPaused { stream?.pause(); isRecordingPaused = true }
    }
    private func resumeVideoRecording() {
        if isRecordingPaused { stream?.resume(); isRecordingPaused = false }
    }
    private func stopRecording() {
        if recordFileName != nil {
            stream?.close()
            recordFileName = nil
            isRecordingPaused = false
        }
    }
  private func availableCameras() -> [[String: Any]] {
      let discovery = AVCaptureDevice.DiscoverySession(
          deviceTypes: [.builtInWideAngleCamera, .builtInDualCamera, .builtInTripleCamera],
          mediaType: .video,
          position: .unspecified
      )
      var cameras: [[String: Any]] = []
      for device in discovery.devices {
          var position = "unspecified"
          switch device.position {
          case .front: position = "front"
          case .back: position = "back"
          case .unspecified: position = "unspecified"
          @unknown default: position = "unknown"
          }
          cameras.append([
              "id": device.uniqueID,
              "name": device.localizedName,
              "position": position
          ])
      }
      return cameras
  }
}

// MARK: - PlatformView for Preview
public class PreviewFactory: NSObject, FlutterPlatformViewFactory {
    private var previewView: MTHKView?
    init(previewView: MTHKView?) { self.previewView = previewView }
    public func create(withFrame frame: CGRect, viewIdentifier viewId: Int64, arguments args: Any?) -> FlutterPlatformView {
        return PreviewPlatformView(frame: frame, previewView: previewView)
    }
}
public class PreviewPlatformView: NSObject, FlutterPlatformView {
    private var _view: UIView
    init(frame: CGRect, previewView: MTHKView?) { _view = previewView ?? UIView(); super.init() }
    public func view() -> UIView { return _view }
}
