package net.lang.streamer2;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.Log;
import android.view.SurfaceView;

import net.lang.gpuimage.helper.MagicFilterType;
import net.lang.streamer2.config.LangAnimationConfig;
import net.lang.streamer2.config.LangBeautyhairConfig;
import net.lang.streamer2.config.LangFaceuConfig;
import net.lang.streamer2.config.LangRtcConfig;
import net.lang.streamer2.config.LangStreamerConfig;
import net.lang.streamer2.config.LangWatermarkConfig;
import net.lang.streamer2.engine.capture.CaptureRuntimeException;
import net.lang.streamer2.engine.data.LangAnimationStatus;
import net.lang.streamer2.engine.data.LangAudioConfiguration;
import net.lang.streamer2.engine.data.LangFrameStatistics;
import net.lang.streamer2.engine.data.LangRtcConfiguration;
import net.lang.streamer2.engine.data.LangRtcEvent;
import net.lang.streamer2.engine.data.LangRtmpBufferStatus;
import net.lang.streamer2.engine.data.LangRtmpStatus;
import net.lang.streamer2.engine.data.LangVideoConfiguration;
import net.lang.streamer2.engine.encoder.EncoderRuntimeException;
import net.lang.streamer2.engine.session.LangMediaSession;
import net.lang.streamer2.engine.session.LangMediaSessionListener;
import net.lang.streamer2.faceu.IFaceuListener;
import net.lang.streamer2.utils.DebugLog;
import net.lang.streamer2.utils.FilterUtils;
import net.lang.streamer2.utils.Permission;

public final class LangMediaCameraStreamer implements ILangCameraStreamer, LangMediaSessionListener {
    private static final String TAG = LangMediaCameraStreamer.class.getSimpleName();

    private static int mLevel = LangStreamerLogLevel.LANG_LOG_DEBUG;

    private static final String[] sRequestPermissions = {
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            //android.Manifest.permission.READ_EXTERNAL_STORAGE,
    };

    private ILangCameraStreamer.ILangCameraStreamerOnEventListener mEventListener;
    private ILangCameraStreamer.ILangCameraStreamerOnErrorListener mErrorListener;

    private static final Object mFence = new Object();
    private Status mStatus = Status.kUnInit;

    private LangRtmpInfo mRtmpLiveInfo = new LangRtmpInfo();
    private LangRtmpStatus mRtmpStatus = LangRtmpStatus.LANG_RTMP_STATUS_READY;
    private LangMediaSession mMediaSession;


    public static ILangCameraStreamer create() {
        LangMediaCameraStreamer instance = new LangMediaCameraStreamer();
        return instance;
    }

    @Override
    public void setDebugLevel(int level) {
        if (level > LangStreamerLogLevel.LANG_LOG_NONE) {
            level = LangStreamerLogLevel.LANG_LOG_NONE;
        }
        if (level < LangStreamerLogLevel.LANG_LOG_INFO) {
            level = LangStreamerLogLevel.LANG_LOG_INFO;
        }
        mLevel = level;
    }

    @Override
    public void setOnEventListener(ILangCameraStreamerOnEventListener listener) {
        mEventListener = listener;
    }

    @Override
    public void setOnErrorListener(ILangCameraStreamerOnErrorListener listener) {
        mErrorListener = listener;
    }

    @Override
    public int init(LangStreamerConfig config, GLSurfaceView glSurfaceView) {
        synchronized (mFence) {
            if (status() != Status.kUnInit) {
                DebugLog.w(TAG, "init failed due to invalid status");
                return -1;
            }

            Context context = glSurfaceView.getContext();
            if (!Permission.checkPermissions(context, sRequestPermissions)) {
                sendError(LangStreamerError.LANG_ERROR_NO_PERMISSIONS, -1);
                return -1;
            }

            LangAudioConfiguration audioConfiguration = audioConfigurationFromConfig(config);
            LangVideoConfiguration videoConfiguration = videoConfigurationFromConfig(config);
            if (!initInternal(audioConfiguration, videoConfiguration, glSurfaceView)) {
                return -1;
            }
            mMediaSession.setSessionListener(this);

            updateStatus(Status.kInit);
            return 0;
        }
    }

    @Override
    public int init(LangAudioQuality acfg, LangVideoQuality vcfg, GLSurfaceView glSurfaceView) {
        synchronized (mFence) {
            if (status() != Status.kUnInit) {
                DebugLog.w(TAG, "init failed due to invalid status");
                return -1;
            }

            Context context = glSurfaceView.getContext();
            if (!Permission.checkPermissions(context, sRequestPermissions)) {
                sendError(LangStreamerError.LANG_ERROR_NO_PERMISSIONS, -1);
                return -1;
            }

            LangAudioConfiguration audioConfiguration = audioConfigurationFromQuality(acfg);
            LangVideoConfiguration videoConfiguration = videoConfigurationFromQuality(vcfg);
            if (!initInternal(audioConfiguration, videoConfiguration, glSurfaceView)) {
                return -1;
            }
            mMediaSession.setSessionListener(this);

            updateStatus(Status.kInit);
            return 0;
        }
    }

