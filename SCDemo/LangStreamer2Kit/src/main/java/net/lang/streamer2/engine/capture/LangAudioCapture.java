package net.lang.streamer2.engine.capture;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;

import net.lang.streamer2.engine.data.LangAudioConfiguration;
import net.lang.streamer2.utils.DebugLog;

public final class LangAudioCapture {
    private static final String TAG = LangAudioCapture.class.getSimpleName();

    private LangAudioCaptureListener mListener;

    private AudioRecordThread mAudioRecordThread;
    private AudioRecord mAudioSource;
    private AcousticEchoCanceler mAEC;
    private AutomaticGainControl mAGC;
    private byte[] mPcmBuffer = new byte[4096];

    private final Object mFence = new Object();
    private boolean mStarted = false;

    public LangAudioCapture(LangAudioConfiguration audioConfiguration)
            throws IllegalArgumentException, CaptureRuntimeException {
        int samplerate = audioConfiguration.getSampleRate();
        if (samplerate != 24000 && samplerate != 44100 && samplerate != 48000) {
            throw new IllegalArgumentException("audio samplerate unsupported, samplerate = " + samplerate);
        }

        int channel = audioConfiguration.getChannel();
        if (channel > 2) {
            throw new IllegalArgumentException("audio channel count invalid, channel = " + channel);
        }

        mAudioSource = chooseAudioRecord(samplerate, channel);
        if (mAudioSource == null) {
            throw new CaptureRuntimeException("AudioRecord create failed");
        }
        if (AcousticEchoCanceler.isAvailable()) {
            mAEC = AcousticEchoCanceler.create(mAudioSource.getAudioSessionId());
        }
        if (AutomaticGainControl.isAvailable()) {
            mAGC = AutomaticGainControl.create(mAudioSource.getAudioSessionId());
        }
    }

    public void setCaptureListener(LangAudioCaptureListener listener) {
        mListener = listener;
    }

    public void start() throws IllegalStateException {
        synchronized (mFence) {
            if (!mStarted) {
                if (mAEC != null) {
                    mAEC.setEnabled(true);
                }
                if (mAGC != null) {
                    mAGC.setEnabled(true);
                }
                mAudioSource.startRecording();
                mAudioRecordThread = new AudioRecordThread();
                mAudioRecordThread.start();

                mStarted = true;

                DebugLog.d(TAG, "audio capture start");
            }
        }
    }

    public void stop() {
        synchronized (mFence) {
            if (mStarted) {
                mAudioRecordThread.quit();
                try {
                    mAudioRecordThread.join();
                } catch (InterruptedException ignored) {
                    // ignore
                }
                mAudioSource.setRecordPositionUpdateListener(null);
                mAudioSource.stop();

                if (mAEC != null) {
                    mAEC.setEnabled(false);
                }
                if (mAGC != null) {
                    mAGC.setEnabled(false);
                }

                mStarted = false;

                DebugLog.d(TAG, "audio capture stop");
            }
        }
    }

    public void release() {
        synchronized (mFence) {
            if (mAudioSource != null) {
                mAudioSource.release();
                mAudioSource = null;
            }

            if (mAEC != null) {
                mAEC.release();
                mAEC = null;
            }
            if (mAGC != null) {
                mAGC.release();
                mAGC = null;
            }

            mListener = null;
            DebugLog.d(TAG, "audio capture release");
        }
    }

    private class AudioRecordThread extends Thread {
        private boolean running;

        AudioRecordThread() {
            running = true;
        }

        void quit() {
            running = false;
        }

        @Override
        public void run() {
            DebugLog.d(TAG, "AudioRecordThread,tid=" + Thread.currentThread().getId());
            while (running) {
                int size = mAudioSource.read(mPcmBuffer, 0, mPcmBuffer.length);
                if (running && size > 0) {
                    if (mListener != null) {
                        mListener.onCapturedPcmFrame(mPcmBuffer, mPcmBuffer.length, System.nanoTime());
                    }
                }
            }
        }
    }

    private static AudioRecord chooseAudioRecord(int samplerate, int channel) {
        int channelConfig = channel >= 2 ? AudioFormat.CHANNEL_IN_STEREO : AudioFormat.CHANNEL_IN_MONO;
        AudioRecord mic = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, samplerate,
                channelConfig, AudioFormat.ENCODING_PCM_16BIT, getPcmBufferSize(samplerate, channel) * 4);
        if (mic.getState() != AudioRecord.STATE_INITIALIZED) {
            DebugLog.d(TAG, "audio record create failed due to invalid status");
            mic = null;
        }
        return mic;
    }

    private static int getPcmBufferSize(int samplerate, int channel) {
        int channelConfig = channel >= 2 ? AudioFormat.CHANNEL_IN_STEREO : AudioFormat.CHANNEL_IN_MONO;
        int pcmBufSize = AudioRecord.getMinBufferSize(samplerate, channelConfig, AudioFormat.ENCODING_PCM_16BIT) + 8191;
        return pcmBufSize - (pcmBufSize % 8192);
    }

    public interface LangAudioCaptureListener {
        void onCapturedPcmFrame(byte[] data, int size, long timestampNs);
    }
}
