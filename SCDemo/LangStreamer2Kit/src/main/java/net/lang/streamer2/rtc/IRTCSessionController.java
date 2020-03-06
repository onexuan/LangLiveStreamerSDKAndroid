package net.lang.streamer2.rtc;

import android.view.SurfaceView;

import net.lang.streamer2.config.LangRtcConfig;
import net.lang.streamer2.engine.data.LangRtcConfiguration;

public abstract class IRTCSessionController {
    protected LangRtcConfig.RtcDisplayParams mRtcDisplayParams;
    protected IRTCSessionListener mListener;
    public final void setListener(IRTCSessionListener listener) {
        this.mListener = listener;
    }
    public void setDisplayParamsWhenStreaming(LangRtcConfig.RtcDisplayParams displayParams) {
        mRtcDisplayParams = displayParams.dup();
    }
    public abstract void initialize();
    public void onExtraInitialize() {
    }
    public abstract boolean joinChannel(LangRtcConfiguration config, SurfaceView localView);
    public abstract boolean setVoiceChat(boolean voiceOnly);
    public abstract boolean muteLocalVoice(boolean mute);
    public abstract void leaveChannel();
    public abstract SurfaceView createRtcRenderView();
    public abstract boolean setupRemoteUser(final int uid, SurfaceView remoteView);
    public abstract boolean muteRemoteVoice(final int uid, boolean mute);
    public abstract boolean pushVideoFrame(int srcTextureId, long timestamp);
    public abstract void destroy();
    public void onExtraDestroy() {
    }
}
