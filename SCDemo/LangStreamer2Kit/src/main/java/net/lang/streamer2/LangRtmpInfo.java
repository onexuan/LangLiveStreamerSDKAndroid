package net.lang.streamer2;

/**
 * Created by lichao on 17-5-26.
 */
public class LangRtmpInfo {
    public int uploadSpeed;//当前上传速度，单位 Byte
    public int localBufferAudioCount; //本地缓存audio帧数
    public int localBufferVideoCount; //本地缓存video帧数
    public int videoEncodeFrameCountPerSecond; //　视频编码速度
    public int videoDropFrameCountPerSencond; // 视频编码丢帧速度
    public int videoPushFrameCountPerSecond; //推流速度

    public int totalPushAudioFrameCount; //总推流audio帧数
    public int totalPushVideoFrameCount; //总推流video帧数
    public int totalDiscardFrameCount; //总video丢帧数
    public int previewFrameCountPerSecond; //camera实时预览帧率
}
