package net.lang.streamer2.engine.data;

import net.lang.streamer2.utils.DebugLog;

public enum LangAnimationStatus implements IStatusObject {
    // animation decoding process
    LANG_ANIMATION_STATUS_DECODING  (0, "LANG_ANIMATION_STATUS_DECODING"),
    // animation decoding complete.
    LANG_ANIMATION_STATUS_READY     (1, "LANG_ANIMATION_STATUS_READY"),
    // animation decoding error.
    LANG_ANIMATION_STATUS_ERROR     (2, "LANG_ANIMATION_STATUS_ERROR"),
    // animation in playing progress
    LANG_ANIMATION_STATUS_PLAY      (3, "LANG_ANIMATION_STATUS_PLAY"),
    // animation play complete.
    LANG_ANIMATION_STATUS_COMPLETE  (4, "LANG_ANIMATION_STATUS_COMPLETE");

    private static final String TAG = LangAnimationStatus.class.getSimpleName();

    private int value;
    private String name;

    LangAnimationStatus(int value, String name) {
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