    @Override
    public void onResume() {

    }

    @Override
    public void onPause() {

    }

    @Override
    public void enablePureAudio(boolean pureAudio) {
        synchronized (mFence) {
            if (status() != Status.kInit && status() != Status.kStop) {
                DebugLog.w(TAG, "enablePureAudio should be called in right status");
                return;
            }
            mMediaSession.enablePureAudio(pureAudio);
        }
    }

    @Override
    public int startPreview() {
        synchronized (mFence) {
            if (status() == Status.kUnInit || status() == Status.kRelease) {
                DebugLog.w(TAG, "startPreview failed due to sdk not init or released");
                return -1;
            }

            if (status() == Status.kStart) {
                DebugLog.d(TAG, "startPreview called duplicated");
                return 0;
            }

            mMediaSession.setRunning(true);
            updateStatus(Status.kStart);
        }
        return 0;
    }

    @Override
    public int stopPreview() {
        synchronized (mFence) {
            if (status() != Status.kStart) {
                DebugLog.d(TAG, "no need to call stopPreview");
                return 0;
            }

            mMediaSession.setRunning(false);
            updateStatus(Status.kStop);
        }
        return 0;
    }

    @Override
    public void setReconnectOption(int retryTimes, int retryIntervalSecs) {
        synchronized (mFence) {
            if (status() != Status.kStart) {
                DebugLog.d(TAG, "setReconnectOption failed due to invalid status");
                return;
            }
            mMediaSession.setLiveOption(retryTimes, retryIntervalSecs);
        }
    }

    @Override
    public void setAutoAdjustBitrate(boolean enable) {
        synchronized (mFence) {
            if (status() != Status.kStart) {
                DebugLog.d(TAG, "setAutoAdjustBitrate failed due to invalid status");
                return;
            }
            mMediaSession.setAutoBitrate(enable);
        }
    }

    @Override
    public int startStreaming(String  url) {
        synchronized (mFence) {
            if (status() != Status.kStart) {
                DebugLog.d(TAG, "startStreaming failed due to invalid status");
                return -1;
            }
            mMediaSession.startLive(url);
        }
        return 0;
    }

    @Override
    public int stopStreaming() {
        synchronized (mFence) {
            if (status() != Status.kStart) {
                DebugLog.d(TAG, "stopStreaming failed due to invalid status");
                return -1;
            }
            mMediaSession.stopLive();
        }
        return 0;
    }

    @Override
    public boolean isStreaming() {
        if (mRtmpStatus == LangRtmpStatus.LANG_RTMP_STATUS_START) {
            return true;
        }
        return false;
    }

    @Override
    public LangRtmpInfo getLiveInfo() {
        if (mRtmpStatus != LangRtmpStatus.LANG_RTMP_STATUS_START) {
            return null;
        }
        return mRtmpLiveInfo;
    }

    @Override
    public int startRecording(String  url) {
        synchronized (mFence) {
            if (status() != Status.kStart) {
                DebugLog.d(TAG, "startRecording failed due to invalid status");
                return -1;
            }
            mMediaSession.startRecord(url);
        }
        return 0;
    }

    @Override
    public int stopRecording() {
        synchronized (mFence) {
            if (status() != Status.kStart) {
                DebugLog.d(TAG, "stopRecording failed due to invalid status");
                return -1;
            }
            mMediaSession.stopRecord();
        }
        return 0;
    }

    @Override
    public int screenshot(String  url) {
        synchronized (mFence) {
            if (status() != Status.kStart) {
                DebugLog.w(TAG, "screenshot failed due to invalid status");
                return -1;
            }
            mMediaSession.screenshot(url);
        }
        return 0;
    }

    @Override
    public int switchCamera() {
        synchronized (mFence) {
            if (status() != Status.kStart) {
                DebugLog.w(TAG, "switchCamera failed due to invalid status");
                return -1;
            }
            mMediaSession.switchCamera();
        }
        return 0;
    }

    @Override
    public void setCameraBrightLevel(float level) {
        synchronized (mFence) {
            if (status() != Status.kStart) {
                DebugLog.w(TAG, "setCameraBrightLevel failed due to invalid status");
                return;
            }
            mMediaSession.setCameraBrightLevel(level);
        }
    }

    @Override
    public void setCameraToggleTorch(boolean enable) {
        synchronized (mFence) {
            if (status() != Status.kStart) {
                DebugLog.w(TAG, "setCameraToggleTorch failed due to invalid status");
                return;
            }
            mMediaSession.setCameraToggleTorch(enable);
        }
    }

