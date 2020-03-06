package net.lang.streamer.video.gles;

import android.opengl.GLES20;
import android.util.Log;

import java.nio.FloatBuffer;
/**
 * Created by lang on 2017/9/20.
 */

public class Yuv2RgbProgram {
    private static final String TAG = GlUtil.TAG;

    private static final String VERTEX_SHADER =
        "attribute vec4 aPosition;\n" +
        "attribute vec2 aTextureCoord;\n" +
        "varying vec2 vvTextureCoord;\n" +
        "void main() {\n" +
        "    gl_Position = aPosition;\n" +
        "    vvTextureCoord = aTextureCoord;\n" +
        "}\n";

    private static final String FRAGMENT_SHADER =
        "precision mediump float;\n" +
        "varying highp vec2 vvTextureCoord;\n" +
        "uniform sampler2D Ytex;\n" +
        "uniform sampler2D Utex;\n" +
        "uniform sampler2D Vtex;\n" +
        "uniform mediump mat3 colorConversionMatrix;\n" +
        "void main() {\n" +
        "    highp vec3 yuv;\n" +
        "    highp vec3 rgb;\n" +
        "    yuv.x = texture2D(Ytex, vvTextureCoord).r - 0.062;\n" +
        "    yuv.y = texture2D(Utex, vvTextureCoord).r - 0.5;\n" +
        "    yuv.z = texture2D(Vtex, vvTextureCoord).r - 0.5;\n" +
        "    rgb = colorConversionMatrix * yuv;\n" +
        "    gl_FragColor = vec4(rgb, 1);\n" +
        "}\n";

    private static final float[] kColorConversion601FullRange = {
        1.0f, 1.0f, 1.0f,
        0.0f, -0.343f, 1.765f,
        1.4f, -0.711f, 0.0f
    };

    // Handles to the GL program and various components of it.
    private int mProgramHandle = -1;
    private int maPositionLoc = -1;
    private int maTextureCoordLoc = -1;
    private int muYtexLoc = -1;
    private int muUtexLoc = -1;
    private int muVtexLoc = -1;
    private int mucolorConversionMatrixLoc = -1;

    public Yuv2RgbProgram() {
        mProgramHandle = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        if (mProgramHandle == 0) {
            throw new RuntimeException("Unable to create program");
        }
        Log.d(TAG, "Created program " + mProgramHandle);

        // get locations of attributes and uniforms
        maPositionLoc = GLES20.glGetAttribLocation(mProgramHandle, "aPosition");
        GlUtil.checkLocation(maPositionLoc, "aPosition");
        maTextureCoordLoc = GLES20.glGetAttribLocation(mProgramHandle, "aTextureCoord");
        GlUtil.checkLocation(maTextureCoordLoc, "aTextureCoord");
        muYtexLoc = GLES20.glGetUniformLocation(mProgramHandle, "Ytex");
        GlUtil.checkLocation(muYtexLoc, "Ytex");
        muUtexLoc = GLES20.glGetUniformLocation(mProgramHandle, "Utex");
        GlUtil.checkLocation(muUtexLoc, "Utex");
        muVtexLoc = GLES20.glGetUniformLocation(mProgramHandle, "Vtex");
        GlUtil.checkLocation(muVtexLoc, "Vtex");
        mucolorConversionMatrixLoc = GLES20.glGetUniformLocation(mProgramHandle, "colorConversionMatrix");
        GlUtil.checkLocation(mucolorConversionMatrixLoc, "colorConversionMatrix");

        GLES20.glUseProgram(mProgramHandle);
        GLES20.glUniformMatrix3fv(mucolorConversionMatrixLoc, 1, false, kColorConversion601FullRange, 0);
        GlUtil.checkGlError("glUniformMatrix4fv");
    }

    public void release() {
        GLES20.glDeleteProgram(mProgramHandle);
        mProgramHandle = -1;
    }

    public void draw(FloatBuffer vertexBuffer,
                     FloatBuffer textureBuffer,
                     int yUniform, int uUniform, int vUniform) {
        GlUtil.checkGlError("draw start");

        // Select the program.
        GLES20.glUseProgram(mProgramHandle);
        GlUtil.checkGlError("glUseProgram");

        // Enable the "aPosition" vertex attribute.
        GLES20.glEnableVertexAttribArray(maPositionLoc);
        GlUtil.checkGlError("glEnableVertexAttribArray");

        // Connect vertexBuffer to "aPosition".
        GLES20.glVertexAttribPointer(maPositionLoc, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);
        GlUtil.checkGlError("glVertexAttribPointer");

        // Enable the "aTextureCoord" vertex attribute.
        GLES20.glEnableVertexAttribArray(maTextureCoordLoc);
        GlUtil.checkGlError("glEnableVertexAttribArray");

        // Connect textureBuffer to "aTextureCoord".
        GLES20.glVertexAttribPointer(maTextureCoordLoc, 2, GLES20.GL_FLOAT, false, 0, textureBuffer);
        GlUtil.checkGlError("glVertexAttribPointer");

        //GLES20.glUniformMatrix3fv(mucolorConversionMatrixLoc, 1, false, colorConvertionMatrix, 0);
        //GlUtil.checkGlError("glUniformMatrix4fv");

        GLES20.glUniform1i(muYtexLoc, yUniform);
        GlUtil.checkGlError("glUniform1i");

        GLES20.glUniform1i(muUtexLoc, uUniform);
        GlUtil.checkGlError("glUniform1i");

        GLES20.glUniform1i(muVtexLoc, vUniform);
        GlUtil.checkGlError("glUniform1i");

        // Draw the rect.
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GlUtil.checkGlError("glDrawArrays");

        // Done -- disable vertex array and program.
        GLES20.glDisableVertexAttribArray(maPositionLoc);
        GLES20.glDisableVertexAttribArray(maTextureCoordLoc);
        GLES20.glUseProgram(0);
    }
}
