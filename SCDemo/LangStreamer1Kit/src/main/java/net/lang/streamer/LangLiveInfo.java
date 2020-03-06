package net.lang.streamer;

/**
 * Created by lichao on 17-5-26.
 */
public class LangLiveInfo {
    public int cameraPresent;  //当前camera 位置， 0前置（默认），1（后置）
    public int connectStatus;  //连接状态 0 未连接 ，1连接中， 2，连接成功
    public int uploadSpeed;//当前上传速度，单位 Byte
    public int localBufferAudioCount; //本地缓存audio帧数
    public int localBufferVideoCount; //本地缓存video帧数
    public int videoEncodeFrameCountPerSecond; //　视频编码速度
    public int videoPushFrameCountPerSecond; //推流速度
    public int videoDropFrameCountPerSencond; // 丢帧速度

    public int encodeVideoFrameCount;//总编码video帧数
    public int pushVideoFrameCount;//总推流video帧数
    public int videoDiscardFrameCount;//video丢帧数
    public int previewFrameCountPerSecond;        //camera实时预览帧率
}
