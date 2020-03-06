package net.lang.gpuimage.filter.advanced.beauty;

import android.content.Context;
import android.graphics.PointF;
import android.opengl.GLES20;

import net.lang.gpuimage.filter.base.gpuimage.GPUImageExposureFilter;
import net.lang.gpuimage.filter.base.gpuimage.GPUImageFilter;
import net.lang.gpuimage.filter.base.gpuimage.GPUImageSharpenFilter;
import net.lang.gpuimage.filter.base.gpuimage.GPUImageToneCurveFilter;
import net.lang.gpuimage.utils.OpenGlUtils;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

public class MagicBeautySmoothingFilter extends GPUImageFilter {

    protected List<GPUImageFilter> filters;
    private static int[] frameBuffers = null;
    private static int[] frameBufferTextures = null;

    private int frameWidth = -1;
    private int frameHeight = -1;

    private float mMixFactor = 0.75f;
    private float mSharpenFactor = 0.4f;
    private PointF[] rgbCurvePoints = null;

    private GPUImageExposureFilter mExposureFilter = null;
    private int mFbExposure = -1;
    private int mTextureExposure = -1;

    private MagicOverlayFilter mChannelOverlayFilter = null;
    private int mFbOverlay = -1;
    private int mTextureOverlay = -1;

    private MagicBlurFilter mBlurFilterHor = null;
    private int mFbBlurHor = -1;
    private int mTextureBlurHor = -1;

    private MagicBlurFilter mBlurFilterVer = null;
    private int mFbBlurVer = -1;
    private int mTextureBlurVer = -1;

    private MagicHighPassFilter mHighPassFilter = null;
    private int mFbHighpass = -1;
    private int mTextureHighpass = -1;

    private MagicMaskBoostFilter mMaskBoostFilter = null;
    private int mFbMaskBoost = -1;
    private int mTextureMaskBoost = -1;

    private GPUImageToneCurveFilter mToneCurveFilter = null;
    private int mFbToneCurve = -1;
    private int mTextureToneCurve = -1;

    private MagicDissolveBlendFilter mDissolveBlendFilter = null;
    private int mFbBlend = -1;
    private int mTextureBlend = -1;

    private MagicCompositingFilter mCompositingFilter = null;
    private int mFbCompositing = -1;
    private int mTextureCompositing = -1;

    private GPUImageSharpenFilter mSharpenFilter = null;

    public MagicBeautySmoothingFilter(Context context) {
        filters = new ArrayList<>();
        filters.clear();

        mExposureFilter = new GPUImageExposureFilter(-1.0f);
        filters.add(mExposureFilter);

        mChannelOverlayFilter = new MagicOverlayFilter();
        filters.add(mChannelOverlayFilter);

        mBlurFilterHor = new MagicBlurFilter(true, 1.0f, context);
        filters.add(mBlurFilterHor);

        mBlurFilterVer = new MagicBlurFilter(false, 1.0f, context);
        filters.add(mBlurFilterVer);

        mHighPassFilter = new MagicHighPassFilter();
        filters.add(mHighPassFilter);

        mMaskBoostFilter = new MagicMaskBoostFilter();
        filters.add(mMaskBoostFilter);

        mToneCurveFilter = new GPUImageToneCurveFilter();
        filters.add(mToneCurveFilter);

        mDissolveBlendFilter = new MagicDissolveBlendFilter(0.75f);
        filters.add(mDissolveBlendFilter);

        mCompositingFilter = new MagicCompositingFilter();
        filters.add(mCompositingFilter);

        mSharpenFilter = new GPUImageSharpenFilter(0.4f);
        filters.add(mSharpenFilter);

        rgbCurvePoints = new PointF[]{new PointF(0.0f, 0.0f), new PointF(120.0f/255.0f, 146.0f/255.0f), new PointF(1.0f, 1.0f)};
        mToneCurveFilter.setRgbCompositeControlPoints(rgbCurvePoints);

        setAmount(0.75f);
        setSharpenFactor(0.4f);
    }

