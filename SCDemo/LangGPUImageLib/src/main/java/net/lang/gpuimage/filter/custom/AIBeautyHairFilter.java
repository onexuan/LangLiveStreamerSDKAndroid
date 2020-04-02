package net.lang.gpuimage.filter.custom;

import android.content.Context;
import android.opengl.GLES20;
import android.os.SystemClock;
import android.util.Log;

import com.langlive.LangAIKit.ITFLiteInterpreter;
import com.langlive.LangAIKit.TFLiteInterprerHair;

import net.lang.gpuimage.filter.advanced.MagicGrayScaleFilter;
import net.lang.gpuimage.filter.advanced.MagicSoftlightBlendFilter;
import net.lang.gpuimage.utils.OpenGlUtils;

import java.nio.FloatBuffer;

public final class AIBeautyHairFilter extends AISegmentationFilter {
    private static final String TAG = AIBeautyHairFilter.class.getSimpleName();

    private MagicGrayScaleFilter mGrayScaleFilter;
    private MagicSoftlightBlendFilter mSoftlightBlendFilter;

    private int mFgMaskCeiling;
    private int mFgMaskHeight;

    private int mStartColor;
    private int mEndColor;

    private int mGradientAlpha = 0;
    private int mRedGradientStart = 0;
    private int mRedGradientEnd = 0;
    private int mGreenGradientStart = 0;
    private int mGreenGradientEnd = 0;
    private int mBlueGradientStart = 0;
    private int mBlueGradientEnd = 0;

    public static AIBeautyHairFilter createInstance(Context context) {
        ITFLiteInterpreter tfLiteInterpreter = new TFLiteInterprerHair(context);
        return new AIBeautyHairFilter(context, tfLiteInterpreter, 0, 0, 0.5f);
    }

    private AIBeautyHairFilter(Context context, ITFLiteInterpreter tfLiteInterpreter,
                               int color1, int color2, float saturation) {
        super(context, tfLiteInterpreter);
        mStartColor = color1;
        mEndColor = color2;
        mGrayScaleFilter = new MagicGrayScaleFilter();
        mSoftlightBlendFilter = new MagicSoftlightBlendFilter(context, true, saturation, 4.f);
    }

    public void setBeautyHairConfig(int color1, int color2, float saturation) {
        mStartColor = color1;
        mEndColor = color2;
        mSoftlightBlendFilter.setSaturation(saturation);
        initGradientColors();
    }

    @Override
    public void init() {
        super.init();
        mGrayScaleFilter.init();
        mSoftlightBlendFilter.init();
        initGradientColors();
    }

    @Override
    public void onInputSizeChanged(int width, int height) {
        super.onInputSizeChanged(width, height);
        mGrayScaleFilter.onInputSizeChanged(width, height);
        mSoftlightBlendFilter.onInputSizeChanged(width, height);
    }

    @Override
    public int onDrawFrame(final int textureId, final FloatBuffer cubeBuffer, final FloatBuffer textureBuffer) {
        return OpenGlUtils.NOT_INIT;
    }

    @Override
    protected boolean skipFrame() {
        return false;
    }

    @Override
    protected void onDrawReadFrame(final int textureId, final FloatBuffer cubeBuffer,
                                   final FloatBuffer textureBuffer) {
        mGrayScaleFilter.onDrawFrame(textureId, cubeBuffer, textureBuffer);
    }

    @Override
    protected void onHandleSegmentation(byte[] resultPixels, int[] maskPixels, int maskWidth, int maskHeight) {

        int threshold = getTfThreshold();

        if (mStartColor == mEndColor) {
            setColors(maskPixels, maskWidth, maskHeight, resultPixels, maskWidth, maskHeight,
                    threshold, mStartColor);
        } else {
            setGradientColors(maskPixels, maskWidth, maskHeight, resultPixels, threshold);
        }
    }

    @Override
    protected void onApplyOverlayToTexture(int srcTexId, int maskTexId, FloatBuffer maskTextureBuffer) {

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        mSoftlightBlendFilter.setMaskTextureId(maskTexId, maskTextureBuffer);
        mSoftlightBlendFilter.onDrawFrame(srcTexId, mGLCubeBuffer, mGLTextureBuffer);

        GLES20.glDisable(GLES20.GL_BLEND);
    }