    @Override
    public void setCameraFocusing(float x, float y) {
        synchronized (mFence) {
            if (status() != Status.kStart) {
                DebugLog.w(TAG, "setCameraFocusing failed due to invalid status");
                return;
            }
            mMediaSession.setCameraFocusing(x, y);
        }
    }

    @Override
    public void setMirror(boolean enable) {
        synchronized (mFence) {
            if (status() != Status.kStart) {
                DebugLog.w(TAG, "setMirror failed due to invalid status");
                return;
            }
            mMediaSession.setMirror(enable);
        }
    }

    @Override
    public void setZoom(float scale) {
        synchronized (mFence) {
            if (status() != Status.kStart) {
                DebugLog.w(TAG, "setZoom failed due to invalid status");
                return;
            }
            mMediaSession.setZoom(scale);
        }
    }

    @Override
    public int setFilter(LangCameraFilter filter) {
        synchronized (mFence) {
            if (status() != Status.kStart) {
                DebugLog.w(TAG, "setFilter failed due to invalid status");
                return -1;
            }
            MagicFilterType filterType = FilterUtils.filterTypeFromFilter(filter);
            mMediaSession.setFilter(filterType);
        }
        return 0;
    }

    @Override
    public int setBeauty(LangCameraBeauty info) {
        synchronized (mFence) {
            if (status() != Status.kStart) {
                DebugLog.w(TAG, "setBeauty failed due to invalid status");
                return -1;
            }
            mMediaSession.setBeautyLevel(info.value);
        }
        return 0;
    }

    @Override
    public int setWatermark(LangWatermarkConfig config) {
        synchronized (mFence) {
            if (status() != Status.kStart) {
                DebugLog.w(TAG, "setWatermark failed due to invalid status");
                return -1;
            }
            if (config.url.isEmpty() && config.picture == null && config.enable) {
                DebugLog.w(TAG, "watermark picture or url is empty");
                return -1;
            }
            if (config.x < 0 || config.y < 0) {
                DebugLog.w(TAG, "x/y position should not be negative, " +
                        "config.x = " + config.x + " config.y = " + config.y);
                return -1;
            }
            if (!config.fullScreen && (config.w < 1 || config.h < 1)) {
                DebugLog.w(TAG, "w/h dimension should be positive, " +
                        "config.w = " + config.w + " config.h = " + config.h);
                return -1;
            }
            mMediaSession.setWatermark(config);
        }
        return 0;
    }

    @Override
    public int setFaceu(LangFaceuConfig config) {
        synchronized (mFence) {
            if (status() != Status.kStart) {
                DebugLog.w(TAG, "setFaceu failed due to invalid status");
                return -1;
            }
            mMediaSession.setFaceu(config);
        }
        return 0;
    }

    @Override
    public void enableMakeups(boolean enable) {

    }

    @Override
    public int setMattingAnimation(LangAnimationConfig config) {
        synchronized (mFence) {
            if (status() != Status.kStart) {
                DebugLog.w(TAG, "setMattingAnimation failed due to invalid status");
                return -1;
            }
            mMediaSession.setMattingAnimation(config);
        }
        return 0;
    }

    @Override
    public int setHairColors(LangBeautyhairConfig config) {
        synchronized (mFence) {
            if (status() != Status.kStart) {
                DebugLog.w(TAG, "setHairColors failed due to invalid status");
                return -1;
            }
            mMediaSession.setHairColors(config);
        }
        return 0;
    }

    @Override
    public int setMute(boolean mute) {
        synchronized (mFence) {
            if (status() != Status.kStart) {
                DebugLog.w(TAG, "setMute failed due to invalid status");
                return -1;
            }
            mMediaSession.setSilence(mute);
        }
        return 0;
    }

    @Override
    public int enableAudioPlay(boolean enableAudioPlay) {
        return 0;
    }

    @Override
    public boolean rtcAvailable(LangRtcConfig config) {
        if (mMediaSession != null) {
            return mMediaSession.rtcAvailable(config);
        }
         return false;
    }

    @Override
    public void setRtcDisplayLayoutParams(LangRtcConfig.RtcDisplayParams displayParams) {
        synchronized (mFence) {
            if (status() != Status.kStart) {
                DebugLog.w(TAG, "setRtcDisplayLayoutParams failed due to invalid status");
                return;
            }
            mMediaSession.setRtcDisplayLayoutParams(displayParams);
        }
    }

    @Override
    public int joinRtcChannel(LangRtcConfig config, SurfaceView localView) {
        synchronized (mFence) {
            if (status() != Status.kStart) {
                DebugLog.w(TAG, "joinRtcChannel failed due to invalid status");
                return -1;
            }
            if (mRtmpStatus != LangRtmpStatus.LANG_RTMP_STATUS_START) {
                DebugLog.w(TAG, "joinRtcChannel: current rtmp is not connected now!");
            }
            LangRtcConfiguration rtcConfiguration = new LangRtcConfiguration(
                    mMediaSession.audioConfiguration(), mMediaSession.videoConfiguration(), config);
            return mMediaSession.joinRtcChannel(rtcConfiguration, localView);
        }
    }

