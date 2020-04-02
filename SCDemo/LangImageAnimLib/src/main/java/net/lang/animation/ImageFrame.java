package net.lang.animation;

import android.graphics.Bitmap;

public class ImageFrame {
    private Bitmap mImage;
    private int mDelay;
    private ImageFrame mNextFrame;

    public ImageFrame(Bitmap image, int delay) {
        this.mImage = image;
        this.mDelay = delay;
        this.mNextFrame = null;
    }

    public Bitmap getImage() {
        return this.mImage;
    }

    public int getDelay() {
        return this.mDelay;
    }

    public void setNextFrame(ImageFrame imageFrame) {
        this.mNextFrame = imageFrame;
    }

    public ImageFrame getNextFrame() {
        return this.mNextFrame;
    }
}
