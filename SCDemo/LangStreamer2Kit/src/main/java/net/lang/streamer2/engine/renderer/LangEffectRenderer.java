package net.lang.streamer2.engine.renderer;

import android.content.Context;
import android.graphics.PixelFormat;
import android.opengl.GLES20;

import com.yunfan.graphicbuffer.GraphicBufferWrapper;

import net.lang.gpuimage.filter.custom.MagicWaterMarkFilter;
import net.lang.gpuimage.filter.base.MagicCameraInputFilter;
import net.lang.gpuimage.filter.base.gpuimage.GPUImageFilter;
import net.lang.gpuimage.helper.MagicFilterFactory;
import net.lang.gpuimage.helper.MagicFilterType;
import net.lang.gpuimage.utils.Rotation;
import net.lang.gpuimage.utils.TextureRotationUtil;

import net.lang.streamer2.config.LangFaceuConfig;
import net.lang.streamer2.config.LangWatermarkConfig;

import net.lang.streamer2.faceu.IFaceuListener;
import net.lang.streamer2.faceu.SenseMEFilter;

import net.lang.streamer2.camera.CameraParams;
import net.lang.streamer2.camera.ICameraInterface;
import net.lang.streamer2.utils.DebugLog;
import net.lang.streamer2.utils.SpeedStatistics;
import net.lang.streamer2.utils.TextureUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;


public class LangEffectRenderer extends LangBaseRenderer {
    private static final String TAG = LangEffectRenderer.class.getSimpleName();

    private static final float[] sIdentityMatrx = {
            1.0f, 0.0f, 0.f, 0f,
            0.0f, 1.0f, 0.f, 0f,
            0.0f, 0.0f, 1.f, 0f,
            0.0f, 0.0f, 0.f, 0f,
    };

    private int imageWidth = 0, imageHeight = 0;

    private FloatBuffer gLCubeBuffer;
    private FloatBuffer gLTextureBuffer;
    private FloatBuffer gLCameraTextureBuffer;
    private FloatBuffer glVideoTextureBuffer;

    private MagicCameraInputFilter mCameraInputFilter;
    private GPUImageFilter mOutputFilter;
    private MagicWaterMarkFilter mWaterMarkFilter;
    private GPUImageFilter mBeautyFilter, mEffectFilter;
    private SenseMEFilter.FaceuConfig mInternalFaceuConfig = null;
    private SenseMEFilter mFaceTracker = null;

    private GraphicBufferWrapper mGraphicBuffer = null;
    private TextureUtils mSwapTexture1 = null;
    private TextureUtils mSwapTexture2 = null;

    private SpeedStatistics mCameraPreviewFps = null;

    private boolean inputSizeChanged = false;
    private IRenderListener mListener;

    public interface IRenderListener {
        boolean skip(long timestampNs);

        void onRenderPrepared();

        void onRenderFrame(GraphicBufferWrapper gb, long timestampNs);
    }

    public LangEffectRenderer(Context context) {
        createBuffers();
        createFilters(context);
        mCameraPreviewFps = new SpeedStatistics();
    }

    public double getPreviewFps() {
        return mCameraPreviewFps.rate();
    }

    public void setRenderListener(IRenderListener listener) {
        this.mListener = listener;
    }

    public void setFaceuListener(IFaceuListener listener) {
        if (mFaceTracker != null) {
            mFaceTracker.setDetectionListener(listener);
        }
    }

    public void setVideoOutputSize(Context context, int videoWidth, int videoHeight) {
        if (videoWidth == 0 || videoHeight == 0) {
            throw new RuntimeException("video width or height is not set");
        }
        mGraphicBuffer = GraphicBufferWrapper.createInstance(context, videoWidth, videoHeight, PixelFormat.RGBA_8888);
    }

