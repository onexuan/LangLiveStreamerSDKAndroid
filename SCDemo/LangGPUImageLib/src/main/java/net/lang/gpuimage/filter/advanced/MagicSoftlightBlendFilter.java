package net.lang.gpuimage.filter.advanced;

import android.content.Context;
import android.opengl.GLES20;

import net.lang.gpuimage.R;
import net.lang.gpuimage.filter.base.gpuimage.GPUImageFilter;
import net.lang.gpuimage.utils.OpenGlUtils;

import java.nio.FloatBuffer;

public class MagicSoftlightBlendFilter extends GPUImageFilter {

    private int glUniformMaskTexture = 0;
    private int saturationLocation = 0;
    private int glAttribMaskTextureCoordinate = 0;
    private int maskTextureId = OpenGlUtils.NO_TEXTURE;
    private FloatBuffer maskTextureBuffer = null;

    private float saturation;
    private boolean horizontal;
    private float blurSize;
    private int texelWidthOffsetLocation;
    private int texelHeightOffsetLocation;

    public MagicSoftlightBlendFilter(Context context,
                                     boolean horizontal,
                                     float saturation,
                                     float blurSize) {
        super(OpenGlUtils.readShaderFromRawResource(R.raw.softlight_vertex, context),
                OpenGlUtils.readShaderFromRawResource(R.raw.softlight_fragment, context));
        this.saturation = saturation;
        this.horizontal = horizontal;
        this.blurSize = blurSize;
    }

    public void setMaskTextureId(int maskTextureId, FloatBuffer maskTextureBuffer) {
        this.maskTextureId = maskTextureId;
        this.maskTextureBuffer = maskTextureBuffer;
    }

    public void setSaturation(float saturation) {
        setFloat(saturationLocation, saturation);
    }

    public void setBlurSize(float blurSize) {
        this.blurSize = blurSize;
        initTexelOffsets(mIntputWidth, mIntputHeight);
    }

    protected void onDrawArraysAfter() {
        GLES20.glDisableVertexAttribArray(glAttribMaskTextureCoordinate);

        if (maskTextureId != OpenGlUtils.NO_TEXTURE) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        }
    }

    protected void onDrawArraysPre() {
        // may use vbo to improve the performance
        maskTextureBuffer.position(0);
        GLES20.glVertexAttribPointer(glAttribMaskTextureCoordinate, 2, GLES20.GL_FLOAT, false, 0,
                maskTextureBuffer);
        GLES20.glEnableVertexAttribArray(glAttribMaskTextureCoordinate);

        if (maskTextureId != OpenGlUtils.NO_TEXTURE) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, maskTextureId);
            GLES20.glUniform1i(glUniformMaskTexture, 1);
        }
    }

    protected void onInit(){
        super.onInit();
        glAttribMaskTextureCoordinate = GLES20.glGetAttribLocation(mGLProgId, "maskTextureCoordinate");
        glUniformMaskTexture = GLES20.glGetUniformLocation(getProgram(), "maskTexture");
        saturationLocation = GLES20.glGetUniformLocation(getProgram(), "saturation");
        texelWidthOffsetLocation = GLES20.glGetUniformLocation(getProgram(), "texelWidthOffset");
        texelHeightOffsetLocation = GLES20.glGetUniformLocation(getProgram(), "texelHeightOffset");
    }

    public void onInputSizeChanged(int width, int height){
        super.onInputSizeChanged(width, height);
        setFloat(saturationLocation, saturation);
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
