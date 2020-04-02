package net.lang.streamer2.config;

public final class LangBeautyhairConfig {
    private float mSaturation;
    private int mStartColor;
    private int mEndColor;
    private boolean mEnable = true;

    public LangBeautyhairConfig(LangBeautyhairConfig beautyHairConfig) {
        this(beautyHairConfig.getSaturation(), beautyHairConfig.getStartColor(), beautyHairConfig.getEndColor());
    }

    public LangBeautyhairConfig(float saturation) {
        this(saturation, 0, 0);
    }

    public LangBeautyhairConfig(float saturation, int startColor, int endColor) {
        mSaturation = saturation;
        mStartColor = startColor;
        mEndColor = endColor;
    }

    public void setSaturation(float saturation) {
        mSaturation = saturation;
    }

    public float getSaturation() {
        return mSaturation;
    }

    public void setStartColor(int startColor) {
        mStartColor = startColor;
    }

    public int getStartColor() {
        return mStartColor;
    }

    public void setEndColor(int endColor) {
        mEndColor = endColor;
    }

    public int getEndColor() {
        return mEndColor;
    }

    public void setEnable(boolean enable) {
        mEnable = enable;
    }

    public boolean enable() {
        return mEnable;
    }
}
