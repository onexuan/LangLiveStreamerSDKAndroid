package net.lang.streamer.video;

import android.opengl.GLES20;

import net.lang.gpuimage.filter.base.gpuimage.GPUImageFilter;
import net.lang.gpuimage.utils.OpenGlUtils;
import net.lang.streamer.widget.LangMagicCameraView;

import java.nio.FloatBuffer;

/**
 * Created by lichao on 17-6-2.
 */

public class EncoderDraw extends GPUImageFilter{
    private float color = 0.3f;
    private int cnt = 0;
    public static final String NO_FILTER_VERTEX_SHADER2 = "" +
            "attribute vec4 position;\n" +
            "attribute vec4 inputTextureCoordinate;\n" +
            " \n" +
            "varying vec2 textureCoordinate;\n" +
            " \n" +
            "void main()\n" +
            "{\n" +
            "    gl_Position = position;\n" +
            "    textureCoordinate = inputTextureCoordinate.xy;\n" +
            "}";
    public static final String NO_FILTER_FRAGMENT_SHADER2 = "" +
            "varying highp vec2 textureCoordinate;\n" +
            " \n" +
            "uniform sampler2D inputImageTexture;\n" +
            "void main()\n" +
            "{\n" +
            "     gl_FragColor = vec4(1.0,0,0,0);\n" +
            "}";
    public EncoderDraw() {
        super();
    }

    public void init() {
        super.init();
    }
    public int onDrawFrame(final int textureId, final FloatBuffer cubeBuffer,
                           final FloatBuffer textureBuffer)
    {



//        GLES20.glClearColor(color, 1 - color,0.1f,0f);
//        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
//        if (cnt++ % 5 == 0) {
//            color += 0.2;
//            if (color > 1.0) {
//                color = 0.3f;
//            }
//        }
// return 0;
        //return super.onDrawFrame(textureId, cubeBuffer, textureBuffer);

//        GLES20.glUseProgram(mGLProgId);
//        runPendingOnDrawTasks();
//        if (!mIsInitialized) {
//            return OpenGlUtils.NOT_INIT;
//        }
        //GLES20.glClearColor(color, 0f,0f,0.5f);

        GLES20.glClearColor(1.0f, 0f, 0f, 0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glUseProgram(mGLProgId);
        runPendingOnDrawTasks();
        if (!mIsInitialized) {
            return OpenGlUtils.NOT_INIT;
        }

        cubeBuffer.position(0);
        GLES20.glVertexAttribPointer(mGLAttribPosition, 2, GLES20.GL_FLOAT, false, 0, cubeBuffer);
        LangMagicCameraView.glCheckError("glVertexAttribPointer");
        GLES20.glEnableVertexAttribArray(mGLAttribPosition);

        LangMagicCameraView.glCheckError("glEnableVertexAttribArray");
        textureBuffer.position(0);

        GLES20.glVertexAttribPointer(mGLAttribTextureCoordinate, 2, GLES20.GL_FLOAT, false, 0,
                textureBuffer);
        LangMagicCameraView.glCheckError("glVertexAttribPointer");

        GLES20.glEnableVertexAttribArray(mGLAttribTextureCoordinate);
        LangMagicCameraView.glCheckError("glEnableVertexAttribArray");

        if (textureId != OpenGlUtils.NO_TEXTURE) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            LangMagicCameraView.glCheckError("glActiveTexture");
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
            LangMagicCameraView.glCheckError("glBindTexture");
            //GLES20.glUniform1i(mGLUniformTexture, 0);
            LangMagicCameraView.glCheckError("glUniform1i");
        }
        onDrawArraysPre();
        LangMagicCameraView.glCheckError("onDrawArraysPre");
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        LangMagicCameraView.glCheckError("glDrawArrays");
        GLES20.glDisableVertexAttribArray(mGLAttribPosition);
        LangMagicCameraView.glCheckError("glDisableVertexAttribArray");
        GLES20.glDisableVertexAttribArray(mGLAttribTextureCoordinate);
        LangMagicCameraView.glCheckError("glDisableVertexAttribArray");
        onDrawArraysAfter();
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        LangMagicCameraView.glCheckError("glBindTexture");
        GLES20.glUseProgram(0);
        LangMagicCameraView.glCheckError("glUseProgram");
        return OpenGlUtils.ON_DRAWN;
    }
}
