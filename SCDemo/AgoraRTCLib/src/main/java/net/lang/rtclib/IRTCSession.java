package net.lang.rtclib;

import android.view.SurfaceView;

public abstract class IRTCSession {
    private int mUid;
    private int mWidth;
    private int mHeight;
    private int mRotation;
    private int mBitrate;
    private int mFps;
    private int mVolume;
    private boolean mAudioMuted;
    private boolean mVideoMuted;

    public final int uid() {
        return mUid;
    }

    public final void setUid(int uid) {
        mUid = uid;
    }

    public final int width() {
        return mWidth;
    }

    public final void setWidth(int width) {
        mWidth = width;
    }

    public final int height() {
        return mHeight;
    }

    public final void setHeight(int height) {
        mHeight = height;
    }

    public final int rotation() {
        return mRotation;
    }

    public final void setRotation(int rotation) {
        mRotation = rotation;
    }

    public final int bitrate() {
        return mBitrate;
    }

    public final void setBitrate(int bitrate) {
        mBitrate = bitrate;
    }

    public final int fps() {
        return mFps;
    }

    public final void setFps(int fps) {
        mFps = fps;
    }

    public final int volume() {
        return mVolume;
    }

    public final void setVolume(int volume) {
        mVolume = volume;
    }

    public final boolean audioMuted() {
        return mAudioMuted;
    }

    public final void setAudioMuted(boolean audioMuted) {
        mAudioMuted = audioMuted;
    }

    public final boolean videoMuted() {
        return mVideoMuted;
    }

    public final void setVideoMuted(boolean videoMuted) {
        mVideoMuted = videoMuted;
    }
}
