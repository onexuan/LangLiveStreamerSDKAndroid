package net.lang.streamer2.engine.publish;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import net.lang.streamer2.engine.data.LangAudioConfiguration;
import net.lang.streamer2.engine.data.LangVideoConfiguration;
import net.lang.streamer2.utils.DebugLog;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.Locale;

public class LangLocalPublisher implements Runnable {
    private static final String TAG = LangLocalPublisher.class.getSimpleName();
    private static final int TRACK_AUDIO = 0;
    private static final int TRACK_VIDEO = 1;

    private IRecordListener mListener;
    private final Object mGuardFence = new Object();
    private final Object mReadyFence = new Object();      // guards ready/running
    private String mUrl;
    private LangAudioConfiguration mAudioConfiguration;
    private LangVideoConfiguration mVideoConfiguration;
    private volatile MediaMuxerHandler mHandler;
    private LangStreamingBuffer mStreamingBuffer;
    private MediaFormat mAudioFormat;
    private MediaFormat mVideoFormat;
    private MediaMuxer mMediaMuxer;
    private int mAudioTrackIndex = -1;
    private int mVideoTrackIndex = -1;
    private long mLastWrittenTimestampMs = -1;
    private boolean mHasAudio;
    private boolean mHasVideo;
    private boolean mReady;
    private STATE mState;
    private enum STATE {
        IDLE,
        RUNNING,
        STOPPED
    }

    public LangLocalPublisher(LangAudioConfiguration audioConfiguration, LangVideoConfiguration videoConfiguration) {
        mAudioConfiguration = audioConfiguration.dup();
        mVideoConfiguration = videoConfiguration.dup();
        mStreamingBuffer = new LangStreamingBuffer(null);
        initWorkerThread();
        mState = STATE.IDLE;
    }

    public void setRecordListener(IRecordListener listener) {
        mListener = listener;
    }

