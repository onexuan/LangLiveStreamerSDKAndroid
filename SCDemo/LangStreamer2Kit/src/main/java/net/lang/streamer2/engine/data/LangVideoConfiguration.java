package net.lang.streamer2.engine.data;

public final class LangVideoConfiguration {
    private String mCodecInfo = "video/avc";
    private int mWidth;
    private int mHeight;
    private int mFps;
    private int mBitrateBps;
    private int mKeyFrameIntervalSec;
    private VideoEncoderType mEncoderType = VideoEncoderType.kHardware;
    private boolean mLandscape = false;

    public LangVideoConfiguration(int width, int height, int fps, int bitrateBps, int keyFrameIntervalSec) {
        if (width < height) {
            throw new IllegalArgumentException("input width must be larger than height" + " width=" + width + " height=" + height);
        }
        mWidth = width;
        mHeight = height;
        mFps = fps;
        mBitrateBps = bitrateBps;
        mKeyFrameIntervalSec = keyFrameIntervalSec;
    }

    public LangVideoConfiguration(int width, int height, int fps, int bitrateBps, int keyFrameIntervalSec, VideoEncoderType encoderType) {
        if (width < height) {
            throw new IllegalArgumentException("input width must be larger than height" + " width=" + width + " height=" + height);
        }
        mWidth = width;
        mHeight = height;
        mFps = fps;
        mBitrateBps = bitrateBps;
        mKeyFrameIntervalSec = keyFrameIntervalSec;
        mEncoderType = encoderType;
    }

    public LangVideoConfiguration(int width, int height, int fps, int bitrateBps, int keyFrameIntervalSec, VideoEncoderType encoderType, boolean landscape) {
        if (width < height) {
            throw new IllegalArgumentException("input width must be larger than height" + " width=" + width + " height=" + height);
        }
        mWidth = width;
        mHeight = height;
        mFps = fps;
        mBitrateBps = bitrateBps;
        mKeyFrameIntervalSec = keyFrameIntervalSec;
        mEncoderType = encoderType;
        mLandscape = landscape;
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    public int getFps() {
        return mFps;
    }

    public int getBitrateBps() {
        return mBitrateBps;
    }

    public int getKeyFrameIntervalSec() {
        return mKeyFrameIntervalSec;
    }

    public VideoEncoderType getEncoderType() {
        return mEncoderType;
    }

    public String getCodecInfo() {
        return mCodecInfo;
    }

    public boolean getLandscape() {
        return mLandscape;
    }

    public LangVideoConfiguration dup() {
        return new LangVideoConfiguration(getWidth(), getHeight(), getFps(), getBitrateBps(), getKeyFrameIntervalSec(), getEncoderType(), getLandscape());
    }

    public enum VideoEncoderType{
        kHardwareSurface,
        kHardware,
        kSoftwareX264,
        kSoftwareOpenH264
    }
}
