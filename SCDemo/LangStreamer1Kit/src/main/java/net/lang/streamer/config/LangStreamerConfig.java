package net.lang.streamer.config;
import net.lang.streamer.ILangCameraStreamer.LangVideoResolution;

/**
 * Created by lichao on 17-5-2.
 */

public class LangStreamerConfig {
    public LangVideoResolution videoResolution;
    public int videoFPS;
    public int videoMaxKeyframeInterval;
    public int videoBitrate;
    // audio record sample rate in Hz (44100/48000)
    public int audioSampleRate;
    // audio encoded bitrate in bps. (64000/96000/128000/1920000)
    public int audioBitrate;
    public int audioChannel;
}