    @Override
    public void leaveRtcChannel() {
        synchronized (mFence) {
            if (status() != Status.kStart) {
                DebugLog.w(TAG, "leaveRtcChannel failed due to invalid status");
                return;
            }
            mMediaSession.leaveRtcChannel();
        }
    }

    @Override
    public int setRtcVoiceChat(boolean voiceOnly) {
        synchronized (mFence) {
            if (status() != Status.kStart) {
                DebugLog.w(TAG, "setRtcVoiceChat failed due to invalid status");
                return -1;
            }
            return mMediaSession.setRtcVoiceChat(voiceOnly);
        }
    }

    @Override
    public int muteRtcLocalVoice(boolean mute) {
        synchronized (mFence) {
            if (status() != Status.kStart) {
                DebugLog.w(TAG, "muteRtcLocalVoice failed due to invalid status");
                return -1;
            }
            return mMediaSession.muteRtcLocalVoice(mute);
        }
    }

    @Override
    public int muteRtcRemoteVoice(final int uid, boolean mute) {
        synchronized (mFence) {
            if (status() != Status.kStart) {
                DebugLog.w(TAG, "muteRtcRemoteVoice failed due to invalid status");
                return -1;
            }
            return mMediaSession.muteRtcRemoteVoice(uid, mute);
        }
    }

    @Override
    public SurfaceView createRtcRenderView() {
        return mMediaSession.createRtcRenderView();
    }

    @Override
    public int setupRtcRemoteUser(final int uid, SurfaceView remoteView) {
        synchronized (mFence) {
            if (status() != Status.kStart) {
                DebugLog.w(TAG, "setupRtcRemoteUser failed due to invalid status");
                return -1;
            }
            return mMediaSession.setupRtcRemoteUser(uid, remoteView);
        }
    }

    @Override
    public int release() {
        synchronized (mFence) {
            if (status() == Status.kRelease) {
                DebugLog.e(TAG, "already released");
                return -1;
            }

            if (status() == Status.kStart) {
                DebugLog.w(TAG, "release: stop preview before release");
                mMediaSession.setRunning(false);
            }

            if (status() != Status.kUnInit) {
                mMediaSession.setSessionListener(null);
                mMediaSession.release();
            }

            mEventListener = null;
            mErrorListener = null;

            updateStatus(Status.kRelease);
        }
        return 0;
    }

    // implement LangMediaSessionListener
    @Override
    public void onSessionRtmpStatusChange(LangRtmpStatus rtmpStatus) {

        if (rtmpStatus == LangRtmpStatus.LANG_RTMP_STATUS_PENDING) {
            sendEvent(LangStreamerEvent.LANG_EVENT_PUSH_CONNECTING, 0);
        } else if (rtmpStatus == LangRtmpStatus.LANG_RTMP_STATUS_START) {
            sendEvent(LangStreamerEvent.LANG_EVENT_PUSH_CONNECT_SUCC, 0);
        } else if (rtmpStatus == LangRtmpStatus.LANG_RTMP_STATUS_STOP) {
            sendEvent(LangStreamerEvent.LANG_EVENT_PUSH_DISCONNECT, 0);
        } else if (rtmpStatus == LangRtmpStatus.LANG_RTMP_STATUS_REFRESH) {
            sendEvent(LangStreamerEvent.LANG_EVENT_PUSH_RECONNECTING, 0);
        } else if (rtmpStatus == LangRtmpStatus.LANG_RTMP_STATUS_ERROR) {
            sendError(LangStreamerError.LANG_ERROR_PUSH_CONNECT_FAIL, 0);
        }
        mRtmpStatus = rtmpStatus;
    }

    @Override
    public void onSessionRtmpStatisticsUpdate(LangFrameStatistics frameStatistics) {

        if (mRtmpStatus != LangRtmpStatus.LANG_RTMP_STATUS_START) {
            return;
        }

        mRtmpLiveInfo.uploadSpeed = (int)frameStatistics.currentBandwidth;
        mRtmpLiveInfo.localBufferAudioCount = frameStatistics.unSendAudioCount;
        mRtmpLiveInfo.localBufferVideoCount = frameStatistics.unSendVideoCount;
        mRtmpLiveInfo.videoPushFrameCountPerSecond = frameStatistics.currentCapturedVideoCount;

        //mRtmpLiveInfo.encodeVideoFrameCount = frameStatistics.totalFrames;
        mRtmpLiveInfo.totalPushAudioFrameCount = frameStatistics.totalAudioFrames;
        mRtmpLiveInfo.totalPushVideoFrameCount = frameStatistics.totalVideoFrames;
        mRtmpLiveInfo.totalDiscardFrameCount = frameStatistics.dropFrames;
        mRtmpLiveInfo.previewFrameCountPerSecond = (int)mMediaSession.getPreviewFps();

        sendEvent(LangStreamerEvent.LANG_EVENT_PUSH_STATS_UPDATE, mRtmpLiveInfo);
    }

