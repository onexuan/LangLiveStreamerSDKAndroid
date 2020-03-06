package net.lang.rtclib;

public interface IRTCEventListener {
    void onFirstRemoteVideoFrame(int uid, int width, int height, int elapsed);
    void onJoinChannelSuccess(String channel, int uid, int elapsed);
    void onRejoinChannelSuccess(String channel, int uid, int elapsed);
    void onLeaveChannel();
    void onUserJoined(int uid, int elapsed);
    void onUserOffline(int uid, int reason);
    void onExtraCallback(int type, Object... data);

    int EVENT_TYPE_ON_DATA_CHANNEL_MSG = 3;
    int EVENT_TYPE_ON_VIDEO_SIZE_CHANGED = 5;
    int EVENT_TYPE_ON_USER_AUDIO_MUTED = 6;
    int EVENT_TYPE_ON_USER_VIDEO_MUTED = 7;
    int EVENT_TYPE_ON_SPEAKER_STATS = 8;
    int EVENT_TYPE_ON_RTC_STATS = 9;
    int EVENT_TYPE_ON_AGORA_MEDIA_WARNING = 10;
    int EVENT_TYPE_ON_AGORA_MEDIA_ERROR = 11;
    int EVENT_TYPE_ON_USER_VIDEO_STATS = 12;
    int EVENT_TYPE_ON_APP_ERROR = 13;
    int EVENT_TYPE_ON_AUDIO_ROUTE_CHANGED = 18;

    int ERROR_NO_NETWORK_CONNECTION = 3;
    int ERROR_NETWORK_CONNECTION_LOST = 4;
    int ERROR_NETWORK_CONNECTION_TIMEOUT = 5;
}
