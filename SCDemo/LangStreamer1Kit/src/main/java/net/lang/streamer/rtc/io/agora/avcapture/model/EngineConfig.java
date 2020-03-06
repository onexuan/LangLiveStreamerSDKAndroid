package net.lang.streamer.rtc.io.agora.avcapture.model;

/**
 * Created by lang on 2017/9/1.
 */

public class EngineConfig {
    public int mVideoProfile;

    public int mClientRole;

    public int mUid;

    public String mChannel;

    public void reset() {
        mChannel = null;
    }

    EngineConfig() {
    }
}