    @Override
    public void onSessionRtmpSocketBufferChange(LangRtmpBufferStatus rtmpBufferStatus) {

        if (mRtmpStatus != LangRtmpStatus.LANG_RTMP_STATUS_START) {
            return;
        }

        LangStreamerEvent event;
        if (rtmpBufferStatus == LangRtmpBufferStatus.LANG_RTMP_BUFFER_STATUS_DECLINE) {
            event = LangStreamerEvent.LANG_EVENT_PUSH_NETWORK_STRONG;
        } else if (rtmpBufferStatus == LangRtmpBufferStatus.LANG_RTMP_BUFFER_STATUS_INCREASE) {
            event = LangStreamerEvent.LANG_EVENT_PUSH_NETWORK_WEAK;
        } else {
            event = LangStreamerEvent.LANG_EVENT_PUSH_NETWORK_NORMAL;
        }

        sendEvent(event, 0);
    }

    @Override
    public void onSessionFaceTrackerFaceUpdate(int faceCount) {

        LangStreamerEvent event = LangStreamerEvent.LANG_EVENT_FACE_UPDATE;
        Integer faceCountObj = faceCount;
        sendEvent(event, faceCountObj);
    }

    @Override
    public void onSessionFaceTrackerHandUpdate(int handCount, IFaceuListener.FaceuGestureType gesture) {

        LangStreamerEvent event = LangStreamerEvent.LANG_EVENT_HAND_UPDATE;

        LangFaceuGesture langGesture;
        if (gesture == IFaceuListener.FaceuGestureType.FACEU_GESTURE_PALM) {
            langGesture = LangFaceuGesture.LANG_FACEU_GESTURE_PALM;
        } else if (gesture == IFaceuListener.FaceuGestureType.FACEU_GESTURE_GOOD) {
            langGesture = LangFaceuGesture.LANG_FACEU_GESTURE_GOOD;
        } else if (gesture == IFaceuListener.FaceuGestureType.FACEU_GESTURE_OK) {
            langGesture = LangFaceuGesture.LANG_FACEU_GESTURE_OK;
        } else if (gesture == IFaceuListener.FaceuGestureType.FACEU_GESTURE_PISTOL) {
            langGesture = LangFaceuGesture.LANG_FACEU_GESTURE_PISTOL;
        } else if (gesture == IFaceuListener.FaceuGestureType.FACEU_GESTURE_FINGER_INDEX) {
            langGesture = LangFaceuGesture.LANG_FACEU_GESTURE_FINGER_INDEX;
        } else if (gesture == IFaceuListener.FaceuGestureType.FACEU_GESTURE_FINGER_HEART) {
            langGesture = LangFaceuGesture.LANG_FACEU_GESTURE_FINGER_HEART;
        } else if (gesture == IFaceuListener.FaceuGestureType.FACEU_GESTURE_LOVE) {
            langGesture = LangFaceuGesture.LANG_FACEU_GESTURE_LOVE;
        } else if (gesture == IFaceuListener.FaceuGestureType.FACEU_GESTURE_SCISSOR) {
            langGesture = LangFaceuGesture.LANG_FACEU_GESTURE_SCISSOR;
        } else if (gesture == IFaceuListener.FaceuGestureType.FACEU_GESTURE_CONGRATULATE) {
            langGesture = LangFaceuGesture.LANG_FACEU_GESTURE_CONGRATULATE;
        } else if (gesture == IFaceuListener.FaceuGestureType.FACEU_GESTURE_HOLDUP) {
            langGesture = LangFaceuGesture.LANG_FACEU_GESTURE_HOLDUP;
        } else if (gesture == IFaceuListener.FaceuGestureType.FACEU_GESTURE_FIST) {
            langGesture = LangFaceuGesture.LANG_FACEU_GESTURE_FIST;
        } else {
            langGesture = LangFaceuGesture.LANG_FACEU_GESTURE_NONE;
        }

        sendEvent(event, langGesture);
    }

