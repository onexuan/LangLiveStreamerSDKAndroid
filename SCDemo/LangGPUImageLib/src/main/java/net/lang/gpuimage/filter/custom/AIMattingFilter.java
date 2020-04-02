package net.lang.gpuimage.filter.custom;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.langlive.LangAIKit.ITFLiteInterpreter;
import com.langlive.LangAIKit.TFLiteInterpreterPortrait;

import net.lang.animation.IAnimationDecoder;
import net.lang.animation.IDecodeActionListener;
import net.lang.animation.PNGSequenceDecoder;
import net.lang.animation.WEBPAnimationDecoder;
import net.lang.gpuimage.filter.advanced.MagicBlurFilter;
import net.lang.gpuimage.filter.advanced.MagicMattingBlendFilter;
import net.lang.gpuimage.utils.OpenGlUtils;
import net.lang.gpuimage.utils.FBMHelper;

import java.nio.FloatBuffer;


public final class AIMattingFilter extends AISegmentationFilter {
    private static final String TAG = AIMattingFilter.class.getSimpleName();

    private String mAnimationPath;

    private IAnimationStatusListener mAnimationCallback;

    private IAnimationDecoder mAnimationDecoder;
    private int mTextureIndex = 0;

    private int mWebpTexId = OpenGlUtils.NO_TEXTURE;

    private MagicBlurFilter mMagicHorizontalBlurFilter;
    private MagicBlurFilter mMagicVerticalBlurFilter;
    private MagicMattingBlendFilter mMattingBlendFilter;

    private FBMHelper mBlurTextureH;
    private FBMHelper mBlurTextureV;

    private Handler mHandler;
    private HandlerThread mHandlerThread;

    private int mFramesToInsert = 0;
    private int mPrevFramesToInsert = 0;
    private int mRemaining = -1;

    private long mPrevFps = 0L;
    private float mFps = 0.f;

    public static AIMattingFilter createInstance(Context context) {
        ITFLiteInterpreter tfLiteInterpreter = new TFLiteInterpreterPortrait(context);
        return new AIMattingFilter(context, tfLiteInterpreter);
    }

    private AIMattingFilter(Context context, ITFLiteInterpreter tfLiteInterpreter) {
        super(context, tfLiteInterpreter);

        mMagicHorizontalBlurFilter = new MagicBlurFilter(context, true, 4.f);
        mMagicVerticalBlurFilter = new MagicBlurFilter(context, false, 4.f);
        mMattingBlendFilter = new MagicMattingBlendFilter(context);

        mBlurTextureH = new FBMHelper();
        mBlurTextureV = new FBMHelper();

        mAnimationDecoder = null;
    }

    public void setAnimationCallback(IAnimationStatusListener listener) {
        mAnimationCallback = listener;
    }

    public void setAnimationData(final String inputpath) {
        synchronized (this) {
            releaseAnimation();
            loadAnimation(inputpath);
        }
    }

    @Override
    public void init() {
        super.init();
        initBackgroundThread();
        mMagicHorizontalBlurFilter.init();
        mMagicVerticalBlurFilter.init();
        mMattingBlendFilter.init();
    }

    @Override
    public void onInputSizeChanged(int width, int height) {
        super.onInputSizeChanged(width, height);

        mBlurTextureH.initialize(width, height);
        mBlurTextureV.initialize(width, height);

        mMagicHorizontalBlurFilter.onInputSizeChanged(width, height);
        mMagicVerticalBlurFilter.onInputSizeChanged(width, height);
        mMattingBlendFilter.onInputSizeChanged(width, height);
    }

    @Override
    public int onDrawFrame(final int textureId, final FloatBuffer cubeBuffer, final FloatBuffer textureBuffer) {
        return OpenGlUtils.NOT_INIT;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mBlurTextureH.deinitialize();
        mBlurTextureV.deinitialize();
        mMagicHorizontalBlurFilter.destroy();
        mMagicVerticalBlurFilter.destroy();
        mMattingBlendFilter.destroy();

        releaseAnimation();

        releaseBackgroundThread();
    }

    @Override
    protected boolean skipFrame() {
        synchronized (this) {
            return !animationResourceReady();
        }
    }

    @Override
    protected void onHandleSegmentation(byte[] resultPixels, int[] maskPixels, int maskWidth, int maskHeight) {

        byte[] rgbaData = getReadFrameData();

        // load prediction from tflite as alpha
        for (int i = 0; i < maskWidth; i++) {
            for (int j = 0; j < maskHeight; j++) {
                int currentIndex = j * maskHeight + i;
                if (resultPixels[currentIndex] > 0) {
                    maskPixels[currentIndex] = android.graphics.Color.argb(
                            rgbaData[currentIndex * 4 + 3],
                            rgbaData[currentIndex * 4 + 0],
                            rgbaData[currentIndex * 4 + 1],
                            rgbaData[currentIndex * 4 + 2]);
                } else {
                    maskPixels[currentIndex] = android.graphics.Color.TRANSPARENT;
                }
            }
        }
    }

    @Override
    protected void onApplyOverlayToTexture(int srcTexId, int maskTexId, FloatBuffer maskTextureBuffer) {
        synchronized (this) {
            onApplyOverlayBefore(maskTexId);
            loadAnimationFrame(maskTextureBuffer);
            mMattingBlendFilter.setBackgroundTextureId(mBlurTextureV.textureID(), maskTextureBuffer);
            mMattingBlendFilter.onDrawFrame(srcTexId, mGLCubeBuffer, mGLTextureBuffer);
            onApplyOverlayAfter();
        }
    }

