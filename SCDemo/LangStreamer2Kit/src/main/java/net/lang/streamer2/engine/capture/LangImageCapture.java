package net.lang.streamer2.engine.capture;

import net.lang.streamer2.engine.data.LangVideoConfiguration;
import net.lang.streamer2.utils.DebugLog;

import java.util.Arrays;

public class LangImageCapture extends IBaseVideoCapture {
    private static final String TAG = LangImageCapture.class.getSimpleName();

    private final Object mFence = new Object();
    private int[] mImageFrameRGBA;
    private byte[] mImageFrameI420;
    private int mPreviewFps;
    private ImageCaptureThread mImageCaptureThread;
    private boolean mStarted = false;

    public LangImageCapture(LangVideoConfiguration videoConfiguration) {
        super(videoConfiguration);
        mPreviewFps = videoConfiguration.getFps() / 2;
        if (mPreviewFps < 10) mPreviewFps = 10;
    }

    @Override
    public double getPreviewFps() {
        return mPreviewFps;
    }

    @Override
    public void start() {
        synchronized (mFence) {
            if (!mStarted) {
                int imageWidth = getVideoSize().getWidth();
                int imageHeight = getVideoSize().getHeight();
                mImageFrameRGBA = new int[imageWidth * imageHeight];
                mImageFrameI420 = rgba2YCbCr420(mImageFrameRGBA, imageWidth, imageHeight);

                byte initialValue = 0x0;
                Arrays.fill(mImageFrameRGBA, initialValue);

                mImageCaptureThread = new ImageCaptureThread();
                mImageCaptureThread.start();

                mStarted = true;

                DebugLog.d(TAG, "image capture start");
            }
        }
    }

    @Override
    public void stop() {
        synchronized (mFence) {
            if (mStarted) {
                mImageCaptureThread.quit();
                try {
                    mImageCaptureThread.join();
                } catch (InterruptedException ignored) {
                    // ignore
                }

                mStarted = false;

                DebugLog.d(TAG, "image capture stop");
            }
        }
    }

    @Override
    public void release() {
        mListener = null;
        DebugLog.d(TAG, "audio capture release");
    }

    private class ImageCaptureThread extends Thread {
        private boolean running;

        ImageCaptureThread() {
            running = true;
        }

        void quit() {
            running = false;
        }

        @Override
        public void run() {
            DebugLog.d(TAG, "ImageCaptureThread, tid=" + Thread.currentThread().getId());

            int sleepIntervalMs = 1000/(int)getPreviewFps();

            while (running) {
                int imageWidth = getVideoSize().getWidth();
                int imageHeight = getVideoSize().getHeight();

                if (mListener != null) {
                    mListener.onCapturedImageFrame(mImageFrameI420, imageWidth, imageHeight, System.nanoTime());
                }

                try {
                    Thread.sleep(sleepIntervalMs);
                } catch (InterruptedException ie) {
                    //ignore
                }
            }
        }
    }

    private byte[] rgba2YCbCr420(int[] pixels, int width, int height) {
        int len = width * height;
        //yuv格式数组大小，y亮度占len长度，u,v各占len/4长度。
        byte[] yuv = new byte[len * 3 / 2];
        int y, u, v;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                //屏蔽ABGR的透明度值
                int rgb = pixels[i * width + j] & 0x00FFFFFF;
                //像素的颜色顺序为bgr，移位运算。
                int r = rgb & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = (rgb >> 16) & 0xFF;
                //套用公式
                y = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
                u = ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
                v = ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128;
                //调整
                y = y < 16 ? 16 : (y > 255 ? 255 : y);
                u = u < 0 ? 0 : (u > 255 ? 255 : u);
                v = v < 0 ? 0 : (v > 255 ? 255 : v);
                //赋值
                yuv[i * width + j] = (byte) y;
                yuv[len + (i >> 1) * width + (j & ~1) + 0] = (byte) u;
                yuv[len + +(i >> 1) * width + (j & ~1) + 1] = (byte) v;
            }
        }
        return yuv;
    }

}