    @Override
    public void onSessionRtcStatusChange(LangRtcEvent rtcEvent, int uid) {

        if (rtcEvent == LangRtcEvent.RTC_EVENT_CONNECTING) {
            sendEvent(LangStreamerEvent.LANG_EVENT_RTC_CONNECTING, uid);
        } else if (rtcEvent == LangRtcEvent.RTC_EVENT_CONNECTED) {
            sendEvent(LangStreamerEvent.LANG_EVENT_RTC_CONNECT_SUCC, uid);
        } else if (rtcEvent == LangRtcEvent.RTC_EVENT_DISCONNECTED) {
            sendEvent(LangStreamerEvent.LANG_EVENT_RTC_DISCONNECT, uid);
        } else if (rtcEvent == LangRtcEvent.RTC_EVENT_NETWORK_LOST) {
            sendEvent(LangStreamerEvent.LANG_EVENT_RTC_NETWORK_LOST, uid);
        } else if (rtcEvent == LangRtcEvent.RTC_EVENT_NETWORK_TIMEOUT) {
            sendEvent(LangStreamerEvent.LANG_EVENT_RTC_NETWORK_TIMEOUT, uid);
        }else if (rtcEvent == LangRtcEvent.RTC_EVENT_USER_JOINTED) {
            sendEvent(LangStreamerEvent.LANG_EVENT_RTC_USER_JOINED, uid);
        } else if (rtcEvent == LangRtcEvent.RTC_EVENT_USER_OFFLINE) {
            sendEvent(LangStreamerEvent.LANG_EVENT_RTC_USER_OFFLINE, uid);
        }
    }

    @Override
    public void onSessionRtcReceivedNotification(LangRtcEvent rtcEvent, LangRtcUser rtcUser) {

        if (rtcEvent == LangRtcEvent.RTC_EVENT_USER_AUDIO_MUTED) {
            sendEvent(LangStreamerEvent.LANG_EVENT_RTC_USER_AUDIO_MUTED, rtcUser);
        } else if (rtcEvent == LangRtcEvent.RTC_EVENT_USER_VIDEO_MUTED) {
            sendEvent(LangStreamerEvent.LANG_EVENT_RTC_USER_VIDEO_MUTED, rtcUser);
        } else if (rtcEvent == LangRtcEvent.RTC_EVENT_USER_VIDEO_BEGIN_RENDERED) {
            sendEvent(LangStreamerEvent.LANG_EVENT_RTC_USER_VIDEO_RENDERED, rtcUser);
        } else if (rtcEvent == LangRtcEvent.RTC_EVENT_USER_VIDEO_SIZE_CHANGED) {
            sendEvent(LangStreamerEvent.LANG_EVENT_RTC_VIDEO_SIZE_CHANGED, rtcUser);
        }
    }

    @Override
    public void onSessionRtcStatisticsUpdate(LangRtcInfo rtcInfo) {
        sendEvent(LangStreamerEvent.LANG_EVENT_RTC_STATS_UPDATE, rtcInfo);
    }

    @Override
    public void onSessionRtcWarning(int warningCode) {
        //ignore this to app
        DebugLog.w(TAG, "onSessionRtcWarning = " + warningCode);
    }

    @Override
    public void onSessionRtcError(int errorCode, String description) {
        sendError(LangStreamerError.LANG_ERROR_RTC_EXCEPTION, errorCode);
    }

    @Override
    public void onSessionRecordChange(int started) {
        if (started > 0) {
            sendEvent(LangStreamerEvent.LANG_EVENT_RECORD_BEGIN, 0);
        } else {
            sendEvent(LangStreamerEvent.LANG_EVENT_RECORD_END, 0);
        }
    }

    @Override
    public void onSessionRecordProgressUpdate(long milliSeconds) {
        sendEvent(LangStreamerEvent.LANG_EVENT_RECORD_STATS_UPDATE, (int)milliSeconds);
    }

    @Override
    public void onSessionRecordError(int errorCode, String description) {
        LangStreamerError error;
        if (errorCode == -1) {
            error = LangStreamerError.LANG_ERROR_UNSUPPORTED_FORMAT;
        } else {
            error = LangStreamerError.LANG_ERROR_RECORD_FAIL;
        }
        sendError(error, errorCode);
    }

    @Override
    public void onSessionAnimationStatusChange(LangAnimationStatus animationStatus, int value) {
        if (animationStatus == LangAnimationStatus.LANG_ANIMATION_STATUS_DECODING) {
          sendEvent(LangStreamerEvent.LANG_EVENT_ANIMATION_LOADING, value);
        } else if (animationStatus == LangAnimationStatus.LANG_ANIMATION_STATUS_READY) {
            sendEvent(LangStreamerEvent.LANG_EVENT_ANIMATION_LOAD_SUCC, value);
        } else if (animationStatus == LangAnimationStatus.LANG_ANIMATION_STATUS_ERROR) {
            sendError(LangStreamerError.LANG_ERROR_LOAD_ANIMATON_FAIL, value);
        } else if (animationStatus == LangAnimationStatus.LANG_ANIMATION_STATUS_PLAY) {
            sendEvent(LangStreamerEvent.LANG_EVENT_ANIMATION_PLAYING, value);
        } else if (animationStatus == LangAnimationStatus.LANG_ANIMATION_STATUS_COMPLETE) {
            sendEvent(LangStreamerEvent.LANG_EVENT_ANIMATION_PLAY_END, value);
        }
        // other event should not be in this callback.
    }

