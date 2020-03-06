package net.lang.streamer2.engine.encoder;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;

import net.lang.streamer2.engine.data.LangMediaBuffer;
import net.lang.streamer2.engine.data.LangVideoConfiguration;
import net.lang.streamer2.utils.DebugLog;

import java.io.IOException;
import java.nio.ByteBuffer;

public class LangVideoHardwareEncoder extends IBaseVideoEncoder {
    private static final String TAG = LangVideoHardwareEncoder.class.getSimpleName();

    final Object mFence = new Object();

    // media codec context.
    MediaCodecInfo mMediaCodecInfo;
    MediaCodec vencoder;
    MediaCodec.BufferInfo vebi = new MediaCodec.BufferInfo();
    MediaCodecInfo.CodecProfileLevel mProfileLevel = new MediaCodecInfo.CodecProfileLevel();

    public LangVideoHardwareEncoder(LangVideoConfiguration videoConfiguration)
            throws EncoderRuntimeException {
        super(videoConfiguration);

        initialize();
    }

    public LangVideoHardwareEncoder(int width, int height, int fps, int bitrateBps, int keyFrameIntervalSec)
            throws EncoderRuntimeException {
        super(width, height, fps, bitrateBps, keyFrameIntervalSec);

        initialize();
    }

    /**
     * called from streamer push mode
     */
    @Override
    public boolean start() {
        synchronized (mFence) {
            boolean ready = prepareEncoder(getCodecInfo(), getWidth(), getHeight(), getBitrate(),
                    getFps(), getEncoderFormat(), getEncoderKeyFrameInterval());
            if (!ready) {
                DebugLog.w(TAG, "prepareEncoder failed");
                return false;
            }

            startInternal();
            return true;
        }
    }

    /**
     * Called from global controller media engine, internal stop rountine may be different
     * according to different input type (YUV buffer or direct surface).
     */
    @Override
    public void stop() {
        synchronized (mFence) {
            stopInternal();
        }
    }

