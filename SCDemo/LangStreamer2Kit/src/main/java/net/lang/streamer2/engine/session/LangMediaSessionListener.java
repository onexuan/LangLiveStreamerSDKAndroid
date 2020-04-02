package net.lang.streamer2.engine.session;

import net.lang.streamer2.LangRtcInfo;
import net.lang.streamer2.LangRtcUser;
import net.lang.streamer2.engine.data.LangAnimationStatus;
import net.lang.streamer2.engine.data.LangFrameStatistics;
import net.lang.streamer2.engine.data.LangRtcEvent;
import net.lang.streamer2.engine.data.LangRtmpBufferStatus;
import net.lang.streamer2.engine.data.LangRtmpStatus;
import net.lang.streamer2.faceu.IFaceuListener;

public interface LangMediaSessionListener {

    void onSessionRtmpStatusChange(LangRtmpStatus rtmpStatus);

    void onSessionRtmpStatisticsUpdate(LangFrameStatistics frameStatistics);

    void onSessionRtmpSocketBufferChange(LangRtmpBufferStatus rtmpBufferStatus);

    void onSessionFaceTrackerFaceUpdate(int faceCount);

    void onSessionFaceTrackerHandUpdate(int handCount, IFaceuListener.FaceuGestureType gesture);

    void onSessionRtcStatusChange(LangRtcEvent rtcEvent, int uid);

    void onSessionRtcReceivedNotification(LangRtcEvent rtcEvent, LangRtcUser rtcUser);

    void onSessionRtcStatisticsUpdate(LangRtcInfo rtcInfo);

    void onSessionRtcWarning(int warningCode);

    void onSessionRtcError(int errorCode, String description);

    void onSessionRecordChange(int started);

    void onSessionRecordProgressUpdate(long milliSeconds);

    void onSessionRecordError(int errorCode, String description);

    void onSessionAnimationStatusChange(LangAnimationStatus animationStatus, int value);
}
