package net.lang.streamer2.engine.capture;

import android.graphics.Rect;
import android.opengl.GLSurfaceView;
import android.util.Size;

import com.yunfan.graphicbuffer.GraphicBufferWrapper;

import net.lang.gpuimage.helper.MagicFilterType;

import net.lang.streamer2.config.LangFaceuConfig;
import net.lang.streamer2.config.LangObjectSegmentationConfig;
import net.lang.streamer2.config.LangWatermarkConfig;
import net.lang.streamer2.camera.CameraParams;
import net.lang.streamer2.camera.ICameraInterface;
import net.lang.streamer2.camera.LangCamera;
import net.lang.streamer2.engine.data.LangVideoConfiguration;
import net.lang.streamer2.engine.renderer.LangEffectRenderer;
import net.lang.streamer2.faceu.IFaceuListener;
import net.lang.streamer2.utils.DebugLog;

import java.io.InputStream;
import java.nio.FloatBuffer;

public final class LangVideoCapture implements LangEffectRenderer.IRenderListener, IFaceuListener {
    private static final String TAG = LangVideoCapture.class.getSimpleName();
    private static final Size sDefaultPreviewSize = new Size(1280, 720);

    private LangVideoCaptureListener mListener;
    private IFaceuListener mFaceuListener;

    private final Object mFence = new Object();
    private boolean mRenderReady = false;
    private boolean mCameraReady = false;

    private Size mVideoSize;

    private FloatBuffer mCameraEffectValue = null;

    private GLSurfaceView mGlSurafceView;

    private LangEffectRenderer mRenderer;

    private CameraParams mCameraParams;

    private ICameraInterface mCameraInterface;

    private ICameraInterface.ICameraControlCallback mCameraCallback = new ICameraInterface.ICameraControlCallback() {
        @Override
        public void onCameraOpened(ICameraInterface cameraInterface, boolean first) {
            DebugLog.d(TAG, "onCameraOpened, tid =" + Thread.currentThread().getId());
            synchronized (mFence) {
                mCameraReady = true;
                if (mRenderReady) {
                    mRenderer.setCameraPreviewSize(cameraInterface, mCameraParams);
                    mRenderer.startPreview(cameraInterface);
                }
            }
        }

        @Override
        public void onCameraClosed() {
            DebugLog.d(TAG, "onCameraClosed");
        }

        @Override
        public void onCameraError(int code) {
            DebugLog.d(TAG, "onCameraError, code=" + code);
        }
    };

    public LangVideoCapture(LangVideoConfiguration videoConfiguration) {

        if (videoConfiguration.getLandscape()) {
            mVideoSize = new Size(videoConfiguration.getWidth(), videoConfiguration.getHeight());
        } else {
            mVideoSize = new Size(videoConfiguration.getHeight(), videoConfiguration.getWidth());
        }

        mCameraParams = new CameraParams();
        mCameraParams.setPreviewWidth(sDefaultPreviewSize.getWidth());
        mCameraParams.setPreviewHeight(sDefaultPreviewSize.getHeight());
        mCameraParams.setFrameRate(videoConfiguration.getFps());
        mCameraParams.setLandscape(videoConfiguration.getLandscape());

        mCameraInterface = LangCamera.getInstance();
        mCameraInterface.setCallback(mCameraCallback);
    }

    public void setSurfaceView(GLSurfaceView glSurfaceView) {
        mRenderer = new LangEffectRenderer(glSurfaceView.getContext());
        mRenderer.setRenderListener(this);
        mRenderer.setFaceuListener(this);
        mRenderer.bindGlSurfaceView(glSurfaceView);

        glSurfaceView.setEGLContextClientVersion(2);
        glSurfaceView.setRenderer(mRenderer);
        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        mGlSurafceView = glSurfaceView;
    }

    public GLSurfaceView getSurfaceView() {
        return mGlSurafceView;
    }

