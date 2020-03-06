package net.lang.streamer2.engine.renderer;

import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;

import net.lang.gpuimage.utils.OpenGlUtils;
import net.lang.streamer2.utils.DebugLog;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;


public abstract class LangBaseRenderer implements GLSurfaceView.Renderer {
    private static final String TAG = LangBaseRenderer.class.getSimpleName();

    private int surfaceWidth, surfaceHeight;
    private SurfaceTexture mSurfaceTexture;
    private GLSurfaceView mGlSurfaceView;

    private int mTextureId = OpenGlUtils.NO_TEXTURE;

    public final void bindGlSurfaceView(GLSurfaceView mGlSurfaceView) {
        this.mGlSurfaceView = mGlSurfaceView;
    }

    public final void queueEvent(Runnable r) {
        mGlSurfaceView.queueEvent(r);
        //mGlSurfaceView.requestRender();
    }

    // implement GLSurfaceView.Renderer
    @Override
    public final void onSurfaceCreated(GL10 gl, EGLConfig config) {
        DebugLog.d(TAG, "onSurfaceCreated");
        GLES20.glDisable(GL10.GL_DITHER);
        GLES20.glClearColor(0, 0, 0, 0);
        GLES20.glEnable(GL10.GL_CULL_FACE);
        GLES20.glEnable(GL10.GL_DEPTH_TEST);
        setupSurfaceTexture();
        onFilterInitialized();
    }

    @Override
    public final void onSurfaceChanged(GL10 gl, int width, int height) {
        DebugLog.d(TAG, "onSurfaceChanged " + " width=" + width + " height=" + height);
        GLES20.glViewport(0, 0, width, height);
        surfaceWidth = width;
        surfaceHeight = height;
        onFilterChanged();
    }

    @Override
    public final void onDrawFrame(GL10 gl) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        if (getInputWidth() <= 0 || getInputHeight() <= 0)
            return;
        if (mSurfaceTexture == null)
            return;
        mSurfaceTexture.updateTexImage();

        onFilterDraw(mTextureId);
    }

    private void setupSurfaceTexture() {
        DebugLog.d(TAG, "setupSurfaceTexture");
        if (mTextureId == OpenGlUtils.NO_TEXTURE) {
            mTextureId = OpenGlUtils.getExternalOESTextureID();
            mSurfaceTexture = new SurfaceTexture(mTextureId);
            mSurfaceTexture.setOnFrameAvailableListener(onFrameAvailableListener);
        }
    }

    private SurfaceTexture.OnFrameAvailableListener onFrameAvailableListener = new SurfaceTexture.OnFrameAvailableListener() {

        @Override
        public void onFrameAvailable(SurfaceTexture surfaceTexture) {
            mGlSurfaceView.requestRender();
        }
    };

    protected abstract void onFilterInitialized();

    protected abstract void onFilterChanged();

    protected abstract void onFilterDraw(final int srcTextureId);

    protected abstract int getInputWidth();

    protected abstract int getInputHeight();

    protected final int getSurfaceWidth() {
        return surfaceWidth;
    }

    protected final int getSurfaceHeight() {
        return surfaceHeight;
    }

    protected final SurfaceTexture getSurfaceTexture() {
        return mSurfaceTexture;
    }

    protected final long getCurrentTimestampNs() {
        //return mSurfaceTexture.getTimestamp();
        return System.nanoTime();
    }
}
