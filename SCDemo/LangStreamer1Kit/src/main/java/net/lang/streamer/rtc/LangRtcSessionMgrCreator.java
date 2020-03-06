package net.lang.streamer.rtc;

import android.content.Context;

import net.lang.streamer.engine.LangMagicEngine;
import net.lang.streamer.rtc.io.agora.AgoraRtcSessionManager;

/**
 * Created by lang on 2017/9/5.
 */

public class LangRtcSessionMgrCreator {

    public static IRtcSessionManager createRtcSessionManager(LangRtcMessageHandler.SnailRtcListener listener, Context context, LangMagicEngine engine) {
        return new AgoraRtcSessionManager(new LangRtcMessageHandler(listener), context, engine);
    }
}