    public Size getVideoSize() {
        return mVideoSize;
    }

    public void setCaptureListener(LangVideoCaptureListener listener) {
        mListener = listener;
    }

    public void setFaceuListener(IFaceuListener listener) {
        mFaceuListener = listener;
    }

    public void start() {
        DebugLog.d(TAG, "start, tid =" + Thread.currentThread().getId());
        if (mRenderer == null) {
            throw new IllegalStateException("capture surfaceView has not been set");
        }
        // set video encoder output size to graphicbuffer
        mRenderer.setVideoOutputSize(mGlSurafceView.getContext(), mVideoSize.getWidth(), mVideoSize.getHeight());

        mCameraInterface.openCamera(mCameraParams);
    }

    public void stop() {
        if (mRenderer == null) {
            throw new IllegalStateException("capture surfaceView has not been set");
        }
        mCameraInterface.closeCamera();
        mCameraInterface.setDefaultCamera(true);
    }

    // set camera horizontal mirror
    public void setCameraMirror(boolean enable) {
        if (mRenderer == null) {
            throw new IllegalStateException("capture surfaceView has not been set");
        }
        mRenderer.flip(enable, false);
    }

    public void setZoom(float scale) {
        if (scale < 1.0) scale = 1.0f;
        if (scale > 3.0) scale = 3.0f;
        float zoom = (float) (scale * (-0.335) + 1.335);
        if (mCameraEffectValue == null) {
            mCameraEffectValue = FloatBuffer.allocate(4);
            mCameraEffectValue.position(0);
            mCameraEffectValue.put(0.f);
            mCameraEffectValue.put(1.0f);
            mCameraEffectValue.put(1.0f);
            mCameraEffectValue.put(1.0f);
            mCameraEffectValue.position(0);
        }
        mCameraEffectValue.position(0);
        mCameraEffectValue.array()[1] = zoom;

        if (mRenderer == null) {
            throw new IllegalStateException("capture surfaceView has not been set");
        }
        mRenderer.setMixColor(mCameraEffectValue);;
    }

    public void setCameraBrightLevel(float level) {
        if (mCameraEffectValue == null) {
            mCameraEffectValue = FloatBuffer.allocate(4);
            mCameraEffectValue.position(0);
            mCameraEffectValue.put(1.0f);
            mCameraEffectValue.put(1.0f);
            mCameraEffectValue.put(1.0f);
            mCameraEffectValue.put(1.0f);
            mCameraEffectValue.position(0);
        }
        mCameraEffectValue.position(0);
        float v = level > 1.0f ? 1.0f : level;
        mCameraEffectValue.array()[0] = v;

        if (mRenderer == null) {
            throw new IllegalStateException("capture surfaceView has not been set");
        }
        mRenderer.setMixColor(mCameraEffectValue);;
    }

    // switch camera front/back.
    public void switchCamera() {
        mCameraInterface.switchCamera(mCameraParams);
    }

    // set camera torch enabled.
    public void setCameraToggleTorch(boolean enable) {
        mCameraInterface.setFlash(enable);
    }

    // set camera focus position.
    public void setCameraFocusing(float x, float y) {
        DebugLog.d(TAG, "setCameraFocusing" + " x=" + x + " y=" + y);
        if (mRenderer == null) {
            throw new IllegalStateException("capture surfaceView has not been set");
        }

        float width = mGlSurafceView.getWidth();
        float height = mGlSurafceView.getHeight();
        Rect focusRect = calculateTapArea(x, y, width, height, 1.0f);
        mCameraInterface.manualFocus(focusRect);
    }

    // set gpu filter type.
    public void setFilter(MagicFilterType type) {
        if (mRenderer == null) {
            throw new IllegalStateException("capture surfaceView has not been set");
        }
        mRenderer.setEffectFilter(mGlSurafceView.getContext(), type);
    }

