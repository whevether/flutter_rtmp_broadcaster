import Flutter
import UIKit
import AVFoundation
import Accelerate
import CoreMotion
import HaishinKit
import os
import ReplayKit
import VideoToolbox
@MainActor
@objc
public class FlutterRTMPStreaming : NSObject {
    private let connection = RTMPConnection()
    private var stream: RTMPStream!
    private let mixer = MediaMixer()
    private var pausedState = false
    private var url: String? = nil
    private var name: String? = nil
    private let eventSink: FlutterEventSink
    private let bitrateStrategy = HKStreamVideoAdaptiveBitRateStrategy(mamimumVideoBitrate: 2500 * 1024)

    @objc
    public init(sink: @escaping FlutterEventSink) {
        eventSink = sink
    }

    
    private func open(url: String, width: Int, height: Int, bitrate: Int) async{
        stream = RTMPStream(connection: connection)
        await mixer.addOutput(stream)

        // 配置视频参数
    
            do {
                try await mixer.attachVideo(AVCaptureDevice.default(.builtInWideAngleCamera,
                                                                    for: .video,
                                                                    position: .back))
                try await mixer.attachAudio(AVCaptureDevice.default(for: .audio))
            } catch {
                print("Attach error: \(error)")
            }
        

        await stream.setVideoSettings(VideoCodecSettings(
            videoSize: CGSize(width: width, height: height),
            bitRate: bitrate,
            profileLevel: kVTProfileLevel_H264_Baseline_AutoLevel as String,
            scalingMode: .trim,
            bitRateMode: .average,
            maxKeyFrameIntervalDuration: 2,
            allowFrameReordering: nil,
            isHardwareEncoderEnabled: true,
        ))
        await mixer.setFrameRate(30.0)

        let uri = URL(string: url)
        self.name = uri?.lastPathComponent
        var bits = url.components(separatedBy: "/")
        bits.removeLast()
        self.url = bits.joined(separator: "/")

        // 使用带 retries 的连接逻辑
        await  self.connectAndPublish(url: self.url ?? "", name: self.name, maxRetries: 3)
    }
     // ObjC 桥接方法（供 RtmppublisherPlugin.m 调用）
   @objc
   public func openWithUrl(_ url: String, width: CGFloat, height: CGFloat, bitrate: Int) {
       Task {
           await self.open(url: url, width: Int(width), height: Int(height), bitrate: bitrate)
       }
   }
    
    private  func connectAndPublish(url: String, name: String?, maxRetries: Int = 3) async{
        
            var attempt = 0
            while attempt <= maxRetries {
                do {
                    let connectResponse = try await connection.connect(url)
                    print("connect: \(connectResponse)");
                    let publishResponse = try await stream.publish(name)
                    print("publish: \(publishResponse)");
                    await stream.setBitrateStorategy(bitrateStrategy)
                    eventSink(["event": "rtmp_connected"])
                    return
                } catch {
                    if attempt >= maxRetries {
                        eventSink(["event": "error",
                                   "errorDescription": "RTMP connect failed after \(attempt) retries: \(error)"])
                        return
                    }
                    attempt += 1
                    let delay = UInt64(pow(2.0, Double(attempt)) * 1_000_000_000) // 纳秒
                    eventSink(["event": "rtmp_retry",
                               "errorDescription": "retry \(attempt) after error: \(error)"])
                    try? await Task.sleep(nanoseconds: delay)
                }
            }
        
    }

    @objc
    public func pauseVideoStreaming() {
        Task { await self.pauseVideoStreaming() }
    }

    private func pauseVideoStreaming() async{
        let response = try? await stream.pause(true)
        pausedState = true
        print("pauseVideoStreaming: \(String(describing:response))")
    }

    @objc
    public func resumeVideoStreaming() {
        Task { await self.resumeVideoStreaming() }
    }
    
    private func resumeVideoStreaming() async{
       let response = try? await stream.pause(false)
        pausedState = false

        print("resumeVideoStreaming: \(String(describing:response))")
        
            
    }

    @objc
    public func isPaused() -> Bool {
        return pausedState
    }
    @objc
    public func getStreamStatistics() -> NSDictionary {
        var result: NSDictionary = [:]
        let semaphore = DispatchSemaphore(value: 0)

        Task {
            let stats = await self.getStreamStatistics()
            result = stats
            semaphore.signal()
        }

        semaphore.wait()
        return result
    }
    