    @Override
    public void encodeFrame(byte[] data, int dataLength, long presentationTimeUs) {
        synchronized (mFence) {
            if (vencoder == null) {
                DebugLog.w(TAG, "encodeFrame failed due to video encoder not available");
                return;
            }
            if (data == null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    vencoder.signalEndOfInputStream();
                }
                return;
            }

            ByteBuffer[] inBuffers = vencoder.getInputBuffers();
            ByteBuffer[] outBuffers = vencoder.getOutputBuffers();
            int inBufferIndex = vencoder.dequeueInputBuffer(-1);
            if (inBufferIndex >= 0) {
                ByteBuffer bb = inBuffers[inBufferIndex];
                bb.clear();
                bb.put(data, 0, dataLength);
                vencoder.queueInputBuffer(inBufferIndex, 0, dataLength, presentationTimeUs, 0);
            }

            for (; ; ) {
                int outBufferIndex = vencoder.dequeueOutputBuffer(vebi, 0);
                if (outBufferIndex >= 0) {
                    ByteBuffer bb = outBuffers[outBufferIndex];
                    if (mEncodeListener != null) {
                        mEncodeListener.onEncodedAnnexbFrame(bb, vebi);
                    }
                    vencoder.releaseOutputBuffer(outBufferIndex, false);
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

    /**
     * Returns auto bitrate supported
     */
    @Override
    public boolean autoBitrate(boolean enable) {
        synchronized (mFence) {
            if (vencoder != null) {
                Bundle bundle = new Bundle();
                bundle.putInt(MediaFormat.KEY_BITRATE_MODE,
                        enable ? MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR:
                                MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    vencoder.setParameters(bundle);
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
            if (vencoder != null) {
                Bundle bundle = new Bundle();
                bundle.putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, bitrateBps);
                //bundle.putInt(MediaFormat.KEY_BIT_RATE, bitrateBps);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    vencoder.setParameters(bundle);
                } else {
                    return false;
                }
            }
            return super.setBitrate(bitrateBps);
        }
    }

    /*
     * initialize video encoder profile/level/color-format
     */
    void initialize() {
        mProfileLevel.profile = MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline;
        mProfileLevel.level = MediaCodecInfo.CodecProfileLevel.AVCLevel1;

        int actualColorFormat = initializeEncoder();
        setEncoderFormat(actualColorFormat);
    }

    /**
     * configures encoder,
     */
    boolean prepareEncoder(String mime, int width, int height, int bitrate, int fps,
                                   int format, int keyFrameInterval) {
        try {
            vencoder = MediaCodec.createByCodecName(mMediaCodecInfo.getName());
        } catch (IOException e) {
            Log.e(TAG, "create vencoder internal failed.");
            e.printStackTrace();
            return false;
        }
        MediaFormat videoFormat = null;
        try {
            // configure mediacodec encoder.
            videoFormat = MediaFormat.createVideoFormat(mime, width, height);
            videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, format);
            videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
            videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
            videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, keyFrameInterval);
            videoFormat.setInteger(MediaFormat.KEY_PROFILE, mProfileLevel.profile);
            videoFormat.setInteger(MediaFormat.KEY_LEVEL, mProfileLevel.level);
            //videoFormat.setInteger(MediaFormat.KEY_INTRA_REFRESH_PERIOD, keyFrameInterval/*1*/);
            //videoFormat.setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1);
            videoFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR);
            //videoFormat.setInteger(MediaFormat.KEY_STRIDE, ((width + 31) / 32 ) * 32);
            //videoFormat.setInteger(MediaFormat.KEY_SLICE_HEIGHT, ((height + 31) / 32 ) * 32);

            String str = videoFormat.toString();
            vencoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        } catch (RuntimeException e) {
            Log.w(TAG, "configure vencoder internal failed with reconfig...(" + e.getMessage() + ")");
            videoFormat = MediaFormat.createVideoFormat(mime, width, height);
            videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, format);
            videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
            videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
            videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, keyFrameInterval);
            try {
                vencoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            }catch (RuntimeException e2) {
                e2.printStackTrace();
                return false;
            }
        }

        DebugLog.d(TAG, "encoder configure: " + videoFormat.toString());

        return true;
    }

    private void startInternal() {
        if (vencoder != null) {
            vencoder.start();
        }
    }

    private void stopInternal() {
        if (vencoder != null) {
            vencoder.stop();
            vencoder.release();
            vencoder = null;
        }
    }

    private int initializeEncoder() throws EncoderRuntimeException {
        mMediaCodecInfo = chooseVideoEncoder(getCodecInfo());
        if (mMediaCodecInfo == null) {
            throw new EncoderRuntimeException("initailize video encoder failed");
        }
        MediaCodecInfo.CodecCapabilities codecCapabilities = mMediaCodecInfo.getCapabilitiesForType(getCodecInfo());
        mProfileLevel = chooseEncoderProfile(getCodecInfo(), codecCapabilities);

        return chooseEncoderFormat(codecCapabilities);
    }

    // choose the video encoder by name.
    private MediaCodecInfo chooseVideoEncoder(String encoderName) {
        if (encoderName == null) {
            throw new EncoderRuntimeException("chooseVideoEncoder: encoder name is null");
        }
        int codecCount = MediaCodecList.getCodecCount();
        for (int i = 0; i < codecCount; i++) {
            MediaCodecInfo mediaCodecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!mediaCodecInfo.isEncoder()) {
                continue;
            }

            String[] types = mediaCodecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(encoderName)) {
                    DebugLog.d(TAG, String.format("vencoder %s types: %s", mediaCodecInfo.getName(), types[j]));
                    if (mediaCodecInfo.getName().contains("OMX.google")) {
                        DebugLog.w(TAG, "no hardware encoder available now, force using google encoder");
                    } else if (mediaCodecInfo.getName().contains("OMX.MTK")) {
                        DebugLog.w(TAG, "Warning: you are using MediaTek video encoder");
                        continue;
                    } else if (mediaCodecInfo.getName().contains("OMX.SEC")) {
                        DebugLog.d(TAG, "you are using Samsung video encoder");
                    } else {
                        DebugLog.d(TAG, "your encoder is:" + mediaCodecInfo.getName());
                    }
                    return mediaCodecInfo;
                }
            }
        }

        DebugLog.w(TAG, "no available video encoder: " + encoderName);
        return null;
    }

    // choose the encoder profile by encoder name
    private MediaCodecInfo.CodecProfileLevel chooseEncoderProfile(String encoderName, MediaCodecInfo.CodecCapabilities codecCapabilities) {
        MediaCodecInfo.CodecProfileLevel selectedProfileLevel = new MediaCodecInfo.CodecProfileLevel();

        if (encoderName.equalsIgnoreCase("video/avc")) {
            selectedProfileLevel.profile = MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline;
            selectedProfileLevel.level = MediaCodecInfo.CodecProfileLevel.AVCLevel1;

            for (int i = 0; i < codecCapabilities.profileLevels.length; i++) {
                MediaCodecInfo.CodecProfileLevel currentProfileLevel = codecCapabilities.profileLevels[i];
                // encoder support avc main profile, just use it.
                if (currentProfileLevel.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileMain
                        && selectedProfileLevel.profile < MediaCodecInfo.CodecProfileLevel.AVCProfileMain) {
                    selectedProfileLevel = currentProfileLevel;
                }
                // encoder support avc high profile, just use it.
                else if (currentProfileLevel.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileHigh
                        && selectedProfileLevel.profile < MediaCodecInfo.CodecProfileLevel.AVCProfileHigh) {
                    selectedProfileLevel = currentProfileLevel;
                }
            }
            return  selectedProfileLevel;
        } else {
            throw new EncoderRuntimeException("chooseEncoderProfile: other encoder is not supported");
        }
    }

    private int chooseEncoderFormat(MediaCodecInfo.CodecCapabilities codecCapabilities) {
        int matchedColorFormat = 0;

        for (int i = 0; i < codecCapabilities.colorFormats.length; i++) {
            int cf = codecCapabilities.colorFormats[i];
            DebugLog.i(TAG, String.format("vencoder supports color fomart 0x%x(%d)",cf, cf));

            // choose YUV for h.264, prefer the bigger one.
            // corresponding to the color space transform in onPreviewFrame
            if (cf >= codecCapabilities.COLOR_FormatYUV420Planar && cf <= codecCapabilities.COLOR_FormatYUV420SemiPlanar) {
                if (cf > matchedColorFormat) {
                    matchedColorFormat = cf;
                }
            }
        }
        DebugLog.i(TAG, String.format("choose color format 0x%x(%d)", matchedColorFormat, matchedColorFormat));

        return matchedColorFormat;
    }
}
