package net.lang.streamer.engine;

import android.media.AudioRecord;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;

import com.github.faucamp.simplertmp.RtmpHandler;

import java.io.File;

public class LangMediaPublisher {

    private static AudioRecord mic;
    private static AcousticEchoCanceler aec;
    private static AutomaticGainControl agc;
    private byte[] mPcmBuffer = new byte[4096];
    private Thread aworker;

    //private boolean sendAudioOnly = false;
    private boolean audioStart = false;

    private IRtmpMediaMuxer mFlvMuxer;//private LangFlvMuxer mFlvMuxer;
    private LangMp4Muxer mMp4Muxer;
    private LangAudioEncoder mAudioEncoder;
    private LangVideoEncoder mVideoEncoder;
    private IMediaPublisherListener mPublisherListener;

    public LangMediaPublisher() {

    }

    public void setPublisherListener(IMediaPublisherListener listener) {
        mPublisherListener = listener;
    }

    public void startAudio() {
        mic = LangAudioEncoder.chooseAudioRecord();
        if (mic == null) {
            if (mPublisherListener != null) {
                mPublisherListener.onPublisherEvent(IMediaPublisherListener.Type.kTypeMic, IMediaPublisherListener.Value.kFailed);
            }
            return;
        } else {
            if (mPublisherListener != null) {
                mPublisherListener.onPublisherEvent(IMediaPublisherListener.Type.kTypeMic, IMediaPublisherListener.Value.kSucceed);
            }
        }

        if (AcousticEchoCanceler.isAvailable()) {
            aec = AcousticEchoCanceler.create(mic.getAudioSessionId());
            if (aec != null) {
                aec.setEnabled(true);
            }
        }

        if (AutomaticGainControl.isAvailable()) {
            agc = AutomaticGainControl.create(mic.getAudioSessionId());
            if (agc != null) {
                agc.setEnabled(true);
            }
        }

        aworker = new Thread(new Runnable() {
            @Override
            public void run() {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
                mic.startRecording();
                while (!Thread.interrupted()) {
                    int size = mic.read(mPcmBuffer, 0, mPcmBuffer.length);
                    if (size <= 0) {
                        break;
                    }
                    if (mAudioEncoder != null && audioStart) {
                        mAudioEncoder.onGetPcmFrame(mPcmBuffer, size);
                    }
                    //mAudioEncoder.onGetPcmFrame(mPcmBuffer, size);
                }
            }
        });
        aworker.setName("AudioPublisher");
        aworker.start();
    }

    public void stopAudio() {
        if (aworker != null) {
            aworker.interrupt();
            try {
                aworker.join();
            } catch (InterruptedException e) {
                aworker.interrupt();
            }
            aworker = null;
        }

        if (mic != null) {
            mic.setRecordPositionUpdateListener(null);
            mic.stop();
            mic.release();
            mic = null;
        }

        if (aec != null) {
            aec.setEnabled(false);
            aec.release();
            aec = null;
        }

        if (agc != null) {
            agc.setEnabled(false);
            agc.release();
            agc = null;
        }
    }

    public void startEncode() {
        if (!mAudioEncoder.start()) {
            if (mPublisherListener != null) {
                mPublisherListener.onPublisherEvent(IMediaPublisherListener.Type.kTypeAudioEncoder, IMediaPublisherListener.Value.kFailed);
            }
            return;
        }else {
            if (mPublisherListener != null) {
                mPublisherListener.onPublisherEvent(IMediaPublisherListener.Type.kTypeAudioEncoder, IMediaPublisherListener.Value.kSucceed);
            }
        }
        if (!mVideoEncoder.start()) {
            if (mPublisherListener != null) {
                mPublisherListener.onPublisherEvent(IMediaPublisherListener.Type.kTypeAudioEncoder, IMediaPublisherListener.Value.kFailed);
            }
            return;
        }else {
            if (mPublisherListener != null) {
                mPublisherListener.onPublisherEvent(IMediaPublisherListener.Type.kTypeAudioEncoder, IMediaPublisherListener.Value.kSucceed);
            }
        }
        //startAudio();
        audioStart = true;
    }

    // this method is accessed from rtc module, which is used to judge whether rtc should push
    // raw audio streams to encoder for avoiding encoder crash.(eg, when rtmp push is stopped,
    // rtc need to know the encoder status)
    public boolean isAudioStart() {
        return audioStart;
    }

    public void stopEncode() {
        //stopAudio();
        audioStart = false;

        mVideoEncoder.stop();
        mAudioEncoder.stop();
    }