    /**
     * add an audio track or a video track with config data
     * @param trackIndex specify a track index using TRACK_AUDIO/TRACK_VIDEO
     * @param byteBuf contains the header data in byteBuf
     * @param bufferInfo contains the information of header data
     */
    public void addTrack(int trackIndex, ByteBuffer byteBuf, MediaCodec.BufferInfo bufferInfo)
            throws IllegalArgumentException {
        synchronized (mGuardFence) {
            if (mState == STATE.RUNNING) {
                DebugLog.w(TAG, "addTrack(): failed MediaMuxer is in working progress ");
                return;
            }
            if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                byteBuf.position(bufferInfo.offset);

                if (trackIndex == TRACK_AUDIO) {
                    mHasAudio = true;
                    byte[] audioConfigData = new byte[bufferInfo.size];
                    byteBuf.get(audioConfigData);
                    ByteBuffer audioConfigBuffer = ByteBuffer.wrap(audioConfigData);

                    mAudioFormat = MediaFormat.createAudioFormat(mAudioConfiguration.getCodecInfo(),
                            mAudioConfiguration.getSampleRate(), mAudioConfiguration.getChannel());
                    mAudioFormat.setByteBuffer("csd-0", audioConfigBuffer);

                    DebugLog.i(TAG, String.format(Locale.getDefault(),
                            "aac audio header data length = %d pts = %d",
                            bufferInfo.size, bufferInfo.presentationTimeUs));
                } else if (trackIndex == TRACK_VIDEO) {
                    mHasVideo = true;
                    byte[] videoConfigData = new byte[bufferInfo.size];
                    byteBuf.get(videoConfigData);

                    mVideoFormat = new MediaFormat();
                    mVideoFormat.setString(MediaFormat.KEY_MIME, mVideoConfiguration.getCodecInfo());
                    if (mVideoConfiguration.getLandscape()) {
                        mVideoFormat.setInteger(MediaFormat.KEY_WIDTH, mVideoConfiguration.getWidth());
                        mVideoFormat.setInteger(MediaFormat.KEY_HEIGHT, mVideoConfiguration.getHeight());
                    } else {
                        mVideoFormat.setInteger(MediaFormat.KEY_WIDTH, mVideoConfiguration.getHeight());
                        mVideoFormat.setInteger(MediaFormat.KEY_HEIGHT, mVideoConfiguration.getWidth());
                    }

                    if (mVideoConfiguration.getCodecInfo().contains(MediaFormat.MIMETYPE_VIDEO_AVC)) {
                        // h264 uses csd-0(sps) & csd-1(pps) in MediaFormat
                        ByteBuffer spsPpsConfigBuffer = ByteBuffer.wrap(videoConfigData);
                        ByteBuffer spsConfigBuffer, ppsConfigBuffer;
                        if (spsPpsConfigBuffer.getInt() == 0x00000001) {
                            DebugLog.d(TAG, "addTrack: h264 parsing sps/pps");
                        }
                        while(!(spsPpsConfigBuffer.get() == 0x00 &&
                                spsPpsConfigBuffer.get() == 0x00 &&
                                spsPpsConfigBuffer.get() == 0x00 &&
                                spsPpsConfigBuffer.get() == 0x01)) {

                        }
                        DebugLog.d(TAG, "addTrack: split sps & pps");
                        int ppsIndex = spsPpsConfigBuffer.position();

                        // attention: carefully copy sps & pps
                        byte[] spsData = new byte[ppsIndex - 4];
                        System.arraycopy(videoConfigData, 0, spsData, 0, spsData.length);
                        byte[] ppsData = new byte[videoConfigData.length - ppsIndex + 4];
                        System.arraycopy(videoConfigData, ppsIndex - 4, ppsData, 0, ppsData.length);

                        spsConfigBuffer = ByteBuffer.wrap(spsData);
                        ppsConfigBuffer = ByteBuffer.wrap(ppsData);

                        mVideoFormat.setByteBuffer("csd-0", spsConfigBuffer);
                        mVideoFormat.setByteBuffer("csd-1", ppsConfigBuffer);

                    } else if (mVideoConfiguration.getCodecInfo().contains(MediaFormat.MIMETYPE_VIDEO_HEVC)) {
                        // hevc uses csd-0(vps/sps/pps) only in MediaFormat
                        DebugLog.d(TAG, "addTrack: hevc");

                        ByteBuffer audioConfigBuffer = ByteBuffer.wrap(videoConfigData);
                        mVideoFormat.setByteBuffer("csd-0", audioConfigBuffer);
                    }
                } else {
                    throw new IllegalArgumentException("trackIndex must be TRACK_AUDIO/TRACK_VIDEO");
                }
            } else {
                throw new IllegalArgumentException("input bufferInfo doesn't contain MediaCodec.BUFFER_FLAG_CODEC_CONFIG");
            }
        }
    }

    public void start(String url) {
        synchronized (mGuardFence) {
            if (mState == STATE.RUNNING) {
                DebugLog.w(TAG, "start(): MediaMuxer is already started");
                return;
            }
            mUrl = url;
            sendStartMessage();
        }
    }

    public void writeSampleData(int trackIndex, ByteBuffer byteBuf, MediaCodec.BufferInfo bufferInfo) {
        synchronized (mGuardFence) {
            if (mState != STATE.RUNNING) {
                DebugLog.w(TAG, "writeSampleData() failed due to invalid status");
                return;
            }
            if (trackIndex != TRACK_AUDIO && trackIndex != TRACK_VIDEO) {
                DebugLog.w(TAG, "trackIndex is invalid, trackIndex = " + trackIndex);
                return;
            }
            boolean isAudio = (trackIndex == TRACK_AUDIO);
            LangStreamingBuffer.LangMediaFrame frame = new LangStreamingBuffer.LangMediaFrame(byteBuf, bufferInfo, isAudio);
            mStreamingBuffer.appendObjectNoDrop(frame);

            sendMediaFrameMessage();
        }
    }

    public void stop() {
        synchronized (mGuardFence) {
            if (mState == STATE.IDLE) {
                DebugLog.w(TAG, "stop(): MediaMuxer is not started");
                return;
            } else if (mState == STATE.STOPPED) {
                DebugLog.w(TAG, "stop(): MediaMuxer is already stopped");
                return;
            }
            sendStopMessage();
        }
    }

    public void release() {
        DebugLog.d(TAG, "release()");
        deinitWorkerThread();
        mListener = null;
    }

    public final int getAudioTrackIndex() {
        return TRACK_AUDIO;
    }

    public final int getVideoTrackIndex() {
        return TRACK_VIDEO;
    }

    /**
     * Media-muxer thread entry point.  Establishes Looper/Handler and waits for messages.
     * <p>
     * @see Thread#run()
     */
    @Override
    public void run() {
        // Establish a Looper for this thread, and define a Handler for it.
        Looper.prepare();
        synchronized (mReadyFence) {
            mHandler = new MediaMuxerHandler(this);
            mReady = true;
            mReadyFence.notify();
        }
        Looper.loop();

        DebugLog.d(TAG, "Local muxer thread exiting");
        synchronized (mReadyFence) {
            mReady = false;
            mHandler = null;
            mReadyFence.notify();
        }
    }

    private void initWorkerThread() {
        synchronized (mReadyFence) {
            new Thread(this, "LangMediaMuxerThread").start();
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

    private void sendStartMessage() {
        mHandler.sendMessage(mHandler.obtainMessage(MediaMuxerHandler.MSG_START));
    }

    private void sendMediaFrameMessage() {
        mHandler.sendMessage(mHandler.obtainMessage(MediaMuxerHandler.MSG_WRITE));
    }

    private void sendStopMessage() {
        mHandler.removeCallbacksAndMessages(null);
        mHandler.sendMessage(mHandler.obtainMessage(MediaMuxerHandler.MSG_STOP));
    }

    private void sendQuitMessage() {
        mHandler.sendMessage(mHandler.obtainMessage(MediaMuxerHandler.MSG_QUIT));
    }

    private static class MediaMuxerHandler extends Handler {
        static final int MSG_START = 1;
        static final int MSG_WRITE = 2;
        static final int MSG_STOP  = 3;
        static final int MSG_QUIT  = 4;

        private WeakReference<LangLocalPublisher> mWeakPublisher;

        public MediaMuxerHandler(LangLocalPublisher localPublisher) {
            mWeakPublisher = new WeakReference<LangLocalPublisher>(localPublisher);
        }

        @Override
        public void handleMessage(Message msg) {
            LangLocalPublisher publisher = mWeakPublisher.get();
            if (publisher == null) {
                DebugLog.w(TAG, "EncoderHandler.handleMessage: publisher is null");
                return;
            }

            switch (msg.what) {
                case MSG_START:
                    publisher.handleStart();
                    break;
                case MSG_WRITE:
                    publisher.handleWriteData();
                    break;
                case MSG_STOP:
                    publisher.handleStop();
                    break;
                case MSG_QUIT:
                    DebugLog.d(TAG, "looper quit.");
                    // exit thread looper
                    Looper.myLooper().quit();
                    break;
                default:
                    throw new RuntimeException("Unhandled msg what=" + msg.what);
            }
        }
    }

    private void handleStart() {
        synchronized (mGuardFence) {
            if (!mUrl.contains(".mp4")) {
                if (mListener != null) {
                    mListener.onRecordError(mUrl, -1, "output url cannot specify a valid file format");
                }
            }
            if (!mHasAudio && !mHasVideo) {
                if (mListener != null) {
                    mListener.onRecordError(mUrl, -2, "no audio or video track found");
                }
            }

            try {
                mMediaMuxer = new MediaMuxer(mUrl, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                if (mHasAudio) {
                    mAudioTrackIndex = mMediaMuxer.addTrack(mAudioFormat);
                }
                if (mHasVideo) {
                    mVideoTrackIndex = mMediaMuxer.addTrack(mVideoFormat);
                }

                mMediaMuxer.start();

                mState = STATE.RUNNING;

                if (mListener != null) {
                    mListener.onRecordStart(mUrl);
                }
            } catch (IOException ioe) {
                if (mListener != null) {
                    mListener.onRecordError(mUrl, -100, ioe.getLocalizedMessage());
                }
            } catch (IllegalStateException ise) {
                if (mListener != null) {
                    mListener.onRecordError(mUrl, -101, ise.getLocalizedMessage());
                }
            }
        }
    }

    private void handleWriteData() {
        synchronized (mGuardFence) {
            LangStreamingBuffer.LangMediaFrame frame = mStreamingBuffer.popFirstObject();
            if (frame == null) {
                return;
            }

            int trackIndex = -1;
            if (frame.isAudio()) {
                trackIndex = mAudioTrackIndex;
            } else {
                trackIndex = mVideoTrackIndex;
            }
            ByteBuffer frameData = frame.getBuffer();
            MediaCodec.BufferInfo frameInfo = frame.getBufferInfo();
            try {
                DebugLog.v(TAG, String.format(Locale.getDefault(),
                        "handleWriteData trackIndex = %d, size = %d pts = %d",
                        trackIndex, frameInfo.size, frameInfo.presentationTimeUs));
                mMediaMuxer.writeSampleData(trackIndex, frameData, frameInfo);
                mLastWrittenTimestampMs = frameInfo.presentationTimeUs/1000L;
            } catch (IllegalStateException ise) {
                if (mListener != null) {
                    mListener.onRecordError(mUrl, -101, ise.getLocalizedMessage());
                }
                return;
            } catch (IllegalArgumentException iae) {
                if (mListener != null) {
                    mListener.onRecordError(mUrl, -102, iae.getLocalizedMessage());
                }
                return;
            }
            if (mListener != null) {
                mListener.onRecordProgress(mUrl, mLastWrittenTimestampMs);
            }
        }
    }

    private void handleStop() {
        synchronized (mGuardFence) {
            try {
                DebugLog.d(TAG, "stop muxer");
                mMediaMuxer.stop();
                if (mListener != null) {
                    mListener.onRecordEnd(mUrl, mLastWrittenTimestampMs);
                }

                mState = STATE.STOPPED;
            } catch (IllegalStateException ise) {
                if (mListener != null) {
                    mListener.onRecordError(mUrl, -101, ise.getLocalizedMessage());
                }
            }
            DebugLog.d(TAG, "release muxer");
            mMediaMuxer.release();
        }
    }

}
