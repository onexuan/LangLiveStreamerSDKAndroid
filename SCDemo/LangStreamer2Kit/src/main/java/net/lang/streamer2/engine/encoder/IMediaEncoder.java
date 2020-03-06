package net.lang.streamer2.engine.encoder;

import net.lang.streamer2.engine.data.LangMediaBuffer;

public interface IMediaEncoder {
    boolean start();
    void stop();
    void encodeFrame(byte[] data, int dataLength, long presentationTimeUs);
    void encodeFrame(LangMediaBuffer mediaBuffer);
}