    private boolean initInternal(LangAudioConfiguration audioConfiguration,
                                 LangVideoConfiguration videoConfiguration,
                                 GLSurfaceView glSurfaceView) {
        try {
            mMediaSession = new LangMediaSession(audioConfiguration, videoConfiguration);
            mMediaSession.setSurfaceView(glSurfaceView);
        } catch (CaptureRuntimeException captureException) {
            DebugLog.w(TAG, "init failed with reason: " + captureException.getMessage());
            if (!mMediaSession.audioSourcePrepared()) {
                sendError(LangStreamerError.LANG_ERROR_OPEN_MIC_FAIL, -1);
                return false;
            }
            if (!mMediaSession.videoSourcePrepared()) {
                sendError(LangStreamerError.LANG_ERROR_OPEN_CAMERA_FAIL, -1);
                return false;
            }
        } catch (EncoderRuntimeException encoderException) {
            if (!mMediaSession.audioEncoderPrepared()) {
                sendError(LangStreamerError.LANG_ERROR_AUDIO_ENCODE_FAIL, -1);
                return false;
            }
            if (!mMediaSession.videoEncoderPrepared()) {
                sendError(LangStreamerError.LANG_ERROR_VIDEO_ENCODE_FAIL, -1);
                return false;
            }
        }

        return true;
    }

    private LangAudioConfiguration audioConfigurationFromConfig(LangStreamerConfig streamerConfig) {
        LangAudioConfiguration.AudioEncoderType encoderType =
                LangAudioConfiguration.AudioEncoderType.kHardware;
        switch (streamerConfig.audioEncoderType) {
            case LANG_AUDIO_ENCODER_HARDWARE:
            case LANG_AUDIO_ENCODER_DEFAULT:
                encoderType = LangAudioConfiguration.AudioEncoderType.kHardware;
                break;
            case LANG_AUDIO_ENCODER_FAAC:
                encoderType = LangAudioConfiguration.AudioEncoderType.kSoftware;
                break;
        }

        return new LangAudioConfiguration(streamerConfig.audioSampleRate,
                streamerConfig.audioChannel, streamerConfig.audioBitrate, encoderType);
    }

    private LangVideoConfiguration videoConfigurationFromConfig(LangStreamerConfig streamerConfig) {
        int outputWidth, outputHeight;

        if (streamerConfig.videoResolution == LangVideoResolution.LANG_VIDEO_RESOLUTION_360P) {
            outputWidth = alignOf(640, 16);
            outputHeight = alignOf(360, 16);
        } else if (streamerConfig.videoResolution == LangVideoResolution.LANG_VIDEO_RESOLUTION_480P) {
            outputWidth = alignOf(854, 16);
            outputHeight = alignOf(480, 16);
        } else if (streamerConfig.videoResolution == LangVideoResolution.LANG_VIDEO_RESOLUTION_540P) {
            outputWidth = alignOf(960, 16);
            outputHeight = alignOf(540, 16);
        } else {
            outputWidth = alignOf(1280, 16);
            outputHeight = alignOf(720, 16);
        }

        int keyFrameIntervalSec = streamerConfig.videoMaxKeyframeInterval / streamerConfig.videoFPS;

        LangVideoConfiguration.VideoEncoderType encoderType =
                LangVideoConfiguration.VideoEncoderType.kHardware;;
        switch (streamerConfig.videoEncoderType) {
            case LANG_VIDEO_ENCODER_HARDWARE:
            case LANG_VIDEO_ENCODER_DEFAULT:
                encoderType = LangVideoConfiguration.VideoEncoderType.kHardware;
                break;
            case LANG_VIDEO_ENCODER_OPENH264:
                encoderType = LangVideoConfiguration.VideoEncoderType.kSoftwareOpenH264;
                break;
            case LANG_VIDEO_ENCODER_X264:
                encoderType = LangVideoConfiguration.VideoEncoderType.kSoftwareX264;
                break;
        }

        return new LangVideoConfiguration(outputWidth, outputHeight, streamerConfig.videoFPS,
                streamerConfig.videoBitrate, keyFrameIntervalSec, encoderType);
    }

    private LangAudioConfiguration audioConfigurationFromQuality(LangAudioQuality audioQuality) {
        int audioBitrate, audioSampleRate;
        int audioChannel = 2;

        if (audioQuality == LangAudioQuality.LANG_AUDIO_QUALITY_LOW) {
            audioBitrate = 128 * 1000;
            audioSampleRate = 24000;
        } else if (audioQuality == LangAudioQuality.LANG_AUDIO_QUALITY_MEDIUM) {
            audioBitrate = 196 * 1000;
            audioSampleRate = 44100;
        } else {
            audioBitrate = 256 * 1000;
            audioSampleRate = 48000;
        }

        return new LangAudioConfiguration(audioSampleRate, audioChannel, audioBitrate);
    }

