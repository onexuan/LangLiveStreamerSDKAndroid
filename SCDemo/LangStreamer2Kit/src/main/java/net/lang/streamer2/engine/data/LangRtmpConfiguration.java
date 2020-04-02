package net.lang.streamer2.engine.data;

public final class LangRtmpConfiguration {
    private LangAudioConfiguration mAudioConfiguration;
    private LangVideoConfiguration mVideoConfiguration;
    private boolean mEnableAudio;
    private boolean mEnableVideo;
    private String mUrl;
    private int mRetryTimesCount = 6;
    private int mRetryTimesIntervalSec = 10;

    public LangRtmpConfiguration(LangAudioConfiguration audioConfiguration, LangVideoConfiguration videoConfiguration) {
        if (audioConfiguration != null) {
            mAudioConfiguration = audioConfiguration.dup();
            setEnableAudio(true);
        }
        if (videoConfiguration != null) {
            mVideoConfiguration = videoConfiguration.dup();
            setEnableVideo(true);
        }
    }

    public final LangAudioConfiguration getAudioConfiguration() {
        return mAudioConfiguration;
    }

    public final LangVideoConfiguration getVideoConfiguration() {
        return mVideoConfiguration;
    }

    public final void setEnableAudio(boolean enableAudio) {
        mEnableAudio = enableAudio;
    }

    public final boolean isEnableAudio() {
        return mEnableAudio;
    }

    public final void setEnableVideo(boolean enableVideo) {
        mEnableVideo = enableVideo;
    }

    public final boolean isEnableVideo() {
        return mEnableVideo;
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
