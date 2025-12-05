import Foundation
#if canImport(Flutter)
import Flutter
#endif
#if canImport(FlutterMacOS)
import FlutterMacOS
#endif
import HaishinKit
import RTMPHaishinKit
import AVFoundation
import VideoToolbox
#if canImport(UIKit)
import UIKit
#endif

final class MediaMixerHandler: NSObject {
  var texture: HKStreamFlutterTexture?
  private lazy var mixer = MediaMixer(multiTrackAudioMixingEnabled: false)
  
  override init() {
    super.init()
#if canImport(UIKit)
    NotificationCenter.default.addObserver(self, selector: #selector(on(_:)), name: UIDevice.orientationDidChangeNotification, object: nil)
#endif
  }
  
  func addOutput(_ output: some MediaMixerOutput, startRunning: Bool) {
    Task {
      await mixer.addOutput(output)
      if(startRunning == true){
        await mixer.startRunning()
      }
      
    }
  }
  
  func removeOutput(_ output: some MediaMixerOutput) {
    Task { await mixer.removeOutput(output) }
  }
  
  func stopRunning() {
    Task {
      await mixer.stopCapturing()
      await mixer.stopRunning()
    }
  }
  
  func dispose()  async{
    stopRunning()
    _ = try? await mixer.attachVideo(nil, track: 0)
    _ = try? await mixer.attachAudio(nil, track: 0)
  }
  
#if canImport(UIKit)
  @objc
  private func on(_ notification: Notification) {
    
    var orientation: AVCaptureVideoOrientation?
    
    if #available(iOS 13.0, *) {
      if let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene {
        orientation = DeviceUtil.videoOrientation(by: windowScene.interfaceOrientation)
      }
    } else {
      orientation = DeviceUtil.videoOrientation(by: UIApplication.shared.statusBarOrientation)
    }
    
    guard let videoOrientation = orientation else { return}
    Task { await mixer.setVideoOrientation(videoOrientation) }
  }
#endif
  // 获取是否开启了声音
  func getHasAudio() async -> Bool{
    let isMuted = await !mixer.audioMixerSettings.isMuted
    return isMuted
  }
  //关闭/开启 声音
  func setHasAudio(hasAudio: Bool?) async {
    if(hasAudio == nil){
      return
    }
    var audioMixerSettings = await mixer.audioMixerSettings
    audioMixerSettings.isMuted = !hasAudio!
    await mixer.setAudioMixerSettings(audioMixerSettings)
  }
  //获取是否有视频
  func getHasVideo()async ->Bool{
    let hasVideo = await !mixer.videoMixerSettings.isMuted
    return hasVideo
  }
  // 关闭/开启 视频
  func setHasVideo(hasVideo: Bool?)async{
    if(hasVideo == nil){
      return
    }
    var videoMixerSettings = await mixer.videoMixerSettings
    videoMixerSettings.isMuted = !hasVideo!
    await mixer.setVideoMixerSettings(videoMixerSettings)
  }
  //设置帧速率
  func setFrameRate(frameRate: NSNumber?) async{
    if(frameRate == nil){
      return
    }
    _ = try? await mixer.setFrameRate(frameRate!.doubleValue)
  }
  //设置分辨率
  func setSessionPreset(sessionPreset: String?) async{
    let preset: AVCaptureSession.Preset = switch sessionPreset {
    case "high": .high
    case "medium": .medium
    case "low": .low
    case "hd1280x720": .hd1280x720
    case "hd1920x1080": .hd1920x1080
    case "hd4K3840x2160": .hd4K3840x2160
    case "vga640x480": .vga640x480
    case "iFrame960x540": .iFrame960x540
    case "iFrame1280x720": .iFrame1280x720
    case "cif352x288": .cif352x288
    default: .hd1280x720
    }
    await mixer.setSessionPreset(preset)
  }
  //附加音频到直播
  func attachAudio(isEnable: Bool?)async{
    if (isEnable == nil || isEnable == false) {
      try? await mixer.attachAudio(nil)
    } else {
      try? await mixer.attachAudio(AVCaptureDevice.default(for: .audio))
    }
    
  }
  //设置屏幕大小
  @ScreenActor
  func setScreenSettings(width: NSNumber?,height: NSNumber?)->Int64? {
    if(width == nil || height == nil){
      return nil
    }
    mixer.screen.size = CGSize(width: CGFloat(width!.floatValue), height: CGFloat(height!.floatValue))
    return texture?.textureId
  }
  
  //附加视频到直播
  func attachVideo(resolution: String?, cameraId: String?)async ->CGSize{
    //    print("cameraId \(cameraId)")
    if(cameraId == nil){
      try? await mixer.attachVideo(nil, track: 0)
      return .zero
    }else{
#if os(iOS)
      let device = AVCaptureDevice(uniqueID: cameraId!)
      //      let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back)
#else
      let device = AVCaptureDevice.devices(for: .video).first
#endif
      if let device = device {
        try? await mixer.attachVideo(device, track: 0)
        if let resolution{
          switch resolution {
          case "max":
            let dimensions = device.activeFormat.highResolutionStillImageDimensions
            return CGSize(width: Int(dimensions.width), height: Int(dimensions.height))
          case "ultraHigh":
            return CGSize(width: 2160, height: 3840)
          case "veryHigh":
            return CGSize(width: 1080, height: 1920)
          case "high":
            return CGSize(width: 720, height: 1280)
          case "medium":
            return CGSize(width: 480, height: 640)
          case "low":
            return CGSize(width: 288, height: 352)
          default:
            return .zero
          }
        }else{
          return .zero
        }
        
      }else{
        return .zero
      }
    }
    
  }
}
