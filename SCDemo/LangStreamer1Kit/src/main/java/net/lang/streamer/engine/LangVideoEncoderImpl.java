package net.lang.streamer.engine;

import android.media.MediaCodecInfo;
import android.os.Build;
import android.view.Surface;

import com.yunfan.graphicbuffer.GraphicBufferWrapper;

import net.lang.streamer.engine.data.LangEngineParams;
import net.lang.streamer.utils.DebugLog;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Created by lichao on 17-6-7.
 */

public class LangVideoEncoderImpl extends LangVideoEncoder implements ISignalBufferReturn {

    static {
        //System.loadLibrary("yuv");
        System.loadLibrary("enc");
    }
    private Thread mEncoderThread = null;
    protected volatile boolean mRun;
    private int mGBColortype = (int)'A' | (int)'B' << 8 | (int)'G' << 16 | (int)'R' << 24;

    private final Object mBufferFence = new Object();

    public enum EncoderType{
        kHardwarePipeline,
        kHardware,
        kSoftware
    }

    public enum ColorType {
        kRGBA,
        kNV12,
        kI420
    }

    public class EncoderBuffer {
        public ByteBuffer buffer;
        public byte[] i420data;
        public byte[] nv12data;
        public byte[] data;
        public long pts;
        public GraphicBufferWrapper gb;
        public ISignalBufferReturn handle;
    }

    private Queue<EncoderBuffer> mFillQueue;
    private Queue<EncoderBuffer> mEmptyQueue;
    private EncoderType mType;
    private final int kMaxBufferCnt = 5;
    private int mBuffercnt = 0;

    public LangVideoEncoderImpl(LangEncodeHandler handle, EncoderType type) {
        super(handle, type == EncoderType.kHardwarePipeline);
        mType = type;
        mRun = false;
        mFillQueue = new LinkedList<>();
        mEmptyQueue = new LinkedList<>();
        setEncoderResolution(LangEngineParams.vOutputWidth, LangEngineParams.vOutputHeight);
        mGBColortype = handleSpecialDeviceFormat();
    }

    public static LangVideoEncoder create(LangEncodeHandler handle, EncoderType type) {
        LangVideoEncoder encoder = null;
        switch (type) {
            case kHardwarePipeline:
                encoder = new LangVideoEncoder(handle, true);
                break;
            case kHardware:
                encoder = new LangVideoEncoderMediaCodec(handle);
                break;
            case kSoftware:
                encoder = new LangVideoEncoderX264(handle);
                break;
            default:
                break;
        }
        return encoder;
    }

    private void allocateBuffer(int videoWidth, int videoHeight) {
        synchronized (mBufferFence) {
            for (int i = 0; i < kMaxBufferCnt; i++) {
                EncoderBuffer buffer = new EncoderBuffer();
                buffer.handle = this;
                buffer.buffer = null;
                buffer.i420data = new byte[videoWidth * videoHeight * 3 / 2];
                buffer.nv12data = new byte[videoWidth * videoHeight * 3 / 2];
                if (!LangEngineParams.enableGraphicBuffer) {
                    buffer.buffer = ByteBuffer.allocate(videoWidth * videoHeight * 4);
                    buffer.buffer.position(0);
                }
                buffer.data = null;
                buffer.pts = 0;
                mEmptyQueue.add(buffer);
            }
        }
    }

    @Override
    public EncoderBuffer getBuffer() {
        EncoderBuffer buffer;
        synchronized (mBufferFence) { //synchronized (mEmptyQueue) {
            buffer = mEmptyQueue.poll();
        }
        /*
        if (buffer == null) {
           if(mBuffercnt < kMaxBufferCnt){
                buffer = new EncoderBuffer();
                buffer.handle = this;
                buffer.buffer = null;
                buffer.i420data = new byte[vOutHeight * vOutWidth * 3 / 2];
                buffer.nv12data = new byte[vOutHeight * vOutWidth * 3 / 2];
                if (!LangEngineParams.enableGraphicBuffer) {
                    buffer.buffer = ByteBuffer.allocate(vOutHeight * vOutWidth * 4);
                    buffer.buffer.position(0);
                }
                buffer.data = null;
                buffer.pts = 0;
                mBuffercnt++;
            }
        }
        */
        return buffer;
    }

