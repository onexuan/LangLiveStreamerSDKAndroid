package net.lang.streamer.engine;

import android.content.res.Configuration;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;

import net.lang.streamer.engine.data.LangEngineParams;
import net.lang.streamer.utils.DebugLog;
import net.lang.streamer.utils.SpeedStatistics;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
import static android.media.MediaCodecInfo.CodecProfileLevel.AVCProfileHigh;
import static android.media.MediaCodecInfo.CodecProfileLevel.AVCProfileMain;

/**
 * Created by rayman.lee on 2017/03/16.
 */
public class LangVideoEncoder {
    private static final String TAG = "LangVideoEncoder";

    public static final String VCODEC = "video/avc";

    public static int vOutWidth = 720;   // default width value.
    public static int vOutHeight = 1280; // default height value
    //public static int vBitrate = 1200 * 1024;  // 1200 kbps
    //public static final int VFPS = 24;
    //public static final int VGOP = 48;

    private LangEncodeHandler mHandler;

    protected IRtmpMediaMuxer flvMuxer;//protected LangFlvMuxer flvMuxer;
    protected LangMp4Muxer mp4Muxer;

    protected MediaCodecInfo vmci;
    protected MediaCodec vencoder;
    protected MediaCodec.BufferInfo vebi = new MediaCodec.BufferInfo();

    private Surface mInputSurface = null;
    boolean useSurfaceInput = false;

    private boolean networkWeakTriggered = false;
    private boolean useSoftEncoder = false;
    private boolean canSoftEncode = false;

    protected long mPresentTimeUs;

    protected int mVideoColorFormat;
    protected int mNativeColorFormat = (int)'I' | (int)'4' << 8 | (int)'2' << 16 | (int)'0' << 24;

    protected int videoFlvTrack;
    protected int videoMp4Track;

    private int mTotalDiscardFrameCounts = 0;
    private SpeedStatistics mDiscardFrameRate  = null;
    private MediaCodecInfo.CodecProfileLevel mProfileLevel = new MediaCodecInfo.CodecProfileLevel();

    // Y, U (Cb) and V (Cr)
    // yuv420                     yuv yuv yuv yuv
    // yuv420p (planar)   yyyy*2 uu vv
    // yuv420sp(semi-planner)   yyyy*2 uv uv
    // I420 -> YUV420P   yyyy*2 uu vv
    // YV12 -> YUV420P   yyyy*2 vv uu
    // NV12 -> YUV420SP  yyyy*2 uv uv
    // NV21 -> YUV420SP  yyyy*2 vu vu
    // NV16 -> YUV422SP  yyyy uv uv
    // YUY2 -> YUV422SP  yuyv yuyv
    public LangVideoEncoder(LangEncodeHandler handler, boolean fromSurface) {
        mHandler = handler;
        mProfileLevel.profile = MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline;
        mProfileLevel.level = MediaCodecInfo.CodecProfileLevel.AVCLevel1;
        mVideoColorFormat = chooseVideoEncoder();
        useSurfaceInput = fromSurface;
        mDiscardFrameRate = new SpeedStatistics();
    }

    public LangVideoEncoderImpl.EncoderBuffer getBuffer() {
        return null;
    }


    public void setFlvMuxer(IRtmpMediaMuxer flvMuxer) {
        this.flvMuxer = flvMuxer;
    }

    public void setMp4Muxer(LangMp4Muxer mp4Muxer) {
        this.mp4Muxer = mp4Muxer;
    }

    public int getColorFormat() {
        return mVideoColorFormat;
    }

    public boolean start() {
        if (flvMuxer == null || mp4Muxer == null) {
            Log.e(TAG, "no media sink available.");
            return false;
        }

        int bitrateBps = LangEngineParams.vOutputBitrateKbps * 1000;
        int encFps = LangEngineParams.vOutputFps;
        int KeyFrameIntervalSec = LangEngineParams.vOutKeyFrameIntervalSec;

        MediaFormat videoFormat = prepareVideoFormatInfo(VCODEC, vOutWidth, vOutHeight, bitrateBps, encFps);
        // add the video tracker to muxer.
        videoFlvTrack = flvMuxer.addTrack(videoFormat);
        videoMp4Track = mp4Muxer.addTrack(videoFormat);

        // if we use surface to produce video frames, then
        // we should not start media codec directly.
        if (useSurfaceInput) {
            return true;
        } else {
            // the referent PTS for video and audio encoder.
            mPresentTimeUs = System.nanoTime() / 1000;

            // Note: the stride of resolution must be set as 16x
            // for hard encoding with some chip like MTK
            // Since Y component is quadruple size as U and V component,
            // the stride must be set as 32x
            if (!useSoftEncoder && vOutWidth % 32 != 0 || vOutHeight % 32 != 0) {
                if (vmci.getName().contains("MTK")) {
                    //throw new AssertionError("MTK encoding revolution stride must be 32x");
                }
            }

            return prepareEncoder(VCODEC, vOutWidth, vOutHeight, bitrateBps,
                    encFps, mVideoColorFormat, KeyFrameIntervalSec, false);
        }
    }

