package net.lang.streamer2.engine.session;

import android.media.MediaCodec;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.view.SurfaceView;

import com.yunfan.graphicbuffer.GraphicBufferWrapper;

import net.lang.gpuimage.helper.MagicFilterType;
import net.lang.streamer2.LangRtcInfo;
import net.lang.streamer2.LangRtcUser;
import net.lang.streamer2.config.LangFaceuConfig;
import net.lang.streamer2.config.LangObjectSegmentationConfig;
import net.lang.streamer2.config.LangRtcConfig;
import net.lang.streamer2.config.LangWatermarkConfig;
import net.lang.streamer2.engine.capture.CaptureRuntimeException;
import net.lang.streamer2.engine.capture.LangAudioCapture;
import net.lang.streamer2.engine.capture.LangVideoCapture;
import net.lang.streamer2.engine.data.LangAudioConfiguration;
import net.lang.streamer2.engine.data.LangFrameStatistics;
import net.lang.streamer2.engine.data.LangMediaBuffer;
import net.lang.streamer2.engine.data.LangRtcConfiguration;
import net.lang.streamer2.engine.data.LangRtcEvent;
import net.lang.streamer2.engine.data.LangRtmpBufferStatus;
import net.lang.streamer2.engine.data.LangRtmpStatus;
import net.lang.streamer2.engine.data.LangVideoConfiguration;
import net.lang.streamer2.engine.encoder.EncoderRuntimeException;
import net.lang.streamer2.engine.encoder.IBaseAudioEncoder;
import net.lang.streamer2.engine.encoder.IBaseVideoEncoder;
import net.lang.streamer2.engine.encoder.LangAudioEncoderFactory;
import net.lang.streamer2.engine.encoder.LangVideoEncoderFactory;
import net.lang.streamer2.engine.publish.INetworkListener;
import net.lang.streamer2.engine.publish.IRecordListener;
import net.lang.streamer2.engine.publish.LangLocalPublisher;
import net.lang.streamer2.engine.publish.LangRtmpPublisher;
import net.lang.streamer2.faceu.IFaceuListener;
import net.lang.streamer2.rtc.IRTCSessionController;
import net.lang.streamer2.rtc.IRTCSessionListener;
import net.lang.streamer2.rtc.RTCControllerFactory;
import net.lang.streamer2.utils.DebugLog;

import java.io.InputStream;
import java.nio.ByteBuffer;

