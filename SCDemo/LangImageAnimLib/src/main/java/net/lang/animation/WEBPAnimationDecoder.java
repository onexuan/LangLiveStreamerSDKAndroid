package net.lang.animation;

import android.graphics.Bitmap;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

public class WEBPAnimationDecoder extends IAnimationDecoder {
    private static final String TAG = WEBPAnimationDecoder.class.getSimpleName();
    private String mFilePath;
    private IDecodeActionListener mListener;

    private LibwebpProxy mWebpJni;

    private boolean mSaveImage = false;
    private byte[] mImageSource = null;

    private int mDefaultWidth  = 0;
    private int mDefaultHeight = 0;

    public WEBPAnimationDecoder(String filePath, IDecodeActionListener listener) {
        mFilePath = filePath;
        mListener = listener;
        mWebpJni = new LibwebpProxy();
    }

    public void setSize(int width, int height) {
        mDefaultWidth = width;
        mDefaultHeight = height;
    }

    @Override
    public void run() {
        init();
        try {
            int[] width = new int[1];//val width = intArrayOf(0)
            int[] height = new int[1];//val height = intArrayOf(0)
            int[] nbFrames = new int[1];//val nbFrames = intArrayOf(0)

            if (mListener != null) {
                mListener.onParseProgress(this, 0);
            }
            int err = mWebpJni.decodeRGBA(mFilePath, width, height, nbFrames, mDefaultWidth, mDefaultHeight);
            if (mSaveImage)
                mWebpJni.saveImage(android.os.Environment.getExternalStorageDirectory().toString());
            if (0 == err) {
                Log.i(TAG, "width:" + width[0]);
                Log.i(TAG, "height:" + height[0]);
                Log.i(TAG, "nbFrames: ${nbFrames[0]}");

                mFrameCount = nbFrames[0];
                mTotalLength = mFrameCount * mWebpJni.getFrameDuration(0);
                if (mImageSource == null)
                    mImageSource = new byte[width[0] * height[0] * 4];
                Bitmap tempImage = Bitmap.createBitmap(width[0], height[0], Bitmap.Config.ARGB_8888);
                for (int i = 0; i < mFrameCount; i++) {
                    mWebpJni.getDecodedFrame(i, mImageSource);
                    tempImage.copyPixelsFromBuffer(ByteBuffer.wrap(mImageSource));
                    Bitmap image = Bitmap.createBitmap(tempImage, 0, 0, mDefaultWidth, mDefaultHeight);
                    if (mImageFrame == null) {
                        mImageFrame = new ImageFrame(image, mDelayMilliSecs);
                        mCurrentFrame = mImageFrame;
                    } else {
                        ImageFrame f = mImageFrame;
                        while (f.getNextFrame() != null) {
                            f = f.getNextFrame();
                        }
                        f.setNextFrame(new ImageFrame(image, mDelayMilliSecs));
                    }
                    if (mListener != null) {
                        mListener.onParseProgress(this, i+1);
                    }
                }
                tempImage.recycle();
            } else {
                if (err == -1)
                    mStatus = STATUS_NOT_INIT_YET;
                else if (err == -2)
                    mStatus = STATUS_DECODE_ERROR;
                else
                    mStatus = STATUS_UNKNOWN_ERROR;
            }

            if (!err()) {
                if (mFrameCount < 0) {
                    mStatus = STATUS_FORMAT_ERROR;
                    if (mListener != null) {
                        mListener.onParseComplete(this, false, -1);
                    }
                } else {
                    Log.i(TAG, "Finished..");
                    mStatus = STATUS_FINISH;
                    if (mListener != null) {
                        mListener.onParseComplete(this, true, -1);
                    }
                }
            } else {
                Log.e(TAG, "Webp decode failed, status=$status");
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            release();
        }
    }

    private void init() {
        mStatus = STATUS_PARSING;
        mFrameCount = 0;
        mImageFrame = null;
        mImageSource = null;

        mWebpJni.init();
    }

    private void release() {
        mWebpJni.release();
    }
}
