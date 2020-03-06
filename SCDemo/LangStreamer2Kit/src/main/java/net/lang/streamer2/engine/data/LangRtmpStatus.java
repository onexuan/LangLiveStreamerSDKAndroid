package net.lang.streamer2.engine.data;

import net.lang.streamer2.utils.DebugLog;

public enum LangRtmpStatus implements IStatusObject {
    // prepare
    LANG_RTMP_STATUS_READY     (1, "LANG_RTMP_STATUS_READY"),
    // connecting
    LANG_RTMP_STATUS_PENDING   (2, "LANG_RTMP_STATUS_PENDING"),
    // connected
    LANG_RTMP_STATUS_START     (3, "LANG_RTMP_STATUS_START"),
    // disconnected
    LANG_RTMP_STATUS_STOP      (4, "LANG_RTMP_STATUS_STOP"),
    // connecting error.
    LANG_RTMP_STATUS_ERROR     (5, "LANG_RTMP_STATUS_ERROR"),
    // refreshing
    LANG_RTMP_STATUS_REFRESH   (6, "LANG_RTMP_STATUS_REFRESH");

    private static final String TAG = LangRtmpStatus.class.getSimpleName();

    private int value;
    private String name;

    LangRtmpStatus(int value, String name) {
        this.value = value;
        this.name = name;
    }

    @Override
    public void print() {
        DebugLog.i(TAG, "current enum value = " + value + " name = " + name);
    }

    @Override
    public int getValue() {
        return this.value;
    }

    @Override
    public String getInfo() {
        return this.name;
    }

}