    public void setCameraPreviewSize(ICameraInterface cameraInterface, CameraParams cameraParams) {
        if (cameraParams.isLandscape()) {
            imageWidth = cameraParams.getPreviewWidth();
            imageHeight = cameraParams.getPreviewHeight();
        } else {
            imageWidth = cameraParams.getPreviewHeight();
            imageHeight = cameraParams.getPreviewWidth();
        }

        DebugLog.d(TAG, "setCameraPreviewSize: width=" + imageWidth + " height=" + imageHeight);

        updateCameraTextureBuffer(cameraInterface, cameraParams);

        inputSizeChanged = true;
    }

    public void startPreview(ICameraInterface cameraInterface) {
        DebugLog.d(TAG, "startPreview");

        if (getSurfaceTexture() == null) {
            throw new RuntimeException("no valid surfaceTexture assigned to camera");
        }
        cameraInterface.startPreview(getSurfaceTexture());
    }

    private void updateCameraTextureBuffer(ICameraInterface cameraInterface, CameraParams cameraParams) {
        int orientation = cameraParams.getOrientation();
        Rotation rotation = Rotation.fromInt(orientation);
        boolean flip = cameraInterface.usingFrontCamera();
        float[] textureCords = TextureRotationUtil.getRotation(rotation, true, flip);
        gLCameraTextureBuffer.clear();
        gLCameraTextureBuffer.put(textureCords).position(0);
    }

    public void setEffectFilter(final Context context, final MagicFilterType type){
        queueEvent(new Runnable() {
            @Override
            public void run() {
                if (mEffectFilter != null)
                    mEffectFilter.destroy();
                mEffectFilter = null;
                mEffectFilter = MagicFilterFactory.initFilters(type, context);
                if (mEffectFilter != null)
                    mEffectFilter.init();
                onFilterChanged();
            }
        });
    }

    // update video filter texture corrdinate
    public void flip(final boolean flipHorizontal, final boolean flipVertical) {
        queueEvent(new Runnable() {
            @Override
            public void run() {
                glVideoTextureBuffer.clear();
                glVideoTextureBuffer.put(TextureRotationUtil.getRotation(Rotation.NORMAL, flipHorizontal, true)).position(0);
            }
        });
    }

    // update camera brightness
    public final void setMixColor(final FloatBuffer rgba) {
        mCameraInputFilter.setMixColor(rgba);
    }

    public final void updateWaterMarkConfig(final LangWatermarkConfig config) {
        mWaterMarkFilter.setEnable(config.enable);
        if (config.picture != null) {
            mWaterMarkFilter.setBitmap(config.picture);
        } else {
            mWaterMarkFilter.setUrl(config.url);
        }

        mWaterMarkFilter.setFullScreen(config.fullScreen);
        mWaterMarkFilter.setRect(config.x, config.y, config.w, config.h);
    }

    public void enableAutoBeautyFilter(final boolean enable) {
        queueEvent(new Runnable() {
            @Override
            public void run() {
                mInternalFaceuConfig.enableBeauty = enable;
                mFaceTracker.setFaceuConfig(mInternalFaceuConfig);
            }
        });
    }

    public final void setAutoBeautyLevel(final int level) {
        queueEvent(new Runnable() {
            @Override
            public void run() {
                if (level < 1 || level > 5) {
                    return;
                }
                if (level == 1) {
                    mInternalFaceuConfig.setBasicParams(0.18f, 0.09f, 0.01f);
                } else if (level == 2) {
                    mInternalFaceuConfig.setBasicParams(0.36f, 0.18f, 0.02f);
                } else if (level == 3) {
                    mInternalFaceuConfig.setBasicParams(0.54f, 0.27f, 0.03f);
                } else if (level == 4) {
                    mInternalFaceuConfig.setBasicParams(0.72f, 0.36f, 0.04f);
                } else if (level == 5) {
                    mInternalFaceuConfig.setBasicParams(0.90f, 0.45f, 0.05f);
                }
                mFaceTracker.setFaceuConfig(mInternalFaceuConfig);
            }
        });
    }

