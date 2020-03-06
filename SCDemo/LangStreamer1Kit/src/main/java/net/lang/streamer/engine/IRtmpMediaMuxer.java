package net.lang.streamer.engine;

import android.media.MediaCodec;
import android.media.MediaFormat;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by lang on 2018/2/7.
 */

public abstract class IRtmpMediaMuxer implements IMediaMuxer {
    public abstract int addTrack(MediaFormat format);
    public abstract void start(final String url);
    public abstract void writeSampleData(int trackIndex, ByteBuffer byteBuf, MediaCodec.BufferInfo bufferInfo);
    public abstract void stop();
    public abstract void release();

    public abstract void setVideoResolution(int width, int height);
    public abstract AtomicInteger getAudioFrameCacheNumber();
    public abstract AtomicInteger getVideoFrameCacheNumber();
    public abstract int getPushVideoFrameCounts();
    public abstract double getPushVideoFps();
}
