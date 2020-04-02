package net.lang.streamer2.engine.encoder;

import android.media.MediaCodec;

import net.lang.streamer2.engine.data.LangMediaBuffer;
import net.lang.streamer2.engine.data.LangVideoConfiguration;
import net.lang.streamer2.utils.DebugLog;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;

public class LangOpenh264Encoder extends IBaseVideoEncoder {
    private static final String TAG = LangOpenh264Encoder.class.getSimpleName();
    private long mNativeObject;
    private final Object mFence = new Object();
    private boolean mStarted;
    private boolean mHeaderDataCallbacked;
    private MediaCodec.BufferInfo vebi = new MediaCodec.BufferInfo();

    public LangOpenh264Encoder(LangVideoConfiguration videoConfiguration)
            throws EncoderRuntimeException {
        super(videoConfiguration);
        mNativeObject = 0;
        mStarted = false;
        mHeaderDataCallbacked = false;
    }

    public LangOpenh264Encoder(int width, int height, int fps, int bitrateBps, int keyFrameIntervalSec)
            throws EncoderRuntimeException {
        super(width, height, fps, bitrateBps, keyFrameIntervalSec);
        mNativeObject = 0;
        mStarted = false;
        mHeaderDataCallbacked = false;
    }

    /**
     * called from streamer push mode
     */
    @Override
    public boolean start() {
        synchronized (mFence) {
            if (!mStarted) {
                mStarted = true;
                setupEncoder(getWidth(), getHeight(), getFps(), getBitrate(), getEncoderKeyFrameInterval());
            }
        }
        return true;
    }

    @Override
    public void stop() {
        synchronized (mFence) {
            if (mStarted) {
                mStarted = false;
                cleanEncoder();
                mHeaderDataCallbacked = false;
            }
        }
    }

    @Override
    public void encodeFrame(byte[] data, int dataLength, long presentationTimeUs) {
        synchronized (mFence) {
            if (!mStarted) {
                return;
            }
            if (!mHeaderDataCallbacked) {
                getParameterSets();
                mHeaderDataCallbacked = true;
            }
            encodeFrame(data, dataLength, getWidth(), getHeight(), presentationTimeUs);

            // update realtime bitrate.
            int currentBitrateBps = getEncoderBitrate();
            DebugLog.v(TAG, "encoder current bitrateKbps = " + currentBitrateBps/1024);
            //super.setBitrate(currentBitrateBps);
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
            if (encoderReady()) {
                return true;
            }
        }
        return false;
    }

    /*
     * Returns the new bitrate supported.
     */
    @Override
    public boolean setBitrate(int bitrateBps) {
        boolean result = false;
        synchronized (mFence) {
            if (setEncoderBitrate(bitrateBps) == 0) {
                result = true;
                super.setBitrate(bitrateBps);
            }
        }
        return result;
    }

    // By native callback when a frame is encoded ok.
    @SuppressWarnings("native call")
    private void onSoftEncodedData(byte[] es, long pts, int flags) {
        ByteBuffer bb = ByteBuffer.wrap(es);
        vebi.offset = 0;
        vebi.size = es.length;
        vebi.presentationTimeUs = pts;
        vebi.flags = flags;

        if (mEncodeListener != null) {
            mEncodeListener.onEncodedAnnexbFrame(bb, vebi);
        }
    }


    private void setupEncoder(int width, int height, int fps, int bitrateBps, int keyFrameIntervalSec) {
        if (mNativeObject != 0)
            return;
        mNativeObject = nativeSetup(width, height, fps, bitrateBps, keyFrameIntervalSec);
        if (mNativeObject != 0) {
            nativeSetWeakReference(mNativeObject, new WeakReference<>(this));
        }
    }

    private void getParameterSets() {
        if (mNativeObject != 0) {
            nativeEncodeParameterSets(mNativeObject);
        }
    }

    private int getEncoderBitrate() {
        if (mNativeObject != 0) {
            return nativeGetBitrate(mNativeObject);
        }
        return super.getBitrate();
    }

    private int encodeFrame(byte[] data, int dataLength, int width, int height, long presentationTimeUs) {
        if (mNativeObject != 0) {
            return nativeEncodeFrame(mNativeObject, data, dataLength, width, height, presentationTimeUs);
        }
        return -1;
    }

    private int setEncoderBitrate(int bitrateBps) {
        if (mNativeObject != 0) {
            nativeSetBitrate(mNativeObject, bitrateBps);
            return 0;
        }
        return -1;
    }

    private void cleanEncoder() {
        if (mNativeObject != 0) {
            nativeRelease(mNativeObject);
            mNativeObject = 0;
        }
    }

    private boolean encoderReady() {
        return  mNativeObject != 0;
    }

    private static native long nativeSetup(int width, int height, int fps, int bitrateBps, int keyFrameIntervalSec);
    private static native void nativeRelease(long nativeObject);

    private static native void nativeSetWeakReference(long nativeObject, Object object);
    private static native void nativeSetBitrate(long nativeObject, int bitrateBps);

    private static native int nativeGetBitrate(long nativeObject);

    private static native void nativeEncodeParameterSets(long nativeObject);
    private static native int nativeEncodeFrame(long nativeObject, byte[] inputData, int dataLength, int width, int height, long presentationTimeUs);

    static {
        System.loadLibrary("lang_openh264enc");
    }
}
