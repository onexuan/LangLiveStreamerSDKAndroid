package net.lang.streamer2.engine.publish;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.support.annotation.NonNull;

import net.lang.streamer2.utils.DebugLog;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;


/**
 * Created by lang on 2018/1/15.
 */
public class LangRtmpMuxer implements IMediaWriter {
    private static final String TAG = LangRtmpMuxer.class.getSimpleName();

    private static final int sUnknownTrackIndex = -1;
    private static final int sAudioTrackIndex = 0;
    private static final int sVideoTrackIndex = 1;

    private static final int MSG_RTMP_CONNECTING = 0;
    private static final int MSG_RTMP_CONNECTED = 1;
    private static final int MSG_RTMP_STOPPED = 2;
    private static final int MSG_RTMP_SOCKET_EXCEPTION = 3;
    private static final int MSG_RTMP_IO_EXCEPTION = 4;
    private static final int MSG_RTMP_ILLEGAL_ARGUMENT_EXCEPTION = 5;
    private static final int MSG_RTMP_VIDEO_FPS_CHANGED = 6;
    private static final int MSG_RTMP_VIDEO_BITRATE_CHANGED = 7;
    private static final int MSG_RTMP_AUDIO_BITRATE_CHANGED = 8;

    private long mNativeObject;

    private IRtmpEventListener mHandler;
    private Status mStatus;

    public LangRtmpMuxer(IRtmpEventListener handler) {
        if (handler == null) {
            throw new IllegalArgumentException("rtmp handler must not be null");
        }
        mHandler = handler;
        mStatus = Status.kUnInit;

        setUpMediaMuxer();
    }

    private void setUpMediaMuxer() {
        mNativeObject = nativeSetup(new WeakReference<>(this));
        updateStatus(Status.kInit);
    }

    /**
     * Adds a track with the specified format.
     * @param format The media format for the track.
     * @return The track index for this newly added track.
     */
    @Override
    public int addTrack(MediaFormat format) {
        if (format == null) {
            throw new IllegalArgumentException("format must not be null.");
        }
        if (status() != Status.kInit && status() != Status.kStop) {
            throw new IllegalStateException("RtmpMuxer is not initialized or stopped.");
        }

        if (format.getString(MediaFormat.KEY_MIME).contentEquals("video/avc")) {
            int width = format.getInteger(MediaFormat.KEY_WIDTH);
            int height = format.getInteger(MediaFormat.KEY_HEIGHT);
            int fps = format.getInteger(MediaFormat.KEY_FRAME_RATE);
            int bitRateBps = format.getInteger(MediaFormat.KEY_BIT_RATE);

            return addVideoTrack(width, height, fps, bitRateBps);
        } else if (format.getString(MediaFormat.KEY_MIME).contentEquals("audio/mp4a-latm")) {
            int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int channel = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            int bitRateBps = format.getInteger(MediaFormat.KEY_BIT_RATE);

            return addAudioTrack(sampleRate, channel, bitRateBps);
        } else {
            DebugLog.w(TAG, "no valid audio nor video track found, check format");
            return sUnknownTrackIndex;
        }
    }

    private int addAudioTrack(int aSampleRate, int aChannel, int aBitrateBps) {
        if (mNativeObject == 0) {
            throw new IllegalStateException("Muxer has been released!");
        }

        nativeAddAudioTrack(mNativeObject, aSampleRate, aChannel, aBitrateBps/1024);

        return sAudioTrackIndex;
    }

    private int addVideoTrack(int width, int height,  int fps, int vBitrateBps) {
        if (mNativeObject == 0) {
            throw new IllegalStateException("Muxer has been released!");
        }

        nativeAddVideoTrack(mNativeObject, width, height, fps, vBitrateBps/1024);

        return sVideoTrackIndex;
    }

    @Override
    public void start(final String rtmpUrl) {

        if (status() == Status.kInit || status() == Status.kStop) {

            nativeStart(mNativeObject, rtmpUrl);

            updateStatus(Status.kStart);
        } else {
            throw new IllegalStateException("Can't start due to wrong state.");
        }
    }

