package net.lang.gpuimage.filter.custom;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.util.Log;

import com.langlive.LangAIKit.ITFLiteInterpreter;
import com.yunfan.graphicbuffer.GraphicBufferWrapper;

import net.lang.gpuimage.utils.Accelerometer;
import net.lang.gpuimage.utils.FBMHelper;

import net.lang.gpuimage.filter.base.gpuimage.GPUImageFilter;
import net.lang.gpuimage.utils.OpenGlUtils;
import net.lang.gpuimage.utils.Rotation;
import net.lang.gpuimage.utils.TextureRotationUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Locale;

public abstract class AISegmentationFilter extends GPUImageFilter {
    private static final String TAG = AISegmentationFilter.class.getSimpleName();

    private final Object mLock = new Object();

    private Context mContext;
    private ITFLiteInterpreter mTfLiteInterpreter;

    private GraphicBufferWrapper mGraphicBuffer;

    private FloatBuffer mSrcTextureBuffer;
    private FloatBuffer mMaskTextureBuffer;

    private int mMaskTexId = OpenGlUtils.NO_TEXTURE;
    private Bitmap mMaskBmp = null;
    private int[] mMaskPixels = null;

    private FBMHelper mProcessedTexture;

    private Accelerometer mAccelerometer;


    public AISegmentationFilter(Context context, ITFLiteInterpreter tfLiteInterpreter) {
        mAccelerometer = new Accelerometer(context);

        mProcessedTexture = new FBMHelper();

        mSrcTextureBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mSrcTextureBuffer.put(TextureRotationUtil.getRotation(Rotation.NORMAL, false, true))
                .position(0);

        mMaskTextureBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mMaskTextureBuffer.put(TextureRotationUtil.getRotation(Rotation.NORMAL, false, true)).position(0);

        mContext = context;
        mTfLiteInterpreter = tfLiteInterpreter;
    }

    @Override
    public void init() {
        synchronized (mLock) {
            super.init();

            int maskWidth = getTfMaskWidth();
            int maskHeight = getTfMaskHeight();
            mMaskPixels = new int[maskWidth * maskHeight];
            Log.d(TAG, String.format(Locale.getDefault(),
                    "tflite maskWidth = %d maskHeight = %d", maskWidth, maskHeight));
            initMaskTextures(maskWidth, maskHeight);

            int inputWidth = getTfInputWidth();
            int inputHeight = getTfInputHeight();
            initGraphicBuffer(inputWidth, inputHeight);

            mAccelerometer.start();
        }
    }

    @Override
    public void onInputSizeChanged(int width, int height) {
        synchronized (mLock) {
            super.onInputSizeChanged(width, height);

            mProcessedTexture.initialize(width, height);
        }
    }