    public final void updateFaceuConfig(final LangFaceuConfig config) {
        queueEvent(new Runnable() {
            @Override
            public void run() {
                setFaceuConfig(config);
                if (config.needSpecialSticker) {
                    mFaceTracker.enableGraphicBuffer(false);
                    mFaceTracker.enableFaceExtraPoint(true);
                } else {
                    mFaceTracker.enableGraphicBuffer(true);
                    mFaceTracker.enableFaceExtraPoint(false);
                }
            }
        });
    }

    private void setFaceuConfig(final LangFaceuConfig config) {
        mInternalFaceuConfig.enableBeauty = config.needBeautify;
        mInternalFaceuConfig.enableBodyBeauty = config.needBodyBeautify;
        mInternalFaceuConfig.enableSticker = config.needSticker;
        mInternalFaceuConfig.reddenStrength = config.reddenStrength;
        mInternalFaceuConfig.smoothStrength = config.smoothStrength;
        mInternalFaceuConfig.whitenStrength = config.whitenStrength;
        mInternalFaceuConfig.contrastStrength = config.contrastStrength;
        mInternalFaceuConfig.saturationStrength = config.saturationStrength;
        mInternalFaceuConfig.enlargeEyeRatio = config.enlargeEyeRatio;
        mInternalFaceuConfig.shrinkFaceRatio = config.shrinkFaceRatio;
        mInternalFaceuConfig.shrinkJawRatio = config.shrinkJawRatio;
        mInternalFaceuConfig.narrowFaceStrength = config.narrowFaceStrength;
        mInternalFaceuConfig.stickerItem = config.beautyStickerPath;
        mFaceTracker.setFaceuConfig(mInternalFaceuConfig);
    }

    private void createFilters(Context context) {
        mCameraInputFilter = new MagicCameraInputFilter(context);
        mCameraInputFilter.setTextureTransformMatrix(sIdentityMatrx);

        mOutputFilter = new GPUImageFilter();

        mWaterMarkFilter = new MagicWaterMarkFilter(context);
        mWaterMarkFilter.setRect(0, 0, 300, 200);

        mInternalFaceuConfig = new SenseMEFilter.FaceuConfig();
        mInternalFaceuConfig.defaultBeautyParams();
        mFaceTracker = new SenseMEFilter(context);

        mSwapTexture1 = new TextureUtils();
        mSwapTexture2 = new TextureUtils();

        DebugLog.d(TAG, "onFiltersCreated, time = " + System.currentTimeMillis());
    }

    private void createBuffers() {
        gLCubeBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.CUBE.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        gLCubeBuffer.put(TextureRotationUtil.CUBE).position(0);

        gLTextureBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        gLTextureBuffer.put(TextureRotationUtil.TEXTURE_NO_ROTATION).position(0);

        gLCameraTextureBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();

        glVideoTextureBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        glVideoTextureBuffer.put(TextureRotationUtil.getRotation(Rotation.NORMAL, false, true)).position(0);
    }

    @Override
    protected void onFilterInitialized() {
        DebugLog.d(TAG, "onFilterInitialized, time =" + System.currentTimeMillis());

        mCameraInputFilter.init();

        mWaterMarkFilter.init();

        mFaceTracker.init();

        mOutputFilter.init();

        if (mListener != null) {
            mListener.onRenderPrepared();
        }
    }

    @Override
    protected void onFilterChanged() {
        if(mEffectFilter != null) {
            mEffectFilter.onDisplaySizeChanged(getSurfaceWidth(), getSurfaceHeight());
            mEffectFilter.onInputSizeChanged(imageWidth, imageHeight);
        }
        if (mWaterMarkFilter != null) {
            mWaterMarkFilter.setSurface(getSurfaceWidth(), getSurfaceHeight());
        }
    }

    @Override
    protected void onFilterDraw(final int srcTextureId) {
        prepare();

        int inputTextureId = srcTextureId;
        int outputFrameBufferId = mSwapTexture1.id();

        // draw camera texture
        GLES20.glViewport(0, 0, imageWidth, imageHeight);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, outputFrameBufferId);
        mCameraInputFilter.onDrawFrame(inputTextureId, gLCubeBuffer, gLCameraTextureBuffer);
        inputTextureId = mSwapTexture1.textureID();

