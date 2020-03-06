package net.lang.rtclib;

import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

import io.agora.rtc.IRtcEngineEventHandler;
import io.agora.rtc.RtcEngine;

public class LangRTCEventHandler {
    private final ConcurrentHashMap<IRTCEventListener, Integer> mEventListenerList = new ConcurrentHashMap<>();

    public LangRTCEventHandler() {

    }

    public void addListener(IRTCEventListener listener) {
        mEventListenerList.put(listener, 0);
    }

    public void removeListener(IRTCEventListener listener) {
        mEventListenerList.remove(listener);
    }

    final IRtcEngineEventHandler mRtcEventHandler = new IRtcEngineEventHandler() {
        private final String TAG = this.getClass().getSimpleName();
        @Override
        public void onFirstRemoteVideoDecoded(int uid, int width, int height, int elapsed) {
            RTCLog.d(TAG, "onFirstRemoteVideoDecoded " + (uid & 0xFFFFFFFFL) + " " + width + " " + height + " " + elapsed);
            /*
            Iterator<IRTCEventListener> it = mEventListenerList.keySet().iterator();
            while (it.hasNext()) {
                IRTCEventListener listener = it.next();
                listener.onFirstRemoteVideoFrame(uid, width, height, elapsed);
            }
             */
        }

        @Override
        public void onFirstRemoteVideoFrame(int uid, int width, int height, int elapsed) {
            RTCLog.d(TAG, "onFirstRemoteVideoDecoded " + (uid & 0xFFFFFFFFL) + " " + width + " " + height + " " + elapsed);

            Iterator<IRTCEventListener> it = mEventListenerList.keySet().iterator();
            while (it.hasNext()) {
                IRTCEventListener listener = it.next();
                listener.onFirstRemoteVideoFrame(uid, width, height, elapsed);
            }
        }

        @Override
        public void onFirstLocalVideoFrame(int width, int height, int elapsed) {
            RTCLog.d(TAG, "onFirstLocalVideoFrame " + width + " " + height + " " + elapsed);
        }

        @Override
        public void onUserJoined(int uid, int elapsed) {
            RTCLog.d(TAG, "onUserJoined " + (uid & 0xFFFFFFFFL) + " " + elapsed);

            Iterator<IRTCEventListener> it = mEventListenerList.keySet().iterator();
            while (it.hasNext()) {
                IRTCEventListener listener = it.next();
                listener.onUserJoined(uid, elapsed);
            }
        }
        @Override
        public void onUserOffline(int uid, int reason) {
            RTCLog.d(TAG, "onUserOffline " + (uid & 0xFFFFFFFFL) + " " + reason);

            // FIXME this callback may return times
            Iterator<IRTCEventListener> it = mEventListenerList.keySet().iterator();
            while (it.hasNext()) {
                IRTCEventListener listener = it.next();
                listener.onUserOffline(uid, reason);
            }
        }

        @Override
        public void onUserMuteAudio(int uid, boolean muted) {
            RTCLog.d(TAG, "onUserMuteAudio " + (uid & 0xFFFFFFFFL) + " " + muted);

            Iterator<IRTCEventListener> it = mEventListenerList.keySet().iterator();
            while (it.hasNext()) {
                IRTCEventListener listener = it.next();
                listener.onExtraCallback(IRTCEventListener.EVENT_TYPE_ON_USER_AUDIO_MUTED, uid, muted);
            }

        }

        @Override
        public void onUserMuteVideo(int uid, boolean muted) {
            RTCLog.d(TAG, "onUserMuteVideo " + (uid & 0xFFFFFFFFL) + " " + muted);

            Iterator<IRTCEventListener> it = mEventListenerList.keySet().iterator();
            while (it.hasNext()) {
                IRTCEventListener listener = it.next();
                listener.onExtraCallback(IRTCEventListener.EVENT_TYPE_ON_USER_VIDEO_MUTED, uid, muted);
            }
        }

        @Override
        public void onVideoSizeChanged(int uid, int width, int height, int rotation) {
            RTCLog.d(TAG, "onVideoSizeChanged " + (uid & 0xFFFFFFFFL) + " " + width + " " + height + " " + rotation);

            Iterator<IRTCEventListener> it = mEventListenerList.keySet().iterator();
            while (it.hasNext()) {
                IRTCEventListener listener = it.next();
                listener.onExtraCallback(IRTCEventListener.EVENT_TYPE_ON_VIDEO_SIZE_CHANGED, uid, width, height, rotation);
            }
        }

        @Override
        public void onRtcStats(RtcStats stats) {
            Iterator<IRTCEventListener> it = mEventListenerList.keySet().iterator();
            while (it.hasNext()) {
                IRTCEventListener listener = it.next();
                listener.onExtraCallback(IRTCEventListener.EVENT_TYPE_ON_RTC_STATS, stats);
            }
        }

        @Override
        public void onRemoteVideoStats(RemoteVideoStats stats) {
            RTCLog.d(TAG, "onRemoteVideoStats " + stats.uid + " " + stats.delay + " " + stats.receivedBitrate + " " + stats.width + " " + stats.height);

            Iterator<IRTCEventListener> it = mEventListenerList.keySet().iterator();
            while (it.hasNext()) {
                IRTCEventListener listener = it.next();
                listener.onExtraCallback(IRTCEventListener.EVENT_TYPE_ON_USER_VIDEO_STATS, stats);
            }
        }

        @Override
        public void onAudioVolumeIndication(AudioVolumeInfo[] speakerInfos, int totalVolume) {
            if (speakerInfos == null) {
                // quick and dirty fix for crash
                // TODO should reset UI for no sound
                return;
            }

            Iterator<IRTCEventListener> it = mEventListenerList.keySet().iterator();
            while (it.hasNext()) {
                IRTCEventListener listener = it.next();
                listener.onExtraCallback(IRTCEventListener.EVENT_TYPE_ON_SPEAKER_STATS, (Object) speakerInfos);
            }
        }

        @Override
        public void onLeaveChannel(RtcStats stats) {

            Iterator<IRTCEventListener> it = mEventListenerList.keySet().iterator();
            while (it.hasNext()) {
                IRTCEventListener listener = it.next();
                listener.onLeaveChannel();
            }
        }

        @Override
        public void onLastmileQuality(int quality) {
            RTCLog.d(TAG, "onLastmileQuality " + quality);
        }

        @Override
        public void onStreamMessage(int uid, int streamId, byte[] data) {
            RTCLog.d(TAG, "onStreamMessage " + (uid & 0xFFFFFFFFL) + " " + streamId + " " + Arrays.toString(data));

            Iterator<IRTCEventListener> it = mEventListenerList.keySet().iterator();
            while (it.hasNext()) {
                IRTCEventListener listener = it.next();
                listener.onExtraCallback(IRTCEventListener.EVENT_TYPE_ON_DATA_CHANNEL_MSG, uid, data);
            }
        }

        @Override
        public void onStreamMessageError(int uid, int streamId, int error, int missed, int cached) {
            RTCLog.w(TAG, "onStreamMessageError " + (uid & 0xFFFFFFFFL) + " " + streamId + " " + error + " " + missed + " " + cached);

            Iterator<IRTCEventListener> it = mEventListenerList.keySet().iterator();
            while (it.hasNext()) {
                IRTCEventListener listener = it.next();
                listener.onExtraCallback(IRTCEventListener.EVENT_TYPE_ON_AGORA_MEDIA_ERROR, error, "on stream msg error " + (uid & 0xFFFFFFFFL) + " " + missed + " " + cached);
            }
        }

        @Override
        public void onConnectionLost() {
            RTCLog.d(TAG, "onConnectionLost");

            Iterator<IRTCEventListener> it = mEventListenerList.keySet().iterator();
            while (it.hasNext()) {
                IRTCEventListener listener = it.next();
                listener.onExtraCallback(IRTCEventListener.EVENT_TYPE_ON_APP_ERROR, IRTCEventListener.ERROR_NETWORK_CONNECTION_LOST);
            }
        }

        @Override
        public void onConnectionInterrupted() {
            RTCLog.d(TAG, "onConnectionInterrupted");

            Iterator<IRTCEventListener> it = mEventListenerList.keySet().iterator();
            while (it.hasNext()) {
                IRTCEventListener listener = it.next();
                listener.onExtraCallback(IRTCEventListener.EVENT_TYPE_ON_APP_ERROR, IRTCEventListener.ERROR_NETWORK_CONNECTION_TIMEOUT);
            }
        }

        @Override
        public void onJoinChannelSuccess(String channel, int uid, int elapsed) {
            RTCLog.d(TAG, "onJoinChannelSuccess " + channel + " " + uid + " " + (uid & 0xFFFFFFFFL) + " " + elapsed);

            Iterator<IRTCEventListener> it = mEventListenerList.keySet().iterator();
            while (it.hasNext()) {
                IRTCEventListener listener = it.next();
                listener.onJoinChannelSuccess(channel, uid, elapsed);
            }
        }

        @Override
        public void onRejoinChannelSuccess(String channel, int uid, int elapsed) {
            RTCLog.d(TAG, "onRejoinChannelSuccess " + channel + " " + uid + " " + elapsed);

            Iterator<IRTCEventListener> it = mEventListenerList.keySet().iterator();
            while (it.hasNext()) {
                IRTCEventListener listener = it.next();
                listener.onRejoinChannelSuccess(channel, uid, elapsed);
            }
        }

        @Override
        public void onAudioRouteChanged(int routing) {
            RTCLog.d(TAG, "onAudioRouteChanged " + routing);

            Iterator<IRTCEventListener> it = mEventListenerList.keySet().iterator();
            while (it.hasNext()) {
                IRTCEventListener listener = it.next();
                listener.onExtraCallback(IRTCEventListener.EVENT_TYPE_ON_AUDIO_ROUTE_CHANGED, routing);
            }
        }

        @Override
        public void onWarning(int warn) {
            RTCLog.d(TAG, "onWarning " + warn);

            Iterator<IRTCEventListener> it = mEventListenerList.keySet().iterator();
            while (it.hasNext()) {
                IRTCEventListener listener = it.next();
                listener.onExtraCallback(IRTCEventListener.EVENT_TYPE_ON_AGORA_MEDIA_WARNING, warn);
            }
        }

        @Override
        public void onError(int error) {
            RTCLog.d(TAG, "onError " + error + " " + RtcEngine.getErrorDescription(error));

            Iterator<IRTCEventListener> it = mEventListenerList.keySet().iterator();
            while (it.hasNext()) {
                IRTCEventListener listener = it.next();
                listener.onExtraCallback(IRTCEventListener.EVENT_TYPE_ON_AGORA_MEDIA_ERROR, error, RtcEngine.getErrorDescription(error));
            }
        }

    };
}
