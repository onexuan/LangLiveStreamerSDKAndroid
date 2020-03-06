package net.lang.streamer2.gles;

import android.opengl.GLES20;
import android.util.Log;

import net.lang.gpuimage.utils.OpenGlUtils;

import java.nio.FloatBuffer;

public class LangTexture2dProgram {
    private static final String TAG = LangTexture2dProgram.class.getSimpleName();

    // Simple vertex shader, used for all programs.
    private static final String VERTEX_SHADER =
            "attribute vec4 aPosition;\n" +
                    "attribute vec2 aTextureCoord;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "void main() {\n" +
                    "    gl_Position = aPosition;\n" +
                    "    vTextureCoord = aTextureCoord;\n" +
                    "}\n";

    // Simple fragment shader for use with "normal" 2D textures.
    private static final String FRAGMENT_SHADER_2D =
            "precision mediump float;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "uniform sampler2D uTexture;\n" +
                    "void main() {\n" +
                    "    gl_FragColor = texture2D(uTexture, vTextureCoord);\n" +
                    "}\n";

    // Handles to the GL program and various components of it.
    private int mProgramHandle;
    private int maPositionLoc;
    private int maTextureCoordLoc;
    private int muTextureLoc;

    /**
     * Prepares the program in the current EGL context.
     */
    public LangTexture2dProgram() {
        mProgramHandle = OpenGlUtils.loadProgram(VERTEX_SHADER, FRAGMENT_SHADER_2D);
        if (mProgramHandle == 0) {
            throw new RuntimeException("Unable to create program");
        }
        Log.d(TAG, "Created program " + mProgramHandle);

        // get locations of attributes and uniforms
        maPositionLoc = GLES20.glGetAttribLocation(mProgramHandle, "aPosition");
        LangTexture2dProgram.checkLocation(maPositionLoc, "aPosition");
        maTextureCoordLoc = GLES20.glGetAttribLocation(mProgramHandle, "aTextureCoord");
        LangTexture2dProgram.checkLocation(maTextureCoordLoc, "aTextureCoord");
        muTextureLoc = GLES20.glGetUniformLocation(mProgramHandle, "uTexture");
        LangTexture2dProgram.checkLocation(muTextureLoc, "uTexture");
    }

    /**
     * Releases the program.
     * <p>
     * The appropriate EGL context must be current (i.e. the one that was used to create
     * the program).
     */
    public void release() {
        Log.d(TAG, "deleting program " + mProgramHandle);
        GLES20.glDeleteProgram(mProgramHandle);
        mProgramHandle = -1;
    }

    /**
     * Issues the draw call.  Does the full setup on every call.
     *
     */
    public void draw(FloatBuffer vertexBuffer, FloatBuffer textureBuffer, int textureId) {
        OpenGlUtils.checkGlError("draw start");

        // Select the program.
        GLES20.glUseProgram(mProgramHandle);
        OpenGlUtils.checkGlError("glUseProgram");

        // Set the texture.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);

        // Enable the "aPosition" vertex attribute.
        GLES20.glEnableVertexAttribArray(maPositionLoc);
        OpenGlUtils.checkGlError("glEnableVertexAttribArray");

        // Connect vertexBuffer to "aPosition".
        GLES20.glVertexAttribPointer(maPositionLoc, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);
        OpenGlUtils.checkGlError("glVertexAttribPointer");

        // Enable the "aTextureCoord" vertex attribute.
        GLES20.glEnableVertexAttribArray(maTextureCoordLoc);
        OpenGlUtils.checkGlError("glEnableVertexAttribArray");

        // Connect texBuffer to "aTextureCoord".
        GLES20.glVertexAttribPointer(maTextureCoordLoc, 2, GLES20.GL_FLOAT, false, 0, textureBuffer);
        OpenGlUtils.checkGlError("glVertexAttribPointer");

        GLES20.glUniform1i(muTextureLoc, 0);
        OpenGlUtils.checkGlError("glUniform1i");

        // Draw the rect.
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        OpenGlUtils.checkGlError("glDrawArrays");

        // Done -- disable vertex array, texture, and program.
        GLES20.glDisableVertexAttribArray(maPositionLoc);
        GLES20.glDisableVertexAttribArray(maTextureCoordLoc);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glUseProgram(0);
    }

    /**
     * Checks to see if the location we obtained is valid.  GLES returns -1 if a label
     * could not be found, but does not set the GL error.
     * <p>
     * Throws a RuntimeException if the location is invalid.
     */
    public static void checkLocation(int location, String label) {
        if (location < 0) {
            throw new RuntimeException("Unable to locate '" + label + "' in program");
        }
    }
}
