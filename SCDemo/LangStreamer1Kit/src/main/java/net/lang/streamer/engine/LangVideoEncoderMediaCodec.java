package net.lang.streamer.engine;

import android.media.MediaCodecInfo;
import android.os.Build;

import net.lang.streamer.engine.data.LangEngineParams;

import java.nio.ByteBuffer;

/**
 * Created by lichao on 17-6-7.
 */

public class LangVideoEncoderMediaCodec extends LangVideoEncoderImpl {
    private int mFrame = 0;
    public LangVideoEncoderMediaCodec(LangEncodeHandler handle) {
        super(handle, EncoderType.kHardware);
    }

    public boolean encode(EncoderBuffer buffer) {
        if (buffer == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                vencoder.signalEndOfInputStream();
            }
            return false;
        }

        if (buffer.buffer == null) {
            return mRun;
        }

//        buffer.dataSize = buffer.w * buffer.h * 3 / 2;

        ByteBuffer[] inBuffers = vencoder.getInputBuffers();
        ByteBuffer[] outBuffers = vencoder.getOutputBuffers();

        int inBufferIndex = vencoder.dequeueInputBuffer(-1);
        if (inBufferIndex >= 0) {
            ByteBuffer bb = inBuffers[inBufferIndex];
            bb.clear();
            mFrame++;
//            if (mFrame == 10 || mFrame == 20 || mFrame == 50) {
//                Bitmap bitmap = Bitmap.createBitmap(buffer.w, buffer.h, Bitmap.Config.ARGB_8888);
//                FileOutputStream outstream = null;
//                try {
//                    outstream = new FileOutputStream(new File("/sdcard/snail.codec." +buffer.w + "x" + buffer.h +"."+ mFrame + ".png"));
//                    buffer.buffer.position(0);
//                    bitmap.copyPixelsFromBuffer(buffer.buffer);
//                    buffer.buffer.position(0);
//                    bitmap.compress(Bitmap.CompressFormat.PNG, 90, outstream);
//                    try {
//                        outstream.close();
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                } catch (FileNotFoundException e) {
//                    e.printStackTrace();
//                }
//            }
            if (!mRun)
                return mRun;

            byte[] yuv = null;
            // OpenGl is RGBA but the read buffer is need swap, the maybe is little/bit question???
            long startms = System.currentTimeMillis();
            if (LangEngineParams.enableGraphicBuffer) {
                yuv = buffer.data;
            }else {
                int type = (int) 'A' | (int) 'B' << 8 | (int) 'G' << 16 | (int) 'R' << 24;
                switch (mVideoColorFormat) {
                    case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
                        yuv = ConvertToI420(buffer.buffer.array(), 0, vOutWidth, vOutHeight, 0, false, 0, type);
                        break;
                    case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar: {
                        yuv = ConvertToNV12(buffer.buffer.array(), 0, vOutWidth, vOutHeight, 0, false, 0, type);
                        break;
                    }
                    case MediaCodecInfo.CodecCapabilities.COLOR_Format32bitARGB8888:
                        yuv = buffer.buffer.array();
                        break;
                    default:
                        vencoder.queueInputBuffer(inBufferIndex, 0, 0, 0, 0);
                        buffer.data = null;
                        return mRun;
                }
            }

//            if (mFrame == 10 || mFrame == 20 || mFrame == 50)  {
//                try {
//                    FileOutputStream outstream = new FileOutputStream(new File("/sdcard/snail.codec." +buffer.w + "x" + buffer.h +"." + mFrame + ".yuv"));
//                    try {
//                        outstream.write(yuv, 0, yuv.length);
//                        outstream.close();
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                } catch (FileNotFoundException e) {
//                    e.printStackTrace();
//                }
//            }

            bb.put(yuv, 0, yuv.length);
            vencoder.queueInputBuffer(inBufferIndex, 0, yuv.length, System.nanoTime() / 1000 - mPresentTimeUs, 0);

            buffer.data = null;
        }

        for (;mRun ; ) {
            int outBufferIndex = vencoder.dequeueOutputBuffer(vebi, 0);
            if (outBufferIndex >= 0) {
                ByteBuffer bb = outBuffers[outBufferIndex];
                onEncodedAnnexbFrame(bb, vebi);
                vencoder.releaseOutputBuffer(outBufferIndex, false);
            } else {
                break;
            }
        }
        return mRun;
    }

    @Override
    public boolean onStartAsync() {
        return false;
    }

    @Override
    public void onStopAsync() {
        return;
    }

    public boolean start() {
        setEncoderResolution(getOutputWidth(), getOutputHeight());
        if (super.start()) {
            startThread();
        }
        return true;
    }

    public void stop() {
        stopThreadWait();
        super.stop();
    }

    public void onProcessedSurfaceFrame(boolean endOfStream) {

    }
}
