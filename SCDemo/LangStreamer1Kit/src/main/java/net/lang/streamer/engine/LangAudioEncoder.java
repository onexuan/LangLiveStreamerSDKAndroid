package net.lang.streamer.engine;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.util.Log;

import net.lang.streamer.engine.data.LangEngineParams;

import java.io.IOException;
import java.nio.ByteBuffer;

import static java.util.Arrays.fill;

public class LangAudioEncoder {
    private static final String TAG = "LangAudioEncoder";

    public static final String ACODEC = "audio/mp4a-latm";
    //public static final int ASAMPLERATE = 44100;
    public static int aChannelConfig = AudioFormat.CHANNEL_IN_STEREO;
    //public static final int ABITRATE = 128 * 1024;  // 128 kbps

    private IRtmpMediaMuxer flvMuxer;//private LangFlvMuxer flvMuxer;
    private LangMp4Muxer mp4Muxer;

    private MediaCodec aencoder;
    private MediaCodec.BufferInfo aebi = new MediaCodec.BufferInfo();

    private long mPresentTimeUs;

    private int audioFlvTrack;
    private int audioMp4Track;
    private boolean mMute = false;

    public LangAudioEncoder() {

    }

    public void mute(boolean mute) {
        mMute = mute;
    }

    public void setFlvMuxer(IRtmpMediaMuxer flvMuxer) {
        this.flvMuxer = flvMuxer;
    }

    public void setMp4Muxer(LangMp4Muxer mp4Muxer) {
        this.mp4Muxer = mp4Muxer;
    }

    public boolean start() {
        if (flvMuxer == null || mp4Muxer == null) {
            return false;
        }

        // the referent PTS for video and audio encoder.
        mPresentTimeUs = System.nanoTime() / 1000;

        // aencoder pcm to aac raw stream.
        // requires sdk level 16+, Android 4.1, 4.1.1, the JELLY_BEAN
        try {
            aencoder = MediaCodec.createEncoderByType(ACODEC);
        } catch (IOException e) {
            Log.e(TAG, "create aencoder failed.");
            e.printStackTrace();
            return false;
        }

        // setup the aencoder.
        // @see https://developer.android.com/reference/android/media/MediaCodec.html
        aChannelConfig = LangEngineParams.aChannel >=2 ? AudioFormat.CHANNEL_IN_STEREO : AudioFormat.CHANNEL_IN_MONO;
        int ach = aChannelConfig == AudioFormat.CHANNEL_IN_STEREO ? 2 : 1;
        int aSamplerate = LangEngineParams.aSamplerate;
        int bitrateBps = LangEngineParams.aOutputBitrateKbps * 1000;
        MediaFormat audioFormat = MediaFormat.createAudioFormat(ACODEC, aSamplerate, ach);
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrateBps);
        audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 4096);
        aencoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        // add the audio tracker to muxer.
        audioFlvTrack = flvMuxer.addTrack(audioFormat);
        audioMp4Track = mp4Muxer.addTrack(audioFormat);

        // start device and encoder.
        aencoder.start();
        return true;
    }

    public void stop() {
        if (aencoder != null) {
            Log.i(TAG, "stop aencoder");
            aencoder.stop();
            aencoder.release();
            aencoder = null;
        }
    }

    // when got encoded aac raw stream.
    private void onEncodedAacFrame(ByteBuffer es, MediaCodec.BufferInfo bi) {
        mp4Muxer.writeSampleData(audioMp4Track, es.duplicate(), bi);
        flvMuxer.writeSampleData(audioFlvTrack, es, bi);
    }

    public void onGetPcmFrame(byte[] data, int size) {
        ByteBuffer[] inBuffers = aencoder.getInputBuffers();
        ByteBuffer[] outBuffers = aencoder.getOutputBuffers();

        int inBufferIndex = aencoder.dequeueInputBuffer(-1);
        if (inBufferIndex >= 0) {
            ByteBuffer bb = inBuffers[inBufferIndex];
            bb.clear();
            if (mMute) {
                byte[]  mute_data = new byte[size];
                fill(mute_data, (byte)0);
                bb.put(mute_data, 0, size);
            }else {
                bb.put(data, 0, size);
            }
            long pts = System.nanoTime() / 1000 - mPresentTimeUs;
            aencoder.queueInputBuffer(inBufferIndex, 0, size, pts, 0);
        }

        for (; ; ) {
            //aebi = new MediaCodec.BufferInfo();
            int outBufferIndex = aencoder.dequeueOutputBuffer(aebi, 0);
            if (outBufferIndex >= 0) {
                ByteBuffer bb = outBuffers[outBufferIndex];
                onEncodedAacFrame(bb, aebi);
                aencoder.releaseOutputBuffer(outBufferIndex, false);
            } else {
                break;
            }
        }
    }

    public static AudioRecord chooseAudioRecord() {
        int aSamplerate = LangEngineParams.aSamplerate;
        int channelConfig = LangEngineParams.aChannel >= 2 ? AudioFormat.CHANNEL_IN_STEREO : AudioFormat.CHANNEL_IN_MONO;
        AudioRecord mic = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, aSamplerate,
                channelConfig, AudioFormat.ENCODING_PCM_16BIT, getPcmBufferSize() * 4);
        if (mic.getState() != AudioRecord.STATE_INITIALIZED) {
            mic = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, aSamplerate,
                    channelConfig, AudioFormat.ENCODING_PCM_16BIT, getPcmBufferSize() * 4);
            if (mic.getState() != AudioRecord.STATE_INITIALIZED) {
                mic = null;
            } else {
                LangAudioEncoder.aChannelConfig = channelConfig;
            }
        } else {
            LangAudioEncoder.aChannelConfig = channelConfig;
        }

        return mic;
    }

    private static int getPcmBufferSize() {
        int aSamplerate = LangEngineParams.aSamplerate;
        int pcmBufSize = AudioRecord.getMinBufferSize(aSamplerate, AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT) + 8191;
        return pcmBufSize - (pcmBufSize % 8192);
    }
}