    private void setColors(int[] maskPixels, int maskBmpWidth, int maskBmpHeight, byte[] resultPixels,
                           int maskWidth, int maskHeight, int threshold, int color) {
        long startTime = SystemClock.uptimeMillis();
        boolean isMatting = false;
        for (int i = 0; i < maskWidth; i++) {
            for (int j = 0; j < maskHeight; j++) {
                int classNo = resultPixels[j * maskWidth + i] & 0xFF; // very tricky part
                if (!isMatting) {
                    if (classNo >= threshold) {
                        maskPixels[j * maskBmpWidth + i] = color;
                    } else {
                        maskPixels[j * maskBmpHeight + i] = android.graphics.Color.TRANSPARENT;
                    }
                } else {
                    if (classNo < threshold) {
                        maskPixels[j * maskBmpWidth + i] = color;
                    } else {
                        maskPixels[j * maskBmpHeight + i] = android.graphics.Color.TRANSPARENT;
                    }
                }
            }
        }

        long endTime = SystemClock.uptimeMillis();
        long inferenceTime = endTime - startTime;

        Log.d(TAG, "setColors time=" + inferenceTime);
    }

    private void setGradientColors(int[] maskPixels, int maskBmpWidth, int maskBmpHeight,
                                   byte[] resultPixels, int threshold) {
        long startTime = SystemClock.uptimeMillis();

        scanGradientFg(resultPixels, maskBmpWidth, maskBmpHeight, threshold);

        int classNo;
        int i = 0;

        // Paint current mask with mapping of gradient color and mask height
        float redGradient = (float)(mRedGradientEnd - mRedGradientStart) / (float)mFgMaskHeight;
        float greenGradient = (float)(mGreenGradientEnd - mGreenGradientStart) / (float)mFgMaskHeight;
        float blueGradient = (float)(mBlueGradientEnd - mBlueGradientStart) / (float)mFgMaskHeight;
        float gradientSpeed = -(float)mFgMaskHeight / maskBmpHeight + 2.f;
        while (i < maskBmpWidth) {
            int j = 0;
            while (j < maskBmpHeight) {
                classNo = resultPixels[j * maskBmpWidth + i] & 0xFF;
                if (classNo >= threshold) {
                    int red = calculateGradients(
                            mRedGradientStart,
                            mRedGradientEnd,
                            j,
                            mFgMaskCeiling,
                            redGradient,
                            gradientSpeed);
                    int green = calculateGradients(
                            mGreenGradientStart,
                            mGreenGradientEnd,
                            j,
                            mFgMaskCeiling,
                            greenGradient,
                            gradientSpeed);
                    int blue = calculateGradients(
                            mBlueGradientStart,
                            mBlueGradientEnd,
                            j,
                            mFgMaskCeiling,
                            blueGradient,
                            gradientSpeed);
                    maskPixels[j * maskBmpHeight + i] = android.graphics.Color.argb(mGradientAlpha, red, green, blue);
                } else {
                    maskPixels[j * maskBmpHeight + i] = android.graphics.Color.TRANSPARENT;
                }
                j++;
            }
            i++;
        }

        long endTime = SystemClock.uptimeMillis();
        long inferenceTime = endTime - startTime;

        Log.d(TAG, "setGradientColors time=" + inferenceTime);
    }

    private void scanGradientFg(byte[] resultPixels, int maskBmpWidth, int maskBmpHeight,
                             int threshold) {
        mFgMaskCeiling = maskBmpHeight;
        int i = 0;
        int maskFloor = 0;
        while (i < maskBmpWidth) {
            int j = 0;
            while (j < maskBmpHeight) {
                int classNo = resultPixels[j * maskBmpWidth + i] & 0xFF; // very tricky part
                if (classNo >= threshold) {
                    if (j < mFgMaskCeiling) {
                        mFgMaskCeiling = j;
                    }
                    if (j > maskFloor) {
                        maskFloor = j;
                        j = (j < maskBmpHeight - 1) ? (j + 1) : j;
                    }
                }
                j++;
            }
            i++;
        }
        // Calculate moving mean of mask height
        mFgMaskHeight = (maskFloor - mFgMaskCeiling <= 0) ? 1 : (maskFloor - mFgMaskCeiling);
    }

    private int calculateGradients(int start, int end, int index, int ceiling,
                                   float gradient, float gradient_speed) {
        int gradientColor = (int)(start + (float)(index - ceiling) * gradient * gradient_speed);
        if (gradient < 0) {
            if (gradientColor < end)
                gradientColor = end;
        } else {
            if (gradientColor > end)
                gradientColor = end;
        }

        return gradientColor;
    }

    private void initGradientColors() {
        if (mStartColor != mEndColor) {
            mGradientAlpha = android.graphics.Color.alpha(mStartColor);

            mRedGradientStart = android.graphics.Color.red(mStartColor);
            mRedGradientEnd = android.graphics.Color.red(mEndColor);

            mGreenGradientStart = android.graphics.Color.green(mStartColor);
            mGreenGradientEnd = android.graphics.Color.green(mEndColor);

            mBlueGradientStart = android.graphics.Color.blue(mStartColor);
            mBlueGradientEnd = android.graphics.Color.blue(mEndColor);
        }
    }
}