    /**
     * called asynchromously from TextureMovieEncoder handler thread.
     */
    public boolean onStartAsync() {
        Log.i(TAG, "start vencoder async!!!");
        int surfaceFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;
        int bitrateBps = LangEngineParams.vOutputBitrateKbps * 1000;
        int encFps = LangEngineParams.vOutputFps;
        int KeyFrameIntervalSec = LangEngineParams.vOutKeyFrameIntervalSec;
        boolean success = prepareEncoder(VCODEC, vOutWidth, vOutHeight, bitrateBps,
                encFps, surfaceFormat, KeyFrameIntervalSec, true);
        return success;
    }

    public boolean autoBitrate(boolean enable) {
        boolean rev = false;
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

    /**
     * Returns the encoder's input surface.
     */
    public Surface getInputSurface() {
        return mInputSurface;
    }

    /**
     * Configures encoder, and prepares the input Surface.
     */
    private boolean prepareEncoder(String mime, int width, int height, int bitrate, int fps,
                                   int format, int keyFrameInterval, boolean useSurface) {
        try {
            vencoder = MediaCodec.createByCodecName(vmci.getName());
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
        if (useSurface) {
            mInputSurface = vencoder.createInputSurface();
        }
        vencoder.start();
        return true;
    }

    /**
     * Construct a media format info for flv and mp4 container,
     * indicating basic stream info.
     */
    protected MediaFormat prepareVideoFormatInfo(String mime, int width, int height, int bitrate, int fps) {
        MediaFormat videoFormat = MediaFormat.createVideoFormat(mime, width, height);
        videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, fps);

        return videoFormat;
    }

    /**
     * Called from global controller media engine, internal stop rountine may be different
     * according to different input type (YUV buffer or direct surface).
     */
    public void stop() {
        // if we use surface to produce video frames, then
        // we should not stop media codec directly.
        if (useSurfaceInput) {
            return;
        }
        stopInternal();
    }

    /**
     * called asynchromously from TextureMovieEncoder handler thread.
     */
    public void onStopAsync() {
        Log.i(TAG, "stop vencoder async!!!");
        stopInternal();
    }

    private void stopInternal() {
        if (vencoder != null) {
            vencoder.stop();
            vencoder.release();
            vencoder = null;
        }

        // clear all encoder messages when stopped.
        mHandler.removeCallbacksAndMessages(null);
    }

    public void setVideoResolution(int width, int height) {
        vOutWidth = width;
        vOutHeight = height;
    }

    public int getOutputWidth() {
        return vOutWidth;
    }

    public int getOutputHeight() {
        return vOutHeight;
    }

    public void setScreenOrientation(int orientation) {
        // make sure tmpWidth is always smaller than tmpHeight
        int tmpWidth, tmpHeight;
        if (vOutWidth < vOutHeight) {
            tmpWidth = vOutWidth;
            tmpHeight = vOutHeight;
        } else {
            tmpWidth = vOutHeight;
            tmpHeight = vOutWidth;
        }
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            vOutWidth = tmpWidth;
            vOutHeight = tmpHeight;
        } else if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            vOutWidth = tmpHeight;
            vOutHeight = tmpWidth;
        }
    }

    public boolean shouldSkipInputWhenStreaming() {
        boolean drop;
        // Check video frame cache number to judge the networking situation.
        // Just cache GOP / FPS seconds data according to latency.
        AtomicInteger videoFrameCacheNumber = flvMuxer.getVideoFrameCacheNumber();

        int encFps = LangEngineParams.vOutputFps;
        int KeyFrameIntervalSec = LangEngineParams.vOutKeyFrameIntervalSec;
        int vEncFramesGop = encFps * KeyFrameIntervalSec;
        if (videoFrameCacheNumber != null && videoFrameCacheNumber.get() < vEncFramesGop) {
            if (networkWeakTriggered) {
                networkWeakTriggered = false;
                mHandler.notifyNetworkResume();
            }
            drop = false;
        } else {
            mHandler.notifyNetworkWeak();
            networkWeakTriggered = true;
            drop = true;
            mDiscardFrameRate.add();
            mTotalDiscardFrameCounts++;
        }
        return drop;
    }

    public int getDiscardFrameCounts() {
        return mTotalDiscardFrameCounts;
    }

    public double getDiscardFps() {
        return mDiscardFrameRate.rate();
    }

    /**
     * This rountine is called asynchronously from LangTextureMovieEncoder handler thread.
     * MediaCodec using surface to produce encoded frames.
     */
    public void onProcessedSurfaceFrame(boolean endOfStream) {
        if (vencoder == null) {
            return;
        }

        if (endOfStream) {
            DebugLog.d(TAG, "sending EOS to encoder");
            vencoder.signalEndOfInputStream();
            // Fixed by lichao
            return;
        }

        ByteBuffer[] outBuffers = vencoder.getOutputBuffers();
        for (; ; ) {
            vebi = new MediaCodec.BufferInfo();
            int outBufferIndex = vencoder.dequeueOutputBuffer(vebi, 20000);
            if (outBufferIndex >= 0) {
                if (!endOfStream) {
                    ByteBuffer bb = null;
                    //if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        bb = outBuffers[outBufferIndex];
                    //}
                    //if (bb != null)
                    onEncodedAnnexbFrame(bb, vebi);
                }
                vencoder.releaseOutputBuffer(outBufferIndex, false);
            } else {
                break;
            }
        }
    }

    // when got encoded h264 es stream.
    protected void onEncodedAnnexbFrame(ByteBuffer es, MediaCodec.BufferInfo bi) {
        DebugLog.i(TAG, "onEncodedAnnexbFrame: pts =" + bi.presentationTimeUs/1000);
        mp4Muxer.writeSampleData(videoMp4Track, es.duplicate(), bi);
        flvMuxer.writeSampleData(videoFlvTrack, es.duplicate(), bi);
    }

    // choose the video encoder by name.
    private MediaCodecInfo chooseVideoEncoder(String name) {
        int nbCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < nbCodecs; i++) {
            MediaCodecInfo mci = MediaCodecList.getCodecInfoAt(i);
            if (!mci.isEncoder()) {
                continue;
            }

            String[] types = mci.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(VCODEC)) {
                    DebugLog.d(TAG, String.format("vencoder %s types: %s", mci.getName(), types[j]));
                    if (name == null) {
                        return mci;
                    }

                    if (mci.getName().contains(name)) {
                        return mci;
                    }
                }
            }
        }

        return null;
    }

    // choose the right supported color format. @see below:
    protected int chooseVideoEncoder() {
        // choose the encoder "video/avc":
        //      1. select default one when type matched.
        //      2. google avc is unusable.
        //      3. choose qcom avc.
        vmci = chooseVideoEncoder(null);
        boolean isQcom = vmci.getName().contains("qcom") || vmci.getName().contains("QCOM");
        //vmci = chooseVideoEncoder("google");
        //vmci = chooseVideoEncoder("qcom");

        int matchedColorFormat = 0;
        MediaCodecInfo.CodecCapabilities cc = vmci.getCapabilitiesForType(VCODEC);
        for (int i = 0; i < cc.colorFormats.length; i++) {
            int cf = cc.colorFormats[i];
            DebugLog.i(TAG, String.format("vencoder %s supports color fomart 0x%x(%d)", vmci.getName(), cf, cf));

            // choose YUV for h.264, prefer the bigger one.
            // corresponding to the color space transform in onPreviewFrame
            if (cf >= cc.COLOR_FormatYUV420Planar && cf <= COLOR_FormatYUV420SemiPlanar) {
                if (cf > matchedColorFormat) {
                    matchedColorFormat = cf;
                }
            }

            if (!LangEngineParams.enableGraphicBuffer && cc.COLOR_Format32bitARGB8888 == cf) {
                matchedColorFormat =cf;
                break;
            }
        }

        // MTK no MP on letv
        // IMG no HP on huawei
        // Exynos is ok.
        // qcom is ok.
        if (VCODEC.equals("video/avc")) {
            for (int i = 0; i < cc.profileLevels.length; i++) {
                MediaCodecInfo.CodecProfileLevel profileLevel = cc.profileLevels[i];
                // encoder support avc main profile, just use it.
                if (profileLevel.profile == AVCProfileMain && mProfileLevel.profile < AVCProfileMain) {
                    mProfileLevel = profileLevel;
                }
                // encoder support avc high profile, just use it.
                else if (profileLevel.profile == AVCProfileHigh && mProfileLevel.profile < AVCProfileHigh) {
                    mProfileLevel = profileLevel;
                }
            }
        }
        DebugLog.i(TAG, String.format("vencoder %s choose color format 0x%x(%d)", vmci.getName(), matchedColorFormat, matchedColorFormat));

        if (matchedColorFormat == COLOR_FormatYUV420SemiPlanar) {
            mNativeColorFormat = (int)'N' | (int)'V' << 8 | (int)'1' << 16 | (int)'2' << 24;
        }
        return matchedColorFormat;
    }
}