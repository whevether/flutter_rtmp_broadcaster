package com.app.rtmp_publisher

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import android.util.SparseIntArray
import android.view.Surface
import androidx.annotation.RequiresApi
import com.pedro.encoder.Frame
import com.pedro.encoder.audio.AudioEncoder
import com.pedro.encoder.audio.GetAacData
import com.pedro.encoder.input.audio.CustomAudioEffect
import com.pedro.encoder.input.audio.GetMicrophoneData
import com.pedro.encoder.input.audio.MicrophoneManager
import com.pedro.encoder.utils.CodecUtil.Force
import com.pedro.encoder.video.FormatVideoEncoder
import com.pedro.encoder.video.GetVideoData
import com.pedro.rtplibrary.util.FpsListener
import com.pedro.rtplibrary.util.RecordController
import com.pedro.rtplibrary.view.OffScreenGlThread
import net.ossrs.rtmp.ConnectCheckerRtmp
import net.ossrs.rtmp.SrsFlvMuxer
import java.nio.ByteBuffer


@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
class RtmpCameraConnector(val context: Context, val useOpenGL: Boolean, val isPortrait: Boolean, val connectChecker: ConnectCheckerRtmp) :
        GetAacData, GetVideoData, GetMicrophoneData, FpsListener.Callback,
        RecordController.Listener, ConnectCheckerRtmp {
    private var videoEncoder: AppVideoEncoder? = null
    private var microphoneManager: MicrophoneManager
    private var audioEncoder: AudioEncoder
    private var srsFlvMuxer: SrsFlvMuxer
    private var curFps: Int
    private var pausedStreaming: Boolean = false
    private var pausedRecording: Boolean = false
    private val glInterface: OffScreenGlThread = OffScreenGlThread(context)
    private var recordController: RecordController = RecordController()

    /**
     * Get stream state.
     *
     * @return true if streaming, false if not streaming.
     */
    var isStreaming = false
        private set
    var isRecording = false
        private set

    // Orientations to setup the output rotations correctly.
    private val ORIENTATIONS: SparseIntArray = SparseIntArray(4)

    private val fpsListener = FpsListener()

    init {
        microphoneManager = MicrophoneManager(this)
        audioEncoder = AudioEncoder(this)
        srsFlvMuxer = SrsFlvMuxer(this)
        fpsListener.setCallback(this)
        curFps = 0
        if (useOpenGL) {
            glInterface.init()
        }
        ORIENTATIONS.append(0, 270)
        ORIENTATIONS.append(90, 0)
        ORIENTATIONS.append(180, 90)
        ORIENTATIONS.append(270, 0)
    }

    /**
     * Set an audio effect modifying microphone's PCM buffer.
     */
    fun setCustomAudioEffect(customAudioEffect: CustomAudioEffect?) {
        microphoneManager.setCustomAudioEffect(customAudioEffect)
    }

    /**
     * @param callback get fps while record or stream
     */
    fun setFpsListener(callback: FpsListener.Callback?) {
        fpsListener.setCallback(callback)
    }

    /**
     * H264 profile.
     *
     * @param profileIop Could be ProfileIop.BASELINE or ProfileIop.CONSTRAINED
     */
    fun setProfileIop(profileIop: Byte) {
        srsFlvMuxer.setProfileIop(profileIop)
    }


    val inputSurface: Surface
        get() {
            if (useOpenGL) {
                return glInterface.surface
            } else {
                return videoEncoder!!.surface!!
            }
        }


    /**
     * Call this method before use @startStream. If not you will do a stream without video.
     *
     * @param width resolution in px.
     * @param height resolution in px.
     * @param fps frames per second of the stream.
     * @param bitrate H264 in bps.
     * @param hardwareRotation true if you want rotate using encoder, false if you with OpenGl if you
     * are using OpenGlView.
     * @param rotation could be 90, 180, 270 or 0 (Normally 0 if you are streaming in landscape or 90
     * if you are streaming in Portrait). This only affect to stream result. NOTE: Rotation with
     * encoder is silence ignored in some devices.
     * @return true if success, false if you get a error (Normally because the encoder selected
     * doesn't support any configuration seated or your device hasn't a H264 encoder).
     */
    @JvmOverloads
    fun prepareVideo(width: Int, height: Int, fps: Int, bitrate: Int, hardwareRotation: Boolean,
                     iFrameInterval: Int, rotation: Int, avcProfile: Int = -1, avcProfileLevel: Int =
                             -1, aspectRatio: Double = 1.0): Boolean {
        pausedStreaming = false
        pausedRecording = false
        videoEncoder = AppVideoEncoder(
                this,
                width,
                height,
                fps,
                bitrate,
                if (useOpenGL) 0 else rotation,
                hardwareRotation,
                iFrameInterval,
                FormatVideoEncoder.SURFACE,
                avcProfile,
                avcProfileLevel,
                aspectRatio)

        val result = videoEncoder!!.prepare()
        if (useOpenGL) {
            prepareGlInterface(ORIENTATIONS[rotation], aspectRatio)
            glInterface.addMediaCodecSurface(videoEncoder!!.surface)
        }
        return result
    }

    /**
     * backward compatibility reason
     */
    fun prepareVideo(width: Int, height: Int, fps: Int, bitrate: Int, hardwareRotation: Boolean,
                     rotation: Int, aspectRatio: Double): Boolean {
        return prepareVideo(512, 640, fps, bitrate, hardwareRotation, 2, rotation, aspectRatio = aspectRatio)
    }

    private fun prepareGlInterface(rotation: Int, aspectRatio: Double) {
        Log.i(TAG, "prepareGlInterface $rotation $isPortrait")
        this.glInterface.setEncoderSize(videoEncoder!!.width, videoEncoder!!.height)
//        this.glInterface.setPreviewSize(videoEncoder!!.width, videoEncoder!!.height)
        this.glInterface.setRotation(rotation)
        this.glInterface.start()
    }

    /**
     * Call this method before use @startStream. If not you will do a stream without audio.
     *
     * @param bitrate AAC in kb.
     * @param sampleRate of audio in hz. Can be 8000, 16000, 22500, 32000, 44100.
     * @param isStereo true if you want Stereo audio (2 audio channels), false if you want Mono audio
     * (1 audio channel).
     * @param echoCanceler true enable echo canceler, false disable.
     * @param noiseSuppressor true enable noise suppressor, false  disable.
     * @return true if success, false if you get a error (Normally because the encoder selected
     * doesn't support any configuration seated or your device hasn't a AAC encoder).
     */
    /**
     * Same to call: prepareAudio(64 * 1024, 32000, true, false, false);
     *
     * @return true if success, false if you get a error (Normally because the encoder selected
     * doesn't support any configuration seated or your device hasn't a AAC encoder).
     */
    @JvmOverloads
    fun prepareAudio(bitrate: Int = 64 * 1024, sampleRate: Int = 32000, isStereo: Boolean = true, echoCanceler: Boolean = false,
                     noiseSuppressor: Boolean = false): Boolean {
        microphoneManager.createMicrophone(sampleRate, isStereo, echoCanceler, noiseSuppressor)
        prepareAudioRtp(isStereo, sampleRate)
        return audioEncoder.prepareAudioEncoder(bitrate, sampleRate, isStereo,
                microphoneManager.maxInputSize)
    }

    /**
     * @param forceVideo force type codec used. FIRST_COMPATIBLE_FOUND, SOFTWARE, HARDWARE
     * @param forceAudio force type codec used. FIRST_COMPATIBLE_FOUND, SOFTWARE, HARDWARE
     */
    fun setForce(forceVideo: Force, forceAudio: Force) {
        videoEncoder!!.force = forceVideo
        audioEncoder.setForce(forceAudio)
    }


    /**
     * Need be called after @prepareVideo or/and @prepareAudio. This method override resolution of
     *
     * @param url of the stream like: protocol://ip:port/application/streamName
     *
     * RTSP: rtsp://192.168.1.1:1935/live/pedroSG94 RTSPS: rtsps://192.168.1.1:1935/live/pedroSG94
     * RTMP: rtmp://192.168.1.1:1935/live/pedroSG94 RTMPS: rtmps://192.168.1.1:1935/live/pedroSG94
     * @startPreview to resolution seated in @prepareVideo. If you never startPreview this method
     * startPreview for you to resolution seated in @prepareVideo.
     */
    fun startStream(url: String) {
        if (isStreaming) {
            return
        }
        isStreaming = true
        startStreamRtp(url)
    }

    fun startRecord(path: String) {
        if (isRecording) {
            return
        }
        recordController.startRecord(path, this)
        isRecording = true
        if (!isStreaming) {
            startEncoders()
        }
    }

    fun stopRecord() {
        if (isRecording) {
            recordController.stopRecord()
        }
        isRecording = false
        if (!isStreaming) {
            stopStream()
        }
    }

    fun startEncoders() {
        audioEncoder.start()
        microphoneManager.start()
        videoEncoder?.start()
    }

    private fun resetVideoEncoder() {
        videoEncoder!!.reset()
    }


    /**
     * Stop stream started with @startStream.
     */
    fun stopStream() {
        if (isStreaming) {
            isStreaming = false
            stopStreamRtp()
        }
        if (!isRecording) {
            microphoneManager.stop()
            videoEncoder?.stop()
            audioEncoder.stop()
            glInterface.stop()
        }
    }

    fun pauseStream() {
        pausedStreaming = true
    }

    fun resumeStream() {
        pausedStreaming = false
    }

    fun pauseRecord() {
        pausedRecording = true
    }

    fun resumeRecord() {
        pausedRecording = false
    }

    fun reTry(delay: Long, reason: String): Boolean {
        val result = shouldRetry(reason)
        if (result) {
            resetVideoEncoder()
            reConnect(delay)
        }
        return result
    }

    //cache control
    @Throws(RuntimeException::class)
    fun resizeCache(newSize: Int) {
        srsFlvMuxer.resizeFlvTagCache(newSize)

    }

    val cacheSize: Int
        get() = srsFlvMuxer.flvTagCacheSize

    val sentAudioFrames: Long
        get() = srsFlvMuxer.sentAudioFrames


    val sentVideoFrames: Long
        get() = srsFlvMuxer.sentVideoFrames


    val droppedAudioFrames: Long
        get() =
            srsFlvMuxer.droppedAudioFrames


    val droppedVideoFrames: Long
        get() =
            srsFlvMuxer.droppedVideoFrames

    fun resetSentAudioFrames() {
        srsFlvMuxer.resetSentAudioFrames()
    }

    fun resetSentVideoFrames() {
        srsFlvMuxer.resetSentVideoFrames()
    }

    fun resetDroppedAudioFrames() {
        srsFlvMuxer.resetDroppedAudioFrames()
    }

    fun resetDroppedVideoFrames() {
        srsFlvMuxer.resetDroppedVideoFrames()
    }

    /**
     * Basic auth developed to work with Wowza. No tested with other server
     *
     * @param user auth.
     * @param password auth.
     */

    fun setAuthorization(user: String, password: String) {
        srsFlvMuxer.setAuthorization(user, password)
    }

    fun prepareAudioRtp(isStereo: Boolean, sampleRate: Int) {
        srsFlvMuxer.setIsStereo(isStereo)
        srsFlvMuxer.setSampleRate(sampleRate)
    }

    fun startStreamRtp(url: String) {
        if (videoEncoder!!.rotation == 90 || videoEncoder!!.rotation == 270) {
            srsFlvMuxer.setVideoResolution(videoEncoder!!.height, videoEncoder!!.width)
        } else {
            srsFlvMuxer.setVideoResolution(videoEncoder!!.width, videoEncoder!!.height)
        }
        srsFlvMuxer.start(url)
    }

    fun stopStreamRtp() {
        srsFlvMuxer.stop()
    }

    fun setReTries(reTries: Int) {
        srsFlvMuxer.setReTries(reTries)
    }

    fun shouldRetry(reason: String): Boolean {
        return srsFlvMuxer.shouldRetry(reason)
    }

    fun reConnect(delay: Long) {
        srsFlvMuxer.reConnect(delay)
    }

    /**
     * Mute microphone, can be called before, while and after stream.
     */
    fun disableAudio() {
        microphoneManager.mute()
    }

    /**
     * Enable a muted microphone, can be called before, while and after stream.
     */
    fun enableAudio() {
        microphoneManager.unMute()
    }

    /**
     * Get mute state of microphone.
     *
     * @return true if muted, false if enabled
     */
    val isAudioMuted: Boolean
        get() = microphoneManager.isMuted


    fun getBitrate(): Int {
        val rate = videoEncoder!!.bitrate
        if (rate == null) {
            return 0
        }
        return rate
    }

    fun getStreamWidth(): Int {
        return videoEncoder!!.width
    }

    fun getStreamHeight(): Int {
        return videoEncoder!!.height
    }

    fun getFps(): Int {
        return curFps
    }


    /**
     * Set video bitrate of H264 in bits per second while stream.
     *
     * @param bitrate H264 in bits per second.
     */
    fun setVideoBitrateOnFly(bitrate: Int) {
        videoEncoder!!.setVideoBitrateOnFly(bitrate)
    }

    /**
     * Set limit FPS while stream. This will be override when you call to prepareVideo method. This
     * could produce a change in iFrameInterval.
     *
     * @param fps frames per second
     */
    fun setLimitFPSOnFly(fps: Int) {
        videoEncoder!!.limitFps = fps
    }


    override fun getAacData(aacBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        if (isStreaming && !pausedStreaming) getAacDataRtp(aacBuffer, info)
        if (isRecording && !pausedRecording) recordController.recordAudio(aacBuffer, info)
    }

    override fun onSpsPps(sps: ByteBuffer, pps: ByteBuffer) {
        if (isStreaming && !pausedStreaming) onSpsPpsVpsRtp(sps, pps, null)
    }

    override fun onSpsPpsVps(sps: ByteBuffer, pps: ByteBuffer, vps: ByteBuffer) {
        if (isStreaming && !pausedStreaming) onSpsPpsVpsRtp(sps, pps, vps)
    }

    override fun getVideoData(h264Buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        fpsListener.calculateFps()
        if (isStreaming && !pausedStreaming) getH264DataRtp(h264Buffer, info)
//        if (isRecording && !pausedRecording) recordController.recordVideo(h264Buffer, info)
        recordController.recordVideo(h264Buffer, info)
    }

    override fun inputPCMData(frame: Frame) {
        audioEncoder.inputPCMData(frame)
    }

    override fun onVideoFormat(mediaFormat: MediaFormat) {
        recordController.setVideoFormat(mediaFormat)
    }

    override fun onAudioFormat(mediaFormat: MediaFormat) {
        recordController.setAudioFormat(mediaFormat)
    }

    fun getAacDataRtp(aacBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        srsFlvMuxer.sendAudio(aacBuffer, info)
    }

    fun onSpsPpsVpsRtp(sps: ByteBuffer, pps: ByteBuffer, vps: ByteBuffer?) {
        srsFlvMuxer.setSpsPPs(sps, pps)
    }

    fun getH264DataRtp(h264Buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        srsFlvMuxer.sendVideo(h264Buffer, info)
    }

    override fun onFps(fps: Int) {
        curFps = fps
    }

    override fun onStatusChange(status: RecordController.Status) {
        Log.d("TAG", "Recorder status: $status")
    }


    // Connect checker callback.
    override fun onConnectionSuccessRtmp() {
        // Succeessful connection, start the media stuff.
        if (!videoEncoder!!.running) {
            startEncoders()
        }
        connectChecker.onConnectionSuccessRtmp()
    }

    override fun onConnectionFailedRtmp(reason: String) {
        connectChecker.onConnectionFailedRtmp(reason)
    }

    override fun onNewBitrateRtmp(bitrate: Long) {
        connectChecker.onNewBitrateRtmp(bitrate)
    }

    override fun onDisconnectRtmp() {
        connectChecker.onDisconnectRtmp()
    }

    override fun onAuthErrorRtmp() {
        connectChecker.onAuthErrorRtmp()
    }

    override fun onAuthSuccessRtmp() {
        connectChecker.onAuthSuccessRtmp()
    }

    companion object {
        private val TAG: String? = "RtmpCameraConnector"
    }
}