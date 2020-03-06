package net.lang.streamer.rtc;

import android.content.Context;
import android.view.SurfaceView;

import net.lang.streamer.ILangRtcAudience;
import net.lang.streamer.LangRtcInfo;
import net.lang.streamer.config.LangRtcConfig;
import net.lang.streamer.utils.DebugLog;

/**
 * Created by lang on 2018/3/20.
 */

public class LangRtcAudience implements ILangRtcAudience,
        LangRtcMessageHandler.SnailRtcListener {

    private static final String TAG = LangRtcAudience.class.getSimpleName();

    private IRtcAudienceEventListener mEventListener = null;
    private IRtcSessionManager mSessionManager = null;

    public LangRtcAudience(Context context, IRtcAudienceEventListener listener) {
        if (context == null) {
            throw new IllegalArgumentException("context should not be null!");
        }
        mSessionManager = LangRtcSessionMgrCreator.createRtcSessionManager(this, context, null);
        setEventListener(listener);
    }

    public LangRtcAudience(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context should not be null!");
        }
        mSessionManager = LangRtcSessionMgrCreator.createRtcSessionManager(this, context, null);
    }

    @Override
    public void release() {
        if (mSessionManager != null) {
            mSessionManager.release();
            mSessionManager = null;
        }
    }

    @Override
    public void setEventListener(IRtcAudienceEventListener listener) {
        mEventListener = listener;
    }

    @Override
    public SurfaceView createRtcRenderView() {
        if (mSessionManager != null) {
            return mSessionManager.createRtcRenderView();
        }
        return null;
    }

    @Override
    public int joinRtcChannel(LangRtcConfig config) {
        if (mSessionManager != null) {
            if (!config.audience) {
                DebugLog.w(TAG, "joinRtcChannel(): param audience invalid");
                return -1;
            }
            if (mEventListener != null) {
                mEventListener.onEvent(this, RtcAudienceEvent.AUDIENCE_EVENT_RTC_CONNECTING, null);
            }
            mSessionManager.joinChannel(config, null);
            return 0;
        }
        return -1;
    }

    @Override
    public void leaveRtcChannel() {
        if (mSessionManager != null) {
            mSessionManager.leaveChannel();
        }
    }

    @Override
    public int setupRtcRemoteUser(final int uid, SurfaceView remoteView) {
        if (mSessionManager != null) {
            mSessionManager.setupRemoteUser(uid, remoteView);
            return 0;
        }
        return -1;
    }

    @Override
    public void onRtcError(int error) {
        if (mEventListener != null) {
            Object obj = new Object[]{error};
            mEventListener.onEvent(this, RtcAudienceEvent.AUDIENCE_EVENT_RTC_EXCEPTION, obj);
        }
    }

    @Override
    public void onRtcLocalUserJoined(int uid) {
        if (mEventListener != null) {
            Object obj = new Object[]{uid};
            mEventListener.onEvent(this, RtcAudienceEvent.AUDIENCE_EVENT_RTC_CONNECT_SUCC, obj);
        }
    }

    @Override
    public void onRtcLocalUserOffline() {
        if (mEventListener != null) {
            mEventListener.onEvent(this, RtcAudienceEvent.AUDIENCE_EVENT_RTC_DISCONNECT, null);
        }
    }

    @Override
    public void onRtcRemoteUserJoined(int uid) {
        if (mEventListener != null) {
            Object obj = new Object[]{uid};
            mEventListener.onEvent(this, RtcAudienceEvent.AUDIENCE_EVENT_RTC_USER_JOINED, obj);
        }
    }

    @Override
    public void onRtcRemoteUserVideoRendered(int uid, int width, int height) {
        if (mEventListener != null) {
            Object obj = new Object[]{uid, width, height};
            mEventListener.onEvent(this, RtcAudienceEvent.AUDIENCE_EVENT_RTC_USER_VIDEO_RENDERED, obj);
        }
    }

    @Override
    public void onRtcRemoteUserOffline(int uid, int reason) {
        if (mEventListener != null) {
            Object obj = new Object[]{uid, reason};
            mEventListener.onEvent(this, RtcAudienceEvent.AUDIENCE_EVENT_RTC_USER_OFFLINE, obj);
        }
    }

    @Override
    public void onRtcRemoteUserAudioMuted(int uid, boolean muted) {
        if (mEventListener != null) {
            Object obj = new Object[]{uid, muted};
            mEventListener.onEvent(this, RtcAudienceEvent.AUDIENCE_EVENT_RTC_USER_AUDIO_MUTED, obj);
        }
    }

    @Override
    public void onRtcRemoteUserVideoMuted(int uid, boolean muted) {
        if (mEventListener != null) {
            Object obj = new Object[]{uid, muted};
            mEventListener.onEvent(this, RtcAudienceEvent.AUDIENCE_EVENT_RTC_USER_VIDEO_MUTED, obj);
        }
    }

    @Override
    public void onRtcLocalAudioRouteChanged(int data) {

    }

    @Override
    public void onRtcStatsUpdate(LangRtcInfo rtcInfo) {
        if (mEventListener != null) {
            mEventListener.onEvent(this, RtcAudienceEvent.AUDIENCE_EVENT_RTC_STATS_UPDATE, rtcInfo);
        }
    }

    @Override
    public void onRtcNetworkLost() {
        if (mEventListener != null) {
            mEventListener.onEvent(this, RtcAudienceEvent.AUDIENCE_EVENT_RTC_NETWORK_LOST, null);
        }
    }

    @Override
    public void onRtcNetworkTimeout() {
        if (mEventListener != null) {
            mEventListener.onEvent(this, RtcAudienceEvent.AUDIENCE_EVENT_RTC_NETWORK_TIMEOUT, null);
        }
    }
}
