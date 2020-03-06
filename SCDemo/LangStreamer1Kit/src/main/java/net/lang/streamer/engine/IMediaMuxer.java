package net.lang.streamer.engine;

import android.media.MediaCodec;
import android.media.MediaFormat;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by lang on 2018/2/2.
 */

public interface IMediaMuxer {
    int addTrack(MediaFormat format);
    void start(final String url);
    void writeSampleData(int trackIndex, ByteBuffer byteBuf, MediaCodec.BufferInfo bufferInfo);
    void stop();
    void release();
}