    public void startPublish(String rtmpUrl) {
        if (mFlvMuxer != null) {
            mFlvMuxer.setVideoResolution(mVideoEncoder.getOutputWidth(), mVideoEncoder.getOutputHeight());
            mFlvMuxer.start(rtmpUrl);
            startEncode();
        }
    }

    public void stopPublish() {
        if (mFlvMuxer != null) {
            stopEncode();
            mFlvMuxer.stop();
        }
    }

    public boolean startRecord(String recPath) {
        boolean rev = mMp4Muxer != null && mMp4Muxer.record(new File(recPath));
        if (mPublisherListener != null) {
            mPublisherListener.onPublisherEvent(IMediaPublisherListener.Type.kTypeRecord,
                    rev ? IMediaPublisherListener.Value.kSucceed : IMediaPublisherListener.Value.kFailed);
        }
        return rev;
    }

    public void stopRecord() {
        if (mMp4Muxer != null) {
            mMp4Muxer.stop();
        }
    }

    public void pauseRecord() {
        if (mMp4Muxer != null) {
            mMp4Muxer.pause();
        }
    }

    public void resumeRecord() {
        if (mMp4Muxer != null) {
            mMp4Muxer.resume();
        }
    }

    public int getVideoWidth() {
        if (mVideoEncoder != null) {
            return mVideoEncoder.getOutputWidth();
        }
        return 0;
    }

    public int getVideoHeight() {
        if (mVideoEncoder != null) {
            return mVideoEncoder.getOutputHeight();
        }
        return 0;
    }

    public void setVideoResolution(int width, int height) {
        if (mVideoEncoder != null) {
            mVideoEncoder.setVideoResolution(width, height);
        }
    }

    public void setScreenOrientation(int orientation) {
        if (mVideoEncoder != null) {
            mVideoEncoder.setScreenOrientation(orientation);
        }
    }

    public void setRtmpHandler(RtmpHandler handler) {
        mFlvMuxer = new LangFlvMuxer(handler);
        //mFlvMuxer = new LangRtmpMuxer(handler);
        if (mAudioEncoder != null) {
            mAudioEncoder.setFlvMuxer(mFlvMuxer);
        }
        if (mVideoEncoder != null) {
            mVideoEncoder.setFlvMuxer(mFlvMuxer);
        }
    }

    public void setRecordHandler(LangRecordHandler handler) {
        mMp4Muxer = new LangMp4Muxer(handler);
        if (mAudioEncoder != null) {
            mAudioEncoder.setMp4Muxer(mMp4Muxer);
        }
        if (mVideoEncoder != null) {
            mVideoEncoder.setMp4Muxer(mMp4Muxer);
        }
    }

    public void setEncodeHandler(LangEncodeHandler handler, LangVideoEncoderImpl.EncoderType type) {
        mAudioEncoder = new LangAudioEncoder();
        mVideoEncoder = LangVideoEncoderImpl.create(handler, type);
        if (mFlvMuxer != null) {
            mAudioEncoder.setFlvMuxer(mFlvMuxer);
            mVideoEncoder.setFlvMuxer(mFlvMuxer);
        }
        if (mMp4Muxer != null) {
            mAudioEncoder.setMp4Muxer(mMp4Muxer);
            mVideoEncoder.setMp4Muxer(mMp4Muxer);
        }
    }

    public LangVideoEncoder getVideoEncoder() {
        return mVideoEncoder;
    }
    public LangAudioEncoder getAudioEncoder() { return mAudioEncoder; }

    public int getVideoFrameCacheNumber() {
        if (mFlvMuxer != null) {
            return mFlvMuxer.getVideoFrameCacheNumber().get();
        }
        return -1;
    }

    /**
     * get cached audio frame number in publisher
     */
    public int getAudioFrameCacheNumber() {
        if (mFlvMuxer != null) {
            return mFlvMuxer.getAudioFrameCacheNumber().get();
        }
        return -1;
    }

    public int getPushVideoFrameCounts() {
        if (mFlvMuxer != null) {
            return mFlvMuxer.getPushVideoFrameCounts();
        }
        return -1;
    }

    public double getPushVideoFps() {
        if (mFlvMuxer != null) {
            return mFlvMuxer.getPushVideoFps();
        }
        return 0f;
    }

    /**
     * release media component elements.
     */
    public void release() {
        if (mFlvMuxer != null) {
            mFlvMuxer.release();
        }
    }
}