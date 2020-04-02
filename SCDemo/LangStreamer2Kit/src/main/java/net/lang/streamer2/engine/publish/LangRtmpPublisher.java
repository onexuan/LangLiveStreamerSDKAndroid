package net.lang.streamer2.engine.publish;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import net.lang.streamer2.engine.data.LangAudioConfiguration;
import net.lang.streamer2.engine.data.LangFrameStatistics;
import net.lang.streamer2.engine.data.LangRtmpBufferStatus;
import net.lang.streamer2.engine.data.LangRtmpConfiguration;
import net.lang.streamer2.engine.data.LangRtmpStatus;
import net.lang.streamer2.engine.data.LangVideoConfiguration;
import net.lang.streamer2.utils.DebugLog;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.Locale;

public class LangRtmpPublisher implements Runnable, LangRtmpMuxer.IRtmpEventListener, LangStreamingBuffer.IStreamingBufferListener {
    private static final String TAG = LangRtmpPublisher.class.getSimpleName();

    private INetworkListener mListener = null;
    private LangRtmpStatus mStatus;

    private final Object mGuardFence = new Object();
    private LangRtmpConfiguration mRtmpConfiguration;
    private LangRtmpMuxer mRtmpMuxer;
    private LangStreamingBuffer mStreamingBuffer;
    private LangFrameStatistics mStatistics = new LangFrameStatistics();
    private int mAudioTrackIndex = -1;
    private int mVideoTrackIndex = -1;
    private int mRetryTimes = 0;
    private boolean mConnectionRequestComplete;
    private boolean mDiscardConnect = false;
    private boolean mReconnecting = false;
    private byte[] mAudioHeader;
    private byte[] mVideoHeader;
    private boolean mSendAudioHeader = false;
    private boolean mSendVideoHeader = false;

    private volatile RtmpWorkHandler mHandler;
    private final Object mConnectionFence = new Object();
    private final Object mReadyFence = new Object();      // guards ready/running
    private boolean mReady;


    public LangRtmpPublisher(LangAudioConfiguration audioConfiguration,
                             LangVideoConfiguration videoConfiguration) {
        this(new LangRtmpConfiguration(audioConfiguration, videoConfiguration));
    }

    private LangRtmpPublisher(LangRtmpConfiguration rtmpConfiguration) {
        mRtmpConfiguration = rtmpConfiguration;
        initStreamingContext();
        initWorkerThread();

        mStatus = LangRtmpStatus.LANG_RTMP_STATUS_READY;
    }

    public void setNetworkListener(INetworkListener listener) {
        mListener = listener;
    }

    public LangRtmpConfiguration getRtmpConfiguration() {
        return mRtmpConfiguration;
    }

