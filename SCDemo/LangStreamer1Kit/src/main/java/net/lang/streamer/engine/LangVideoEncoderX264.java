package net.lang.streamer.engine;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.view.Surface;

import net.lang.streamer.engine.data.LangEngineParams;
import net.lang.streamer.utils.DebugLog;

import java.nio.ByteBuffer;

/**
 * Created by lichao on 17-6-9.
 */

public class LangVideoEncoderX264 extends LangVideoEncoderImpl {
    private final String x264Preset = "superfast";
    private static final String TAG = LangVideoEncoderX264.class.getName();
    private boolean mStart;

    public LangVideoEncoderX264(LangEncodeHandler handle) {
        super(handle, EncoderType.kSoftware);
        mStart = false;
    }

    @Override
    public boolean start() {
        setEncoderResolution(getOutputWidth(), getOutputHeight());
        setEncoderFps(LangEngineParams.vOutputFps);
        setEncoderGop(LangEngineParams.vOutKeyFrameIntervalSec * LangEngineParams.vOutputFps);
        setEncoderBitrate(LangEngineParams.vOutputBitrateKbps * 1000);
        setEncoderPreset(x264Preset);
        if (flvMuxer == null || mp4Muxer == null) {
            DebugLog.e(TAG, "no media sink available.");
            return false;
        }

        int bitrateBps = LangEngineParams.vOutputBitrateKbps * 1000;
        int encFps = LangEngineParams.vOutputFps;

        MediaFormat videoFormat = prepareVideoFormatInfo(VCODEC, vOutWidth, vOutHeight, bitrateBps, encFps);
        // add the video tracker to muxer.
        videoFlvTrack = flvMuxer.addTrack(videoFormat);
        videoMp4Track = mp4Muxer.addTrack(videoFormat);
        mPresentTimeUs = System.nanoTime() / 1000;
        mStart = openSoftEncoder();
        if (mStart) {
           startThread();
        }
        return mStart;
    }
    protected int chooseVideoEncoder () {
        // openGl default color format
        mNativeColorFormat = (int)'A' | (int)'B' << 8 | (int)'G' << 16 | (int)'R' << 24;
        int color = MediaCodecInfo.CodecCapabilities.COLOR_Format32bitARGB8888;
        if (LangEngineParams.enableGraphicBuffer) {
            mNativeColorFormat = (int)'I' | (int)'4' << 8 | (int)'2' << 16 | (int)'0' << 24;
            color = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar;
        }
        return color;
    }

    @Override
    protected boolean encode(EncoderBuffer buffer) {
        softEncode(buffer.data, vOutWidth, vOutHeight, false, 0, System.nanoTime() / 1000 - mPresentTimeUs, mNativeColorFormat);
        buffer.data = null;
        return mRun;
    }

    // By native callback when a frame is encode ok.
    private void onSoftEncodedData(byte[] es, long pts, boolean isKeyFrame) {
        ByteBuffer bb = ByteBuffer.wrap(es);
        vebi.offset = 0;
        vebi.size = es.length;
        vebi.presentationTimeUs = pts;
        vebi.flags = isKeyFrame ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
        if (pts == 0) {
            vebi.flags |=  MediaCodec.BUFFER_FLAG_CODEC_CONFIG;
        }
        onEncodedAnnexbFrame(bb, vebi);
    }

    @Override
    public boolean onStartAsync() {
        return false;
    }

    @Override
    public boolean autoBitrate(boolean enable) {
        return false;
    }

    @Override
    public Surface getInputSurface() {
        return null;
    }

    @Override
    public void stop() {
        stopThreadWait();
        if (mStart)
            closeSoftEncoder();
    }

    @Override
    public void onStopAsync() {

    }

    @Override
    public void setScreenOrientation(int orientation) {
        super.setScreenOrientation(orientation);
    }


    @Override
    public void onProcessedSurfaceFrame(boolean endOfStream) {

    }
}