    @Override
    public final int onDrawFrame(final int textureId) {
        synchronized (mLock) {
            if (skipFrame()) {
                return textureId;
            }

            mSrcTextureBuffer.position(0);
            mMaskTextureBuffer.position(0);
            switch (getCurrentOrientation()) {
                case 0:
                    updateTextureBuffer(mSrcTextureBuffer, Rotation.NORMAL, false, true);
                    updateTextureBuffer(mMaskTextureBuffer, Rotation.NORMAL, false, true);
                    break;
                case 1:
                    updateTextureBuffer(mSrcTextureBuffer, Rotation.ROTATION_270, false, true);
                    updateTextureBuffer(mMaskTextureBuffer, Rotation.ROTATION_90, false, true);
                    break;
                case 2:
                    updateTextureBuffer(mSrcTextureBuffer, Rotation.ROTATION_180, false, true);
                    updateTextureBuffer(mMaskTextureBuffer, Rotation.ROTATION_180, false, true);
                    break;
                case 3:
                    updateTextureBuffer(mSrcTextureBuffer, Rotation.ROTATION_90, false, true);
                    updateTextureBuffer(mMaskTextureBuffer, Rotation.ROTATION_270, false, true);
                    break;
                default:
                    Log.e(TAG, "getCurrentOrientation return unknown orientation");
                    return textureId;
            }
            drawReadFrame(textureId);

            byte[] pixelClasses = readFrameAndDetect();
            if (pixelClasses == null) {
                Log.e(TAG, "run detection error");
                return textureId;
            }
            onHandleSegmentation(pixelClasses, mMaskPixels, getTfMaskWidth(), getTfMaskHeight());

            updateMaskTextures(mMaskPixels, getTfMaskWidth(), getTfMaskHeight());

            // bind internal final result framebuffer and perform draw on that.
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mProcessedTexture.fbId());

            onApplyOverlayToTexture(textureId, mMaskTexId, mMaskTextureBuffer);

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

            return mProcessedTexture.textureID();
        }
    }

    @Override
    protected void onDestroy() {
        synchronized (mLock) {
            releaseGraphicBuffer();
            releaseMaskTextures();

            mTfLiteInterpreter.close();

            mAccelerometer.stop();

            mProcessedTexture.deinitialize();

            mContext = null;
        }
    }

    /**
     * callback of draw read frames, sub-class of segmentation can override the bas method using different
     * filters, for example, hair detection should use grayscale filter instead of RGBA filter.
     * @param textureId: original source image texture id.
     * @param cubeBuffer: draw cube vertex buffer of read frame.
     * @param textureBuffer: draw texture buffer  of read frame.
     */
    protected void onDrawReadFrame(final int textureId, final FloatBuffer cubeBuffer,
                                   final FloatBuffer textureBuffer) {
        super.onDrawFrame(textureId, cubeBuffer, textureBuffer);
    }

    protected final int getTfInputWidth() {
        return mTfLiteInterpreter.getInputWidth();
    }

    protected final int getTfInputHeight() {
        return mTfLiteInterpreter.getInputHeight();
    }

    protected final int getTfMaskWidth() {
        return mTfLiteInterpreter.getMaskWidth();
    }

    protected final int getTfMaskHeight() {
        return mTfLiteInterpreter.getMaskHeight();
    }

    protected final int getTfThreshold() {
        return mTfLiteInterpreter.getThreshold();
    }

    /**
     * obtain the draw frame rgba data of current read buffer
     * @return rgba image frame of current read buffer
     */
    protected final byte[] getReadFrameData() {
        if (mGraphicBuffer != null) {
            return mGraphicBuffer.getRgbaVideoData();
        }
        return null;
    }

    protected abstract boolean skipFrame();

    /**
     * callback of image segmentation, user must implement this method, user can use the result of image
     * and perform customized image context loading, eg mask texture loading
     * @param resultPixels: byte array which tf-lite returns
     * @param maskPixels: user should fill this integer array according to resultPixels
     * @param maskWidth: mask width of maskPixels integer array
     * @param maskHeight: mask height of maskPixels integer array
     */
    protected abstract void onHandleSegmentation(byte[] resultPixels, int[] maskPixels, int maskWidth, int maskHeight);

    /**
     * callback of image overlay, the final stage of image processing, user must implement this method, user should
     * blend the mask texture with source texture, and produce the mixed final image, the specified blend
     * processing routine should be considered by the sub-class of filters.
     * @param srcTexId: original source image texture id.
     * @param maskTexId: the mask image texture id
     * @param textureBuffer: the draw texture buffer of mask image
     */
    protected abstract void onApplyOverlayToTexture(int srcTexId, int maskTexId, FloatBuffer textureBuffer);

    /**
     * draw textureId to GraphicBuffer for reading pixels used in tensor-flow lite
     * @param textureId source texture id for drawing
     */
    private void drawReadFrame(int textureId) {
        int[] previousViewport = new int[4];
        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, previousViewport, 0);
        GLES20.glViewport(0, 0, mTfLiteInterpreter.getInputWidth(), mTfLiteInterpreter.getInputHeight());

        if (mGraphicBuffer != null) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mGraphicBuffer.framebufferId());
            OpenGlUtils.checkGlError("glBindFramebuffer");

            onDrawReadFrame(textureId, mGLCubeBuffer, mSrcTextureBuffer);
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3]);
    }

    /**
     * read RGBA frame data from GraphicBuffer and run image detection
     * @return byte array containing tensor-flow-lite detection result
     */
    private byte[] readFrameAndDetect() {
        long bufAddr = mGraphicBuffer.lock();
        int bufStride = mGraphicBuffer.stride();

        byte[] pixelClasses = mTfLiteInterpreter.run(bufAddr, bufStride);

        mGraphicBuffer.unlock();

        return pixelClasses;
    }

    /**
     * initialize a mask texture with given maskWidth and maskHeight, first we create a bitmap and
     * associate it with a new created texture
     * @param maskWidth: mask width of tflite
     * @param maskHeight: mask height of tflite
     */
    private void initMaskTextures(int maskWidth, int maskHeight) {
        if (mMaskBmp == null) {
            mMaskBmp = Bitmap.createBitmap(maskWidth, maskHeight, Bitmap.Config.ARGB_8888);
        }

        mMaskTexId = OpenGlUtils.loadTexture(mMaskBmp, mMaskTexId, false);
    }

    /**
     * load mask pixels into bitmap and convert to an opengl-es texture
     * @param maskPixels: maskPixels user previously has filled
     * @param maskWidth:  mask width of maskPixels integer array
     * @param maskHeight: mask height of maskPixels integer array
     */
    private void updateMaskTextures(int[] maskPixels, int maskWidth, int maskHeight) {
        mMaskBmp.setPixels(maskPixels, 0, maskHeight, 0, 0, maskWidth, maskHeight);
        mMaskTexId = OpenGlUtils.loadTexture(mMaskBmp, mMaskTexId, false);
    }

    /**
     * release the bitmap and mask texture
     */
    private void releaseMaskTextures() {
        if (mMaskBmp != null && !mMaskBmp.isRecycled()) {
            mMaskBmp.recycle();
            mMaskBmp = null;
        }

        if (mMaskTexId != OpenGlUtils.NO_TEXTURE) {
            int[] textures = new int[1];
            textures[0] = mMaskTexId;
            GLES20.glDeleteTextures(1, textures, 0);
            mMaskTexId = OpenGlUtils.NO_TEXTURE;
        }
    }

    private void initGraphicBuffer(int width, int height) {
        mGraphicBuffer = GraphicBufferWrapper.createInstance(mContext, width, height, android.graphics.PixelFormat.RGBA_8888);
        if (mGraphicBuffer != null) {
            int[] graphicBufferFbID = new int[1];
            GLES20.glGenFramebuffers(1, graphicBufferFbID, 0);
            int graphicBufferTexID = mGraphicBuffer.createTexture(graphicBufferFbID[0]);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                    GLES20.GL_TEXTURE_2D, graphicBufferTexID, 0);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        }
    }

    private void releaseGraphicBuffer() {
        if (mGraphicBuffer != null) {
            mGraphicBuffer.destroy();
            mGraphicBuffer = null;
        }
    }

    private int getCurrentOrientation() {
        int dir = Accelerometer.getDirection();
        int orientation = dir - 1;
        if (orientation < 0) {
            orientation = dir ^ 3;
        }
        return orientation;
    }

    private void updateTextureBuffer(FloatBuffer textureBuffer, Rotation rotation,
                                     boolean flipHorizontal, boolean flipVertical) {
        textureBuffer.put(TextureRotationUtil.getRotation(rotation, flipHorizontal, flipVertical)).position(0);
    }
}
