package net.lang.animation;

import android.graphics.Bitmap;

public abstract class IAnimationDecoder implements Runnable {

    static final int STATUS_PARSING = 0;
    static final int STATUS_FORMAT_ERROR = 1;
    static final int STATUS_OPEN_ERROR = 2;
    static final int STATUS_FINISH = -1;
    static final int STATUS_NOT_INIT_YET = -2;
    static final int STATUS_DECODE_ERROR = -3;
    static final int STATUS_UNKNOWN_ERROR = -4;

    protected ImageFrame mImageFrame;
    protected ImageFrame mCurrentFrame;

    protected int mStatus = 0;

    int mFrameCount = 0;
    int mTotalLength = 0;

    protected int mDelayMilliSecs = 0; // delay in milliseconds

    public final boolean parseOk() {
        return mStatus == STATUS_FINISH;
    }

    /**
     * the delay time to get the nth frame
     * @param index no. of frame
     * @return delay time in ms
     */
    public final int getDelay(int index) {
        mDelayMilliSecs = -1;
        if (index >= 0 && index < mFrameCount) {
            ImageFrame f = getFrame(index);
            if (f != null)
                mDelayMilliSecs = f.getDelay();
        }
        return mDelayMilliSecs;
    }

    /**
     * the delay time to get all frames
     * @return
     */
    public final int[] getDelays() {
        ImageFrame f = mImageFrame;
        int d[] = new int[mFrameCount];//val d = IntArray(frameCount)
        int i = 0;
        while (f != null && i < mFrameCount) {
            d[i] = f.getDelay();
            f = f.getNextFrame();
            i++;
        }
        return d;
    }

    /**
     * get the first frame
     * @return
     */
    public final Bitmap getImage() {
        return getFrameImage(0);
    }

    /**
     * get the nth bitmap
     * @param index the number of frame
     * @return the nth bitmap or null if no bitmap or error occurred
     */
    public final Bitmap getFrameImage(int index) {
        ImageFrame frame = getFrame(index);
        return frame.getImage();
    }

    /**
     * get the nth ImageFrame
     * @param index the number of frame
     * @return the desired image frame of index
     */
    public final ImageFrame getFrame(int index) {
        ImageFrame frame = mImageFrame;
        int i = 0;
        while (frame != null) {
            if (i == index) {
                return frame;
            } else {
                frame = frame.getNextFrame();
            }
            i++;
        }
        return null;
    }

    /**
     * @return total frame count of sequence animation images
     */
    public final int getTotalFrameCount() {
        return mFrameCount;
    }

    /**
     * @return total duration time of sequence animation images in milliseconds
     */
    public final int getTotalDuration() {
        return mTotalLength;
    }

    /**
     * reset to the first frame
     */
    public final void reset() {
        mCurrentFrame = mImageFrame;
    }

    public final boolean err() {
        return mStatus != STATUS_PARSING;
    }

    @Override
    public abstract void run();
}