    @Override
    public void release(EncoderBuffer buffer) {
        if (buffer.data == null && buffer.gb == null) {
            synchronized (mBufferFence) { //synchronized (mEmptyQueue) {
                mEmptyQueue.add(buffer);
                mBufferFence.notifyAll(); //mEmptyQueue.notifyAll();
            }
        }else {
            synchronized (mBufferFence) { //synchronized (mFillQueue) {
                if (buffer.gb != null) {
                    final int type = mGBColortype; //(int)'A' | (int)'B' << 8 | (int)'G' << 16 | (int)'R' << 24;
                    byte[] data = null;
                    final int stride =  buffer.gb.stride();
                    final long gbAddr = buffer.gb.lock();
                    if (mVideoColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
                        RGBAToI420Fast(null, gbAddr, buffer.i420data, vOutWidth, vOutHeight, stride, false, 0, type);
                        I420ToNV12Fast(buffer.i420data, buffer.nv12data, vOutWidth, vOutHeight);
                        data = buffer.nv12data;
                        //data = ConvertToNV12(null, gbAddr, vOutWidth, vOutHeight, stride, false, 0, type);
                    }else if (mVideoColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar){
                        RGBAToI420Fast(null, gbAddr, buffer.i420data, vOutWidth, vOutHeight, stride, false, 0, type);
                        data = buffer.i420data;
                        //data = ConvertToI420(null, gbAddr, vOutWidth, vOutHeight, stride, false, 0, type);
                    }
                    buffer.gb.unlock();
                    if (data != null) {
                        buffer.buffer = ByteBuffer.wrap(data);
                        buffer.data = data;
                    }else {
                        buffer.buffer = null;
                        buffer.data = null;
                        DebugLog.e(getClass().getName(), "RGBA to I420 with virtual address failed.");
                    }
                    buffer.gb = null;
                }

                //Log.i("LangVideoEncoderImpl", "filled queue size：" + mFillQueue.size() + " empty queue size: " + mEmptyQueue.size());
                mFillQueue.add(buffer);
                mBufferFence.notifyAll(); //mFillQueue.notifyAll();
            }
        }
    }

    @Override
    public void setFlvMuxer(IRtmpMediaMuxer flvMuxer) {
        super.setFlvMuxer(flvMuxer);
    }

    @Override
    public void setMp4Muxer(LangMp4Muxer mp4Muxer) {
        super.setMp4Muxer(mp4Muxer);
    }

    @Override
    public boolean start() {
       return super.start();
    }

