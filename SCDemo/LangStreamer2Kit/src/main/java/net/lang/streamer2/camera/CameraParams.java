package net.lang.streamer2.camera;

import android.graphics.ImageFormat;

public final class CameraParams {
    private int previewWidth = 1280;
    private int previewHeight = 720;
    private int frameFormat = ImageFormat.NV21;
    private int frameRate = 24;
    private int orientation;
    private String focusMode = "continuous-video";
    private boolean landscape = false;


    public int getPreviewWidth() {
        return previewWidth;
    }

    public void setPreviewWidth(int previewWidth) {
        this.previewWidth = previewWidth;
    }

    public int getPreviewHeight() {
        return previewHeight;
    }

    public void setPreviewHeight(int previewHeight) {
        this.previewHeight = previewHeight;
    }

    public int getFrameFormat() {
        return frameFormat;
    }

    public void setFrameFormat(int frameFormat) {
        this.frameFormat = frameFormat;
    }

    public void setFrameRate(int frameRate) {
        this.frameRate = frameRate;
    }

    public int getFrameRate() {
        return frameRate;
    }

    public void setOrientation(int orientation) {
        this.orientation = orientation;
    }

    public int getOrientation() {
        return orientation;
    }

    public void setFocusMode(String focusMode) {
        this.focusMode = focusMode;
    }

    public String getFocusMode() {
        return focusMode;
    }

    public boolean isLandscape() {
        return landscape;
    }

    public void setLandscape(boolean landscape) {
        this.landscape = landscape;
    }
}
