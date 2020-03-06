package net.lang.streamer2.engine.data;

public final class LangFrameStatistics {
    // 距离上次统计的时间(单位ms)
    public long elapsedMs;
    // 当前的时间戳，从而计算1s内的数据
    public long timestampMs;
    // 总流量
    public float dataFlow;
    // 1s内总带宽
    public float bandWidth;
    // 上次的带宽
    public float currentBandwidth;

    // 丢掉的帧数(video丢帧数)
    public int dropFrames;
    // audio总帧数
    public int totalAudioFrames;
    // video总帧数
    public int totalVideoFrames;

    // 1s内音频捕获个数
    public int capturedAudioCount;
    // 1s内视频捕获个数
    public int capturedVideoCount;
    // 上次的音频捕获个数
    public int currentCapturedAudioCount;
    // 上次的视频捕获个数
    public int currentCapturedVideoCount;

    // 未发送个数 (代表当前缓冲区等待发送的)
    public int unSendAudioCount;
    public int unSendVideoCount;

    public LangFrameStatistics() {

    }
}
