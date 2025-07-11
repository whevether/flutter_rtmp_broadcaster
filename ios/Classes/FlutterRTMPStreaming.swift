import Flutter
import UIKit
import AVFoundation
import Accelerate
import CoreMotion
import HaishinKit
import os
import ReplayKit
import VideoToolbox

@objc
public class FlutterRTMPStreaming : NSObject {
    private var rtmpConnection = RTMPConnection()
    private var rtmpStream: RTMPStream!
    private var url: String? = nil
    private var name: String? = nil
    private var retries: Int = 0
    private let eventSink: FlutterEventSink
    private let myDelegate = MyRTMPStreamQoSDelagate()
    
    @objc
    public init(sink: @escaping FlutterEventSink) {
        eventSink = sink
    }
    @MainActor
    @objc
    public func open(url: String, width: Int, height: Int, bitrate: Int) {
        rtmpConnection.delegate = myDelegate
        rtmpStream = RTMPStream(connection: rtmpConnection)
        rtmpStream.sessionPreset = .medium
        guard let device = AVCaptureDevice.default(for: AVMediaType.video) else{
            print("No video device found")
            return
        }
        do {
            try device.lockForConfiguration()
            if device.isFocusModeSupported(.continuousAutoFocus) {
                device.focusMode = .continuousAutoFocus
            }
            if device.isExposureModeSupported(.continuousAutoExposure) {
                device.exposureMode = .continuousAutoExposure
            }
            device.unlockForConfiguration()
        } catch let error as NSError {
            print("while locking device for exposurePointOfInterest: \(error)")
        }
        
        rtmpConnection.addEventListener(.rtmpStatus, selector:#selector(rtmpStatusHandler), observer: self)
        rtmpConnection.addEventListener(.ioError, selector: #selector(rtmpErrorHandler), observer: self)
        
        let uri = URL(string: url)
        self.name = uri?.pathComponents.last
        var bits = url.components(separatedBy: "/")
        bits.removeLast()
        self.url = bits.joined(separator: "/")
        
        // TODO: Da correggere
        rtmpStream.videoSettings = VideoCodecSettings(
            videoSize: CGSize(width: .init(width), height: .init(height)),
            bitRate: bitrate,
            profileLevel:  kVTProfileLevel_H264_Baseline_AutoLevel as String,
            scalingMode: .trim,
            bitRateMode: .average,
            maxKeyFrameIntervalDuration: 2,
            allowFrameReordering: nil,
            isHardwareEncoderEnabled: true
            ) 
        rtmpStream.frameRate = 30.0
        self.retries = 0
        // Run this on the ui thread.
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            
            if let orientation = DeviceUtil.videoOrientation(by: UIApplication.shared.statusBarOrientation) {
                self.rtmpStream.videoOrientation = orientation
                print(String(format: "Orient %d", orientation.rawValue))
                
                switch orientation {
                case .landscapeLeft, .landscapeRight:
                     self.rtmpStream.videoSettings.videoSize = CGSize(width: .init(width), height: .init(height))
                    break;
                default:
                    break
                }
            }
            
            self.rtmpConnection.connect(self.url ?? "frog")
        }

    }
    @objc
    private func rtmpStatusHandler(_ notification: Notification) {
        let e = Event.from(notification)
        guard let data: ASObject = e.data as? ASObject, let code: String = data["code"] as? String else {
            return
        }
        print(e)
        
        switch code {
        case RTMPConnection.Code.connectSuccess.rawValue:
            rtmpStream.publish(name)
            retries = 0
            break
        case RTMPConnection.Code.connectFailed.rawValue, RTMPConnection.Code.connectClosed.rawValue:
            guard retries <= 3 else {
                eventSink(["event" : "error",
                           "errorDescription" : "connection failed " + e.type.rawValue])
                return
            }
            retries += 1
            Thread.sleep(forTimeInterval: pow(2.0, Double(retries)))
            rtmpConnection.connect(url!)
            eventSink(["event" : "rtmp_retry",
                       "errorDescription" : "connection failed " + e.type.rawValue])
            break
        default:
            break
        }
    }
    @objc
    private func rtmpErrorHandler(_ notification: Notification) {
        if #available(iOS 10.0, *) {
            os_log("%s", notification.name.rawValue)
        }
        guard retries <= 3 else {
            eventSink(["event" : "rtmp_stopped",
                       "errorDescription" : "rtmp disconnected"])
            return
        }
        retries+=1
        Thread.sleep(forTimeInterval: pow(2.0, Double(retries)))
        rtmpConnection.connect(url!)
        eventSink(["event" : "rtmp_retry",
                   "errorDescription" : "rtmp disconnected"])
        
    }
    @objc
    public func pauseVideoStreaming() {
        rtmpStream.paused = true
    }
    
