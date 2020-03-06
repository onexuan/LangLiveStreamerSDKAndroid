package net.lang.rtclib;

import java.nio.ByteBuffer;

public class MediaPreProcessingNative {
    static {
        System.loadLibrary("apm-rtc-media-preprocessing");
    }

    public static native void setCallback(IRTCFrameListener listener);

    public static native void setVideoCaptureByteBuffer(ByteBuffer byteBuffer);

    public static native void setAudioRecordByteBuffer(ByteBuffer byteBuffer);

    public static native void setAudioPlayByteBuffer(ByteBuffer byteBuffer);

    public static native void setBeforeAudioMixByteBuffer(ByteBuffer byteBuffer);

    public static native void setAudioMixByteBuffer(ByteBuffer byteBuffer);

    public static native void setVideoDecodeByteBuffer(int uid, ByteBuffer byteBuffer);

    public static native void releasePoint();
}