    public void enableAutoBeauty(final boolean enable) {
        if (mRenderer == null) {
            throw new IllegalStateException("capture surfaceView has not been set");
        }
        mRenderer.enableAutoBeautyFilter(enable);
    }

    public void setAutoBeautyLevel(final int level) {
        if (mRenderer == null) {
            throw new IllegalStateException("capture surfaceView has not been set");
        }
        mRenderer.setAutoBeautyLevel(level);
    }

    public void updateWaterMarkConfig(final LangWatermarkConfig config) {
        if (mRenderer == null) {
            throw new IllegalStateException("capture surfaceView has not been set");
        }
        mRenderer.updateWaterMarkConfig(config);
    }

    public void updateFaceuConfig(final LangFaceuConfig config) {
        if (mRenderer == null) {
            throw new IllegalStateException("capture surfaceView has not been set");
        }
        mRenderer.updateFaceuConfig(config);
    }

    public void updateMattingConfig(final LangObjectSegmentationConfig params, final InputStream stream, final InputStream giftStream) {
        //((LangMagicCameraView)baseView).updateMattingConfig(params, stream, giftStream);
    }

    public void updateBeautyHairConfig(final LangObjectSegmentationConfig params) {
        //((LangMagicCameraView)baseView).updateBeautyHairConfig(params);
    }

    public void enableMakeups(final boolean enable) {
        //((LangMagicCameraView)baseView).enableMakeups(enable);
    }

    // screenshot to the given path.
    public void screenshot(String path) {
        //((LangMagicCameraView)baseView).screenshot(path);
    }

    public double getPreviewFps() {
        return mRenderer.getPreviewFps();
    }

    public void release() {
        mRenderer.release();
        mListener = null;
        mFaceuListener = null;
    }


    private static int clamp(int x, int min, int max) {
        if (x > max) {
            return max;
        }
        if (x < min) {
            return min;
        }
        return x;
    }

    private static Rect calculateTapArea(float x, float y, float width, float height, float coefficient) {
        float focusAreaSize = 300;
        int areaSize = Float.valueOf(focusAreaSize * coefficient).intValue();

        int centerX = (int) (x / width * 2000 - 1000);
        int centerY = (int) (y / height * 2000 - 1000);

        int left = clamp(centerX - areaSize / 2, -1000, 1000);
        int right = clamp(left + areaSize, -1000, 1000);
        int top = clamp(centerY - areaSize / 2, -1000, 1000);
        int bottom = clamp(top + areaSize, -1000, 1000);

        return new Rect(left, top, right, bottom);
    }


    // implemente LangEffectRenderer.IRenderListener
    @Override
    public boolean skip(long timestampNs) {
        if (mListener != null) {
            return mListener.skip(timestampNs);
        }
        return false;
    }

    @Override
    public void onRenderPrepared() {
        DebugLog.d(TAG, "onRenderPrepared, tid =" + Thread.currentThread().getId());
        synchronized (mFence) {
            mRenderReady = true;
            if (mCameraReady) {
                mRenderer.setCameraPreviewSize(mCameraInterface, mCameraParams);
                mRenderer.startPreview(mCameraInterface);
            }
        }
    }

    @Override
    public void onRenderFrame(GraphicBufferWrapper gb, long timestampNs) {
        if (mListener != null) {
            mListener.onCapturedVideoFrame(gb, timestampNs);
        }
    }

    // implements IFaceuListener
    @Override
    public void onHumanFaceDetected(int faceCount) {
        if (mFaceuListener != null) {
            mFaceuListener.onHumanFaceDetected(faceCount);
        }
    }

    @Override
    public void onHumanHandDetected(int handCount, FaceuGestureType gesture) {
        if (mFaceuListener != null) {
            mFaceuListener.onHumanHandDetected(handCount, gesture);
        }
    }

    public interface LangVideoCaptureListener {

        boolean skip(long timestampNs);

        void onCapturedVideoFrame(GraphicBufferWrapper gb, long timestampNs);
    }
}
