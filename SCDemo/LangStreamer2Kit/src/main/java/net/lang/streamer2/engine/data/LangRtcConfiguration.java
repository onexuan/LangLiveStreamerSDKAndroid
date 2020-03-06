package net.lang.streamer2.engine.data;

import net.lang.streamer2.config.LangRtcConfig;

public final class LangRtcConfiguration {
    private int audioSampleRate = 0;
    private int audioChannel = 0;
    private int videoWidth = 0;      //live trans-coding width
    private int videoHeight = 0;     //live trans-coding height
    private int videoBitrateBps = 0;    //live trans-coding bitrate
    private int videoFPS = 0;        //live trans-coding fps
    private int rtcVideoProfile = 0;
    private String pushStreamUrl;    //live trans-coding rtmp url
    private String channelName;
    private String encryptionKey;
    private String encryptionMode;
    private String rtcAppId;
    private boolean localMixed;

    public LangRtcConfiguration(LangAudioConfiguration audioConfiguration, LangVideoConfiguration videoConfiguration, LangRtcConfig rtcConfig) {

        audioSampleRate = audioConfiguration.getSampleRate();
        audioChannel = audioConfiguration.getChannel();
        if (videoConfiguration.getLandscape()) {
            videoWidth = videoConfiguration.getWidth();
            videoHeight = videoConfiguration.getHeight();
        } else {
            videoWidth = videoConfiguration.getHeight();
            videoHeight = videoConfiguration.getWidth();
        }
        videoBitrateBps = videoConfiguration.getBitrateBps();
        videoFPS = videoConfiguration.getFps();

        rtcVideoProfile = rtcConfig.rtcVideoProfile;
        channelName = rtcConfig.channelName;
        encryptionKey = rtcConfig.encryptionKey;
        encryptionMode = rtcConfig.encryptionMode;
        rtcAppId = LangRtcConfig.rtcAppId;
        localMixed = rtcConfig.localMixed;
    }

    public LangRtcConfiguration(LangRtmpConfiguration rtmpConfiguration, LangRtcConfig rtcConfig) {
        this(rtmpConfiguration.getAudioConfiguration(), rtmpConfiguration.getVideoConfiguration(), rtcConfig);
        this.pushStreamUrl = rtmpConfiguration.getUrl();
    }

    public void setPushStreamUrl(final String pushStreamUrl) {
        this.pushStreamUrl = pushStreamUrl;
    }

    public int getRtcVideoProfile() {
        return rtcVideoProfile;
    }

    public int getAudioSampleRate() {
        return audioSampleRate;
    }

    public int getAudioChannel() {
        return audioChannel;
    }

    public int getVideoWidth() {
        return videoWidth;
    }

    public int getVideoHeight() {
        return videoHeight;
    }

    public int getVideoBitrateBps() {
        return videoBitrateBps;
    }

    public int getVideoFPS() {
        return videoFPS;
    }

    public String getPushStreamUrl() {
        return pushStreamUrl;
    }

    public String getChannelName() {
        return channelName;
    }

    public String getEncryptionKey() {
        return encryptionKey;
    }

    public String getEncryptionMode() {
        return encryptionMode;
    }

    public String getRtcAppId() {
        return rtcAppId;
    }

    public boolean isLocalMixed() {
        return localMixed;
    }
}
