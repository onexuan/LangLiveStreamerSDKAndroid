package net.lang.streamer;

import android.view.SurfaceView;

import net.lang.streamer.config.LangRtcConfig;

/**
 * Created by lang on 2018/3/20.
 */

public interface ILangRtcAudience {
    enum RtcAudienceEvent {
        AUDIENCE_EVENT_RTC_CONNECTING          (10000, "AUDIENCE_EVENT_RTC_CONNECTING"),
        AUDIENCE_EVENT_RTC_CONNECT_SUCC        (10001, "AUDIENCE_EVENT_RTC_CONNECT_SUCC"),
        AUDIENCE_EVENT_RTC_DISCONNECT          (10002, "AUDIENCE_EVENT_RTC_DISCONNECT"),
        AUDIENCE_EVENT_RTC_USER_JOINED         (10003, "AUDIENCE_EVENT_RTC_USER_JOINED"),
        AUDIENCE_EVENT_RTC_USER_OFFLINE        (10004, "AUDIENCE_EVENT_RTC_USER_OFFLINE"),
        AUDIENCE_EVENT_RTC_USER_AUDIO_MUTED    (10005, "AUDIENCE_EVENT_RTC_USER_AUDIO_MUTED"),
        AUDIENCE_EVENT_RTC_USER_VIDEO_MUTED    (10006, "AUDIENCE_EVENT_RTC_USER_VIDEO_MUTED"),
        AUDIENCE_EVENT_RTC_AUDIO_ROUTE_CHANGE  (10007, "AUDIENCE_EVENT_RTC_AUDIO_ROUTE_CHANGE"),
        AUDIENCE_EVENT_RTC_STATS_UPDATE        (10008, "AUDIENCE_EVENT_RTC_STATS_UPDATE"),
        AUDIENCE_EVENT_RTC_USER_VIDEO_RENDERED (10009, "AUDIENCE_EVENT_RTC_USER_VIDEO_RENDERED"),
        AUDIENCE_EVENT_RTC_VIDEO_SIZE_CHANGED  (10010, "AUDIENCE_EVENT_RTC_VIDEO_SIZE_CHANGED"),
        AUDIENCE_EVENT_RTC_NETWORK_LOST        (10011, "AUDIENCE_EVENT_RTC_NETWORK_LOST"),
        AUDIENCE_EVENT_RTC_NETWORK_TIMEOUT     (10012, "AUDIENCE_EVENT_RTC_NETWORK_TIMEOUT"),
        AUDIENCE_EVENT_RTC_EXCEPTION           (10013, "AUDIENCE_EVENT_RTC_EXCEPTION");

        int value;
        String name;
        RtcAudienceEvent(int v, String string) {
            this.value = v;
            this.name = string;
        }
    }

    SurfaceView createRtcRenderView();

    int joinRtcChannel(LangRtcConfig config);

    void leaveRtcChannel();

    int setupRtcRemoteUser(final int uid, SurfaceView remoteView);

    void setEventListener(IRtcAudienceEventListener listener);

    void release();

    interface IRtcAudienceEventListener {
        void onEvent(ILangRtcAudience audience, RtcAudienceEvent event, Object obj);
    }
}
