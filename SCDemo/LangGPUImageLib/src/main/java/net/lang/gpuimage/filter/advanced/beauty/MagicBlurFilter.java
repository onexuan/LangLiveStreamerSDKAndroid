package net.lang.gpuimage.filter.advanced.beauty;

import android.content.Context;
import android.opengl.GLES20;

import net.lang.gpuimage.R;
import net.lang.gpuimage.filter.base.gpuimage.GPUImageFilter;
import net.lang.gpuimage.utils.OpenGlUtils;

public class MagicBlurFilter extends GPUImageFilter {

    private boolean horizontal;
    private float blurSize;

    public MagicBlurFilter(boolean horizontal, final float blurSize, Context context) {
        super(OpenGlUtils.readShaderFromRawResource(R.raw.gaussian_blur_optimized_vert, context),
                OpenGlUtils.readShaderFromRawResource(R.raw.gaussian_blur_optimized_frag, context));
        this.horizontal = horizontal;
        this.blurSize = blurSize;
    }

    @Override
    public void onInitialized() {
        super.onInitialized();
        setBlurSize(blurSize);
    }

    @Override
    public void onInputSizeChanged(final int width, final int height) {
        super.onInputSizeChanged(width, height);
        initTexelOffsets();
    }

    /**
     * A multiplier for the blur size, ranging from 0.0 on up, with a default of 1.0
     *
     * @param blurSize from 0.0 on up, default 1.0
     */
    public void setBlurSize(float blurSize) {
        this.blurSize = blurSize;
        runOnDraw(new Runnable() {
            @Override
            public void run() {
                initTexelOffsets();
            }
        });
    }

    private void initTexelOffsets() {
        if (horizontal) {
            float ratio = getHorizontalTexelOffsetRatio();
            int texelWidthOffsetLocation = GLES20.glGetUniformLocation(getProgram(), "texelWidthOffset");
            int texelHeightOffsetLocation = GLES20.glGetUniformLocation(getProgram(), "texelHeightOffset");
            setFloat(texelWidthOffsetLocation, ratio / getInputWidth());
            setFloat(texelHeightOffsetLocation, 0);
        } else {
            float ratio = getVerticalTexelOffsetRatio();
            int texelWidthOffsetLocation = GLES20.glGetUniformLocation(getProgram(), "texelWidthOffset");
            int texelHeightOffsetLocation = GLES20.glGetUniformLocation(getProgram(), "texelHeightOffset");
            setFloat(texelWidthOffsetLocation, 0);
            setFloat(texelHeightOffsetLocation, ratio / getInputHeight());
        }
    }

    private float getVerticalTexelOffsetRatio() {
        return blurSize;
    }

    private float getHorizontalTexelOffsetRatio() {
        return blurSize;
    }
}
