package net.lang.streamer2.rtc;

import com.yunfan.graphicbuffer.GraphicBufferWrapper;

import net.lang.streamer2.LangRtcInfo;
import net.lang.streamer2.LangRtcUser;
import net.lang.streamer2.engine.data.LangRtcEvent;

public interface IRTCSessionListener {

    // callback rtc audio captured audio data
    void onRtcMixedPcmFrame(byte[] pcmData, int pcmDataLength, long timestampNs);
    // callback rtc video local mixed with remote user output data
    void onRtcMixedRGBAFrame(GraphicBufferWrapper gb, long timestampNs);
    // callback rtc connection status
    void onRtcStatusChanged(LangRtcEvent rtcEvent, int uid);
    // callback rtc engine event with message
    void onRtcReceivedNotification(LangRtcEvent rtcEvent, LangRtcUser rtcUser);
    // callback rtc statistics debug information
    void onRtcStatistics(LangRtcInfo rtcInfo);
    // callback rtc warning code
    void onRtcWarning(int warningCode);
    // callback rtc error code
    void onRtcError(int errorCode, String description);
}
