package net.lang.streamer2.config;

import net.lang.streamer2.ILangCameraStreamer.LangVideoResolution;
import net.lang.streamer2.ILangCameraStreamer.LangVideoEncoderType;
import net.lang.streamer2.ILangCameraStreamer.LangAudioEncoderType;

/**
 * Created by lichao on 17-5-2.
 */

public class LangStreamerConfig {
    public LangVideoResolution videoResolution;
    public int videoFPS;
    public int videoMaxKeyframeInterval;
    public int videoBitrate;
    public LangVideoEncoderType videoEncoderType = LangVideoEncoderType.LANG_VIDEO_ENCODER_DEFAULT;
    // audio record sample rate in Hz (44100/48000)
    public int audioSampleRate;
    // audio encoded bitrate in bps. (64000/96000/128000/1920000)
    public int audioBitrate;
    public int audioChannel;
    public LangAudioEncoderType audioEncoderType = LangAudioEncoderType.LANG_AUDIO_ENCODER_DEFAULT;
}
