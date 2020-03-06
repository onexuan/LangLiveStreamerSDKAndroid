package net.lang.streamer2.engine.data;

import net.lang.streamer2.utils.DebugLog;

public enum LangRtcEvent implements IStatusObject {
    // local user connecting to server
    RTC_EVENT_CONNECTING                (0, "RTC_EVENT_CONNECTING"),
    // local user connected to server
    RTC_EVENT_CONNECTED                 (1, "RTC_EVENT_CONNECTED"),
    // local user disconnected from server
    RTC_EVENT_DISCONNECTED              (2, "RTC_EVENT_DISCONNECTED"),
    // local network lost connection with server
    RTC_EVENT_NETWORK_LOST              (3, "RTC_EVENT_NETWORK_LOST"),
    // local network connection lost and retry re-connect timeout
    RTC_EVENT_NETWORK_TIMEOUT           (4, "RTC_EVENT_NETWORK_TIMEOUT"),
    // remote user joined channel
    RTC_EVENT_USER_JOINTED              (5, "RTC_EVENT_USER_JOINTED"),
    // remote user leave channel
    RTC_EVENT_USER_OFFLINE              (6, "RTC_EVENT_USER_OFFLINE"),
    // remote user mute audio
    RTC_EVENT_USER_AUDIO_MUTED          (7, "RTC_EVENT_USER_AUDIO_MUTED"),
    // remote user mute video
    RTC_EVENT_USER_VIDEO_MUTED          (8, "RTC_EVENT_USER_VIDEO_MUTED"),
    // remote user video start rendered
    RTC_EVENT_USER_VIDEO_BEGIN_RENDERED (9, "RTC_EVENT_USER_VIDEO_BEGIN_RENDERED"),
    // local or remote user video size changed
    RTC_EVENT_USER_VIDEO_SIZE_CHANGED   (10, "RTC_EVENT_USER_VIDEO_SIZE_CHANGED"),
    // callback rtc user statistics
    RTC_EVENT_STAT_UPDATE               (11, "RTC_EVENT_STAT_UPDATE");

    private static final String TAG = LangRtcEvent.class.getSimpleName();

    private int value;
    private String name;

    LangRtcEvent(int value, String name) {
        this.value = value;
        this.name = name;
    }

    @Override
    public void print() {
        DebugLog.i(TAG, "current enum value = " + value + " name = " + name);
    }

    public int getValue() {
        return value;
    }

    @Override
    public String getInfo() {
        return name;
    }
}
