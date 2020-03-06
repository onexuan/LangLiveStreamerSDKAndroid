package net.lang.streamer2.engine.encoder;

import net.lang.streamer2.engine.data.LangAudioConfiguration;

public class LangAudioEncoderFactory {

    public static IBaseAudioEncoder create(LangAudioConfiguration audioConfiguration) {
        if (audioConfiguration.getEncoderType() == LangAudioConfiguration.AudioEncoderType.kHardware) {
            return new LangAudioHardwareEncoder(audioConfiguration);
        } else {
            throw new RuntimeException("software audio encoder is not supported now");
        }
    }
}
