package net.lang.streamer2.camera;

import android.graphics.Rect;
import android.graphics.SurfaceTexture;

public abstract class ICameraInterface {

    public static final int CAM_ER_INVALID_CAMERA_ID = -1;
    public static final int CAM_ER_INVALID_PARAMETERS = -2;
    public static final int CAM_ER_INVALID_STATE = -3;

    protected ICameraControlCallback mCallback;

    public void setCallback(ICameraControlCallback callback) {
        mCallback = callback;
    }

    public abstract boolean openCamera(CameraParams p);

    public abstract boolean isCameraClosed();

    public abstract boolean closeCamera();

    public abstract boolean switchCamera(CameraParams p);

    public abstract void setDefaultCamera(boolean frontCamera);

    public abstract boolean isFlashOn();

    public abstract boolean supportFlash();

    public abstract boolean setFlash(boolean on);

    public abstract boolean autoFocus();

    public abstract boolean manualZoom(int zoomValue);

    public abstract boolean manualFocus(Rect r);

    public abstract boolean setFrameRate(int fps);

    public abstract int getMaxZoom();

    public abstract int getCurrentZoom();

    public abstract boolean usingFrontCamera();

    public abstract void startPreview(SurfaceTexture surfaceTexture);

    public abstract int[] getFinalPreviewSize();

    public abstract int[] getExposureCompensationValue();

    public abstract boolean manualExposureCompensation(int value);


    public interface ICameraControlCallback {

        void onCameraOpened(ICameraInterface cameraInterface, boolean first);

        void onCameraClosed();

        void onCameraError(int code);
    }
}