    protected void startThread() {
        allocateBuffer(vOutWidth, vOutHeight);
            mEncoderThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    boolean run = true;
                    mRun = true;
                    while (mRun && run) {
                        EncoderBuffer buffer;

                        synchronized (mBufferFence) {
                            buffer = mFillQueue.poll();
                            if (buffer == null) {
                                try {
                                    mBufferFence.wait();
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                buffer = mFillQueue.poll();
                            }
                        }

                        if (buffer != null) {
                            run = encode(buffer);
                            buffer.handle.release(buffer);
                        }

                        try {
                            Thread.sleep(5);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
            mEncoderThread.setName("NonpipeEncoding");
            mEncoderThread.start();
            while(!mRun) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

    protected void stopThreadWait() {
        mRun = false;
        synchronized (mBufferFence) { // synchronized (mFillQueue) {
            mBufferFence.notifyAll();  //mFillQueue.notifyAll();
        }
        try {
            while (mEncoderThread != null && mEncoderThread.isAlive()) mEncoderThread.join(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        while (!mFillQueue.isEmpty()) {
            DebugLog.i(LangVideoEncoderImpl.class.getSimpleName(), "cleanup filled queue" + " size: " + mFillQueue.size());
            EncoderBuffer buffer = mFillQueue.poll();
            buffer.i420data = null;
            buffer.nv12data = null;
            buffer.handle = null;
            buffer.gb = null;
            buffer.buffer = null;
            buffer.data = null;
        }

        while (!mEmptyQueue.isEmpty()) {
            DebugLog.i(LangVideoEncoderImpl.class.getSimpleName(), "cleanup empty queue" + " size: " + mEmptyQueue.size());
            EncoderBuffer buffer = mEmptyQueue.poll();
            buffer.i420data = null;
            buffer.nv12data = null;
            buffer.handle = null;
            buffer.gb = null;
            buffer.buffer = null;
            buffer.data = null;
        }

        mBuffercnt = 0;
    }

    protected boolean encode(EncoderBuffer buffer) {
        return false;
    }

    @Override
    public boolean onStartAsync() {
        return super.onStartAsync();
    }

    @Override
    public boolean autoBitrate(boolean enable) {
        return super.autoBitrate(enable);
    }

    @Override
    public Surface getInputSurface() {
        return super.getInputSurface();
    }

    @Override
    public void stop() {
        super.stop();
    }

    @Override
    public void onStopAsync() {
        super.onStopAsync();
    }

    @Override
    public void setVideoResolution(int width, int height) {
        super.setVideoResolution(width, height);
    }

    @Override
    public int getOutputWidth() {
        return super.getOutputWidth();
    }

    @Override
    public int getOutputHeight() {
        return super.getOutputHeight();
    }

    @Override
    public void setScreenOrientation(int orientation) {
        super.setScreenOrientation(orientation);
    }

    @Override
    public boolean shouldSkipInputWhenStreaming() {
        return super.shouldSkipInputWhenStreaming();
    }

    @Override
    public int getDiscardFrameCounts() {
        return super.getDiscardFrameCounts();
    }

    @Override
    public double getDiscardFps() {
        return super.getDiscardFps();
    }

    @Override
    public void onProcessedSurfaceFrame(boolean endOfStream) {
        super.onProcessedSurfaceFrame(endOfStream);
    }

    public static int handleSpecialDeviceFormat() {
        // most of devices will use RGBA color conversion.
        int colorTyoe = (int)'A' | (int)'B' << 8 | (int)'G' << 16 | (int)'R' << 24;
        DebugLog.d(LangVideoEncoderImpl.class.getSimpleName(),
                "handleSpecialDeviceFormat: " +
                        " manufacturer: [" + Build.MANUFACTURER + "] " +
                        " product: [" + Build.PRODUCT + "] " +
                        " board: [" + Build.BOARD + "] " +
                        " device: [" + Build.DEVICE + "] " +
                        " brand: [" + Build.BRAND + "] " +
                        " model: [" + Build.MODEL + "]" +
                        " hardware: [" + Build.HARDWARE + "]");
        String strManufacture = Build.MANUFACTURER;
        String strProduct = Build.PRODUCT;
        if (strManufacture.contains("Meitu")) {
            if ( strProduct.contains("M8") || strProduct.contains("T8") )
                // force BGRA color conversion.
                colorTyoe = (int)'A' | (int)'R' << 8 | (int)'G' << 16 | (int)'B' << 24;
        } else if (strManufacture.contains("Sony")) {
            if ( strProduct.contains("G3125") )
                // force BGRA color conversion.
                colorTyoe = (int)'A' | (int)'R' << 8 | (int)'G' << 16 | (int)'B' << 24;
        }
        return colorTyoe;
    }

    protected native void setEncoderResolution(int outWidth, int outHeight);
    protected native void setEncoderFps(int fps);
    protected native void setEncoderGop(int gop);
    protected native void setEncoderBitrate(int bitrate);
    protected native void setEncoderPreset(String preset);
    public static native byte[] RGBAToI420(byte[] rgbaFrame, int width, int height, boolean flip, int rotate);
    public static native byte[] RGBAToNV12(byte[] rgbaFrame, int width, int height, boolean flip, int rotate);

    public static native void RGBAToI420Fast(byte[] rgbaFrame, long addr, byte[] i420Frame, int width, int height, int stride, boolean flip, int rotate, int srcType);
    public static native void I420ToNV12Fast(byte[] i420Frame, byte[] nv12Frame, int width, int height);

    public static native byte[] ConvertToI420(byte[] rgbaFrame, long addr, int width, int height,int stride, boolean flip, int rotate, int srcType);
    public static native byte[] ConvertToNV12(byte[] rgbaFrame, long addr, int width, int height,int stride, boolean flip, int rotate, int srcType);

    protected native int RGBASoftEncode(byte[] rgbaFrame, int width, int height, boolean flip, int rotate, long pts);
    protected native int softEncode(byte[] rawFrame,int width, int height, boolean flip, int rotate, long pts, int srcType);
    protected native boolean openSoftEncoder();
    protected native void closeSoftEncoder();
}
