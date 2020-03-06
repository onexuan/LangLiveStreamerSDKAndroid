package net.lang.gpuimage.filter.advanced;

import android.content.Context;
import android.opengl.GLES20;

import net.lang.gpuimage.R;
import net.lang.gpuimage.filter.base.gpuimage.GPUImageFilter;
import net.lang.gpuimage.utils.OpenGlUtils;

import java.nio.FloatBuffer;

public class MagicMattingBlendFilter extends GPUImageFilter {

    private int glUniformBackgroundTexture = 0;
    private int glAttribBackgroundTextureCoordinate = 0;
    private int glUniformWebpTexture = 0;
    private int glAttribWebpTextureCoordinate = 0;

    private int backgroundTextureId = OpenGlUtils.NO_TEXTURE;
    private FloatBuffer backgroundTextureBuffer = null;
    private int webpTextureId = OpenGlUtils.NO_TEXTURE;
    private FloatBuffer webpTextureBuffer = null;

    public MagicMattingBlendFilter(Context context) {
        super(OpenGlUtils.readShaderFromRawResource(R.raw.matting_vertex, context),
                OpenGlUtils.readShaderFromRawResource(R.raw.matting_fragment, context));
    }

    public void setBackgroundTextureId(int backgroundTextureId, FloatBuffer backgroundTextureBuffer) {
        this.backgroundTextureId = backgroundTextureId;
        this.backgroundTextureBuffer = backgroundTextureBuffer;
    }

    public void setWebpTextureId(int webpTextureId, FloatBuffer webpTextureBuffer) {
        this.webpTextureId = webpTextureId;
        this.webpTextureBuffer = webpTextureBuffer;
    }

    protected void onDrawArraysAfter() {
        GLES20.glDisableVertexAttribArray(glAttribBackgroundTextureCoordinate);
        GLES20.glDisableVertexAttribArray(glAttribWebpTextureCoordinate);

        if (backgroundTextureId != OpenGlUtils.NO_TEXTURE) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        }

        if (webpTextureId != OpenGlUtils.NO_TEXTURE) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        }
    }

    protected void onDrawArraysPre() {
        // may use vbo to improve the performance
        backgroundTextureBuffer.position(0);
        GLES20.glVertexAttribPointer(glAttribBackgroundTextureCoordinate, 2, GLES20.GL_FLOAT, false, 0,
                backgroundTextureBuffer);
        GLES20.glEnableVertexAttribArray(glAttribBackgroundTextureCoordinate);

        webpTextureBuffer.position(0);
        GLES20.glVertexAttribPointer(glAttribWebpTextureCoordinate, 2, GLES20.GL_FLOAT, false, 0,
                webpTextureBuffer);
        GLES20.glEnableVertexAttribArray(glAttribWebpTextureCoordinate);

        if (backgroundTextureId != OpenGlUtils.NO_TEXTURE) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, backgroundTextureId);
            GLES20.glUniform1i(glUniformBackgroundTexture, 1);
        }

        if (webpTextureId != OpenGlUtils.NO_TEXTURE) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, webpTextureId);
            GLES20.glUniform1i(glUniformWebpTexture, 2);
        }
    }

    protected void onInit(){
        super.onInit();
        glAttribBackgroundTextureCoordinate = GLES20.glGetAttribLocation(mGLProgId, "backgroundTextureCoordinate");
        glUniformBackgroundTexture = GLES20.glGetUniformLocation(mGLProgId, "backgroundTexture");
        glAttribWebpTextureCoordinate = GLES20.glGetAttribLocation(mGLProgId, "webpTextureCoordinate");
        glUniformWebpTexture = GLES20.glGetUniformLocation(mGLProgId, "webpTexture");
    }
}
