package net.lang.streamer.engine.data;

public class LangEngineParams {

    // Note: the stride of resolution must be set as 16x for hard encoding with some chip like MTK
    public static int vOutputWidth = 960;         // set by user.
    // Since Y component is quadruple size as U and V component, the stride must be set as 32x
    public static int vOutputHeight = 544;        // set by user.

    public static int vOutputFps = 24;            // set by user.

    public static int vOutKeyFrameIntervalSec = 2;// set by user for avc key frame interval(ins secs)

    public static int vOutputBitrateKbps = 1200;  // set by user.(video encoded bitrate in kbps)

    public static int cameraPreveiwWidth = 1080;  // set by user and may changed by actual camera preview size
    public static int cameraPreviewHeight = 1920; // set by user and may changed by actual camera preview size
    public static int cameraFps = 30;

    public static int aSamplerate = 44100;        // audio record sample rate in Hz (44100/48000)

    public static int aOutputBitrateKbps = 96;    // audio encoded bitrate in kbps. (64/96/128/192)

    public static int aChannel = 1;

    public static boolean enableGraphicBuffer = false;
}