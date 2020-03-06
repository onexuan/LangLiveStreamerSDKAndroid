package net.lang.streamer;

import android.view.SurfaceView;

import com.github.faucamp.simplertmp.RtmpHandler;

import net.lang.streamer.config.*;
import net.lang.streamer.engine.IMediaPublisherListener;
import net.lang.streamer.engine.LangCaptureHandler;
import net.lang.streamer.engine.LangEncodeHandler;
import net.lang.streamer.engine.LangRecordHandler;
import net.lang.streamer.faceu.LangFaceHandler;
import net.lang.streamer.config.LangObjectSegmentationConfig;
import net.lang.streamer.rtc.LangRtcMessageHandler;
import net.lang.streamer.utils.DebugLog;
import net.lang.streamer.widget.AnimationCallback;
import net.lang.streamer.widget.LangMagicCameraView;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;

/**
 * Created by lichao on 17-5-4.
 */

public class LangCameraStreamerListener implements
        LangEncodeHandler.SnailEncodeListener,
        LangRtcMessageHandler.SnailRtcListener,
        RtmpHandler.RtmpListener,
        LangRecordHandler.SnailRecordListener,
        LangCaptureHandler.SnailCaptureListener,
        LangFaceHandler.SnailFaceListener,
        IMediaPublisherListener,
        ILangCameraStreamer {
    private static final String TAG = LangCameraStreamerListener.class.getName();
    protected ILangCameraStreamerOnEventListener mEventListener;
    protected ILangCameraStreamerOnErrorListener mErrorListener;

    public LangCameraStreamerListener() {

    }

    @Override
    public void onNetworkWeak() {
        if (mEventListener != null) {
            mEventListener.onEvent(this, LangStreamerEvent.LANG_WARNNING_NETWORK_WEAK, 0);
        }
    }

    @Override
    public void onResume() {

    }

    @Override
    public void onPause() {

    }

    @Override
    public void onNetworkResume() {

    }

    @Override
    public void onEncodeIllegalArgumentException(IllegalArgumentException e) {

    }

    @Override
    public void onRecordPause() {

    }

    @Override
    public void onRecordResume() {

    }

    @Override
    public void onRecordStarted(String msg) {
        if (mEventListener != null) {
            mEventListener.onEvent(this, LangStreamerEvent.LANG_EVENT_RECORD_BEGIN, 0);
        }
    }

    @Override
    public void onRecordFinished(String msg) {
        if (mEventListener != null) {
            mEventListener.onEvent(this, LangStreamerEvent.LANG_EVENT_RECORD_END, 0);
        }
    }

    @Override
    public void onRecordIllegalArgumentException(IllegalArgumentException e) {

    }

    @Override
    public void onRecordIOException(IOException e) {

    }

    @Override
    public void onCaptureStarted(String msg) {
        if (mEventListener != null) {
            Object obj = new Object[]{msg};
            mEventListener.onEvent(this, LangStreamerEvent.LANG_EVENT_SCREENSHOT_BEGIN, obj);
        }
    }

    @Override
    public void onCaptureFinished(String msg) {
        if (mEventListener != null) {
            Object obj = new Object[]{msg};
            mEventListener.onEvent(this, LangStreamerEvent.LANG_EVENT_SCREENSHOT_END, obj);
        }
    }

    @Override
    public void onCaptureIOException(IOException e) {
        if (mErrorListener != null) {
            mErrorListener.onError(this, LangStreamerError.LANG_ERROR_SCREENSHOT_FAIL, 0);
        }
    }

    @Override
    public void onHumanFaceDetected() {
        if (mEventListener != null) {
            mEventListener.onEvent(this, LangStreamerEvent.LANG_EVENT_FACE_DETECTED, null);
        }
    }

    @Override
    public void onHumanFaceLost() {
        if (mEventListener != null) {
            mEventListener.onEvent(this, LangStreamerEvent.LANG_EVENT_FACE_LOST, null);
        }
    }

    @Override
    public void onHumanHandDetected(LangFaceHandler.GestureType gesture) {
        if (mEventListener != null) {
            mEventListener.onEvent(this, LangStreamerEvent.LANG_EVENT_HAND_DETECTED, gesture);
        }
    }

    @Override
    public void onHumanHandLost() {
        if (mEventListener != null) {
            mEventListener.onEvent(this, LangStreamerEvent.LANG_EVENT_HAND_LOST, null);
        }
    }

    @Override
    public void onRtmpConnecting(String msg) {
        if (mEventListener != null) {
            mEventListener.onEvent(this, LangStreamerEvent.LANG_EVENT_PUSH_CONNECTING, 0);
        }
    }

    @Override
    public void onRtmpConnected(String msg) {
        if (mEventListener != null) {
            mEventListener.onEvent(this, LangStreamerEvent.LANG_EVENT_PUSH_CONNECT_SUCC, 0);
        }
    }

    @Override
    public void onRtmpVideoStreaming() {

    }

    @Override
    public void onRtmpAudioStreaming() {

    }

    @Override
    public void onRtmpStopped() {

    }

    @Override
    public void onRtmpDisconnected() {
        if (mEventListener != null) {
            mEventListener.onEvent(this, LangStreamerEvent.LANG_EVENT_PUSH_DISCONNECT, 0);
        }
    }

    @Override
    public void onRtmpVideoFpsChanged(double fps) {

    }

    @Override
    public void onRtmpVideoBitrateChanged(double bitrate) {

    }

    @Override
    public void onRtmpAudioBitrateChanged(double bitrate) {

    }

    @Override
    public void onRtmpSocketException(SocketException e) {
        if (mErrorListener != null) {
            mErrorListener.onError(this, LangStreamerError.LANG_ERROR_PUSH_CONNECT_FAIL, 0);
        }
    }

    @Override
    public void onRtmpIOException(IOException e) {
        if (mErrorListener != null) {
            mErrorListener.onError(this, LangStreamerError.LANG_ERROR_PUSH_CONNECT_FAIL, 0);
        }
    }

    @Override
    public void onRtmpIllegalArgumentException(IllegalArgumentException e) {

    }

    @Override
    public void onRtmpIllegalStateException(IllegalStateException e) {

    }

    @Override
    public int init(LangStreamerConfig config, LangMagicCameraView view) {
        return 0;
    }

    @Override
    public int init(LangAudioQuality acfg, LangVideoQuality vcfg, LangMagicCameraView view) {
        return 0;
    }

    @Override
    public int startPreview() {
        return 0;
    }

    @Override
    public int stopPreview() {
        return 0;
    }

    @Override
    public int startStreaming(String url) {
        return 0;
    }

    @Override
    public int stopStreaming() {
        return 0;
    }

    @Override
    public boolean isStreaming() {
        return false;
    }

    @Override
    public int startRecording(String url) {
        return 0;
    }

    @Override
    public int stopRecording() {
        return 0;
    }

    @Override
    public int screenshot(String url) {
        return 0;
    }

    @Override
    public int release() {
        return 0;
    }

    @Override
    public int switchCamera() {
        return 0;
    }

    @Override
    public void setCameraBrightLevel(float level) {

    }

    @Override
    public void setCameraToggleTorch(boolean enable) {

    }

    @Override
    public void setCameraFocusing(float x, float y) {

    }

    @Override
    public int setFilter(LangCameraFilter filter) {
        return 0;
    }

    @Override
    public int setBeauty(LangCameraBeauty info) {
        return 0;
    }

    @Override
    public int setWatermark(LangWatermarkConfig config) {
        return 0;
    }

    @Override
    public int setFaceu(LangFaceuConfig config) {
        return 0;
    }

    @Override
    public int setGiftAnimation(LangObjectSegmentationConfig params, InputStream inputStream, InputStream giftStream) {
        return 0;
    }

    @Override
    public int setMattingAnimation(LangObjectSegmentationConfig params, String inputPath, String giftPath, AnimationCallback animationCallback) {  return 0;   }

    @Override
    public int setHairColors(LangObjectSegmentationConfig params) {
        return 0;
    }

    @Override
    public int setMute(boolean mute) {
        return 0;
    }

    @Override
    public int enableAudioPlay(boolean enableAudioPlay) {
        return 0;
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
    public void setReconnectOption(int count, int interval) {

    }

    protected void sendEvent(LangStreamerEvent e, int v) {
        if (mEventListener != null) {
            mEventListener.onEvent(this, e, v);
        }
    }

    protected void sendError(LangStreamerError e, int v) {
        if (mErrorListener != null) {
            mErrorListener.onError(this, e, v);
        }
    }

    @Override
    public void setAutoAdjustBitrate(boolean enable) {

    }

    @Override
    public void setDebugLevel(int level) {

    }


    @Override
    public void setMirror(boolean enable) {

    }

    @Override
    public void setZoom(float scale) {

    }

    @Override
    public LangLiveInfo getLiveInfo() {
        return null;
    }

    @Override
    public void onPublisherEvent(Type t, Value v) {
        boolean isEvent = false;
        LangStreamerEvent event = LangStreamerEvent.LANG_WARNNING_HW_ACCELERATION_FAIL;
        LangStreamerError error = LangStreamerError.LANG_ERROR_RECORD_FAIL;
        boolean isDefine = false;
            switch (t) {
                case kTypeMic:
                     if (v == Value.kFailed) {
                         error = LangStreamerError.LANG_ERROR_OPEN_MIC_FAIL;
                         isDefine = true;
                     }
                    break;
                case kTypeAudioEncoder:
                    if (v == Value.kFailed) {
                        error = LangStreamerError.LANG_ERROR_AUDIO_ENCODE_FAIL;
                        isDefine = true;
                    }
                    break;
                case kTypeVideoEncoder:
                    if (v == Value.kFailed) {
                        error = LangStreamerError.LANG_ERROR_VIDEO_ENCODE_FAIL;
                        isDefine = true;
                    }
                    break;
                case kTypeRecord:
                    if (v == Value.kFailed) {
                        error = LangStreamerError.LANG_ERROR_RECORD_FAIL;
                        isDefine = true;
                    }
                    break;
                default:
                    DebugLog.efmt(TAG, "Unknown publisher event %d value %d", t, v);
                    return;
            }

            if (isDefine && isEvent && mEventListener != null) {
                mEventListener.onEvent(this, event, 0);
            }else if (isDefine && !isEvent && mErrorListener != null) {
                mErrorListener.onError(this, error, 0);
            }
    }

    @Override
    public boolean rtcAvailable(LangRtcConfig config) {
        return false;
    }

    @Override
    public SurfaceView createRtcRenderView() {
        return null;
    }

    @Override
    public void setRtcDisplayLayoutParams(LangRtcConfig.RtcDisplayParams displayParams) {

    }

    @Override
    public int joinRtcChannel(LangRtcConfig config, SurfaceView localView) {
        if (mEventListener != null) {
            mEventListener.onEvent(this, LangStreamerEvent.LANG_EVENT_RTC_CONNECTING, null);
        }

        return 0;
    }

    @Override
    public void leaveRtcChannel() {

    }

    @Override
    public int setRtcVoiceChat(boolean voiceOnly) {
        return 0;
    }

    @Override
    public int muteRtcLocalVoice(boolean mute) {
        return 0;
    }

    @Override
    public int muteRtcRemoteVoice(final int uid, boolean mute) {
        return 0;
    }

    @Override
    public int setupRtcRemoteUser(final int uid, SurfaceView remoteView) {
        return 0;
    }

    @Override
    public void onRtcError(int error) {
        if (mErrorListener != null) {
            mErrorListener.onError(this, LangStreamerError.LANG_ERROR_RTC_EXCEPTION, error);
        }
    }

    @Override
    public void onRtcLocalUserJoined(int uid) {
        if (mEventListener != null) {
            Object obj = new Object[]{uid};
            mEventListener.onEvent(this, LangStreamerEvent.LANG_EVENT_RTC_CONNECT_SUCC, obj);
        }
    }

    @Override
    public void onRtcLocalUserOffline() {
        if (mEventListener != null) {
            mEventListener.onEvent(this, LangStreamerEvent.LANG_EVENT_RTC_DISCONNECT, null);
        }
    }

    @Override
    public void onRtcRemoteUserJoined(int uid) {
        if (mEventListener != null) {
            Object obj = new Object[]{uid};
            mEventListener.onEvent(this, LangStreamerEvent.LANG_EVENT_RTC_USER_JOINED, obj);
        }
    }

    @Override
    public void onRtcRemoteUserVideoRendered(int uid, int width, int height) {
        if (mEventListener != null) {
            Object obj = new Object[]{uid, width, height};
            mEventListener.onEvent(this, LangStreamerEvent.LANG_EVENT_RTC_USER_VIDEO_RENDERED, obj);
        }
    }

    @Override
    public void onRtcRemoteUserOffline(int uid, int reason) {
        if (mEventListener != null) {
            Object obj = new Object[]{uid, reason};
            mEventListener.onEvent(this, LangStreamerEvent.LANG_EVENT_RTC_USER_OFFLINE, obj);
        }
    }

    @Override
    public void onRtcRemoteUserAudioMuted(int uid, boolean muted) {
        if (mEventListener != null) {
            Object obj = new Object[]{uid, muted};
            mEventListener.onEvent(this, LangStreamerEvent.LANG_EVENT_RTC_USER_AUDIO_MUTED, obj);
        }
    }

    @Override
    public void onRtcRemoteUserVideoMuted(int uid, boolean muted) {
        if (mEventListener != null) {
            Object obj = new Object[]{uid, muted};
            mEventListener.onEvent(this, LangStreamerEvent.LANG_EVENT_RTC_USER_VIDEO_MUTED, obj);
        }
    }

    @Override
    public void onRtcLocalAudioRouteChanged(int data) {
        if (mEventListener != null) {
            Object obj = new Object[]{data};
            mEventListener.onEvent(this, LangStreamerEvent.LANG_EVENT_RTC_AUDIO_ROUTE_CHANGE, obj);
        }
    }

    @Override
    public void onRtcStatsUpdate(LangRtcInfo rtcInfo) {
        if (mEventListener != null) {
            mEventListener.onEvent(this, LangStreamerEvent.LANG_EVENT_RTC_STATS_UPDATE, rtcInfo);
        }
    }

    @Override
    public void onRtcNetworkLost() {
        if (mEventListener != null) {
            mEventListener.onEvent(this, LangStreamerEvent.LANG_EVENT_RTC_NETWORK_LOST, null);
        }
    }

    @Override
    public void onRtcNetworkTimeout() {
        if (mEventListener != null) {
            mEventListener.onEvent(this, LangStreamerEvent.LANG_EVENT_RTC_NETWORK_TIMEOUT, null);
        }
    }

    @Override
    public void enableMakeups(boolean enable) {

    }

    @Override
    public void enablePushMatting(boolean enable) {

    }
}