        // process faceu
        inputTextureId = mFaceTracker.onDrawFrame(inputTextureId);

        // draw effect texture.
        if (mEffectFilter != null) {
            outputFrameBufferId = mSwapTexture2.id();
            GLES20.glViewport(0, 0, imageWidth, imageHeight);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, outputFrameBufferId);
            mEffectFilter.onDrawFrame(inputTextureId);
            inputTextureId = mSwapTexture2.textureID();
        }

        boolean skipCurrentFrame = false;
        if (mListener != null) {
            skipCurrentFrame = mListener.skip(getCurrentTimestampNs());
        }

        if (!skipCurrentFrame) {
            // draw camera output texture to graphic buffer
            outputFrameBufferId = mGraphicBuffer.framebufferId();
            int videoWidth = mGraphicBuffer.width();
            int videoHeight = mGraphicBuffer.height();
            GLES20.glViewport(0, 0, videoWidth, videoHeight);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, outputFrameBufferId);
            mOutputFilter.onDrawFrame(inputTextureId, gLCubeBuffer, glVideoTextureBuffer);

            // draw watermark
            boolean watermark = mWaterMarkFilter.enable();
            if (watermark) {
                mWaterMarkFilter.onDrawFrameWaterMark(videoWidth, videoHeight);
            }

            if (mListener != null) {
                mListener.onRenderFrame(mGraphicBuffer, getCurrentTimestampNs());
            }
        }

        // draw screen display
        GLES20.glViewport(0, 0, getSurfaceWidth(), getSurfaceHeight());
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        mOutputFilter.onDrawFrame(inputTextureId, gLCubeBuffer, gLTextureBuffer);

        mCameraPreviewFps.add();
    }

    private void prepare() {
        if (inputSizeChanged) {

            mFaceTracker.onInputSizeChanged(imageWidth, imageHeight);

            mSwapTexture1.initTexture_l(imageWidth, imageHeight);
            mSwapTexture2.initTexture_l(imageWidth, imageHeight);

            int[] graphicBufferFrameBufferID = new int[1];
            GLES20.glGenFramebuffers(1, graphicBufferFrameBufferID, 0);

            int graphicBufferTexID = mGraphicBuffer.createTexture(graphicBufferFrameBufferID[0]);
            if (graphicBufferTexID <= 0) {
                throw new RuntimeException("create shared texture failed in GraphicBuffer");
            }
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                    GLES20.GL_TEXTURE_2D, graphicBufferTexID, 0);

            inputSizeChanged = false;
        }
    }

    @Override
    protected int getInputWidth() {
        return imageWidth;
    }

    @Override
    protected int getInputHeight() {
        return imageHeight;
    }

    public void release() {
        DebugLog.d(TAG, "release");
        if (mCameraInputFilter != null) {
            mCameraInputFilter.destroy();
            mCameraInputFilter = null;
        }

        if (mOutputFilter != null) {
            mOutputFilter.destroy();
            mOutputFilter = null;
        }

        if (mWaterMarkFilter != null) {
            mWaterMarkFilter.destroy();
            mWaterMarkFilter = null;
        }

        if (mEffectFilter != null) {
            mEffectFilter.destroy();
            mEffectFilter = null;
        }

        if (mSwapTexture1 != null) {
            mSwapTexture1.initTexture_l(0, 0);
            mSwapTexture1 = null;
        }

        if (mSwapTexture2 != null) {
            mSwapTexture2.initTexture_l(0, 0);
            mSwapTexture2 = null;
        }

        if (mFaceTracker != null) {
            mFaceTracker.destroy();
            mFaceTracker = null;
        }

        if (mGraphicBuffer != null) {
            mGraphicBuffer.destroy();
            mGraphicBuffer = null;
        }

        mListener = null;
    }
}
