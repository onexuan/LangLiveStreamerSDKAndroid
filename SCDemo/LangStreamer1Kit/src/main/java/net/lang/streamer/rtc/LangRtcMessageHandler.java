package net.lang.streamer.rtc;

import android.os.Handler;
import android.os.Message;

import net.lang.streamer.LangRtcInfo;

import java.lang.ref.WeakReference;


/**
 * Created by lang on 2017/9/4.
 */

public class LangRtcMessageHandler extends Handler {

    private static final int MSG_RTC_ERROR = 0;
    private static final int MSG_RTC_LOCAL_USER_JOINED = 1;
    private static final int MSG_RTC_LOCAL_USER_OFFLINE = 2;
    private static final int MSG_RTC_REMOTE_USER_JOINED = 3;
    private static final int MSG_RTC_REMOTE_USER_VIDEO_RENDERED = 4;
    private static final int MSG_RTC_REMOTE_USER_OFFLINE = 5;
    private static final int MSG_RTC_REMOTE_USER_AUDIO_MUTED = 6;
    private static final int MSG_RTC_REMOTE_USER_VIDEO_MUTED = 7;
    private static final int MSG_RTC_LOCAL_AUDIO_ROUTE_CHANGED = 8;
    private static final int MSG_RTC_STATS_UPDATE = 9;
    private static final int MSG_RTC_NETWORK_LOST = 10;
    private static final int MSG_RTC_NETWORK_TIMEOUT = 11;

    private WeakReference<SnailRtcListener> mWeakListener;

    public LangRtcMessageHandler(SnailRtcListener listener) {
        mWeakListener = new WeakReference<>(listener);
    }

    public void notifyRtcError(int error, String msg) {
        obtainMessage(MSG_RTC_ERROR, error, 0, msg).sendToTarget();
    }

    public void notifyRtcLocalUserJoined(int uid) {
        obtainMessage(MSG_RTC_LOCAL_USER_JOINED, uid, 0).sendToTarget();
    }

    public void notifyRtcLocalUserOffline() {
        obtainMessage(MSG_RTC_LOCAL_USER_OFFLINE, null).sendToTarget();
    }

    public void notifyRtcRemoteUserJoined(int uid) {
        obtainMessage(MSG_RTC_REMOTE_USER_JOINED, uid, 0).sendToTarget();
    }

    public void notifyRtcRemoteUserVideoRendered(int uid, int width, int height) {
        obtainMessage(MSG_RTC_REMOTE_USER_VIDEO_RENDERED, new Object[]{uid, width, height}).sendToTarget();
    }

    public void notifyRtcRemoteUserOffline(int uid, int reason) {
        obtainMessage(MSG_RTC_REMOTE_USER_OFFLINE, uid, reason).sendToTarget();
    }

    public void notifyRtcRemoteUserAudioMuted(int uid, boolean muted) {
        obtainMessage(MSG_RTC_REMOTE_USER_AUDIO_MUTED, new Object[]{uid, muted}).sendToTarget();
    }

    public void notifyRtcRemoteUserVideoMuted(int uid, boolean muted) {
        obtainMessage(MSG_RTC_REMOTE_USER_VIDEO_MUTED, new Object[]{uid, muted}).sendToTarget();
    }

    public void notifyRtcLocalAudioRouteChanged(int data) {
        obtainMessage(MSG_RTC_LOCAL_AUDIO_ROUTE_CHANGED, data, 0).sendToTarget();
    }

    public void notifyRtcStatsUpdate(LangRtcInfo rtcInfo) {
        obtainMessage(MSG_RTC_STATS_UPDATE, rtcInfo).sendToTarget();
    }

    public void notifyRtcNetworkLost() {
        obtainMessage(MSG_RTC_NETWORK_LOST).sendToTarget();
    }

    public void notifyRtcNetworkTimeout() {
        obtainMessage(MSG_RTC_NETWORK_TIMEOUT).sendToTarget();
    }

    @Override  // runs on UI thread
    public void handleMessage(Message msg) {
        SnailRtcListener listener = mWeakListener.get();
        if (listener == null) {
            return;
        }

        switch (msg.what) {
            case MSG_RTC_ERROR:
                listener.onRtcError(msg.arg1);
                break;
            case MSG_RTC_LOCAL_USER_JOINED:
                listener.onRtcLocalUserJoined(msg.arg1);
                break;
            case MSG_RTC_LOCAL_USER_OFFLINE:
                listener.onRtcLocalUserOffline();
                break;
            case MSG_RTC_REMOTE_USER_JOINED:
                listener.onRtcRemoteUserJoined(msg.arg1);
                break;
            case MSG_RTC_REMOTE_USER_VIDEO_RENDERED:
                Object[] data1 = (Object[])msg.obj;
                listener.onRtcRemoteUserVideoRendered((int)data1[0], (int)data1[1], (int)data1[2]);
                break;
            case MSG_RTC_REMOTE_USER_OFFLINE:
                listener.onRtcRemoteUserOffline(msg.arg1, msg.arg2);
                break;
            case MSG_RTC_REMOTE_USER_AUDIO_MUTED:
                Object[] data2 = (Object[]) msg.obj;
                listener.onRtcRemoteUserAudioMuted((int)data2[0], (boolean)data2[1]);
                break;
            case MSG_RTC_REMOTE_USER_VIDEO_MUTED:
                Object[] data3 = (Object[]) msg.obj;
                listener.onRtcRemoteUserVideoMuted((int)data3[0], (boolean)data3[1]);
                break;
            case MSG_RTC_LOCAL_AUDIO_ROUTE_CHANGED:
                listener.onRtcLocalAudioRouteChanged(msg.arg1);
                break;
            case MSG_RTC_STATS_UPDATE:
                LangRtcInfo rtcInfo = (LangRtcInfo)msg.obj;
                listener.onRtcStatsUpdate(rtcInfo);
                break;
            case MSG_RTC_NETWORK_LOST:
                listener.onRtcNetworkLost();
                break;
            case MSG_RTC_NETWORK_TIMEOUT:
                listener.onRtcNetworkTimeout();
                break;
            default:
                throw new RuntimeException("unknown msg " + msg.what);
        }
    }

    public interface SnailRtcListener {

        void onRtcError(int error);

        void onRtcLocalUserJoined(int uid);

        void onRtcLocalUserOffline();

        void onRtcRemoteUserJoined(int uid);

        void onRtcRemoteUserVideoRendered(int uid, int width, int height);

        void onRtcRemoteUserOffline(int uid, int reason);

        void onRtcRemoteUserAudioMuted(int uid, boolean muted);

        void onRtcRemoteUserVideoMuted(int uid, boolean muted);

        void onRtcLocalAudioRouteChanged(int data);

        void onRtcStatsUpdate(LangRtcInfo rtcInfo);

        void onRtcNetworkLost();

        void onRtcNetworkTimeout();
    }
}