    public void setBeautyLevel(int level){
        switch (level) {
            case 1:
                setAmount(0.35f);
                break;
            case 2:
                setAmount(0.50f);
                break;
            case 3:
                setAmount(0.75f);
                break;
            case 4:
                setAmount(0.90f);
                break;
            case 5:
                setAmount(0.95f);
                break;
            case 6:
                setAmount(0.99f);
                break;
            default:
                break;
        }
    }

    @Override
    public void init() {
        for (GPUImageFilter filter : filters) {
            filter.init();
        }
    }

    @Override
    public void onInputSizeChanged(final int width, final int height) {
        super.onInputSizeChanged(width, height);
        int size = filters.size();
        for (int i = 0; i < size; i++) {
            filters.get(i).onInputSizeChanged(width, height);
        }
        if(frameBuffers != null && (frameWidth != width || frameHeight != height || frameBuffers.length != size-1)){
            destroyFramebuffers();
            frameWidth = width;
            frameHeight = height;
        }
        if (frameBuffers == null) {
            frameBuffers = new int[size-1];
            frameBufferTextures = new int[size-1];

            for (int i = 0; i < size-1; i++) {
                GLES20.glGenFramebuffers(1, frameBuffers, i);

                GLES20.glGenTextures(1, frameBufferTextures, i);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, frameBufferTextures[i]);
                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
                        GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
                GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                        GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
                GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                        GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
                GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                        GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
                GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                        GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffers[i]);
                GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                        GLES20.GL_TEXTURE_2D, frameBufferTextures[i], 0);

                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            }

            mFbExposure = frameBuffers[0];
            mTextureExposure = frameBufferTextures[0];

            mFbOverlay = frameBuffers[1];
            mTextureOverlay = frameBufferTextures[1];

            mFbBlurHor = frameBuffers[2];
            mTextureBlurHor = frameBufferTextures[2];

            mFbBlurVer = frameBuffers[3];
            mTextureBlurVer = frameBufferTextures[3];

            mFbHighpass = frameBuffers[4];
            mTextureHighpass = frameBufferTextures[4];

            mFbMaskBoost = frameBuffers[5];
            mTextureMaskBoost = frameBufferTextures[5];

            mFbToneCurve = frameBuffers[6];
            mTextureToneCurve = frameBufferTextures[6];

            mFbBlend = frameBuffers[7];
            mTextureBlend = frameBufferTextures[7];

            mFbCompositing = frameBuffers[8];
            mTextureCompositing = frameBufferTextures[8];
        }
    }

    @Override
    public int onDrawFrame(final int textureId,
                           final FloatBuffer cubeBuffer, final FloatBuffer textureBuffer) {
        if (frameBuffers == null || frameBufferTextures == null) {
            return OpenGlUtils.NOT_INIT;
        }

        return drawFrameInternal(textureId, cubeBuffer, textureBuffer);
    }

    @Override
    public int onDrawFrame(final int textureId) {
        if (frameBuffers == null || frameBufferTextures == null) {
            return OpenGlUtils.NOT_INIT;
        }

        return drawFrameInternal(textureId, mGLCubeBuffer, mGLTextureBuffer);
    }

    @Override
    public void onDestroy() {
        for (GPUImageFilter filter : filters) {
            filter.destroy();
        }
        destroyFramebuffers();
    }

    private int drawFrameInternal(final int textureId, final FloatBuffer cubeBuffer,
                                  final FloatBuffer textureBuffer) {
        int[] previousViewport = new int[4];
        previousViewport[0] = previousViewport[1] = previousViewport[2] = previousViewport[3] = 0;
        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, previousViewport, 0);

        int[] drawFboId = new int[1];
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, drawFboId, 0);

        // exposure
        GLES20.glViewport(0, 0, mIntputWidth, mIntputHeight);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFbExposure);
        GLES20.glClearColor(0, 0, 0, 0);
        mExposureFilter.onDrawFrame(textureId, mGLCubeBuffer, mGLTextureBuffer);

        // channel G/B overlay
        GLES20.glViewport(0, 0, mIntputWidth, mIntputHeight);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFbOverlay);
        GLES20.glClearColor(0, 0, 0, 0);
        mChannelOverlayFilter.onDrawFrame(mTextureExposure, mGLCubeBuffer, mGLTextureBuffer);

        // gaussian blur
        GLES20.glViewport(0, 0, mIntputWidth, mIntputHeight);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFbBlurHor);
        GLES20.glClearColor(0, 0, 0, 0);
        mBlurFilterHor.onDrawFrame(mTextureOverlay, mGLCubeBuffer, mGLTextureBuffer);

        GLES20.glViewport(0, 0, mIntputWidth, mIntputHeight);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFbBlurVer);
        GLES20.glClearColor(0, 0, 0, 0);
        mBlurFilterHor.onDrawFrame(mTextureBlurHor, mGLCubeBuffer, mGLTextureBuffer);

        // high pass filter.
        GLES20.glViewport(0, 0, mIntputWidth, mIntputHeight);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFbHighpass);
        GLES20.glClearColor(0, 0, 0, 0);
        mHighPassFilter.onDrawFrame(mTextureOverlay, mTextureBlurVer, mGLCubeBuffer, mGLTextureBuffer);

        // mask boost
        GLES20.glViewport(0, 0, mIntputWidth, mIntputHeight);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFbMaskBoost);
        GLES20.glClearColor(0, 0, 0, 0);
        mMaskBoostFilter.onDrawFrame(mTextureHighpass, mGLCubeBuffer, mGLTextureBuffer);

        // tone curve
        GLES20.glViewport(0, 0, mIntputWidth, mIntputHeight);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFbToneCurve);
        GLES20.glClearColor(0, 0, 0, 0);
        mToneCurveFilter.onDrawFrame(textureId, mGLCubeBuffer, mGLTextureBuffer);

        // dissolve blend
        GLES20.glViewport(0, 0, mIntputWidth, mIntputHeight);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFbBlend);
        GLES20.glClearColor(0, 0, 0, 0);
        mDissolveBlendFilter.onDrawFrame(textureId, mTextureToneCurve, mGLCubeBuffer, mGLTextureBuffer);

        // composite
        GLES20.glViewport(0, 0, mIntputWidth, mIntputHeight);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFbCompositing);
        GLES20.glClearColor(0, 0, 0, 0);
        mCompositingFilter.onDrawFrame(textureId, mTextureBlend, mTextureMaskBoost, mGLCubeBuffer, mGLTextureBuffer);

        // sharpen
        if (previousViewport[0] > 0 || previousViewport[1] > 0 || previousViewport[2] > 0 || previousViewport[3] > 0) {
            GLES20.glViewport(previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3]);
        } else {
            GLES20.glViewport(0, 0, mOutputWidth, mOutputHeight);
        }
        if (drawFboId[0] > 0) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, drawFboId[0]);
        }
        mSharpenFilter.onDrawFrame(mTextureCompositing, cubeBuffer, textureBuffer);

        return OpenGlUtils.ON_DRAWN;
    }

    private void destroyFramebuffers() {
        if (frameBufferTextures != null) {
            GLES20.glDeleteTextures(frameBufferTextures.length, frameBufferTextures, 0);
            frameBufferTextures = null;
        }
        if (frameBuffers != null) {
            GLES20.glDeleteFramebuffers(frameBuffers.length, frameBuffers, 0);
            frameBuffers = null;
        }
    }


    private void setAmount(final float amount) {
        mMixFactor = amount;
        mDissolveBlendFilter.setMix(mMixFactor);
        mSharpenFilter.setSharpness(mSharpenFactor * mMixFactor);
    }

    private void setSharpenFactor(final float sharpenFactor) {
        mSharpenFactor = sharpenFactor;
        mSharpenFilter.setSharpness(mSharpenFactor * mMixFactor);
    }
}
