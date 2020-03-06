package net.lang.streamer.rtc;

import android.view.SurfaceView;
import android.opengl.EGLContext;

import com.yunfan.graphicbuffer.GraphicBufferWrapper;

import net.lang.gpuimage.filter.base.gpuimage.GPUImageFilter;

import net.lang.streamer.config.LangRtcConfig;
import net.lang.streamer.video.LangTextureMovieEncoder;

public abstract class IRtcSessionManager {

    public abstract SurfaceView createRtcRenderView();

    public abstract void setDisplayParamsWhenStreaming(LangRtcConfig.RtcDisplayParams displayParams);

    public abstract boolean joinChannel(LangRtcConfig config, SurfaceView localView);

    public abstract void leaveChannel();

    public abstract boolean setVoiceChat(boolean voiceOnly);

    public abstract boolean muteLocalVoice(boolean mute);

    public abstract boolean muteRemoteVoice(final int uid, boolean mute);

    public abstract boolean setupRemoteUser(final int uid, SurfaceView remoteView);

    public abstract boolean pushExternalVideoFrame(EGLContext eglContext, int textureId, int stride, int height, long timestamp);

    public abstract boolean pushExternalVideoFrame(GPUImageFilter filter, int texId, long timestamp);

    public abstract boolean mixLocalWithRemoteVideoFrame(GPUImageFilter filter, int texId, GraphicBufferWrapper gb, LangTextureMovieEncoder encoder, long timestamp);

    public abstract void changeRtmpStatus(boolean isStreaming);

    public abstract void release();
}