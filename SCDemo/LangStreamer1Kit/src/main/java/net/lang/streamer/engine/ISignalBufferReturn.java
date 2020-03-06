package net.lang.streamer.engine;

/**
 * Created by lichao on 17-6-7.
 */

public interface ISignalBufferReturn {
    void release(LangVideoEncoderImpl.EncoderBuffer buffer);
}
