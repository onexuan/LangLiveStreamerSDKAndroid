package net.lang.streamer2.engine.publish;

import android.media.MediaCodec;
import android.media.MediaFormat;

import java.nio.ByteBuffer;

public interface IMediaWriter {
    int addTrack(MediaFormat format);
    void start(final String url);
    void writeSampleData(int trackIndex, ByteBuffer byteBuf, MediaCodec.BufferInfo bufferInfo);
    void stop();
    void release();
}