    private func getStreamStatistics() async -> NSDictionary  {
        let orientation = await mixer.videoOrientation.rawValue
        let bitrate = await stream.videoSettings.bitRate
        let width = await stream.videoSettings.videoSize.width
        let height = await stream.videoSettings.videoSize.height
        let fps = await stream.currentFPS
        let ret: NSDictionary = [
            "paused": pausedState,
            "bitrate": bitrate,
            "width": width,
            "height": height,
            "fps": fps,
            "orientation": orientation
        ]
        return ret
    }
    @objc
    public func addVideoData(buffer: CMSampleBuffer) {
        Task { await self.addVideoData(buffer: buffer) }
    }

    private func addVideoData(buffer: CMSampleBuffer) async{
        if let description = CMSampleBufferGetFormatDescription(buffer) {
            let dimensions = CMVideoFormatDescriptionGetDimensions(description)
            await stream.setVideoSettings(VideoCodecSettings(
                videoSize: CGSize(width: Int(dimensions.width), height: Int(dimensions.height)),
                bitRate: 1200 * 1024,
                profileLevel: kVTProfileLevel_H264_Baseline_AutoLevel as String,
                scalingMode: .trim,
                bitRateMode: .average,
                maxKeyFrameIntervalDuration: 2,
                allowFrameReordering: nil,
                isHardwareEncoderEnabled: true
            ))
            await mixer.setFrameRate(24.0)
        }
       await mixer.append(buffer)
    }

    @objc
    public func addAudioData(buffer: CMSampleBuffer) {
        Task { await self.addAudioData(buffer: buffer) }
    }

    private func addAudioData(buffer: CMSampleBuffer) async {
        await mixer.append(buffer)
    }

    @objc
    public func close() {
        Task { await self.close() }
    }

    private func close() async{
        try? await connection.close()
    }
}
public final actor HKStreamVideoAdaptiveBitRateStrategy: HKStreamBitRateStrategy {
    /// The status counts threshold for restoring the status
    public static let statusCountsThreshold: Int = 15

    public let mamimumVideoBitRate: Int
    public let mamimumAudioBitRate: Int = 0
    private var sufficientBWCounts: Int = 0
    private var zeroBytesOutPerSecondCounts: Int = 0

    /// Creates a new instance.
    public init(mamimumVideoBitrate: Int) {
        self.mamimumVideoBitRate = mamimumVideoBitrate
    }

    public func adjustBitrate(_ event: NetworkMonitorEvent, stream: some HKStream) async {
        switch event {
        case .status:
            var videoSettings = await stream.videoSettings
            if videoSettings.bitRate == mamimumVideoBitRate {
                return
            }
            if Self.statusCountsThreshold <= sufficientBWCounts {
                let incremental = mamimumVideoBitRate / 10
                videoSettings.bitRate = min(videoSettings.bitRate + incremental, mamimumVideoBitRate)
                await stream.setVideoSettings(videoSettings)
                sufficientBWCounts = 0
            } else {
                sufficientBWCounts += 1
            }
        case .publishInsufficientBWOccured(let report):
            sufficientBWCounts = 0
            var videoSettings = await stream.videoSettings
            let audioSettings = await stream.audioSettings
            if 0 < report.currentBytesOutPerSecond {
                let bitRate = Int(report.currentBytesOutPerSecond * 8) / (zeroBytesOutPerSecondCounts + 1)
                videoSettings.bitRate = max(bitRate - audioSettings.bitRate, mamimumVideoBitRate / 10)
                videoSettings.frameInterval = 0.0
                sufficientBWCounts = 0
                zeroBytesOutPerSecondCounts = 0
            } else {
                switch zeroBytesOutPerSecondCounts {
                case 2:
                    videoSettings.frameInterval = VideoCodecSettings.frameInterval10
                case 4:
                    videoSettings.frameInterval = VideoCodecSettings.frameInterval05
                default:
                    break
                }
                await stream.setVideoSettings(videoSettings)
                zeroBytesOutPerSecondCounts += 1
            }
        case .reset:
            var videoSettings = await stream.videoSettings
            zeroBytesOutPerSecondCounts = 0
            videoSettings.bitRate = mamimumVideoBitRate
            await stream.setVideoSettings(videoSettings)
        }
    }
}