    public void addConfigData(int trackIndex, ByteBuffer byteBuf, MediaCodec.BufferInfo bufferInfo) {
        boolean isAudio = (trackIndex == mAudioTrackIndex);
        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            if (isAudio) {
                mAudioHeader = new byte[bufferInfo.size];
                byteBuf.get(mAudioHeader);
                Log.i(TAG, String.format("aac audio header data length = %d pts = %d",
                        bufferInfo.size, bufferInfo.presentationTimeUs));
            } else {
                mVideoHeader = new byte[bufferInfo.size];
                byteBuf.get(mVideoHeader);
                Log.i(TAG, String.format("h264 video header data length = %d pts = %d",
                        bufferInfo.size, bufferInfo.presentationTimeUs));
            }
        }
    }

    /*
     * start to establish rtmp connection
     */
    public void start() {
        synchronized (mGuardFence) {
            if (mStatus == LangRtmpStatus.LANG_RTMP_STATUS_PENDING) {
                Log.w(TAG, "start(): rtmp is connecting");
                return;
            } else if (mStatus == LangRtmpStatus.LANG_RTMP_STATUS_START) {
                Log.w(TAG, "start(): rtmp is already connected");
                return;
            } else if (mStatus == LangRtmpStatus.LANG_RTMP_STATUS_REFRESH) {
                Log.w(TAG, "start(): rtmp is re-connecting");
                return;
            }

            mConnectionRequestComplete = false;
            mDiscardConnect = false;
            sendStartMessage();
        }
    }

    /*
     * stop rtmp connection.
     */
    public void stop() {
        synchronized (mGuardFence) {
            if (mStatus == LangRtmpStatus.LANG_RTMP_STATUS_READY) {
                Log.w(TAG, "stop(): rtmp is not started");
                return;
            } else if (mStatus == LangRtmpStatus.LANG_RTMP_STATUS_STOP) {
                Log.w(TAG, "stop(): rtmp is already stopped");
                return;
            } else if (mStatus == LangRtmpStatus.LANG_RTMP_STATUS_PENDING) {
                Log.d(TAG, "stop(): stop connecting...");
            } else {
                Log.d(TAG, "stop() called in status:" + mStatus.name());
            }

            mRetryTimes = 0;
            mDiscardConnect = true;

            synchronized (mConnectionFence) {
                if (!mConnectionRequestComplete) {
                    try {
                        mConnectionFence.wait();
                    } catch (InterruptedException ie) {
                        ie.printStackTrace();
                    }
                }
            }

            sendStopMessage();
        }
    }

    /*
     * write media frames to rtmp muxer.
     */
    public void writeSampleData(int trackIndex, ByteBuffer byteBuf, MediaCodec.BufferInfo bufferInfo) {
        synchronized (mGuardFence) {
            if (mStatus != LangRtmpStatus.LANG_RTMP_STATUS_START) {
                Log.w(TAG, "writeSampleData failed due to invalid status" +
                        " trackIndex = " + trackIndex + "byteBuf = " + byteBuf +
                        " length = " + bufferInfo.size +
                        " pts = " + bufferInfo.presentationTimeUs);
                return;
            }

            if (trackIndex != mAudioTrackIndex && trackIndex != mVideoTrackIndex) {
                Log.w(TAG, "trackIndex is invalid, trackIndex = " + trackIndex);
                return;
            }

            //Log.d(TAG, String.format("writeSampleData trackIndex = %d, size = %d pts = %d",
            //        trackIndex, bufferInfo.size, bufferInfo.presentationTimeUs));

            // append frame to buffer queue.
            boolean isAudio = (trackIndex == mAudioTrackIndex);
            LangStreamingBuffer.LangMediaFrame frame = new LangStreamingBuffer.LangMediaFrame(byteBuf, bufferInfo, isAudio);
            mStreamingBuffer.appendObject(frame);

            sendMediaFrameMessage();
        }
    }

    public final int getAudioTrackIndex() {
        return mAudioTrackIndex;
    }

    public final int getVideoTrackIndex() {
        return mVideoTrackIndex;
    }

    // Implements LangRtmpMuxer.IRtmpEventListener
    @Override
    public void onConnecting() {
        Log.d(TAG, "rtmp is connecting...");
        mStatus = LangRtmpStatus.LANG_RTMP_STATUS_PENDING;
        if (mListener != null) {
            mListener.onSocketStatus(mStatus);
        }
    }

    @Override
    public void onConnected() {
        Log.d(TAG, "rtmp is connected");
        // native rtmp connection request is blocked, notify when connection request complete.
        synchronized (mConnectionFence) {
            mConnectionRequestComplete = true;
            mConnectionFence.notify();
        }
        mStatus = LangRtmpStatus.LANG_RTMP_STATUS_START;
        mReconnecting = false;
        if (mListener != null) {
            mListener.onSocketStatus(mStatus);
        }
    }

    @Override
    public void onDisconnected() {
        mStatus = LangRtmpStatus.LANG_RTMP_STATUS_STOP;
        // do not callback disconnected message while re-connecting.
        if (mReconnecting) {
            return;
        }

        Log.d(TAG, "rtmp is disconnected");
        if (mListener != null) {
            mListener.onSocketStatus(mStatus);
        }
    }

    @Override
    public void onConnectingError(String errorInfo) {
        Log.d(TAG, "rtmp is error, try to re-establish connection");
        // native rtmp connection request is blocked, notify when connection request complete.
        synchronized (mConnectionFence) {
            mConnectionRequestComplete = true;
            mConnectionFence.notify();
        }

        // if multiple error callback received, just don't send reconnect message.
        if (mReconnecting) {
            return;
        }
        mReconnecting = true;

        mStreamingBuffer.removeAllObject();

        if (mRetryTimes < mRtmpConfiguration.getRetryTimesCount()) {
            mRetryTimes++;

            if (mDiscardConnect) {
                return;
            }

            mStatus = LangRtmpStatus.LANG_RTMP_STATUS_REFRESH;
            if (mListener != null) {
                mListener.onSocketStatus(mStatus);
            }

            long delayedTimeMs = mRtmpConfiguration.getRetryTimesIntervalSec() * 1000;
            sendRestartMesage(delayedTimeMs);
        } else {
            mStatus = LangRtmpStatus.LANG_RTMP_STATUS_ERROR;
            if (mListener != null) {
                mListener.onSocketStatus(mStatus);
            }
        }
    }

    // Implements LangStreamingBuffer.IStreamingBufferListener
    @Override
    public void onBufferStatusUnknown() {
        if (mListener != null) {
            mListener.onSocketBufferStatus(LangRtmpBufferStatus.LANG_RTMP_BUFFER_STATUS_UNKONOW);
        }
    }

    @Override
    public void onBufferStatusIncrease() {
        if (mListener != null) {
            mListener.onSocketBufferStatus(LangRtmpBufferStatus.LANG_RTMP_BUFFER_STATUS_INCREASE);
        }
    }

    @Override
    public void onBufferStatusDecline() {
        if (mListener != null) {
            mListener.onSocketBufferStatus(LangRtmpBufferStatus.LANG_RTMP_BUFFER_STATUS_DECLINE);
        }
    }

    private void clean() {
        mStreamingBuffer.removeAllObject();
        mReconnecting = false;
        mSendAudioHeader = false;
        mSendVideoHeader = false;
        mConnectionRequestComplete = false;
    }

    public void release() {
        DebugLog.d(TAG, "release()");
        deinitStreamingContext();
        deinitWorkerThread();
    }

    private void initWorkerThread() {
        synchronized (mReadyFence) {
            new Thread(this, "LangRtmpMuxerThread").start();
            while (!mReady) {
                try {
                    mReadyFence.wait();
                } catch (InterruptedException ie) {
                    ie.printStackTrace();// ignore
                }
            }
        }
    }

    private void deinitWorkerThread() {
        sendQuitMessage();
        synchronized (mReadyFence) {
            while (mReady) {
                try {
                    mReadyFence.wait();
                } catch (InterruptedException ie) {
                    ie.printStackTrace();
                }
            }
        }
    }

    private void initStreamingContext() {
        mRtmpMuxer = new LangRtmpMuxer(this);

        LangAudioConfiguration audioConfiguration = mRtmpConfiguration.getAudioConfiguration();
        if (mRtmpConfiguration.getAudioConfiguration() != null) {
            MediaFormat audioFormat = audioFormat(audioConfiguration);
            mAudioTrackIndex = mRtmpMuxer.addTrack(audioFormat);
        }

        LangVideoConfiguration videoConfiguration = mRtmpConfiguration.getVideoConfiguration();
        if (videoConfiguration != null) {
            MediaFormat videoFormat = videoFormat(videoConfiguration);
            mVideoTrackIndex = mRtmpMuxer.addTrack(videoFormat);
        }

        mStreamingBuffer = new LangStreamingBuffer(this);
    }

    private MediaFormat audioFormat(LangAudioConfiguration audioConfiguration) {
        MediaFormat audioFormat = MediaFormat.createAudioFormat(audioConfiguration.getCodecInfo(),
                audioConfiguration.getSampleRate(), audioConfiguration.getChannel());
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, audioConfiguration.getBitrateBps());
        return audioFormat;
    }

    private MediaFormat videoFormat(LangVideoConfiguration videoConfiguration) {
        MediaFormat videoFormat = new MediaFormat();
        videoFormat.setString(MediaFormat.KEY_MIME, videoConfiguration.getCodecInfo());
        if (videoConfiguration.getLandscape()) {
            videoFormat.setInteger(MediaFormat.KEY_WIDTH, videoConfiguration.getWidth());
            videoFormat.setInteger(MediaFormat.KEY_HEIGHT, videoConfiguration.getHeight());
        } else {
            videoFormat.setInteger(MediaFormat.KEY_WIDTH, videoConfiguration.getHeight());
            videoFormat.setInteger(MediaFormat.KEY_HEIGHT, videoConfiguration.getWidth());
        }
        videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, videoConfiguration.getBitrateBps());
        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, videoConfiguration.getFps());
        return videoFormat;
    }

    private void deinitStreamingContext() {
        mRtmpMuxer.release();
        mStreamingBuffer.destroy();
    }


    /**
     * Encoder thread entry point.  Establishes Looper/Handler and waits for messages.
     * <p>
     * @see Thread#run()
     */
    @Override
    public void run() {
        // Establish a Looper for this thread, and define a Handler for it.
        Looper.prepare();
        synchronized (mReadyFence) {
            mHandler = new RtmpWorkHandler(this);
            mReady = true;
            mReadyFence.notify();
        }
        Looper.loop();

        Log.d(TAG, "Encoder thread exiting");
        synchronized (mReadyFence) {
            mReady = false;
            mHandler = null;
            mReadyFence.notify();
        }
    }

    private void sendStartMessage() {
        mHandler.sendMessage(mHandler.obtainMessage(RtmpWorkHandler.MSG_START));
    }

    private void sendRestartMesage(final long delayedTimeMs) {
        mHandler.sendMessageDelayed(mHandler.obtainMessage(RtmpWorkHandler.MSG_RESTART), delayedTimeMs);
    }

    private void sendMediaFrameMessage() {
        mHandler.sendMessage(mHandler.obtainMessage(RtmpWorkHandler.MSG_WRITE));
    }

    private void sendStopMessage() {
        mHandler.removeCallbacksAndMessages(null);
        mHandler.sendMessage(mHandler.obtainMessage(RtmpWorkHandler.MSG_STOP));
    }

    private void sendQuitMessage() {
        mHandler.sendMessage(mHandler.obtainMessage(RtmpWorkHandler.MSG_QUIT));
    }

    private static class RtmpWorkHandler extends Handler {

        static final int MSG_START = 1;
        static final int MSG_RESTART = 2;
        static final int MSG_WRITE = 3;
        static final int MSG_STOP = 4;
        static final int MSG_QUIT = 5;

        private WeakReference<LangRtmpPublisher> mWeakPublisher;

        public RtmpWorkHandler(LangRtmpPublisher rtmpPublisher) {
            mWeakPublisher = new WeakReference<LangRtmpPublisher>(rtmpPublisher);
        }

        @Override
        public void handleMessage(Message msg) {
            int what = msg.what;
            Object obj = msg.obj;

            LangRtmpPublisher publisher = mWeakPublisher.get();
            if (publisher == null) {
                Log.w(TAG, "EncoderHandler.handleMessage: publisher is null");
                return;
            }

            switch (what) {
                case MSG_START:
                    Log.d(TAG, "looper start broadcasting.");
                    publisher.handleStart();
                    break;
                case MSG_RESTART:
                    Log.d(TAG, "looper re-start broadcasting.");
                    publisher.handleRestart();
                    break;
                case MSG_WRITE:
                    //Log.d(TAG, "looper send rtmp frames.");
                    publisher.handleWriteData();
                    break;
                case MSG_STOP:
                    Log.d(TAG, "looper stop broadcasting.");
                    publisher.handleStop();
                    break;
                case MSG_QUIT:
                    Log.d(TAG, "looper quit.");
                    // exit thread looper
                    Looper.myLooper().quit();
                    break;
                default:
                    throw new RuntimeException("Unhandled msg what=" + what);
            }
        }
    }

    private void handleStart() {
        String url = mRtmpConfiguration.getUrl();
        mRtmpMuxer.start(url);
    }

    private void handleWriteData() {
        LangStreamingBuffer.LangMediaFrame frame = mStreamingBuffer.popFirstObject();
        if (frame == null) {
            return;
        }

        int trackIndex = -1;
        if (frame.isAudio() && mRtmpConfiguration.isEnableAudio()) {
            if (!mSendAudioHeader) {
                mSendAudioHeader = true;
                ByteBuffer aacConfig = ByteBuffer.wrap(mAudioHeader);
                MediaCodec.BufferInfo aacConfigInfo = new MediaCodec.BufferInfo();
                aacConfigInfo.set(0, mAudioHeader.length, 0, MediaCodec.BUFFER_FLAG_CODEC_CONFIG);
                mRtmpMuxer.writeSampleData(mAudioTrackIndex, aacConfig, aacConfigInfo);
            }
            trackIndex = mAudioTrackIndex;
        } else {
            if (!mSendVideoHeader && mRtmpConfiguration.isEnableVideo()) {
                mSendVideoHeader = true;
                ByteBuffer h264SpsPps = ByteBuffer.wrap(mVideoHeader);
                MediaCodec.BufferInfo h264SpsPpsInfo = new MediaCodec.BufferInfo();
                h264SpsPpsInfo.set(0, mVideoHeader.length, 0, MediaCodec.BUFFER_FLAG_CODEC_CONFIG);
                mRtmpMuxer.writeSampleData(mVideoTrackIndex, h264SpsPps, h264SpsPpsInfo);
            }
            trackIndex = mVideoTrackIndex;
        }

        ByteBuffer frameData = frame.getBuffer();
        MediaCodec.BufferInfo frameInfo = frame.getBufferInfo();
        mRtmpMuxer.writeSampleData(trackIndex, frameData, frameInfo);
        DebugLog.v(TAG, String.format(Locale.getDefault(),
                "handleWriteData trackIndex = %d, size = %d pts = %d",
                trackIndex, frameInfo.size, frameInfo.presentationTimeUs));

        // frame statistics update.
        if (frame.isAudio()) {
            mStatistics.totalAudioFrames++;
        } else {
            mStatistics.totalVideoFrames++;
        }
        mStatistics.dropFrames += mStreamingBuffer.getLastDropFrames();
        mStreamingBuffer.setLastDropFrames(0);

        mStatistics.dataFlow += frameInfo.size;
        mStatistics.elapsedMs = System.currentTimeMillis() - mStatistics.timestampMs;
        if (mStatistics.elapsedMs < 1000L) {
            mStatistics.bandWidth += frameInfo.size;
            if (frame.isAudio()) {
                mStatistics.capturedAudioCount++;
            } else {
                mStatistics.capturedVideoCount++;
            }
            mStatistics.unSendAudioCount = mStreamingBuffer.getReorderedFrames(true);
            mStatistics.unSendVideoCount = mStreamingBuffer.getReorderedFrames(false);
        } else {
            mStatistics.currentBandwidth = mStatistics.bandWidth;
            mStatistics.currentCapturedAudioCount = mStatistics.capturedAudioCount;
            mStatistics.currentCapturedVideoCount = mStatistics.capturedVideoCount;
            if (mListener != null) {
                mListener.onSocketStatistics(mStatistics);
            }
            mStatistics.bandWidth = 0;
            mStatistics.capturedAudioCount = 0;
            mStatistics.capturedVideoCount = 0;
            mStatistics.timestampMs = System.currentTimeMillis();
        }
    }

    private void handleStop() {
        mRtmpMuxer.stop();
        clean();
    }

    private void handleRestart() {
        mRtmpMuxer.stop();
        clean();

        // do not re-connect when discard flag raised. Here we don't perform next connection request
        // for the sake of blocking stop() methods too long. This is an optimization procedure because
        // stop() method will wait until each connection request complete, no matter it is successful or failed.
        if (mDiscardConnect) {
            return;
        }

        String url = mRtmpConfiguration.getUrl();
        mRtmpMuxer.start(url);
    }
}
