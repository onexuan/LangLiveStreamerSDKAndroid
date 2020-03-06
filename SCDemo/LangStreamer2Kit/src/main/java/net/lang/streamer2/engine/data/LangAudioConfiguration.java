package net.lang.streamer2.engine.data;

public final class LangAudioConfiguration {
    private String mCodecInfo = "audio/mp4a-latm";
    private int mSampleRate;
    private int mChannel;
    private int mBitrateBps;
    private AudioEncoderType mEncoderType = AudioEncoderType.kHardware;

    public LangAudioConfiguration(int sampleRate, int channel, int bitrateBps) {
        mSampleRate = sampleRate;
        mChannel = channel;
        mBitrateBps = bitrateBps;
    }

    public LangAudioConfiguration(int sampleRate, int channel, int bitrateBps, AudioEncoderType encoderType) {
        mEncoderType = encoderType;
        mSampleRate = sampleRate;
        mChannel = channel;
        mBitrateBps = bitrateBps;
    }

    public int getSampleRate() {
        return mSampleRate;
    }

    public int getChannel() {
        return mChannel;
    }

    public int getBitrateBps() {
        return mBitrateBps;
    }

    public AudioEncoderType getEncoderType() {
        return mEncoderType;
    }

    public String getCodecInfo() {
        return mCodecInfo;
    }

    public LangAudioConfiguration dup() {
        return new LangAudioConfiguration(getSampleRate(), getChannel(), getBitrateBps(), getEncoderType());
    }

    public enum AudioEncoderType{
        kHardware,
        kSoftware
    }
}