    private LangVideoConfiguration videoConfigurationFromQuality(LangVideoQuality videoQuality) {
        int outputWidth = 0, outputHeight = 0, outputFps = 0, outputBitrate = 0;
        int keyFrameIntervalSec = 2;

        switch (videoQuality) {
            case LANG_VIDEO_QUALITY_LOW_1:
                outputWidth = alignOf(640, 32);
                outputHeight = alignOf(360, 32);
                outputFps = 15;
                outputBitrate = 400 * 1000;
                break;
            case LANG_VIDEO_QUALITY_LOW_2:
            case LANG_VIDEO_QUALITY_DEFAULT:
                outputWidth = alignOf(640, 32);
                outputHeight = alignOf(360, 32);
                outputFps = 24;
                outputBitrate = 500 * 1000;
                break;
            case LANG_VIDEO_QUALITY_LOW_3:
                outputWidth = alignOf(640, 32);
                outputHeight = alignOf(360, 32);
                outputFps = 30;
                outputBitrate = 600 * 1000;
                break;
            case LANG_VIDEO_QUALITY_MEDIUM_1:
                outputWidth = alignOf(854, 32);
                outputHeight = alignOf(480, 32);
                outputFps= 15;
                outputBitrate = 700 * 1000;
                break;
            case LANG_VIDEO_QUALITY_MEDIUM_2:
                outputWidth = alignOf(854, 32);
                outputHeight = alignOf(480, 32);
                outputFps = 24;
                outputBitrate = 800 * 1000;
                break;
            case LANG_VIDEO_QUALITY_MEDIUM_3:
                outputWidth = alignOf(960, 32);
                outputHeight = alignOf(540, 32);
                outputFps = 24;
                outputBitrate = 900 * 1000;
                break;
            case LANG_VIDEO_QUALITY_HIGH_1:
                outputWidth = alignOf(960, 32);
                outputHeight = alignOf(540, 32);
                outputFps = 30;
                outputBitrate = 1000 * 1000;
                break;
            case LANG_VIDEO_QUALITY_HIGH_2:
                outputWidth = alignOf(1280, 32);
                outputHeight = alignOf(720, 32);
                outputFps = 24;
                outputBitrate = 1100 * 1000;
                break;
            case LANG_VIDEO_QUALITY_HIGH_3:
                outputWidth = alignOf(1280, 32);
                outputHeight = alignOf(720, 32);
                outputFps = 30;
                outputBitrate = 1200 * 1000;
                break;
        }

        return new LangVideoConfiguration(outputWidth, outputHeight, outputFps, outputBitrate, keyFrameIntervalSec);
    }

    private void sendEvent(LangStreamerEvent e, Object obj) {
        if (status() != Status.kStart) {
            return;
        }
        if (mEventListener != null) {
            mEventListener.onEvent(this, e, obj);
        }
    }

    private void sendEvent(LangStreamerEvent e, int v) {
        if (status() != Status.kStart) {
            return;
        }
        if (mEventListener != null) {
            mEventListener.onEvent(this, e, v);
        }
    }

    private void sendError(LangStreamerError e, int v) {
        if (status() != Status.kStart) {
            return;
        }
        if (mErrorListener != null) {
            mErrorListener.onError(this, e, v);
        }
    }


    enum Status {
        kUnInit("UnInit"),
        kInit("Init"),
        kStart("Start"),
        kStop("Stop"),
        kRelease("Release");
        String mName;
        Status(String name) {
            mName = name;
        }
    }

    private Status status() {
        return mStatus;
    }

    private void updateStatus(Status status) {
        DebugLog.d(TAG, "Change status " + mStatus.mName + " -> " + status.mName);
        mStatus = status;
    }


    private static int alignOf(int value, int align) {
        return ((value + align - 1) / align) * align;
    }

    public static void print(String tag, int level, String msg) {
        if (level >= mLevel) {
            switch (level) {
                case LangStreamerLogLevel.LANG_LOG_INFO:
                    Log.i(tag, msg);
                    break;
                case LangStreamerLogLevel.LANG_LOG_DEBUG:
                    Log.d(tag, msg);
                    break;
                case LangStreamerLogLevel.LANG_LOG_WARNING:
                    Log.w(tag, msg);
                    break;
                case LangStreamerLogLevel.LANG_LOG_ERROR:
                    Log.e(tag, msg);
                    break;
                case LangStreamerLogLevel.LANG_LOG_NONE:
                    break;
                default:
                    break;
            }
        }
    }
}
