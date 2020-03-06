package net.lang.streamer2.engine.data;

public final class LangRtmpConfiguration {
    private LangAudioConfiguration mAudioConfiguration;
    private LangVideoConfiguration mVideoConfiguration;
    private String mUrl;
    private int mRetryTimesCount = 6;
    private int mRetryTimesIntervalSec = 10;

    public LangRtmpConfiguration(LangAudioConfiguration audioConfiguration, LangVideoConfiguration videoConfiguration) {
        mAudioConfiguration = audioConfiguration.dup();
        mVideoConfiguration = videoConfiguration.dup();
    }

    public final LangAudioConfiguration getAudioConfiguration() {
        return mAudioConfiguration;
    }

    public final LangVideoConfiguration getVideoConfiguration() {
        return mVideoConfiguration;
    }

    public final void setUrl(String url) {
        mUrl = url;
    }

    public final String getUrl() {
        return mUrl;
    }

    public final void setRetryTimesCount(int retryTimesCount) {
        mRetryTimesCount = retryTimesCount;
    }

    public final int getRetryTimesCount() {
        return mRetryTimesCount;
    }

    public final void setRetryTimesIntervalSec(int retryTimesIntervalSec) {
        mRetryTimesIntervalSec = retryTimesIntervalSec;
    }

    public final int getRetryTimesIntervalSec() {
        return mRetryTimesIntervalSec;
    }
}
