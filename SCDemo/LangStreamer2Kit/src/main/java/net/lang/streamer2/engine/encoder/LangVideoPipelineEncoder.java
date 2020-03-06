package net.lang.streamer2.engine.encoder;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.view.Surface;

import net.lang.streamer2.engine.data.LangVideoConfiguration;
import net.lang.streamer2.utils.DebugLog;

import java.nio.ByteBuffer;

public final class LangVideoPipelineEncoder extends LangVideoHardwareEncoder {

    private static final String TAG = LangVideoPipelineEncoder.class.getSimpleName();

    // media codec surface context.
    private Surface mInputSurface = null;

    public LangVideoPipelineEncoder(LangVideoConfiguration videoConfiguration) {
        super(videoConfiguration);

        // use surface format to override previous format.
        int surfaceFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;
        setEncoderFormat(surfaceFormat);
    }

    public LangVideoPipelineEncoder(int width, int height, int fps, int bitrateBps, int keyFrameIntervalSec) {
        super(width, height, fps, bitrateBps, keyFrameIntervalSec);

        // use surface format to override previous format.
        int surfaceFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;
        setEncoderFormat(surfaceFormat);
    }

    /**
     * Returns the encoder's input surface.
     */
    public Surface getInputSurface() {
        return mInputSurface;
    }

    @Override
    public void encodeFrame(byte[] data, int dataLength, long presentationTimeUs) {

    }

    /**
     * This rountine is called asynchronously from LangTextureMovieEncoder handler thread.
     * MediaCodec using surface to produce encoded frames.
     */
    public void encodeSurfaceFrame(boolean endOfStream) {
        synchronized (mFence) {
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
                        ByteBuffer bb = outBuffers[outBufferIndex];
                        if (mEncodeListener != null) {
                            mEncodeListener.onEncodedAnnexbFrame(bb, vebi);
                        }
                    }
                    vencoder.releaseOutputBuffer(outBufferIndex, false);
                } else {
                    break;
                }
            }
        }
    }

    /**
     * Configures encoder, and prepares the input Surface.
     */
    @Override
    boolean prepareEncoder(String mime, int width, int height, int bitrate, int fps,
                           int format, int keyFrameInterval) {
        if (super.prepareEncoder(mime, width, height, bitrate, fps, format, keyFrameInterval)) {
            mInputSurface = vencoder.createInputSurface();
            return true;
        }
        return false;
    }
}
