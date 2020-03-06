package net.lang.streamer.rtc.io.agora.avcapture.model;

import android.content.Context;
import io.agora.rtc.IRtcEngineEventHandler;
import io.agora.rtc.RtcEngine;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

import net.lang.streamer.utils.DebugLog;

/**
 * Created by lang on 2017/9/1.
 */

public class AgoraEngineEventHandler {

    private final EngineConfig mConfig;

    private final ConcurrentHashMap<AGEventHandler, Integer> mEventHandlerList = new ConcurrentHashMap<>();

    public AgoraEngineEventHandler(Context ctx, EngineConfig config) {
        this.mConfig = config;
    }

    public void addEventHandler(AGEventHandler handler) {
        this.mEventHandlerList.put(handler, 0);
    }

    public void removeEventHandler(AGEventHandler handler) {
        this.mEventHandlerList.remove(handler);
    }

    final IRtcEngineEventHandler mRtcEventHandler = new IRtcEngineEventHandler() {
        private final String TAG = this.getClass().getSimpleName();

        @Override
        public void onFirstRemoteVideoDecoded(int uid, int width, int height, int elapsed) {
            DebugLog.d(TAG, "onFirstRemoteVideoDecoded " + (uid & 0xFFFFFFFFL) + " " + width + " " + height + " " + elapsed);

            Iterator<AGEventHandler> it = mEventHandlerList.keySet().iterator();
            while (it.hasNext()) {
                AGEventHandler handler = it.next();
                handler.onFirstRemoteVideoDecoded(uid, width, height, elapsed);
            }
        }

        @Override
        public void onFirstLocalVideoFrame(int width, int height, int elapsed) {
            DebugLog.d(TAG, "onFirstLocalVideoFrame " + width + " " + height + " " + elapsed);
        }

        @Override
        public void onUserJoined(int uid, int elapsed) {
            DebugLog.d(TAG, "onUserJoined " + (uid & 0xFFFFFFFFL) + " " + elapsed);

            Iterator<AGEventHandler> it = mEventHandlerList.keySet().iterator();
            while (it.hasNext()) {
                AGEventHandler handler = it.next();
                handler.onUserJoined(uid, elapsed);
            }
        }

        @Override
        public void onUserOffline(int uid, int reason) {
            DebugLog.d(TAG, "onUserOffline " + (uid & 0xFFFFFFFFL) + " " + reason);

            // FIXME this callback may return times
            Iterator<AGEventHandler> it = mEventHandlerList.keySet().iterator();
            while (it.hasNext()) {
                AGEventHandler handler = it.next();
                handler.onUserOffline(uid, reason);
            }
        }

        @Override
        public void onUserMuteVideo(int uid, boolean muted) {
            DebugLog.d(TAG, "onUserMuteVideo " + (uid & 0xFFFFFFFFL) + " " + muted);

            Iterator<AGEventHandler> it = mEventHandlerList.keySet().iterator();
            while (it.hasNext()) {
                AGEventHandler handler = it.next();
                handler.onExtraCallback(AGEventHandler.EVENT_TYPE_ON_USER_VIDEO_MUTED, uid, muted);
            }
        }

        @Override
        public void onRtcStats(RtcStats stats) {
            Iterator<AGEventHandler> it = mEventHandlerList.keySet().iterator();
            while (it.hasNext()) {
                AGEventHandler handler = it.next();
                handler.onExtraCallback(AGEventHandler.EVENT_TYPE_ON_RTC_STATS, stats);
            }
        }

        @Override
        public void onRemoteVideoStats(RemoteVideoStats stats) {
            DebugLog.d(TAG, "onRemoteVideoStats " + stats.uid + " " + stats.delay + " " + stats.receivedBitrate + " " + stats.width + " " + stats.height);

            Iterator<AGEventHandler> it = mEventHandlerList.keySet().iterator();
            while (it.hasNext()) {
                AGEventHandler handler = it.next();
                handler.onExtraCallback(AGEventHandler.EVENT_TYPE_ON_USER_VIDEO_STATS, stats);
            }
        }

        @Override
        public void onAudioVolumeIndication(AudioVolumeInfo[] speakerInfos, int totalVolume) {
            if (speakerInfos == null) {
                // quick and dirty fix for crash
                // TODO should reset UI for no sound
                return;
            }

            Iterator<AGEventHandler> it = mEventHandlerList.keySet().iterator();
            while (it.hasNext()) {
                AGEventHandler handler = it.next();
                handler.onExtraCallback(AGEventHandler.EVENT_TYPE_ON_SPEAKER_STATS, (Object) speakerInfos);
            }
        }

        @Override
        public void onLeaveChannel(RtcStats stats) {

            Iterator<AGEventHandler> it = mEventHandlerList.keySet().iterator();
            while (it.hasNext()) {
                AGEventHandler handler = it.next();
                handler.onLeaveChannel();
            }
        }

        @Override
        public void onLastmileQuality(int quality) {
            DebugLog.d(TAG, "onLastmileQuality " + quality);
        }

        @Override
        public void onError(int error) {
            DebugLog.d(TAG, "onError " + error + " " + RtcEngine.getErrorDescription(error));

            Iterator<AGEventHandler> it = mEventHandlerList.keySet().iterator();
            while (it.hasNext()) {
                AGEventHandler handler = it.next();
                handler.onExtraCallback(AGEventHandler.EVENT_TYPE_ON_AGORA_MEDIA_ERROR, error, RtcEngine.getErrorDescription(error));
            }
        }

        @Override
        public void onStreamMessage(int uid, int streamId, byte[] data) {
            DebugLog.d(TAG, "onStreamMessage " + (uid & 0xFFFFFFFFL) + " " + streamId + " " + Arrays.toString(data));

            Iterator<AGEventHandler> it = mEventHandlerList.keySet().iterator();
            while (it.hasNext()) {
                AGEventHandler handler = it.next();
                handler.onExtraCallback(AGEventHandler.EVENT_TYPE_ON_DATA_CHANNEL_MSG, uid, data);
            }
        }

        @Override
        public void onStreamMessageError(int uid, int streamId, int error, int missed, int cached) {
            DebugLog.w(TAG, "onStreamMessageError " + (uid & 0xFFFFFFFFL) + " " + streamId + " " + error + " " + missed + " " + cached);

            Iterator<AGEventHandler> it = mEventHandlerList.keySet().iterator();
            while (it.hasNext()) {
                AGEventHandler handler = it.next();
                handler.onExtraCallback(AGEventHandler.EVENT_TYPE_ON_AGORA_MEDIA_ERROR, error, "on stream msg error " + (uid & 0xFFFFFFFFL) + " " + missed + " " + cached);
            }
        }

        @Override
        public void onConnectionLost() {
            DebugLog.d(TAG, "onConnectionLost");

            Iterator<AGEventHandler> it = mEventHandlerList.keySet().iterator();
            while (it.hasNext()) {
                AGEventHandler handler = it.next();
                //handler.onExtraCallback(AGEventHandler.EVENT_TYPE_ON_APP_ERROR, ConstantApp.AppError.NO_NETWORK_CONNECTION);
                handler.onExtraCallback(AGEventHandler.EVENT_TYPE_ON_APP_ERROR, ConstantApp.AppError.NETWORK_CONNECTION_TIMEOUT);
            }
        }

        @Override
        public void onConnectionInterrupted() {
            DebugLog.d(TAG, "onConnectionInterrupted");

            Iterator<AGEventHandler> it = mEventHandlerList.keySet().iterator();
            while (it.hasNext()) {
                AGEventHandler handler = it.next();
                //handler.onExtraCallback(AGEventHandler.EVENT_TYPE_ON_APP_ERROR, ConstantApp.AppError.NO_NETWORK_CONNECTION);
                handler.onExtraCallback(AGEventHandler.EVENT_TYPE_ON_APP_ERROR, ConstantApp.AppError.NETWORK_CONNECTION_LOST);
            }
        }

        @Override
        public void onJoinChannelSuccess(String channel, int uid, int elapsed) {
            DebugLog.d(TAG, "onJoinChannelSuccess " + channel + " " + uid + " " + (uid & 0xFFFFFFFFL) + " " + elapsed);

            mConfig.mUid = uid;

            Iterator<AGEventHandler> it = mEventHandlerList.keySet().iterator();
            while (it.hasNext()) {
                AGEventHandler handler = it.next();
                handler.onJoinChannelSuccess(channel, uid, elapsed);
            }
        }

        @Override
        public void onRejoinChannelSuccess(String channel, int uid, int elapsed) {
            DebugLog.d(TAG, "onRejoinChannelSuccess " + channel + " " + uid + " " + elapsed);

            mConfig.mUid = uid;

            Iterator<AGEventHandler> it = mEventHandlerList.keySet().iterator();
            while (it.hasNext()) {
                AGEventHandler handler = it.next();
                handler.onRejoinChannelSuccess(channel, uid, elapsed);
            }
        }

        @Override
        public void onAudioRouteChanged(int routing) {
            DebugLog.d(TAG, "onAudioRouteChanged " + routing);

            Iterator<AGEventHandler> it = mEventHandlerList.keySet().iterator();
            while (it.hasNext()) {
                AGEventHandler handler = it.next();
                handler.onExtraCallback(AGEventHandler.EVENT_TYPE_ON_AUDIO_ROUTE_CHANGED, routing);
            }
        }

        public void onWarning(int warn) {
            DebugLog.d(TAG, "onWarning " + warn);
        }
    };
}
