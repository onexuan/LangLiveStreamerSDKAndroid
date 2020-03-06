package net.lang.streamer2.engine.encoder;

import net.lang.streamer2.engine.data.LangVideoConfiguration;

public class LangVideoEncoderFactory {

    public static IBaseVideoEncoder create(LangVideoConfiguration videoConfiguration)
            throws EncoderRuntimeException {
        if (videoConfiguration.getEncoderType() == LangVideoConfiguration.VideoEncoderType.kHardwareSurface) {
            return new LangVideoPipelineEncoder(videoConfiguration);
        } else if (videoConfiguration.getEncoderType() == LangVideoConfiguration.VideoEncoderType.kHardware) {
            return new LangVideoHardwareEncoder(videoConfiguration);
        } else {
            throw new RuntimeException("other type of video encoder is not supported now");
        }
    }
}