public final class LangMediaSession implements
        LangAudioCapture.LangAudioCaptureListener,
        LangVideoCapture.LangVideoCaptureListener,
        IBaseAudioEncoder.IAudioEncoderListener,
        IBaseVideoEncoder.IVideoEncoderListener,
        INetworkListener,
        IRecordListener,
        IFaceuListener,
        IRTCSessionListener
{
    private static final String TAG = LangMediaSession.class.getSimpleName();

    private LangMediaSessionHandler mEventHandler;

    private LangAudioConfiguration mAudioConfiguration;
    private LangVideoConfiguration mVideoConfiguration;

    private LangAudioCapture mAudioCaptureSource;
    private LangVideoCapture mVideoCaptureSource;
    private IBaseAudioEncoder mAudioEncoder;
    private IBaseVideoEncoder mVideoEncoder;
    private LangRtmpPublisher mSocketPublisher;
    private LangLocalPublisher mFilePublisher;

    private IRTCSessionController mRtcController;

    private VideoEncodeThread mVideoThread;
    private RecyclableBufferQueue mCapturedVideoQueue;
    private byte[] mTemporaryI420Frame = null;

    private final Object mVideoQueueLock = new Object();
    private final Object mTimeLock = new Object();

    private long mRtmpRelativeTimestampUs;
    private boolean mRtmpHasCapturedAudio;
    private boolean mRtmpHasKeyFrameVideo;
    private boolean mRtmpUploading;

    private long mLocalRelativeTimestampUs;
    private boolean mLocalHasCapturedAudio;
    private boolean mLocalHasKeyFrameVideo;
    private boolean mLocalUploading;

    private boolean mRtcUploading;
    private boolean mRtcExtraUninitialized = false;
    private boolean mRtcExtraInitialized = false;

    private boolean mAdaptiveBitrate = false;

    public LangMediaSession(LangAudioConfiguration audioConfiguration, LangVideoConfiguration videoConfiguration)
        throws CaptureRuntimeException, EncoderRuntimeException {

        mAudioConfiguration = audioConfiguration;
        mVideoConfiguration = videoConfiguration;

        // create audio/video source
        mAudioCaptureSource = new LangAudioCapture(mAudioConfiguration);
        mAudioCaptureSource.setCaptureListener(this);

        mVideoCaptureSource = new LangVideoCapture(mVideoConfiguration);
        mVideoCaptureSource.setCaptureListener(this);
        mVideoCaptureSource.setFaceuListener(this);

        // create audio/video encoder transform.
        mAudioEncoder = LangAudioEncoderFactory.create(mAudioConfiguration);
        mAudioEncoder.setEncodeListener(this);

        mVideoEncoder = LangVideoEncoderFactory.create(mVideoConfiguration);
        mVideoEncoder.setEncodeListener(this);

        // create socket media sink
        mSocketPublisher = new LangRtmpPublisher(mAudioConfiguration, mVideoConfiguration);
        mSocketPublisher.setNetworkListener(this);

        // create local media sink
        mFilePublisher = new LangLocalPublisher(mAudioConfiguration, mVideoConfiguration);
        mFilePublisher.setRecordListener(this);

        int videoDataCapacity = mVideoConfiguration.getWidth() * mVideoConfiguration.getHeight() * 3 / 2;
        mCapturedVideoQueue = new RecyclableBufferQueue(videoDataCapacity);

        mEventHandler = new LangMediaSessionHandler();
    }

    // release media session
    public void release() {
        mAudioCaptureSource.release();
        mVideoCaptureSource.release();

        mSocketPublisher.release();
        mFilePublisher.release();

        if (mEventHandler != null) {
            mEventHandler.removeCallbacksAndMessages(null);
            mEventHandler = null;
        }
        DebugLog.i(TAG, "session resource release");
    }

    public void setSessionListener(LangMediaSessionListener listener) {
        mEventHandler.setListener(listener);
    }

    public void setSurfaceView(GLSurfaceView glSurfaceView) {
        mVideoCaptureSource.setSurfaceView(glSurfaceView);
    }

    public GLSurfaceView getSurfaceView() {
        return mVideoCaptureSource.getSurfaceView();
    }

    public void setRunning(boolean start) {
        if (start) {
            mCapturedVideoQueue.reset();

            mVideoThread = new VideoEncodeThread();
            //mVideoThread.setName("VideoEncodeThread");
            mVideoThread.start();

            mAudioEncoder.start();
            mVideoEncoder.start();
            mAudioCaptureSource.start();
            mVideoCaptureSource.start();
            DebugLog.d(TAG, "audio video components are running");

            // create rtc controller.
            mRtcController = RTCControllerFactory.create(getSurfaceView().getContext(), RTCControllerFactory.RtcImplType.kAgora);
            mRtcController.initialize();
            mRtcController.setListener(this);
            DebugLog.d(TAG, "rtc controller is initialized");

        } else {
            mAudioEncoder.stop();
            mVideoEncoder.stop();
            mAudioCaptureSource.stop();
            mVideoCaptureSource.stop();

            mVideoThread.quit();
            try {
                mVideoThread.join();
            } catch (InterruptedException ignored) {
                // ignore
            }
            DebugLog.d(TAG, "audio video components are stopped");

            // release rtc controller
            mRtcController.destroy();
            DebugLog.d(TAG, "rtc controller is released");
        }
    }

    public void screenshot(String url) {
        DebugLog.d(TAG, "screenshot url = " + url);
        mVideoCaptureSource.screenshot(url);
    }

    public void switchCamera() {
        DebugLog.d(TAG, "switchCamera");
        mVideoCaptureSource.switchCamera();
    }

    public void setMirror(boolean enable) {
        DebugLog.d(TAG, "setMirror = " + enable);
        mVideoCaptureSource.setCameraMirror(enable);
    }

    public void setZoom(float scale) {
        DebugLog.d(TAG, "setZoom = " + scale);
        mVideoCaptureSource.setZoom(scale);
    }

    public void setCameraBrightLevel(float level) {
        DebugLog.d(TAG, "setCameraBrightLevel = " + level);
        mVideoCaptureSource.setCameraBrightLevel(level);
    }

    public void setCameraToggleTorch(boolean enable) {
        DebugLog.d(TAG, "setCameraToggleTorch = " + enable);
        mVideoCaptureSource.setCameraToggleTorch(enable);
    }

    public void setCameraFocusing(float x, float y) {
        DebugLog.d(TAG, "setCameraFocusing, x = " + x + " y = " + y);
        mVideoCaptureSource.setCameraFocusing(x, y);
    }

    public void setFilter(MagicFilterType type) {
        DebugLog.d(TAG, "setFilter = " + type.name());
        mVideoCaptureSource.setFilter(type);
    }

    public void setBeautyLevel(int beautyLevel) {
        DebugLog.d(TAG, "setBeautyLevel = " + beautyLevel);
        if (beautyLevel == 0) {
            mVideoCaptureSource.enableAutoBeauty(false);
        } else {
            mVideoCaptureSource.enableAutoBeauty(true);
            mVideoCaptureSource.setAutoBeautyLevel(beautyLevel);
        }
    }

    public void setWatermark(LangWatermarkConfig config) {
        DebugLog.d(TAG, "setWatermark = " + config);
        mVideoCaptureSource.updateWaterMarkConfig(config);
    }

    public void setFaceu(LangFaceuConfig config) {
        DebugLog.d(TAG, "setFaceu");
        mVideoCaptureSource.updateFaceuConfig(config);
    }

    public void setGiftAnimation(LangObjectSegmentationConfig params, InputStream inputStream, InputStream giftStream) {
        DebugLog.d(TAG, "setGiftAnimation");
        mVideoCaptureSource.updateMattingConfig(params, inputStream, giftStream);
    }

    public void setHairColors(LangObjectSegmentationConfig params) {
        DebugLog.d(TAG, "setHairColors");
        mVideoCaptureSource.updateBeautyHairConfig(params);
    }

    public void enableMakeups(boolean enable) {
        DebugLog.d(TAG, "enableMakeups = " + enable);
        mVideoCaptureSource.enableMakeups(enable);
    }

    public int setSilence(boolean mute) {
        DebugLog.d(TAG, "setMute=" + mute);
        mAudioEncoder.setSilence(mute);
        return 0;
    }

    // rtmp broadcasting methods.
    public void setLiveOption(int retryTimes, int retryIntervalSecs) {
        DebugLog.d(TAG, "setLiveOption, retryTimes=" +
                retryTimes + " retryIntervalSecs=" + retryIntervalSecs);

        mSocketPublisher.getRtmpConfiguration().setRetryTimesCount(retryTimes);
        mSocketPublisher.getRtmpConfiguration().setRetryTimesIntervalSec(retryIntervalSecs);
    }

    public void setAutoBitrate(boolean enable) {
        DebugLog.d(TAG, "setAutoBitrate =" + enable);
        // if enable flag raised, for MediaCodec encoder, force using CBR mode, else use encoder VBR.
        mVideoEncoder.autoBitrate(!enable);
        mAdaptiveBitrate = enable;
    }

    public void startLive(String url) {
        DebugLog.i(TAG, "startLive = " + url);
        mSocketPublisher.getRtmpConfiguration().setUrl(url);
        mSocketPublisher.start();
    }

    public void stopLive() {
        DebugLog.i(TAG, "stopLive");
        mRtmpUploading = false;
        mSocketPublisher.stop();
    }

    // local file recording methods.
    public void startRecord(String url) {
        DebugLog.i(TAG, "startRecord = " + url);
        mFilePublisher.start(url);
    }

    public void stopRecord() {
        DebugLog.i(TAG, "stopRecord");
        mLocalUploading = false;
        mFilePublisher.stop();
    }

    public double getPreviewFps() {
        return mVideoCaptureSource.getPreviewFps();
    }

    // rtc methods
    public boolean rtcAvailable(LangRtcConfig config) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false;
        }

        if (config.deviceInBlackLists()) {
            return false;
        }

        return true;
    }

    public void setRtcDisplayLayoutParams(LangRtcConfig.RtcDisplayParams displayParams) {
        DebugLog.d(TAG, "setRtcDisplayLayoutParams =" + displayParams);
        mRtcController.setDisplayParamsWhenStreaming(displayParams);
    }

    public SurfaceView createRtcRenderView() {
        DebugLog.d(TAG, "createRtcRenderView");
        return mRtcController.createRtcRenderView();
    }

    public int joinRtcChannel(LangRtcConfiguration rtcConfiguration, SurfaceView localView) {
        DebugLog.d(TAG, "joinRtcChannel");

        // rtc sdk will replace our audio record, so just stop our audio capture now.
        mAudioCaptureSource.stop();

        // set publish stream url in case of server mixer rtc frames.
        if (!rtcConfiguration.isLocalMixed()) {
            rtcConfiguration.setPushStreamUrl(currentLiveUrl());
        }
        boolean result = mRtcController.joinChannel(rtcConfiguration, localView);
        return result ? 0 : -1;
    }

    public void leaveRtcChannel() {
        DebugLog.d(TAG, "leaveRtcChannel");
        mRtcController.leaveChannel();
    }

    public int setRtcVoiceChat(boolean voiceOnly) {
        DebugLog.d(TAG, "leaveRtcChannel");
        boolean result = mRtcController.setVoiceChat(voiceOnly);
        return result ? 0 : -1;
    }

    public int muteRtcLocalVoice(boolean mute) {
        DebugLog.d(TAG, "muteRtcLocalVoice");
        boolean result = mRtcController.muteLocalVoice(mute);
        return result ? 0 : -1;
    }

    public int muteRtcRemoteVoice(final int uid, boolean mute) {
        DebugLog.d(TAG, "muteRtcRemoteVoice uid = " + uid + " mute = " + mute);
        boolean result = mRtcController.muteRemoteVoice(uid, mute);
        return result ? 0 : -1;
    }

    public int setupRtcRemoteUser(final int uid, SurfaceView remoteView) {
        DebugLog.d(TAG, "setupRtcRemoteUser uid = " + uid + " remoteView = " + remoteView.getId());
        boolean result = mRtcController.setupRemoteUser(uid, remoteView);
        return result ? 0 : -1;
    }

    // implement LangAudioCapture.LangAudioCaptureListener
    @Override
    public void onCapturedPcmFrame(byte[] data, int size, long timestampNs) {
        if (mRtmpUploading || mLocalUploading) {
            mAudioEncoder.encodeFrame(data, size, timestampNs/1000L);
        }
    }

    // implement LangVideoCapture.LangVideoCaptureListener
    @Override
    public boolean skip(long timens) {
        return false;
    }

    @Override
    public void onCapturedVideoFrame(GraphicBufferWrapper gb, long timestampNs) {
        if (mRtcExtraInitialized) {
            mRtcExtraInitialized = false;
            mRtcController.onExtraInitialize();
        } else if (mRtcExtraUninitialized) {
            mRtcExtraUninitialized = false;
            mRtcController.onExtraDestroy();
        }

        if (mRtcUploading) {
            mRtcController.pushVideoFrame(gb.textureId(), timestampNs);
        } else {
            if (mRtmpUploading || mLocalUploading) {
                encodeVideoBuffer(gb, timestampNs);
            }
        }
    }

    @Override
    public void onHumanFaceDetected(int faceCount) {
        // post event to UI thread
        if (mEventHandler != null) {
            mEventHandler.notifyFaceTrackerFaceUpdate(faceCount);
        }
    }

    @Override
    public void onHumanHandDetected(int handCount, FaceuGestureType gesture) {
        // post event to UI thread
        if (mEventHandler != null) {
            mEventHandler.notifyFaceTrackerHandUpdate(handCount, gesture);
        }
    }

    // implement IBaseAudioEncoder.IAudioEncoderListener
    @Override
    public void onEncodedAacFrame(ByteBuffer bb, MediaCodec.BufferInfo bi) {
        if ((bi.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            DebugLog.d(TAG, "audio sequence header frame length = " + bi.size + " pts =" + bi.presentationTimeUs);
            pushRtmpConfigBuffer(mSocketPublisher.getAudioTrackIndex(), bb, bi);
            pushLocalRecordConfigBuffer(mFilePublisher.getAudioTrackIndex(), bb, bi);
            return;
        }

        if (mRtmpUploading) {
            mRtmpHasCapturedAudio = true;
            if (rtmpAudioVideoAlignment()) {
                pushRtmpBuffer(mSocketPublisher.getAudioTrackIndex(), bb, bi);
            }
        }

        if (mLocalUploading) {
            mLocalHasCapturedAudio = true;
            if (localAudioVideoAlignment()) {
                pushLocalRecordBuffer(mFilePublisher.getAudioTrackIndex(), bb, bi);
            }
        }
    }

    // implement IBaseVideoEncoder.IVideoEncoderListener
    @Override
    public void onEncodedAnnexbFrame(ByteBuffer bb, MediaCodec.BufferInfo bi) {
        if ((bi.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            DebugLog.d(TAG, "video sequence header frame length = " + bi.size);
            pushRtmpConfigBuffer(mSocketPublisher.getVideoTrackIndex(), bb, bi);
            pushLocalRecordConfigBuffer(mFilePublisher.getVideoTrackIndex(), bb, bi);
            return;
        }

        if (mRtmpUploading) {
            if ((bi.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
                DebugLog.d(TAG, "rtmp video key frame size length = " + bi.size);
                if (mRtmpHasCapturedAudio) {
                    mRtmpHasKeyFrameVideo = true;
                }
            }
            if (rtmpAudioVideoAlignment()) {
                pushRtmpBuffer(mSocketPublisher.getVideoTrackIndex(), bb, bi);
            }
        }

        if (mLocalUploading) {
            if ((bi.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
                DebugLog.d(TAG, "local video key frame size length = " + bi.size);
                if (mLocalHasCapturedAudio) {
                    mLocalHasKeyFrameVideo = true;
                }
            }
            if (localAudioVideoAlignment()) {
                pushLocalRecordBuffer(mFilePublisher.getVideoTrackIndex(), bb, bi);
            }
        }
    }

    // implenent INetworkListener
    @Override
    public void onSocketBufferStatus(LangRtmpBufferStatus rtmpBufferStatus) {
        if (mAdaptiveBitrate) {
            int initialVideoBitrateBps = mVideoConfiguration.getBitrateBps();
            int videoBitrateBps = mVideoEncoder.getBitrate();
            if (rtmpBufferStatus == LangRtmpBufferStatus.LANG_RTMP_BUFFER_STATUS_DECLINE) {
                final float videoMaxBitrateBps = initialVideoBitrateBps * 1.55f;
                if (videoBitrateBps < (int)videoMaxBitrateBps) {
                    videoBitrateBps += 50 * 1000;
                    mVideoEncoder.setBitrate(videoBitrateBps);
                    DebugLog.i(TAG, "Increase video bitrate: " + videoBitrateBps);
                }
            } else {
                final float videoMinBitrateBps = initialVideoBitrateBps * 0.75f;
                if (videoBitrateBps > videoMinBitrateBps) {
                    if (rtmpBufferStatus == LangRtmpBufferStatus.LANG_RTMP_BUFFER_STATUS_INCREASE) {
                        DebugLog.w(TAG, "Network condition unstable, upload bandwith seems to be limited");
                        videoBitrateBps -= 100 * 1000;
                    } else {
                        videoBitrateBps -= 50 * 1000;
                    }
                    mVideoEncoder.setBitrate(videoBitrateBps);
                    DebugLog.i(TAG, "Decline video bitrate: " + videoBitrateBps);
                }
            }
        }

        // post event to UI thread
        if (mEventHandler != null) {
            mEventHandler.notifyRtmpSocketBufferChanged(rtmpBufferStatus);
        }
    }

    @Override
    public void onSocketStatus(LangRtmpStatus rtmpStatus) {
        if (rtmpStatus == LangRtmpStatus.LANG_RTMP_STATUS_START) {
            if (!mRtmpUploading) {
                mRtmpRelativeTimestampUs = 0;
                mRtmpHasCapturedAudio = false;
                mRtmpHasKeyFrameVideo = false;
                mRtmpUploading = true;

                //reset initial video encoder output bitrate.
                if (mAdaptiveBitrate) {
                    int initialVideoBitrateBps = mVideoConfiguration.getBitrateBps();
                    mVideoEncoder.setBitrate(initialVideoBitrateBps);
                }
            }
        } else if (rtmpStatus == LangRtmpStatus.LANG_RTMP_STATUS_STOP ||
                rtmpStatus == LangRtmpStatus.LANG_RTMP_STATUS_ERROR ||
                rtmpStatus == LangRtmpStatus.LANG_RTMP_STATUS_REFRESH ||
                rtmpStatus == LangRtmpStatus.LANG_RTMP_STATUS_PENDING) {
            mRtmpUploading = false;
        } else {
            throw new RuntimeException("onSocketStatus callback status illegal:" + rtmpStatus.getInfo());
        }
        // post event to UI thread
        if (mEventHandler != null) {
            mEventHandler.notifyRtmpStatusChanged(rtmpStatus);
        }
    }

    @Override
    public void onSocketStatistics(LangFrameStatistics frameStatistics) {
        // post event to UI thread
        if (mEventHandler != null) {
            mEventHandler.notifyRtmpStatisticsUpdate(frameStatistics);
        }
    }

    // implenent IRecordListener
    @Override
    public void onRecordStart(String url) {
        if (!mLocalUploading) {
            mLocalRelativeTimestampUs = 0;
            mLocalHasCapturedAudio = false;
            mLocalHasKeyFrameVideo = false;
            mLocalUploading = true;
        }
        // post event to UI thread
        if (mEventHandler != null) {
            mEventHandler.notifyRecordStatusChanged(1);
        }
    }

    @Override
    public void onRecordProgress(String url, long currentMillSeconds) {
        // post event to UI thread
        if (mEventHandler != null) {
            mEventHandler.notifyRecordProgressUpdate(currentMillSeconds);
        }
    }

    @Override
    public void onRecordEnd(String url, long totalTimeMilliSeconds) {
        // post event to UI thread
        if (mEventHandler != null) {
            mEventHandler.notifyRecordStatusChanged(0);
        }
    }

    @Override
    public void onRecordError(String url, int errorCode, String errorDescription) {
        // post event to UI thread
        if (mEventHandler != null) {
            mEventHandler.notifyRecordError(errorCode, errorDescription);
        }
    }

    // implements IRTCSessionListener
    // callback rtc audio captured audio data
    @Override
    public void onRtcMixedPcmFrame(byte[] pcmData, int pcmDataLength, long timestampNs) {
        if (mRtmpUploading || mLocalUploading) {
            mAudioEncoder.encodeFrame(pcmData, pcmDataLength, timestampNs/1000L);
        }
    }

    // callback rtc video local mixed with remote user output data
    @Override
    public void onRtcMixedRGBAFrame(GraphicBufferWrapper gb, long timestampNs) {
        if (mRtmpUploading || mLocalUploading) {
            encodeVideoBuffer(gb, timestampNs);
        }
    }

    // callback rtc connection status
    @Override
    public void onRtcStatusChanged(LangRtcEvent rtcEvent, int uid) {
        if (rtcEvent == LangRtcEvent.RTC_EVENT_CONNECTED) {
            if (!mRtcUploading) {
                mRtcUploading = true;
                mRtcExtraInitialized = true;
                // down-grade bitstream bandwidth for making enough network space for rtc video chat.
                int initialVideoBitrateBps = mVideoConfiguration.getBitrateBps();
                final float targetVideoBitrateBps = initialVideoBitrateBps * 0.8f;
                mVideoEncoder.setBitrate((int)targetVideoBitrateBps);
            }
        } else if (rtcEvent == LangRtcEvent.RTC_EVENT_DISCONNECTED) {
            mRtcUploading = false;
            mRtcExtraUninitialized = true;
            // restore our audio capture source
            mAudioCaptureSource.start();
            mVideoEncoder.setBitrate(mVideoConfiguration.getBitrateBps());
        }
        // post event to UI thread
        if (mEventHandler != null) {
            mEventHandler.notifyRtcStatusChanged(rtcEvent, uid);
        }
    }

    // callback rtc engine event with message
    @Override
    public void onRtcReceivedNotification(LangRtcEvent rtcEvent, LangRtcUser rtcUser) {
        // post event to UI thread
        if (mEventHandler != null) {
            mEventHandler.notifyRtcReceiveNotification(rtcEvent, rtcUser);
        }
    }

    // callback rtc statistics debug information
    @Override
    public void onRtcStatistics(LangRtcInfo rtcInfo) {
        // post event to UI thread
        if (mEventHandler != null) {
            mEventHandler.notifyRtcStatisticsUpdate(rtcInfo);
        }
    }

    // callback rtc warning code
    @Override
    public void onRtcWarning(int warningCode) {
        // post event to UI thread
        if (mEventHandler != null) {
            mEventHandler.notifyRtcWarning(warningCode);
        }
    }

    // callback rtc error code
    @Override
    public void onRtcError(int errorCode, String description) {
        // post event to UI thread
        if (mEventHandler != null) {
            mEventHandler.notifyRtcError(errorCode, description);
        }
    }

    // getter functions
    public LangAudioConfiguration audioConfiguration() {
        return mAudioConfiguration;
    }

    public LangVideoConfiguration videoConfiguration() {
        return mVideoConfiguration;
    }

    public String currentLiveUrl() {
        return mSocketPublisher.getRtmpConfiguration().getUrl();
    }

    public boolean audioSourcePrepared() {
        return mAudioCaptureSource != null;
    }

    public boolean videoSourcePrepared() {
        return mVideoCaptureSource != null;
    }

    public boolean audioEncoderPrepared() {
        return mAudioEncoder != null;
    }

    public boolean videoEncoderPrepared() {
        return mVideoEncoder != null;
    }

    private class VideoEncodeThread extends Thread {
        private boolean running;

        VideoEncodeThread() {
            setName("VideoEncodeThread");
            running = true;
        }

        void quit() {
            running = false;
            synchronized (mVideoQueueLock) {
                mVideoQueueLock.notifyAll();
            }
        }

        @Override
        public void run() {
            DebugLog.d(TAG, "VideoEncodeThread, tid=" + Thread.currentThread().getId());
            while (running) {
                LangMediaBuffer mediaBuffer = null;
                synchronized (mVideoQueueLock) {
                    while (mCapturedVideoQueue.dequeueUsedBuffer() == null) {
                        try {
                            mVideoQueueLock.wait();
                        } catch (InterruptedException ie) {
                            ie.printStackTrace();
                        }
                        if (!running) {
                            break;
                        }
                    }
                    mediaBuffer = mCapturedVideoQueue.dequeueUsedBuffer();
                    if (mediaBuffer != null && running) {
                        mVideoEncoder.encodeFrame(mediaBuffer);
                        mCapturedVideoQueue.queueUsedBuffer();
                    }
                }
            }
        }
    }

    private void encodeVideoBuffer(GraphicBufferWrapper gb, long timestampNs) {
        synchronized (mVideoQueueLock) {
            LangMediaBuffer mediaBuffer = mCapturedVideoQueue.dequeueEmptyBuffer();
            if (mediaBuffer == null) {
                DebugLog.w(TAG, "no empty buffer now, maybe encoder has low performence");
                return;
            }

            final int gbColortype = (int)'A' | (int)'B' << 8 | (int)'G' << 16 | (int)'R' << 24;
            final int gbWidth = gb.width();
            final int gbHeight = gb.height();
            final int gbStride = gb.stride();
            final long gbAddr = gb.lock();

            if (mVideoEncoder.getEncoderFormat() == CodecCapabilities.COLOR_FormatYUV420Planar) {
                //DebugLog.d(TAG, "onCapturedVideoFrame yuv420p time = " + timestampNs/1000000L);

                // if MediaCodec uses i420 format, RecyclableBufferQueue will hold i420 frames directly.
                byte[] i420Frame = mediaBuffer.data();
                GraphicBufferWrapper._RgbaToI420(gbAddr, i420Frame, gbWidth, gbHeight, gbStride,
                        false, 0, gbColortype);

            } else if (mVideoEncoder.getEncoderFormat() == CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
                //DebugLog.d(TAG, "onCapturedVideoFrame nv12 time = " + timestampNs/1000000L);

                    /* if MediaCodec uses nv12 format, first grab graphic buffer data and convert to i420 frame
                        to a temporary buffer, and convert it to nv12 format. Here  RecyclableBufferQueue
                        will hold nv12 frames.
                    */
                byte[] nv12Frame = mediaBuffer.data();

                if (mTemporaryI420Frame == null) {
                    mTemporaryI420Frame = new byte[gbWidth * gbHeight * 3 / 2];
                }
                GraphicBufferWrapper._RgbaToI420(gbAddr, mTemporaryI420Frame, gbWidth, gbHeight, gbStride,
                        false, 0, gbColortype);
                GraphicBufferWrapper._I420ToNv12(mTemporaryI420Frame, nv12Frame, gbWidth, gbHeight);
            }

            gb.unlock();

            mediaBuffer.setDataLength(gbWidth * gbHeight * 3/2);
            mediaBuffer.setPresentationTimeUs(timestampNs/1000L);

            mCapturedVideoQueue.queueEmptyBuffer();
            mVideoQueueLock.notifyAll();
        }
    }


    private boolean rtmpAudioVideoAlignment() {
        if (mRtmpHasCapturedAudio && mRtmpHasKeyFrameVideo) {
            return true;
        } else {
            return false;
        }
    }

    private long rtmpUploadTimestamp(long presentationTimeUs) {
        synchronized (mTimeLock) {
            long currentTimestampUs = 0;
            if (mRtmpRelativeTimestampUs == 0) {
                mRtmpRelativeTimestampUs = presentationTimeUs;
            }
            currentTimestampUs = presentationTimeUs - mRtmpRelativeTimestampUs;
            return currentTimestampUs;
        }
    }

    private void pushRtmpBuffer(int trackIndex, ByteBuffer bb, MediaCodec.BufferInfo bi) {
        bi.presentationTimeUs = rtmpUploadTimestamp(bi.presentationTimeUs);
        mSocketPublisher.writeSampleData(trackIndex, bb, bi);
    }

    private void pushRtmpConfigBuffer(int trackIndex, ByteBuffer bb, MediaCodec.BufferInfo bi) {
        mSocketPublisher.addConfigData(trackIndex, bb, bi);
    }

    private boolean localAudioVideoAlignment() {
        if (mLocalHasCapturedAudio && mLocalHasKeyFrameVideo) {
            return true;
        } else {
            return false;
        }
    }

    private long localUploadTimestamp(long presentationTimeUs) {
        synchronized (mTimeLock) {
            long currentTimestampUs = 0;
            if (mLocalRelativeTimestampUs == 0) {
                mLocalRelativeTimestampUs = presentationTimeUs;
            }
            currentTimestampUs = presentationTimeUs - mLocalRelativeTimestampUs;
            return currentTimestampUs;
        }
    }

    private void pushLocalRecordBuffer(int trackIndex, ByteBuffer bb, MediaCodec.BufferInfo bi) {
        bi.presentationTimeUs = localUploadTimestamp(bi.presentationTimeUs);
        mFilePublisher.writeSampleData(trackIndex, bb, bi);
    }

    private void pushLocalRecordConfigBuffer(int trackIndex, ByteBuffer bb, MediaCodec.BufferInfo bi) {
        mFilePublisher.addTrack(trackIndex, bb, bi);
    }
}
