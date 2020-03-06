package net.lang.streamer2.engine.encoder;

import android.media.MediaCodec;

import net.lang.streamer2.engine.data.LangAudioConfiguration;

import java.nio.ByteBuffer;

public abstract class IBaseAudioEncoder implements IMediaEncoder {

    // audio configuration.
    private String mCodecInfo = "audio/mp4a-latm";
    private int mSampleRate;
    private int mChannel;
    private int mBitrateBps;
    private boolean mMute = false;

    IAudioEncoderListener mEncodeListener = null;

    public IBaseAudioEncoder(int sampleRate, int channel, int bitrateBps) {
        this.mSampleRate = sampleRate;
        this.mChannel = channel;
        this.mBitrateBps = bitrateBps;
    }

    public IBaseAudioEncoder(LangAudioConfiguration audioConfiguration) {
        this.mCodecInfo = audioConfiguration.getCodecInfo();
        this.mSampleRate = audioConfiguration.getSampleRate();
        this.mChannel = audioConfiguration.getChannel();
        this.mBitrateBps = audioConfiguration.getBitrateBps();
    }

    public final void setEncodeListener(IAudioEncoderListener listener) {
        this.mEncodeListener = listener;
    }

    public abstract boolean autoBitrate(boolean enable);

    public final String getCodecInfo() {
        return mCodecInfo;
    }

    public final int getSampleRate() {
        return mSampleRate;
    }

    public final int getChannel() {
        return mChannel;
    }

    public abstract boolean setBitrate(int bitrateBps);

    public final int getBitrate() {
        return mBitrateBps;
    }

    public void setSilence(boolean mute) {
        mMute = mute;
    }

    public final boolean getSilence() {
        return mMute;
    }

    public interface IAudioEncoderListener {
        void onEncodedAacFrame(ByteBuffer es, MediaCodec.BufferInfo bi);
    }
}
