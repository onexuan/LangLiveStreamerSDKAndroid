package net.lang.streamer2.engine.session;

import android.os.Handler;
import android.os.Message;

import net.lang.streamer2.LangRtcInfo;
import net.lang.streamer2.LangRtcUser;
import net.lang.streamer2.engine.data.LangAnimationStatus;
import net.lang.streamer2.engine.data.LangFrameStatistics;
import net.lang.streamer2.engine.data.LangRtcEvent;
import net.lang.streamer2.engine.data.LangRtmpBufferStatus;
import net.lang.streamer2.engine.data.LangRtmpStatus;
import net.lang.streamer2.faceu.IFaceuListener;

import java.lang.ref.WeakReference;


public class LangMediaSessionHandler extends Handler {
    private static final String TAG = LangMediaSessionHandler.class.getSimpleName();
    private static final int MSG_RTMP_CONNECTION_CHANGED = 1;
    private static final int MSG_RTMP_STATS_CHANGED = 2;
    private static final int MSG_RTMP_SOCKET_BUFFER_CHANGED = 3;

    private static final int MSG_FACEU_FACE_CHANGED = 4;
    private static final int MSG_FACEU_HAND_CHANGED = 5;

    private static final int MSG_RTC_CONNECTION_CHANGED = 6;
    private static final int MSG_RTC_RECEIVE_NOTIFICATION = 7;
    private static final int MSG_RTC_STATS_CHANGED = 8;
    private static final int MSG_RTC_WARNING = 9;
    private static final int MSG_RTC_ERROR = 10;

    private static final int MSG_RECORD_CONNECTION_CHANGED = 11;
    private static final int MSG_RECOED_PROGRESS_CHANGED = 12;
    private static final int MSG_RECORD_ERROR = 13;

    private static final int MSG_ANIMATION_STATUS_CHANGED = 14;

    private WeakReference<LangMediaSessionListener> mWeakListener;

    public LangMediaSessionHandler(LangMediaSessionListener listener) {
        mWeakListener = new WeakReference<>(listener);
    }

    public LangMediaSessionHandler() {

    }

    public void setListener(LangMediaSessionListener listener) {
        mWeakListener = new WeakReference<>(listener);
    }

    public void notifyRtmpStatusChanged(LangRtmpStatus rtmpStatus) {
        obtainMessage(MSG_RTMP_CONNECTION_CHANGED, rtmpStatus).sendToTarget();
    }

    public void notifyRtmpStatisticsUpdate(LangFrameStatistics frameStatistics) {
        obtainMessage(MSG_RTMP_STATS_CHANGED, frameStatistics).sendToTarget();
    }

    public void notifyRtmpSocketBufferChanged(LangRtmpBufferStatus rtmpBufferStatus) {
        obtainMessage(MSG_RTMP_SOCKET_BUFFER_CHANGED, rtmpBufferStatus).sendToTarget();
    }

    public void notifyFaceTrackerFaceUpdate(int faceCount) {
        obtainMessage(MSG_FACEU_FACE_CHANGED, faceCount, 0).sendToTarget();
    }

    public void notifyFaceTrackerHandUpdate(int handCount, IFaceuListener.FaceuGestureType gesture) {
        obtainMessage(MSG_FACEU_HAND_CHANGED, handCount,0, gesture).sendToTarget();
    }

    public void notifyRtcStatusChanged(LangRtcEvent rtcEvent, int uid) {
        obtainMessage(MSG_RTC_CONNECTION_CHANGED, uid, 0, rtcEvent).sendToTarget();
    }

    public void notifyRtcReceiveNotification(LangRtcEvent rtcEvent, LangRtcUser rtcUser) {
        Object obj = new Object[]{rtcEvent, rtcUser};
        obtainMessage(MSG_RTC_RECEIVE_NOTIFICATION, obj).sendToTarget();
    }

