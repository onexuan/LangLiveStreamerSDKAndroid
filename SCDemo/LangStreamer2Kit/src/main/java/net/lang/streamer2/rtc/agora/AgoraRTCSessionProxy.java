package net.lang.streamer2.rtc.agora;

import android.content.Context;
import android.util.Log;
import android.view.SurfaceView;

import com.yunfan.graphicbuffer.GraphicBufferWrapper;

import net.lang.rtclib.IMediaAudioObserver;
import net.lang.rtclib.IMediaVideoObserver;
import net.lang.rtclib.IRTCEventListener;
import net.lang.rtclib.IRTCSession;
import net.lang.rtclib.LangRTCVideoSession;
import net.lang.rtclib.LangRTCThread;
import net.lang.rtclib.MediaDataObserverImpl;
import net.lang.rtclib.MediaPreProcessingNative;
import net.lang.streamer2.LangRtcInfo;
import net.lang.streamer2.LangRtcUser;
import net.lang.streamer2.config.LangRtcConfig;
import net.lang.streamer2.engine.data.LangRtcConfiguration;
import net.lang.streamer2.engine.data.LangRtcEvent;
import net.lang.streamer2.rtc.IRTCSessionController;
import net.lang.streamer2.utils.DebugLog;

import java.util.ArrayList;
import java.util.List;

import io.agora.rtc.Constants;
import io.agora.rtc.IRtcEngineEventHandler;
import io.agora.rtc.RtcEngine;
import io.agora.rtc.live.LiveTranscoding;
import io.agora.rtc.video.VideoCanvas;

public class AgoraRTCSessionProxy extends IRTCSessionController implements IRTCEventListener, IMediaAudioObserver, IMediaVideoObserver {
    private static final String TAG = AgoraRTCSessionProxy.class.getSimpleName();

    private static final int STATE_UNINITIALIZED = 0;
    private static final int STATE_INITIALIZED   = 1;

    private static final int AGRTCSTATE_DID_STOPPED  = 2;
    private static final int AGRTCSTATE_WILL_STOPPED = 3;
    private static final int AGRTCSTATE_DID_ACTIVE   = 4;
    private static final int AGRTCSTATE_WILL_ACTIVE  = 5;

    private int mState;
    private int mRtcState;

    private final Object mRtcFence = new Object();
    private Context mContext;
    private LangRtcConfiguration mRtcConfiguration;
    private LangRTCThread mWorkerThread;
    private List<LangRTCVideoSession> mRtcSessions;
    private MediaDataObserverImpl mMediaDataObserver;
    private RTCVideoContext mVideoContext;
    private boolean mVideoMuted = false;
    private boolean mAudioMuted = false;