    private void loadAnimationFrame(FloatBuffer webpTextureBuffer) {
        if (mPrevFps == 0L) {
            mPrevFps = System.currentTimeMillis();
        } else {
            long currentTsMs = System.currentTimeMillis();
            long time = currentTsMs - mPrevFps;
            mFps = 1000f / time;
            mPrevFps = currentTsMs;
        }

        mFramesToInsert = (int)((mAnimationDecoder.getTotalDuration() / 1000f * mFps) / mAnimationDecoder.getTotalFrameCount());
        if (mFramesToInsert != mPrevFramesToInsert || mRemaining < 0)
            mRemaining = mFramesToInsert;
        mPrevFramesToInsert = mFramesToInsert;

        int index = mTextureIndex % mAnimationDecoder.getTotalFrameCount();
        if (mRemaining > 1) {
            --mRemaining;
        } else {
            mRemaining = mFramesToInsert;
            mTextureIndex++;
        }

        Bitmap image = mAnimationDecoder.getFrameImage(index);
        mWebpTexId = OpenGlUtils.loadTexture(image, mWebpTexId, false);
        mMattingBlendFilter.setWebpTextureId(mWebpTexId, webpTextureBuffer);
    }

    private void onApplyOverlayBefore(final int maskTexId) {
        int[] previousFb = new int[1];
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, previousFb, 0);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mBlurTextureH.fbId());
        mMagicHorizontalBlurFilter.onDrawFrame(maskTexId, mGLCubeBuffer, mGLTextureBuffer);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mBlurTextureV.fbId());
        mMagicVerticalBlurFilter.onDrawFrame(mBlurTextureH.textureID(), mGLCubeBuffer, mGLTextureBuffer);

        if (previousFb[0] > 0) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, previousFb[0]);
        }
    }

    private void onApplyOverlayAfter() {
        int animationIndex = mTextureIndex % mAnimationDecoder.getTotalFrameCount();
        if (0 == animationIndex) {
            if (mAnimationCallback != null) {
                mAnimationCallback.onAnimationPlayFinish(mAnimationPath);
            }
        } else {
            if (mAnimationCallback != null) {
                mAnimationCallback.onAnimationPlaying(mAnimationPath, animationIndex);
            }
        }
    }

    private void initBackgroundThread() {
        mHandlerThread = new HandlerThread("lang-tflite-front");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
    }

    private synchronized void runInBackground(final Runnable r) {
        if (mHandler != null) {
            mHandler.post(r);
        }
    }

    private void releaseBackgroundThread() {
        mHandlerThread.quit();
        try {
            mHandlerThread.join();
            mHandlerThread = null;
            mHandler = null;
        } catch (InterruptedException ie) {
            Log.e(TAG, ie.toString());
        }
    }

    private void loadAnimation(final String inputpath) {
        if (inputpath.contains(".webp")) {
            mAnimationDecoder = new WEBPAnimationDecoder(inputpath, new IDecodeActionListener() {
                @Override
                public void onParseProgress(IAnimationDecoder decoder, int currentFrame) {
                    float progress = (float)currentFrame / (float)decoder.getTotalFrameCount();
                    if (mAnimationCallback != null) {
                        mAnimationCallback.onAnimationDecoding(inputpath, progress);
                    }
                }
                @Override
                public void onParseComplete(IAnimationDecoder decoder, boolean parseStatus, int frameIndex) {
                    if (mAnimationCallback != null) {
                        if (parseStatus) {
                            mAnimationCallback.onAnimationDecodeSuccess(inputpath);
                        } else {
                            mAnimationCallback.onAnimationDecodeError(inputpath);
                        }
                    }
                }
            });
            ((WEBPAnimationDecoder)mAnimationDecoder).setSize(getTfMaskWidth(), getTfMaskHeight());
        } else if (inputpath.contains(".zip")) {
            mAnimationDecoder = new PNGSequenceDecoder(inputpath, new IDecodeActionListener() {
                @Override
                public void onParseProgress(IAnimationDecoder decoder, int currentFrame) {

                }
                @Override
                public void onParseComplete(IAnimationDecoder decoder, boolean parseStatus, int frameIndex) {
                    if (mAnimationCallback != null) {
                        if (parseStatus) {
                            mAnimationCallback.onAnimationDecodeSuccess(inputpath);
                        } else {
                            mAnimationCallback.onAnimationDecodeError(inputpath);
                        }
                    }
                }
            });
            ((PNGSequenceDecoder)mAnimationDecoder).setSize(getTfMaskWidth(), getTfMaskHeight());
        } else {
            throw new RuntimeException("Unsupported type");
        }
        mAnimationPath = inputpath;
        runInBackground(mAnimationDecoder);
    }

    private boolean animationResourceReady() {
        return mAnimationDecoder != null && mAnimationDecoder.parseOk();
    }

    private void releaseAnimation() {
        if (mAnimationDecoder != null) {
            for (int i = 0; i < mAnimationDecoder.getTotalFrameCount(); i++) {
                if (!mAnimationDecoder.getFrameImage(i).isRecycled()) {
                    mAnimationDecoder.getFrameImage(i).recycle();
                }
            }
            mAnimationDecoder = null;
        }
    }
}