    @Override
    public void writeSampleData(int trackIndex, ByteBuffer byteBuf, MediaCodec.BufferInfo bufferInfo) {
        if (trackIndex < sAudioTrackIndex || trackIndex > sVideoTrackIndex) {
            throw new IllegalArgumentException("trackIndex is invalid");
        }

        if (byteBuf == null) {
            throw new IllegalArgumentException("byteBuffer must not be null");
        }

        if (bufferInfo == null) {
            throw new IllegalArgumentException("bufferInfo must not be null");
        }
        if (bufferInfo.size < 0 || bufferInfo.offset < 0
                || (bufferInfo.offset + bufferInfo.size) > byteBuf.capacity()
                || bufferInfo.presentationTimeUs < 0) {
            throw new IllegalArgumentException("bufferInfo must specify a" +
                    " valid buffer offset, size and presentation time");
        }

        if (status() != Status.kStart) {
            throw new IllegalStateException("Can't write, muxer is not started");
        }

        if (mNativeObject == 0) {
            throw new IllegalStateException("Muxer has been released!");
        }

        nativeWriteSampleData(mNativeObject, trackIndex, byteBuf,
                bufferInfo.offset, bufferInfo.size,
                bufferInfo.presentationTimeUs, bufferInfo.flags);
    }

    /**
     * stop the muxer, disconnect RTMP connection.
     */
    @Override
    public void stop() {
        if (status() == Status.kStart) {
            nativeStop(mNativeObject);
            updateStatus(Status.kStop);
        } else {
            if (status() == Status.kStop) {
                DebugLog.w(TAG, "duplicated call stop()");
                return;
            }
            throw new IllegalStateException("Can't stop due to wrong state +" + status().mName);
        }
    }

    @Override
    public void release() {
        if (status() == Status.kStart) {
            stop();
        }
        mHandler = null;
        cleanMediaMuxer();
        updateStatus(Status.kUnInit);
    }

    private void cleanMediaMuxer() {
        if (mNativeObject != 0) {
            nativeRelease(mNativeObject);
            mNativeObject = 0;
        }
    }

    @SuppressWarnings("native call")
    private static void postEventFromNative(Object weakThiz, int what, int arg1, int arg2, Object obj) {
        if (weakThiz == null)
            return;

        LangRtmpMuxer muxer = (LangRtmpMuxer) ((WeakReference)weakThiz).get();
        if (muxer == null) {
            return;
        }
        if (muxer.mHandler == null) {
            return;
        }

        switch (what) {
            case MSG_RTMP_CONNECTING:
                muxer.mHandler.onConnecting();
                break;
            case MSG_RTMP_CONNECTED:
                String serverInfo = (String)obj;
                muxer.mHandler.onConnected();
                break;
            case MSG_RTMP_STOPPED:
                muxer.mHandler.onDisconnected();
                break;
            case MSG_RTMP_SOCKET_EXCEPTION:
                String se = (String)obj;
                muxer.mHandler.onConnectingError(se);
                break;
        }
    }

    enum Status {
        kUnInit("UnInit"),
        kInit("Init"),
        kStart("Start"),
        kStop("Stop");

        String mName;
        Status(String name) {
            mName = name;
        }
    }

    private Status status() {
        return mStatus;
    }

    private void updateStatus(Status status) {
        DebugLog.d(TAG, "Change status " + mStatus.mName + " -> " + status.mName);
        mStatus = status;
    }


    private static native long nativeSetup(Object weakObject);
    private static native void nativeRelease(long nativeObject);

    private static native void nativeStart(long nativeObject, String rtmpUrl);
    private static native void nativeStop(long nativeObject);

    private static native int nativeAddAudioTrack(long nativeObject, int aSampleRate, int aChannel, int aBitrateKbps);
    private static native int nativeAddVideoTrack(long NativeObject, int width, int height, int fps, int vBitrateKbps);

    private static native void nativeWriteSampleData(
            long nativeObject, int trackIndex, @NonNull ByteBuffer byteBuf,
            int offset, int size, long presentationTimeUs, int flags);


    public interface IRtmpEventListener {

        void onConnecting();

        void onConnected();

        void onDisconnected();

        void onConnectingError(String errorInfo);
    }


    static {
        System.loadLibrary("lang_mediamuxer");
    }
}
