package net.lang.streamer2.engine.data;

import net.lang.streamer2.utils.DebugLog;

public enum LangRtmpBufferStatus implements IStatusObject {
    // unknown
    LANG_RTMP_BUFFER_STATUS_UNKONOW   (0, "LANG_RTMP_BUFFER_STATUS_UNKONOW"),
    // should decrease bitrate when buffer status bad.
    LANG_RTMP_BUFFER_STATUS_INCREASE  (1, "LANG_RTMP_BUFFER_STATUS_INCREASE"),
    // should increase bitrate when buffer status good.
    LANG_RTMP_BUFFER_STATUS_DECLINE   (2, "LANG_RTMP_BUFFER_STATUS_DECLINE");

    private static final String TAG = LangRtmpBufferStatus.class.getSimpleName();

    private int value;
    private String name;

    LangRtmpBufferStatus(int value, String name) {
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
