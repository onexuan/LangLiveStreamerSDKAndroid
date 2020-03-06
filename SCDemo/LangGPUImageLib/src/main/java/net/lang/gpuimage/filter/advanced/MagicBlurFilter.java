package net.lang.gpuimage.filter.advanced;

import android.content.Context;
import android.opengl.GLES20;

import net.lang.gpuimage.R;
import net.lang.gpuimage.filter.base.gpuimage.GPUImageFilter;
import net.lang.gpuimage.utils.OpenGlUtils;

public class MagicBlurFilter extends GPUImageFilter {

    private boolean horizontal;
    private float blurSize;
    private int texelWidthOffsetLocation;
    private int texelHeightOffsetLocation;

    public MagicBlurFilter(Context context,
                           boolean horizontal,
                           float blurSize) {
        super(OpenGlUtils.readShaderFromRawResource(R.raw.blur_vertex, context),
                OpenGlUtils.readShaderFromRawResource(R.raw.blur_fragment, context));
        this.horizontal = horizontal;
        this.blurSize = blurSize;
    }

    public void setBlurSize(float blurSize) {
        this.blurSize = blurSize;
        initTexelOffsets(mIntputWidth, mIntputHeight);
    }

    protected void onInit(){
        super.onInit();
        texelWidthOffsetLocation = GLES20.glGetUniformLocation(mGLProgId, "texelWidthOffset");
        texelHeightOffsetLocation = GLES20.glGetUniformLocation(mGLProgId, "texelHeightOffset");
    }

    public void onInputSizeChanged(int width, int height){
        super.onInputSizeChanged(width, height);
        setBlurSize(blurSize);
    }

    private void initTexelOffsets(int w, int h) {
        if (horizontal) {
            setFloat(texelWidthOffsetLocation, blurSize / w);
            setFloat(texelHeightOffsetLocation, 0);
        } else {
            setFloat(texelWidthOffsetLocation, 0);
            setFloat(texelHeightOffsetLocation, blurSize / h);
        }
    }
}