    /**
     * @param context the context which rtc engine will use
     */
    public AgoraRTCSessionProxy(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("should pass a valid Context instance first");
        }
        mState = STATE_UNINITIALIZED;
        mRtcState = AGRTCSTATE_DID_STOPPED;
        mRtcConfiguration = null;
        mRtcDisplayParams = new LangRtcConfig.RtcDisplayParams();
        mRtcSessions = new java.util.ArrayList<>();
        mVideoContext = new RTCVideoContext();
        mContext = context;
    }

    public void setDisplayParamsWhenStreaming(LangRtcConfig.RtcDisplayParams displayParams) {
        synchronized (mRtcFence) {
            // now check rtc display layout params.
            if (displayParams.topOffsetPercent + displayParams.subHeightPercentOnTwoWins > 0.7f) {
                DebugLog.w(TAG, "setDisplayParamsWhenStreaming() watch out for LangRtcConfig.RtcDisplayParams: " +
                        " topOffsetPercent " + displayParams.topOffsetPercent +
                        " subHeightPercentOnTwoWins " + displayParams.subHeightPercentOnTwoWins);
            }
            if (displayParams.topOffsetPercent + displayParams.subHeightPercentAboveTwoWins * 2 > 0.9f) {
                DebugLog.w(TAG, "setDisplayParamsWhenStreaming() watch out for LangRtcConfig.RtcDisplayParams: " +
                        " topOffsetPercent " + displayParams.topOffsetPercent +
                        " subHeightPercentAboveTwoWins " + displayParams.subHeightPercentOnTwoWins);
            }

            super.setDisplayParamsWhenStreaming(displayParams);
        }
    }

    @Override
    public void initialize() {
        initWorkerThread();
        mState = STATE_INITIALIZED;
    }

    @Override
    public SurfaceView createRtcRenderView() {
        if (mState != STATE_INITIALIZED) {
            throw new IllegalStateException("joinChannel() called on an "
                    + "uninitialized AgoraRtcSessionManager.");
        }
        return RtcEngine.CreateRendererView(mContext);
    }

    @Override
    public boolean joinChannel(LangRtcConfiguration config, SurfaceView localView) {
        if (mState != STATE_INITIALIZED) {
            throw new IllegalStateException("joinChannel() called on an "
                    + "uninitialized AgoraRtcSessionManager.");
        }

        mRtcConfiguration = config; // Todo: deep copy class
        synchronized (mRtcFence) {
            if (mRtcState != AGRTCSTATE_DID_STOPPED) {
                DebugLog.w(TAG, "joinChannel() called in invalid state: " + rtsStatusString());
                return false;
            }
            joinChannel_l(mRtcConfiguration, localView);
        }

        return true;
    }

    @Override
    public void leaveChannel() {
        if (mState != STATE_INITIALIZED) {
            throw new IllegalStateException("leaveChannel() called " +
                    "in invalid state STATE_UNINITIALIZED");
        }
        synchronized (mRtcFence) {
            if (mRtcState != AGRTCSTATE_DID_ACTIVE && mRtcState != AGRTCSTATE_WILL_ACTIVE) {
                DebugLog.w(TAG, "leaveChannel() called in invalid state: " + rtsStatusString());
                return;
            }

            leaveChannel_l();
        }
    }

    @Override
    public boolean setVoiceChat(boolean voiceOnly) {
        if (mState != STATE_INITIALIZED) {
            throw new IllegalStateException("setVoiceChat() called " +
                    "in invalid state STATE_UNINITIALIZED");
        }
        synchronized (mRtcFence) {
            if (mRtcState != AGRTCSTATE_DID_ACTIVE) {
                DebugLog.w(TAG, "setVoiceChat() called in invalid state: " + rtsStatusString());
                return false;
            }

            setVoiceChat_l(voiceOnly);
        }

        return true;
    }

    @Override
    public boolean muteLocalVoice(boolean mute) {
        if (mState != STATE_INITIALIZED) {
            throw new IllegalStateException("muteLocalVoice() called " +
                    "in invalid state STATE_UNINITIALIZED");
        }
        synchronized (mRtcFence) {
            if (mRtcState != AGRTCSTATE_DID_ACTIVE) {
                DebugLog.w(TAG, "muteLocalVoice() called in invalid state: " + rtsStatusString());
                return false;
            }

            muteLocalVoice_l(mute);
        }

        return true;
    }

    @Override
    public boolean muteRemoteVoice(final int uid, boolean mute) {
        if (mState != STATE_INITIALIZED) {
            throw new IllegalStateException("muteRemoteVoice() called" +
                    "in invalid state STATE_UNINITIALIZED");
        }
        boolean muted = true;
        synchronized (mRtcFence) {
            if (mRtcState != AGRTCSTATE_DID_ACTIVE) {
                DebugLog.w(TAG, "muteRemoteVoice() called in invalid state: " + rtsStatusString());
                return false;
            }

            int result = muteRemoteVoice_l(uid, mute);
            if (result != 0) {
                DebugLog.w(TAG, "muteRemoteVoice_l() failed: uid = " + uid);
                muted = false;
            }
        }

        return muted;
    }

    @Override
    public boolean setupRemoteUser(final int uid, SurfaceView remoteView) {
        if (mState != STATE_INITIALIZED) {
            throw new IllegalStateException("setupRemoteUser() called " +
                    "in invalid state STATE_UNINITIALIZED");
        }
        synchronized (mRtcFence) {
            if (mRtcState != AGRTCSTATE_DID_ACTIVE) {
                DebugLog.w(TAG, "setupRemoteUser() called in invalid state: " + rtsStatusString());
                return false;
            }

            setupRemoteUser_l(uid, remoteView);
        }

        return true;
    }

    /*
     * called in OpenGL thread
     */
    @Override
    public void onExtraInitialize() {
        if (mState != STATE_INITIALIZED) {
            throw new IllegalStateException("setupRTCVideoContext() called " +
                    "in invalid state STATE_UNINITIALIZED");
        }
        mVideoContext.setupLocalRTCGLContext(mContext, mRtcConfiguration);
    }

    /*
     * called in OpenGL thread
     */
    @Override
    public boolean pushVideoFrame(int srcTextureId, long timestampNs) {
        if (mState != STATE_INITIALIZED) {
            throw new IllegalStateException("pushRTCVideoFrame() called " +
                    "in invalid state STATE_UNINITIALIZED");
        }
        synchronized (mRtcFence) {
            if (mRtcState != AGRTCSTATE_DID_ACTIVE) {
                return false;
            }

            // draw push rtc buffer
            if (mVideoContext.drawLocalRTCVideoFrame(srcTextureId)) {
                GraphicBufferWrapper gb = mVideoContext.currentPushGraphicBuffer();
                workerThread().pushGraphicBuffer(gb, timestampNs/1000000L);
            }
            // draw mix rtc buffer
            if (!mRtcConfiguration.isLocalMixed()) {
                return true;
            }
            mVideoContext.mixLocalWithRemoteVideoFrame(srcTextureId, mRtcSessions, mRtcDisplayParams);
            if (mListener != null) {
                GraphicBufferWrapper gb = mVideoContext.currentMixGraphicBuffer();
                mListener.onRtcMixedRGBAFrame(gb, timestampNs);
            }
        }
        return true;
    }

    /*
     * called in OpenGL thread
     */
    @Override
    public void onExtraDestroy() {
        if (mState != STATE_INITIALIZED) {
            throw new IllegalStateException("cleanRTCVideoContext() called " +
                    "in invalid state STATE_UNINITIALIZED");
        }
        mVideoContext.cleanupLocalRTCGLContext();
    }

    @Override
    public void destroy() {
        if (mState == STATE_UNINITIALIZED) {
            return;
        }

        if (mRtcState == AGRTCSTATE_DID_ACTIVE) {
            DebugLog.w(TAG, "leave channel before release");
            leaveChannel_l();
        }

        synchronized (mRtcFence) {
            if (mRtcState == AGRTCSTATE_WILL_STOPPED) {
                try {
                    Log.w(TAG, "wait leave signal");
                    mRtcFence.wait(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            DebugLog.w(TAG, "deinit worker thread");
            deInitWorkerThread();
        }

        mListener = null;
        mContext = null;

        mState = STATE_UNINITIALIZED;
    }

    /*
     * Implements IRTCSessionListener
     */
    @Override
    public void onJoinChannelSuccess(String channel, final int uid, int elapsed) {
        Log.i(TAG, "onJoinChannelSuccess(): local uid = " + uid);

        synchronized (mRtcFence) {
            onJoinChannelSuccess_l(channel, uid, elapsed);
        }
    }

    @Override
    public void onRejoinChannelSuccess(String channel, final int uid, int elapsed) {
        Log.i(TAG, "onRejoinChannelSuccess(): local uid = " + uid);

        synchronized (mRtcFence) {
            onRejoinChannelSuccess_l(channel, uid, elapsed);
        }
    }

    @Override
    public void onLeaveChannel() {
        Log.i(TAG, "onLeaveChannel()");

        synchronized (mRtcFence) {
            onLeaveChannel_l();
        }
    }

    @Override
    public void onUserJoined(int uid, int elapsed) {
        Log.i(TAG, "onUserJoined(): uid = " + uid);

        synchronized (mRtcFence) {
            onUserJoined_l(uid, elapsed);
        }
    }

    @Override
    public void onFirstRemoteVideoFrame(int uid, int width, int height, int elapsed) {
        Log.i(TAG, "onFirstRemoteVideoDecoded(): uid = " + uid + " width = " + width + " height = " + height);

        synchronized (mRtcFence) {
            onFirstRemoteVideoFrame_l(uid, width, height, elapsed);
        }
    }

    @Override
    public void onUserOffline(int uid, int reason) {
        Log.i(TAG, "onUserOffline(): uid =" + uid);

        synchronized (mRtcFence) {
            onUserOffline_l(uid, reason);
        }
    }

    @Override
    public void onExtraCallback(final int type, final Object... data) {
        int peerUid;
        int width;
        int height;
        int rotation;
        boolean muted;

        synchronized (mRtcFence) {
            switch (type) {
                case IRTCEventListener.EVENT_TYPE_ON_VIDEO_SIZE_CHANGED: {
                    peerUid = (Integer) data[0];
                    width = (Integer)data[1];
                    height = (Integer)data[2];
                    rotation = (Integer)data[3];
                    onVideoSizeChanged_l(peerUid, width, height, rotation);
                    break;
                }
                case IRTCEventListener.EVENT_TYPE_ON_USER_AUDIO_MUTED: {
                    peerUid = (Integer) data[0];
                    muted = (boolean) data[1];
                    onUserAudioMuted_l(peerUid, muted);
                    break;
                }
                case IRTCEventListener.EVENT_TYPE_ON_USER_VIDEO_MUTED: {
                    peerUid = (Integer) data[0];
                    muted = (boolean) data[1];
                    onUserVideoMuted_l(peerUid, muted);
                    break;
                }
                case IRTCEventListener.EVENT_TYPE_ON_APP_ERROR: {
                    int subType = (int) data[0];
                    //if (subType == ConstantApp.AppError.NO_NETWORK_CONNECTION) {
                    //    onRtcConnectionError_l();
                    //}
                    if (subType == IRTCEventListener.ERROR_NETWORK_CONNECTION_LOST) {
                        onRtcConnectionLost_l();
                    } else if (subType == IRTCEventListener.ERROR_NETWORK_CONNECTION_TIMEOUT) {
                        onRtcConnectionTimeout_l();
                    }
                    break;
                }
                case IRTCEventListener.EVENT_TYPE_ON_RTC_STATS: {
                    IRtcEngineEventHandler.RtcStats stats = (IRtcEngineEventHandler.RtcStats)data[0];
                    onRtcStats_l(stats);
                    break;
                }
                case IRTCEventListener.EVENT_TYPE_ON_AGORA_MEDIA_WARNING: {
                    int warn = (int)data[0];
                    onRtcWarning_l(warn);
                    break;
                }
                case IRTCEventListener.EVENT_TYPE_ON_AGORA_MEDIA_ERROR: {
                    int error = (int) data[0];
                    String description = (String) data[1];
                    onRtcFatalError_l(error, description);
                    break;
                }
                case IRTCEventListener.EVENT_TYPE_ON_AUDIO_ROUTE_CHANGED: {
                    onRtcLocalAudioRouteChanged_l((int) data[0]);
                    break;
                }
            }
        }
    }


    /*
     * Implements IMediaAudioObserver
     */
    @Override
    public void onRecordAudioFrame(byte[] data,
                                   int audioFrameType,
                                   int samples,
                                   int bytesPerSample,
                                   int channels,
                                   int samplesPerSec,
                                   long renderTimeMs,
                                   int bufferLength) {

    }

    @Override
    public void onPlaybackAudioFrame(byte[] data,
                                     int audioFrameType,
                                     int samples,
                                     int bytesPerSample,
                                     int channels,
                                     int samplesPerSec,
                                     long renderTimeMs,
                                     int bufferLength) {

    }

    @Override
    public void onPlaybackAudioFrameBeforeMixing(int uid,
                                                 byte[] data,
                                                 int audioFrameType,
                                                 int samples,
                                                 int bytesPerSample,
                                                 int channels,
                                                 int samplesPerSec,
                                                 long renderTimeMs,
                                                 int bufferLength) {

    }

    private byte [] mStereoData = null;
    @Override
    public void onMixedAudioFrame(byte[] data,
                                  int audioFrameType,
                                  int samples,
                                  int bytesPerSample,
                                  int channels,
                                  int samplesPerSec,
                                  long renderTimeMs,
                                  int bufferLength) {

        synchronized (mRtcFence) {
            if (mRtcState == AGRTCSTATE_DID_ACTIVE) {
                if (data == null || data.length < 1024) {
                    DebugLog.w(TAG, "onMixedAudioFrame audio buffer invalid");
                    return;
                }

                if (mRtcConfiguration.getAudioChannel() != channels) {
                    if (mStereoData == null) {
                        mStereoData = new byte[bufferLength * 2];
                    }
                    for (int i = 0; i < samples; i++) {
                        mStereoData[i * 4 + 0] = data[i * 2 + 0];
                        mStereoData[i * 4 + 1] = data[i * 2 + 1];
                        mStereoData[i * 4 + 2] = data[i * 2 + 0];
                        mStereoData[i * 4 + 3] = data[i * 2 + 1];
                    }
                    if (mListener != null) {
                        mListener.onRtcMixedPcmFrame(mStereoData, mStereoData.length, System.nanoTime());
                    }
                } else {
                    if (mListener != null) {
                        mListener.onRtcMixedPcmFrame(data, data.length, System.nanoTime());
                    }
                }
            }
        }
    }

    /*
     * Implements IMediaVideoObserver
     */
    @Override
    public void onCaptureVideoFrame(byte[] data,
                                    int frameType,
                                    int width,
                                    int height,
                                    int bufferLength,
                                    int yStride,
                                    int uStride,
                                    int vStride,
                                    int rotation,
                                    long renderTimeMs) {

    }

    @Override
    public void onRenderVideoFrame(int uid,
                                   byte[] data,
                                   int frameType,
                                   int width,
                                   int height,
                                   int bufferLength,
                                   int yStride,
                                   int uStride,
                                   int vStride,
                                   int rotation,
                                   long renderTimeMs) {
        synchronized (mRtcFence) {
            if (mRtcState == AGRTCSTATE_DID_ACTIVE) {
                for (int i = 0; i < mRtcSessions.size(); i++) {
                    LangRTCVideoSession rtcSession = mRtcSessions.get(i);
                    if (uid == rtcSession.uid()) {
                        rtcSession.sendYUVData(data, width, height);
                    }
                }
            }
        }
    }

    //
    // internal API implementation.
    //
    private synchronized void initWorkerThread() {
        if (mWorkerThread == null) {
            mWorkerThread = new LangRTCThread(mContext);
            mWorkerThread.start();

            mWorkerThread.waitForReady();
        }
    }

    private synchronized LangRTCThread workerThread() {
        return mWorkerThread;
    }

    private synchronized void deInitWorkerThread() {
        mWorkerThread.exit();
        try {
            mWorkerThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mWorkerThread = null;
    }

    private RtcEngine rtcEngine() {
        return workerThread().rtcEngine();
    }

    private String rtsStatusString() {
        String statusString = new String();
        if (mRtcState == AGRTCSTATE_DID_STOPPED)
            statusString += "AGRTCSTATE_DID_STOPPED";
        else if (mRtcState == AGRTCSTATE_WILL_STOPPED)
            statusString += "AGRTCSTATE_WILL_STOPPED";
        else if (mRtcState == AGRTCSTATE_DID_ACTIVE)
            statusString += "AGRTCSTATE_DID_ACTIVE";
        else
            statusString += "AGRTCSTATE_WILL_ACTIVE";

        return statusString;
    }

    private void registerRtcMediaDataObserver() {
        mMediaDataObserver = MediaDataObserverImpl.the();
        MediaPreProcessingNative.setCallback(mMediaDataObserver);
//        MediaPreProcessingNative.setVideoCaptureByteBuffer(mMediaDataObserver.byteBufferCapture);
        MediaPreProcessingNative.setAudioRecordByteBuffer(mMediaDataObserver.byteBufferAudioRecord);
//        MediaPreProcessingNative.setAudioPlayByteBuffer(mMediaDataObserver.byteBufferAudioPlay);
//        MediaPreProcessingNative.setBeforeAudioMixByteBuffer(mMediaDataObserver.byteBufferBeforeAudioMix);
        MediaPreProcessingNative.setAudioMixByteBuffer(mMediaDataObserver.byteBufferAudioMix);

        mMediaDataObserver.addVideoObserver(this);
        mMediaDataObserver.addAudioObserver(this);
    }


    private void unregisterRtcMediaDataObserver() {
        if (mMediaDataObserver != null) {
            mMediaDataObserver.removeAudioObserver(this);
            mMediaDataObserver.removeVideoObserver(this);
            mMediaDataObserver.removeAllBuffer();
        }
        MediaPreProcessingNative.releasePoint();
    }

    private void joinChannel_l(LangRtcConfiguration rtcConfiguration, SurfaceView localView) {
        // post connecting message.
        if (mListener != null) {
            mListener.onRtcStatusChanged(LangRtcEvent.RTC_EVENT_CONNECTING, 0);
        }

        workerThread().eventHandler().addListener(this);

        int agoraVideoProfile = rtcVideoProfile(rtcConfiguration);
        workerThread().configEngine(agoraVideoProfile,
                rtcConfiguration.getAudioSampleRate(), rtcConfiguration.getAudioChannel(),
                rtcConfiguration.getEncryptionKey(), rtcConfiguration.getEncryptionMode());

        workerThread().preview(true, localView, 0);
        workerThread().joinChannel(rtcConfiguration.getChannelName(), 0, false);

        if (rtcConfiguration.isLocalMixed()) {
            registerRtcMediaDataObserver();
        }

        mRtcState = AGRTCSTATE_WILL_ACTIVE;
    }

    private int rtcVideoProfile(LangRtcConfiguration config) {
        int agoraVideoProfile;
        if (config.getRtcVideoProfile() == LangRtcConfig.RTC_VIDEO_PROFILE_120P) {
            agoraVideoProfile = Constants.VIDEO_PROFILE_120P;
        } else if (config.getRtcVideoProfile() == LangRtcConfig.RTC_VIDEO_PROFILE_180P) {
            agoraVideoProfile = Constants.VIDEO_PROFILE_180P;
        } else if (config.getRtcVideoProfile() == LangRtcConfig.RTC_VIDEO_PROFILE_240P) {
            agoraVideoProfile = Constants.VIDEO_PROFILE_240P;
        } else if (config.getRtcVideoProfile() == LangRtcConfig.RTC_VIDEO_PROFILE_360P) {
            agoraVideoProfile = Constants.VIDEO_PROFILE_360P;
        } else if (config.getRtcVideoProfile() == LangRtcConfig.RTC_VIDEO_PROFILE_480P) {
            agoraVideoProfile = Constants.VIDEO_PROFILE_480P;
        } else {
            agoraVideoProfile = Constants.VIDEO_PROFILE_720P;
        }
        return agoraVideoProfile;
    }

    private int setVoiceChat_l(boolean voiceOnly) {
        mVideoMuted = voiceOnly;
        if (mVideoMuted) {
            return rtcEngine().disableVideo();
        } else {
            return rtcEngine().enableVideo();
        }
    }

    private int muteLocalVoice_l(boolean mute) {
        mAudioMuted = mute;
        return rtcEngine().muteLocalAudioStream(mAudioMuted);
    }

    private int setupRemoteUser_l(final int uid, SurfaceView remoteView) {
        IRTCSession session = currentSession(uid);
        if (session == null) {
            DebugLog.w(TAG, "setupRemoteUser_l called but no valid user found");
            return -1;
        }
        return rtcEngine().setupRemoteVideo(new VideoCanvas(remoteView, VideoCanvas.RENDER_MODE_HIDDEN, uid));
    }

    private int muteRemoteVoice_l(final int uid, boolean mute) {
        IRTCSession session = currentSession(uid);
        if (session == null) {
            DebugLog.w(TAG, "muteRemoteVoice_l called but no valid user found");
            return -1;
        }
        return rtcEngine().muteRemoteAudioStream(uid, mute);
    }

    private void leaveChannel_l() {
        stopStreamIfNeed();
        unregisterRtcMediaDataObserver();
        workerThread().leaveChannel(mRtcConfiguration.getChannelName());
        workerThread().preview(false, null, 0);

        mRtcState = AGRTCSTATE_WILL_STOPPED;
    }

    private void publishStreamIfNeed() {
        if (mRtcConfiguration.isLocalMixed()) return;
        rtcEngine().setLiveTranscoding(updateLiveTransCoding());
        rtcEngine().addPublishStreamUrl(mRtcConfiguration.getPushStreamUrl(), true);
    }

    private void stopStreamIfNeed() {
        if (mRtcConfiguration.isLocalMixed()) return;
        rtcEngine().removePublishStreamUrl(mRtcConfiguration.getPushStreamUrl());
    }

    private LiveTranscoding updateLiveTransCoding() {
        int number = mRtcSessions.size();
        float kTopOffsetPercent = mRtcDisplayParams.topOffsetPercent;//0.10f;
        float kSubWinWidthPercent = mRtcDisplayParams.subWidthPercentOnTwoWins;//0.5f;
        float kSubWinHeightPercent = mRtcDisplayParams.subHeightPercentOnTwoWins;//0.5f;

        ArrayList<LiveTranscoding.TranscodingUser> users = new ArrayList<>(number);


        LiveTranscoding.TranscodingUser localUser = new LiveTranscoding.TranscodingUser();
        localUser.uid = mRtcSessions.get(0).uid();
        localUser.x = 0;
        localUser.y = (int) (mRtcConfiguration.getVideoHeight() * kTopOffsetPercent);
        localUser.width = (int) (mRtcConfiguration.getVideoWidth() * kSubWinWidthPercent);
        localUser.height = (int) (mRtcConfiguration.getVideoHeight() * kSubWinHeightPercent);

        localUser.zOrder = 1;
        localUser.audioChannel = 0;
        users.add(localUser);


        if (number > 1) {
            LiveTranscoding.TranscodingUser remoteUser = new LiveTranscoding.TranscodingUser();

            remoteUser.uid = mRtcSessions.get(1).uid(); // REMOTE USER

            remoteUser.x = (int) (mRtcConfiguration.getVideoWidth() * kSubWinWidthPercent);
            remoteUser.y = (int) (mRtcConfiguration.getVideoHeight() * kTopOffsetPercent);
            remoteUser.width = (int) (mRtcConfiguration.getVideoWidth() * kSubWinWidthPercent);
            remoteUser.height = (int) (mRtcConfiguration.getVideoHeight() * kSubWinHeightPercent);

            remoteUser.zOrder = 2;
            remoteUser.audioChannel = 0;

            users.add(remoteUser);
        }

        LiveTranscoding liveTranscoding = new LiveTranscoding();
        liveTranscoding.setUsers(users);

        liveTranscoding.width = mRtcConfiguration.getVideoWidth();
        liveTranscoding.height = mRtcConfiguration.getVideoHeight();

        liveTranscoding.videoBitrate = mRtcConfiguration.getVideoBitrateBps() / 1024;
        liveTranscoding.videoFramerate = mRtcConfiguration.getVideoFPS();
        liveTranscoding.lowLatency = true;
        return liveTranscoding;
    }

    /*
     * Internal API callbacks implementations.
     */
    private void onJoinChannelSuccess_l(String channel, final int uid, int elapsed) {
        if (mRtcState == AGRTCSTATE_WILL_ACTIVE) {
            mRtcState = AGRTCSTATE_DID_ACTIVE;

            LangRTCVideoSession localSession = new LangRTCVideoSession();
            localSession.setUid(uid);
            mRtcSessions.add(localSession);

            publishStreamIfNeed();
            if (mListener != null) {
                mListener.onRtcStatusChanged(LangRtcEvent.RTC_EVENT_CONNECTED, uid);
            }
        } else {
            DebugLog.e(TAG, "onJoinChannelSuccess_l() called in invalid state: " + rtsStatusString());
        }
    }

    private void onRejoinChannelSuccess_l(String channel, final int uid, int elapsed) {
        if (mRtcState == AGRTCSTATE_DID_ACTIVE) {
            int local_uid = mRtcSessions.get(0).uid();
            if (local_uid != uid) {
                DebugLog.w(TAG, "onRejoinChannelSuccess_l(): local uid change from" + "(" + local_uid + ")"
                        + " to" + "(" + uid + ")");
                mRtcSessions.get(0).setUid(uid);
            }

            publishStreamIfNeed();
            if (mListener != null) {
                mListener.onRtcStatusChanged(LangRtcEvent.RTC_EVENT_CONNECTED, uid);
            }
        }
    }

    private void onUserJoined_l(int uid, int elapsed) {
        if (mRtcState == AGRTCSTATE_DID_ACTIVE) {
            boolean uidExists = false;
            for (int i = 0; i < mRtcSessions.size(); i++) {
                if (uid == mRtcSessions.get(i).uid()) {
                    uidExists = true;
                }
            }

            if (!uidExists) {
                LangRTCVideoSession session = new LangRTCVideoSession();
                session.setUid(uid);
                session.setWidth(0);
                session.setHeight(0);
                mRtcSessions.add(session);
            }

            if (mMediaDataObserver != null) {
                mMediaDataObserver.addDecodeBuffer(uid);
            }

            publishStreamIfNeed();
            if (mListener != null) {
                mListener.onRtcStatusChanged(LangRtcEvent.RTC_EVENT_USER_JOINTED, uid);
            }
        }
    }

    private void onFirstRemoteVideoFrame_l(int uid, int width, int height, int elapsed) {
        if (mRtcState == AGRTCSTATE_DID_ACTIVE) {
            boolean uidExists = false;
            for (int i = 0; i < mRtcSessions.size(); i++) {
                if (uid == mRtcSessions.get(i).uid()) {
                    uidExists = true;
                }
            }

            if (!uidExists) {
                DebugLog.w(TAG, "onUserJoined(int, int) callback not received, try add new user here.");
                LangRTCVideoSession session = new LangRTCVideoSession();
                session.setUid(uid);
                session.setWidth(width);
                session.setHeight(height);
                mRtcSessions.add(session);
                if (mListener != null) {
                    mListener.onRtcStatusChanged(LangRtcEvent.RTC_EVENT_USER_JOINTED, uid);
                }
            }


            if (mMediaDataObserver != null) {
                mMediaDataObserver.addDecodeBuffer(uid);
            }

            publishStreamIfNeed();

            IRTCSession session = currentSession(uid);
            session.setWidth(width);
            session.setHeight(height);
            if (mListener != null) {
                mListener.onRtcReceivedNotification(LangRtcEvent.RTC_EVENT_USER_VIDEO_BEGIN_RENDERED, userFromSession(session));
            }
        }
    }

    private void onVideoSizeChanged_l(int uid, int width, int height, int rotation) {
        if (mRtcState == AGRTCSTATE_DID_ACTIVE) {
            IRTCSession session = currentSession(uid);
            if (session == null) {
                DebugLog.w(TAG, "onVideoSizeChanged called but no valid user found");
                return;
            }
            session.setWidth(width);
            session.setHeight(height);
            session.setRotation(rotation);
            if (mListener != null) {
                mListener.onRtcReceivedNotification(LangRtcEvent.RTC_EVENT_USER_VIDEO_SIZE_CHANGED, userFromSession(session));
            }
        }
    }

    private void onUserAudioMuted_l(int uid, boolean muted) {
        if (mRtcState == AGRTCSTATE_DID_ACTIVE) {
            IRTCSession session = currentSession(uid);
            if (session == null) {
                DebugLog.w(TAG, "onUserAudioMuted_l called but no valid user found");
                return;
            }
            session.setAudioMuted(muted);
            if (mListener != null) {
                mListener.onRtcReceivedNotification(LangRtcEvent.RTC_EVENT_USER_AUDIO_MUTED, userFromSession(session));
            }
        }
    }

    private void onUserVideoMuted_l(int uid, boolean muted) {
        if (mRtcState == AGRTCSTATE_DID_ACTIVE) {
            IRTCSession session = currentSession(uid);
            if (session == null) {
                DebugLog.w(TAG, "onUserAudioMuted_l called but no valid user found");
                return;
            }
            session.setVideoMuted(muted);
            if (mListener != null) {
                mListener.onRtcReceivedNotification(LangRtcEvent.RTC_EVENT_USER_VIDEO_MUTED, userFromSession(session));
            }
        }
    }

    private void onRtcLocalAudioRouteChanged_l(int data) {
        DebugLog.w(TAG, "onRtcLocalAudioRouteChanged_l = " + data);
        //mListener.onRtcLocalAudioRouteChanged(data);
    }

    private void onRtcConnectionLost_l() {
        if (mListener != null) {
            mListener.onRtcStatusChanged(LangRtcEvent.RTC_EVENT_NETWORK_LOST, 0);
        }
    }

    private void onRtcConnectionTimeout_l() {
        if (mListener != null) {
            mListener.onRtcStatusChanged(LangRtcEvent.RTC_EVENT_NETWORK_TIMEOUT, 0);
        }
    }

    private void onUserOffline_l(int uid, int reason) {
        if (mRtcState == AGRTCSTATE_DID_ACTIVE) {
            int indexTodelete = -1;
            for (int i = 0; i < mRtcSessions.size(); i++) {
                LangRTCVideoSession rtcSession = mRtcSessions.get(i);
                if (rtcSession.uid() == uid) {
                    mRtcSessions.remove(i);
                    indexTodelete = i;
                    break;
                }
            }
            if (indexTodelete == -1) {
                DebugLog.w(TAG, "WARNING: onUserOffline() uid: " + uid + " not match in session list");
            }

            if (mMediaDataObserver != null) {
                mMediaDataObserver.removeDecodeBuffer(uid);
            }
            publishStreamIfNeed();

            if (mListener != null) {
                mListener.onRtcStatusChanged(LangRtcEvent.RTC_EVENT_USER_OFFLINE, uid);
            }
        }
    }

    private void onLeaveChannel_l() {
        if (mRtcState == AGRTCSTATE_WILL_STOPPED) {
            mRtcState = AGRTCSTATE_DID_STOPPED;

            int localUid = mRtcSessions.get(0).uid();
            //remove all session objects.
            for (int i = 0; i < mRtcSessions.size(); i++) {
                LangRTCVideoSession session = mRtcSessions.get(i);
                Log.i(TAG, "remove session uid:" + session.uid());
            }
            mRtcSessions.clear();

            if (mListener != null) {
                mListener.onRtcStatusChanged(LangRtcEvent.RTC_EVENT_DISCONNECTED, localUid);
            }

            workerThread().eventHandler().removeListener(this);

            mRtcFence.notifyAll();
        } else {
            DebugLog.e(TAG, "onLeaveChannel_l() called in invalid state: " + rtsStatusString());
        }
    }

    private void onRtcStats_l(IRtcEngineEventHandler.RtcStats stats) {
        LangRtcInfo rtcInfo = new LangRtcInfo();
        rtcInfo.duration = stats.totalDuration;
        rtcInfo.txBytes = stats.txBytes;
        rtcInfo.rxBytes = stats.rxBytes;
        rtcInfo.rxAudioKBitrate = stats.rxAudioKBitRate;
        rtcInfo.rxVideoKBitrate = stats.rxVideoKBitRate;
        rtcInfo.txAudioKBitrate = stats.txAudioKBitRate;
        rtcInfo.txVideoKBitrate = stats.txVideoKBitRate;
        //rtcInfo.onlineUsers = stats.users;
        rtcInfo.onlineUsers = mRtcSessions.size();

        mListener.onRtcStatistics(rtcInfo);
    }

    private void onRtcWarning_l(int warn) {
        if (mListener != null) {
            mListener.onRtcWarning(warn);
        }
    }

    private void onRtcFatalError_l(int error, String description) {
        if (mListener != null) {
            mListener.onRtcError(error, description);
        }
    }

    private IRTCSession currentSession(int uid) {
        LangRTCVideoSession session = null;
        for (int i = 0; i < mRtcSessions.size(); i++) {
            if (uid == mRtcSessions.get(i).uid()) {
                session = mRtcSessions.get(i);
            }
        }
        return session;
    }

    private static LangRtcUser userFromSession(IRTCSession session) {
        LangRtcUser rtcUser = new LangRtcUser();
        rtcUser.mUid = session.uid();
        rtcUser.mWidth = session.width();
        rtcUser.mHeight = session.height();
        rtcUser.mRotation = session.rotation();
        rtcUser.mFps = session.fps();
        rtcUser.mBitrateKbps = session.bitrate();
        rtcUser.mVolume = session.volume();
        rtcUser.mAudioMuted = session.audioMuted();
        rtcUser.mVideoMuted = session.videoMuted();
        return rtcUser;
    }
}
