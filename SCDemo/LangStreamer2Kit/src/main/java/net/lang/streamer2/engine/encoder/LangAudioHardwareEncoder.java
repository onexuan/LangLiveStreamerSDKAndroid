package net.lang.streamer2.engine.encoder;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import net.lang.streamer2.engine.data.LangAudioConfiguration;
import net.lang.streamer2.engine.data.LangMediaBuffer;
import net.lang.streamer2.utils.DebugLog;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class LangAudioHardwareEncoder extends IBaseAudioEncoder {
    private static final String TAG = LangAudioHardwareEncoder.class.getSimpleName();

    private final Object mFence = new Object();

    private MediaCodec aencoder;
    private MediaCodec.BufferInfo aebi = new MediaCodec.BufferInfo();

    public LangAudioHardwareEncoder(LangAudioConfiguration audioConfiguration) {
        super(audioConfiguration);
    }

    public LangAudioHardwareEncoder(int sampleRate, int channel, int bitrateBps) {
        super(sampleRate, channel, bitrateBps);
    }

    @Override
    public boolean start() {
        synchronized (mFence) {

            // aencoder pcm to aac raw stream.
            // requires sdk level 16+, Android 4.1, 4.1.1, the JELLY_BEAN
            try {
                aencoder = MediaCodec.createEncoderByType(getCodecInfo());
            } catch (IOException e) {
                Log.e(TAG, "create aencoder failed.");
                e.printStackTrace();
                return false;
            }

            MediaFormat audioFormat = MediaFormat.createAudioFormat(getCodecInfo(), getSampleRate(), getChannel());
            audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, getBitrate());
            audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 4096);
            aencoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

            // start device and encoder.
            aencoder.start();
            return true;
        }
    }

    @Override
    public void stop() {
        synchronized (mFence) {
            if (aencoder != null) {
                DebugLog.i(TAG, "stop aencoder");
                aencoder.stop();
                aencoder.release();
                aencoder = null;
            }
        }
    }

    @Override
    public void encodeFrame(byte[] data, int dataLength, long presentationTimeUs) {
        synchronized (mFence) {
            if (aencoder == null) {
                DebugLog.w(TAG, "encodeFrame failed due to audio encoder not available");
                return;
            }
            ByteBuffer[] inBuffers = aencoder.getInputBuffers();
            ByteBuffer[] outBuffers = aencoder.getOutputBuffers();

            int inBufferIndex = aencoder.dequeueInputBuffer(-1);
            if (inBufferIndex >= 0) {
                ByteBuffer bb = inBuffers[inBufferIndex];
                bb.clear();
                if (getSilence()) {
                    byte[]  mute_data = new byte[dataLength];
                    Arrays.fill(mute_data, (byte)0);
                    bb.put(mute_data, 0, dataLength);
                }else {
                    bb.put(data, 0, dataLength);
                }
                aencoder.queueInputBuffer(inBufferIndex, 0, dataLength, presentationTimeUs, 0);
            }

            for (; ; ) {
                //aebi = new MediaCodec.BufferInfo();
                int outBufferIndex = aencoder.dequeueOutputBuffer(aebi, 0);
                if (outBufferIndex >= 0) {
                    ByteBuffer bb = outBuffers[outBufferIndex];
                    if (mEncodeListener != null) {
                        mEncodeListener.onEncodedAacFrame(bb, aebi);
                    }
                    aencoder.releaseOutputBuffer(outBufferIndex, false);
                } else {
                    break;
                }
            }
        }
    }

    @Override
    public void encodeFrame(LangMediaBuffer mediaBuffer) {
        this.encodeFrame(mediaBuffer.data(), mediaBuffer.dataLength(), mediaBuffer.presentationTimeUs());
    }

    @Override
    public void setSilence(boolean mute) {
        synchronized (mFence) {
            super.setSilence(mute);
        }
    }

    /**
     * Returns auto bitrate supported
     */
    @Override
    public boolean autoBitrate(boolean enable) {
        synchronized (mFence) {
            if (aencoder != null) {
                Bundle bundle = new Bundle();
                bundle.putInt(MediaFormat.KEY_BITRATE_MODE,
                        enable ? MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR:
                                MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    aencoder.setParameters(bundle);
                }else {
                    return false;
                }
            }
            return true;
        }
    }

    /*
     * Returns the new bitrate supported.
     */
    @Override
    public boolean setBitrate(int bitrateBps) {
        synchronized (mFence) {
            if (aencoder != null) {
                Bundle bundle = new Bundle();
                bundle.putInt(MediaFormat.KEY_BIT_RATE, bitrateBps);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    aencoder.setParameters(bundle);
                } else {
                    return false;
                }
            }
            return true;
        }
    }

}
