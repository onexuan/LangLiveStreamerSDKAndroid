package net.lang.streamer2.rtc;

import android.content.Context;

import net.lang.streamer2.rtc.agora.AgoraRTCSessionProxy;

public class RTCControllerFactory {
    public enum RtcImplType{
        kAgora,
        kZeego
    }
    public static IRTCSessionController create(Context context, RTCControllerFactory.RtcImplType rtcImplType) {
        if (rtcImplType == RtcImplType.kAgora) {
            return new AgoraRTCSessionProxy(context);
        } else {
            throw new RuntimeException("other rtc vendor is not supported now!");
        }
    }
}
