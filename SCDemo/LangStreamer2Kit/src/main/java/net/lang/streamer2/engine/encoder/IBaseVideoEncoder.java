package net.lang.streamer2.engine.encoder;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;

import net.lang.streamer2.engine.data.LangVideoConfiguration;

import java.nio.ByteBuffer;

public abstract class IBaseVideoEncoder implements IMediaEncoder {

    // video configurations
    private String mCodecInfo = "video/avc";
    private int mWidth;
    private int mHeight;
    private int mFps;
    private int mBitrateBps;
    private int mKeyFrameIntervalSec;
    private int mVideoColorFormat; // runtime check.

    IVideoEncoderListener mEncodeListener = null;

    public IBaseVideoEncoder(LangVideoConfiguration videoConfiguration) {
        this.mCodecInfo = videoConfiguration.getCodecInfo();
        if (videoConfiguration.getLandscape()) {
            this.mWidth = videoConfiguration.getWidth();
            this.mHeight = videoConfiguration.getHeight();
        } else {
            this.mHeight = videoConfiguration.getWidth();
            this.mWidth = videoConfiguration.getHeight();
        }
        this.mFps = videoConfiguration.getFps();
        this.mBitrateBps = videoConfiguration.getBitrateBps();
        this.mKeyFrameIntervalSec = videoConfiguration.getKeyFrameIntervalSec();
        this.mVideoColorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar;
    }

    public IBaseVideoEncoder(int width, int height, int fps, int bitrateBps, int keyFrameIntervalSec) {
        this.mWidth = width;
        this.mHeight = height;
        this.mFps = fps;
        this.mBitrateBps = bitrateBps;
        this.mKeyFrameIntervalSec = keyFrameIntervalSec;
        this.mVideoColorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar;
    }

    public final void setEncodeListener(IVideoEncoderListener listener) {
        this.mEncodeListener = listener;
    }

    public abstract boolean autoBitrate(boolean enable);

    public final String getCodecInfo() {
        return mCodecInfo;
    }

    public final int getWidth() {
        return mWidth;
    }

    public final int getHeight() {
        return mHeight;
    }

    public final int getFps() {
        return mFps;
    }

    public final int getBitrate() {
        return mBitrateBps;
    }

    public boolean setBitrate(int bitrateBps) {
        mBitrateBps = bitrateBps;
        return true;
    }

    public final int getEncoderKeyFrameInterval() {
        return mKeyFrameIntervalSec;
    }

    public final void setEncoderFormat(int format) {
        mVideoColorFormat = format;
    }

    public final int getEncoderFormat() {
        return mVideoColorFormat;
    }

    public interface IVideoEncoderListener {
        void onEncodedAnnexbFrame(ByteBuffer es, MediaCodec.BufferInfo bi);
        //void onEncodeStats(float encodeFps, float encodeBitrateBps);
    }
}