    @objc
    public func resumeVideoStreaming() {
        rtmpStream.paused = false
    }
    
    @objc
    public func isPaused() -> Bool{
        return rtmpStream.paused
    }
    
    
    @objc
    public func getStreamStatistics() -> NSDictionary {
        let ret: NSDictionary = [
            "paused": isPaused(),
            "bitrate": rtmpStream.videoSettings.bitRate,
            "width": rtmpStream.videoSettings.videoSize.width,
            "height": rtmpStream.videoSettings.videoSize.height,
            "fps": rtmpStream.currentFPS,
            "orientation": rtmpStream.videoOrientation.rawValue
        ]
        return ret
    }
    
    @objc
    public func addVideoData(buffer: CMSampleBuffer) {
        if let description = CMSampleBufferGetFormatDescription(buffer) {
            let dimensions = CMVideoFormatDescriptionGetDimensions(description)
            rtmpStream.videoSettings = VideoCodecSettings(
                videoSize: CGSize(width: .init(Int(dimensions.width)), height: .init(Int(dimensions.height))),
                bitRate: 1200 * 1024,
                profileLevel:  kVTProfileLevel_H264_Baseline_AutoLevel as String,
                scalingMode: .trim,
                bitRateMode: .average,
                maxKeyFrameIntervalDuration: 2,
                allowFrameReordering: nil,
                isHardwareEncoderEnabled: true
            ) 
            rtmpStream.frameRate = 24
        }
        rtmpStream.append(buffer)
    }
    
    @objc
    public func addAudioData(buffer: CMSampleBuffer) {
        rtmpStream.append(buffer)
    }
    
    @objc
    public func close() {
        rtmpConnection.close()
    }
}


class MyRTMPStreamQoSDelagate: RTMPConnectionDelegate {
    let minBitrate: Int = 300 * 1024
    let maxBitrate: Int = 2500 * 1024
    let incrementBitrate: Int = 512 * 1024
    
    func connection(_ connection: RTMPConnection, publishSufficientBWOccured stream: RTMPStream) {
        guard let videoBitrate = stream.videoSettings.bitRate as? Int else { return }
        
        var newVideoBitrate = videoBitrate + incrementBitrate
        if newVideoBitrate > maxBitrate {
            newVideoBitrate = maxBitrate
        }
        print("publishSufficientBWOccured update: \(videoBitrate) -> \(newVideoBitrate)")
        stream.videoSettings.bitRate = newVideoBitrate
    }
    
    
    // detect upload insufficent BandWidth
    func connection(_ connection: RTMPConnection, publishInsufficientBWOccured stream: RTMPStream) {
        guard let videoBitrate = stream.videoSettings.bitRate as? Int else { return }
        
        var newVideoBitrate = Int(videoBitrate / 2)
        if newVideoBitrate < minBitrate {
            newVideoBitrate = minBitrate
        }
        print("publishInsufficientBWOccured update: \(videoBitrate) -> \(newVideoBitrate)")
        stream.videoSettings.bitRate = newVideoBitrate
    }
    func connection(_ connection: RTMPConnection, updateStats stream: RTMPStream){}
}