    public void notifyRtcStatisticsUpdate(LangRtcInfo rtcInfo) {
        obtainMessage(MSG_RTC_STATS_CHANGED, rtcInfo).sendToTarget();
    }

    public void notifyRtcWarning(int warning) {
        obtainMessage(MSG_RTC_WARNING, warning).sendToTarget();
    }

    public void notifyRtcError(int error, String errorDescription) {
        obtainMessage(MSG_RTC_ERROR, error, 0, errorDescription).sendToTarget();
    }

    public void notifyRecordStatusChanged(int started) {
        obtainMessage(MSG_RECORD_CONNECTION_CHANGED, started, 0).sendToTarget();
    }

    public void notifyRecordProgressUpdate(long milliSeconds) {
        obtainMessage(MSG_RECOED_PROGRESS_CHANGED, (int) (milliSeconds >> 32), (int)milliSeconds).sendToTarget();
    }

    public void notifyRecordError(int error, String errorDescription) {
        obtainMessage(MSG_RECORD_ERROR, error, 0, errorDescription).sendToTarget();
    }

    public void notifyAnimationStatusChanged(LangAnimationStatus animationStatus, int value) {
        obtainMessage(MSG_ANIMATION_STATUS_CHANGED, value, 0, animationStatus).sendToTarget();
    }

    @Override  // runs on UI thread
    public void handleMessage(Message msg) {
        LangMediaSessionListener listener = mWeakListener.get();
        if (listener == null) {
            return;
        }

        switch (msg.what) {
            case MSG_RTMP_CONNECTION_CHANGED:
                listener.onSessionRtmpStatusChange((LangRtmpStatus)msg.obj);
                break;
            case MSG_RTMP_STATS_CHANGED:
                listener.onSessionRtmpStatisticsUpdate((LangFrameStatistics)msg.obj);
                break;
            case MSG_RTMP_SOCKET_BUFFER_CHANGED:
                listener.onSessionRtmpSocketBufferChange((LangRtmpBufferStatus)msg.obj);
                break;
            case MSG_FACEU_FACE_CHANGED:
                listener.onSessionFaceTrackerFaceUpdate(msg.arg1);
                break;
            case MSG_FACEU_HAND_CHANGED:
                listener.onSessionFaceTrackerHandUpdate(msg.arg1, (IFaceuListener.FaceuGestureType)msg.obj);
                break;
            case MSG_RTC_CONNECTION_CHANGED:
                listener.onSessionRtcStatusChange((LangRtcEvent)msg.obj, msg.arg1);
                break;
            case MSG_RTC_RECEIVE_NOTIFICATION:
                Object[] objs = (Object[])msg.obj;
                listener.onSessionRtcReceivedNotification((LangRtcEvent)objs[0], (LangRtcUser)objs[1]);
                break;
            case MSG_RTC_STATS_CHANGED:
                listener.onSessionRtcStatisticsUpdate((LangRtcInfo)msg.obj);
                break;
            case MSG_RTC_WARNING:
                listener.onSessionRtcWarning(msg.arg1);
                break;
            case MSG_RTC_ERROR:
                listener.onSessionRtcError(msg.arg1, (String)msg.obj);
                break;
            case MSG_RECORD_CONNECTION_CHANGED:
                listener.onSessionRecordChange(msg.arg1);
                break;
            case MSG_RECOED_PROGRESS_CHANGED:
                long timestampMs = (((long) msg.arg1) << 32) | (((long) msg.arg2) & 0xffffffffL);
                listener.onSessionRecordProgressUpdate(timestampMs);
                break;
            case MSG_RECORD_ERROR:
                listener.onSessionRecordError(msg.arg1, (String)msg.obj);
                break;
            case MSG_ANIMATION_STATUS_CHANGED:
                listener.onSessionAnimationStatusChange((LangAnimationStatus)msg.obj, msg.arg1);
                break;
            default:
                throw new RuntimeException("unknown msg " + msg.what);
        }
    }
}
