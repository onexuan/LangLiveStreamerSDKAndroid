package net.lang.gpuimage.filter.base;

import android.content.Context;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;

import net.lang.gpuimage.R;
import net.lang.gpuimage.filter.base.gpuimage.GPUImageFilter;
import net.lang.gpuimage.utils.OpenGlUtils;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

public class MagicCameraInputFilter extends GPUImageFilter{
    private static final String CAMERA_INPUT_VERTEX_SHADER = ""+
            "attribute vec4 position;\n" +
            "attribute vec4 inputTextureCoordinate;\n" +
            "\n" +
            "uniform mat4 textureTransform;\n" +
            "varying vec2 textureCoordinate;\n" +
            "uniform highp vec4 mixColor;\n" +
            "\n" +
            "void main()\n" +
            "{\n" +
            "	textureCoordinate = (textureTransform * inputTextureCoordinate).xy;\n" +
            "   textureCoordinate.x = textureCoordinate.x;\n" +
            "   textureCoordinate.y = textureCoordinate.y;\n" +
            "	gl_Position = position;\n" +
            "}";

    private static final String CAMERA_INPUT_FRAGMENT_SHADER = ""+
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision highp float;" +
            "varying highp vec2 textureCoordinate;\n" +
            "\n" +
            "uniform samplerExternalOES inputImageTexture;\n" +
            "uniform lowp vec4 mixColor;\n" +
            "\n" +
            "void main()\n" +
            "{\n" +
            " mediump vec4 color = texture2D(inputImageTexture, textureCoordinate);\n" +
            " gl_FragColor = color ;\n" +
            "}";

    private float[] mTextureTransformMatrix;
    private int mTextureTransformMatrixLocation;
    private int mMixColorLocation = -1;
    private  int mCustomTransformLocation = -1;

    protected static int[] mFrameBuffers = null;
    protected static int[] mFrameBufferTextures = null;
    private int mFrameWidth = -1;
    private int mFrameHeight = -1;
    private FloatBuffer mMixColor = null;
    private boolean updateTextureMatrx = false;

    private  float[] mTextureMatrx = {
            0.0f, 0.0f, 0.f,0f,
            1.0f, 0.0f, 0.f,0f,
            0.0f, 1.0f, 0.f,0f,
            0.0f, 1.0f, 0.f,0f,
    };

    public MagicCameraInputFilter(Context ctx){
        super(OpenGlUtils.readShaderFromRawResource(R.raw.cam_input_vetx, ctx),  OpenGlUtils.readShaderFromRawResource(R.raw.camera_input, ctx));
        mMixColor = (FloatBuffer) FloatBuffer.allocate(4).position(0);
        Matrix.setIdentityM(mTextureMatrx, 0);
        // 0 ---> bright
        // 1 ---> zoom
        mMixColor.array()[0] = 0.f;
        mMixColor.array()[1] = 1.0f;
        mMixColor.array()[2] = 1.0f;
        mMixColor.array()[3] = 1.0f;
    }

    public MagicCameraInputFilter(){
        super(CAMERA_INPUT_VERTEX_SHADER,  CAMERA_INPUT_FRAGMENT_SHADER);
    }

    public void setMixColor(FloatBuffer xyzw) {
        xyzw.position(0);
        mMixColor.position(0);
        mMixColor.put(xyzw);
        mMixColor.position(0);
        runOnDraw(new Runnable() {
            @Override
            public void run() {
                float scale = mMixColor.get(1);
                Matrix.setIdentityM(mTextureMatrx, 0);
                Matrix.translateM(mTextureMatrx, 0, (1 - scale) / 2,  (1 - scale) / 2, (1 - scale) / 2);
                Matrix.scaleM(mTextureMatrx, 0, scale,  scale,  scale);
                updateTextureMatrx = true;
            }
        });
    }

    protected void onInit() {
        super.onInit();
        mTextureTransformMatrixLocation = GLES20.glGetUniformLocation(mGLProgId, "textureTransform");
        mMixColorLocation = GLES20.glGetUniformLocation(mGLProgId, "mixColor");
        mCustomTransformLocation = GLES20.glGetUniformLocation(mGLProgId, "customTransform");
    }

    public void setTextureTransformMatrix(float[] mtx){
        mTextureTransformMatrix = mtx;
    }

    @Override
    public int onDrawFrame(int textureId) {
        return this.onDrawFrame(textureId, mGLCubeBuffer, mGLTextureBuffer);
    }

    @Override
    public int onDrawFrame(int textureId, FloatBuffer vertexBuffer, FloatBuffer textureBuffer) {
        runPendingOnDrawTasks();
        GLES20.glUseProgram(mGLProgId);
        if(!isInitialized()) {
            return OpenGlUtils.NOT_INIT;
        }
        vertexBuffer.position(0);
        GLES20.glVertexAttribPointer(mGLAttribPosition, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);
        GLES20.glEnableVertexAttribArray(mGLAttribPosition);
        textureBuffer.position(0);
        GLES20.glVertexAttribPointer(mGLAttribTextureCoordinate, 2, GLES20.GL_FLOAT, false, 0, textureBuffer);
        GLES20.glEnableVertexAttribArray(mGLAttribTextureCoordinate);
        GLES20.glUniformMatrix4fv(mTextureTransformMatrixLocation, 1, false, mTextureTransformMatrix, 0);
        GLES20.glUniformMatrix4fv(mCustomTransformLocation, 1, false, mTextureMatrx, 0);

        if (updateTextureMatrx) {
            GLES20.glUniform4fv(mMixColorLocation, 1, mMixColor);
            updateTextureMatrx = false;
        }

        if(textureId != OpenGlUtils.NO_TEXTURE){
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
            GLES20.glUniform1i(mGLUniformTexture, 0);
        }

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(mGLAttribPosition);
        GLES20.glDisableVertexAttribArray(mGLAttribTextureCoordinate);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
        return OpenGlUtils.ON_DRAWN;
    }

    public int onDrawToTexture(final int textureId,FloatBuffer vertexBuffer, FloatBuffer textureBuffer, ByteBuffer buffer) {
        if(mFrameBuffers == null)
            return OpenGlUtils.NO_TEXTURE;
        if (vertexBuffer == null || textureBuffer == null) {
            return onDrawToTexture(textureId);
        }
        runPendingOnDrawTasks();
        GLES20.glViewport(0, 0, mFrameWidth, mFrameHeight);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffers[0]);
        GLES20.glUseProgram(mGLProgId);
        if(!isInitialized()) {
            return OpenGlUtils.NOT_INIT;
        }
        vertexBuffer.position(0);
        GLES20.glVertexAttribPointer(mGLAttribPosition, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);
        GLES20.glEnableVertexAttribArray(mGLAttribPosition);
        textureBuffer.position(0);
        GLES20.glVertexAttribPointer(mGLAttribTextureCoordinate, 2, GLES20.GL_FLOAT, false, 0, textureBuffer);
        GLES20.glEnableVertexAttribArray(mGLAttribTextureCoordinate);
        GLES20.glUniformMatrix4fv(mTextureTransformMatrixLocation, 1, false, mTextureTransformMatrix, 0);
        GLES20.glUniformMatrix4fv(mCustomTransformLocation, 1, false, mTextureMatrx, 0);

        if (updateTextureMatrx ) {
            GLES20.glUniform4fv(mMixColorLocation, 1, mMixColor);
            updateTextureMatrx = false;
        }

        if(textureId != OpenGlUtils.NO_TEXTURE){
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
            GLES20.glUniform1i(mGLUniformTexture, 0);
        }

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        if (buffer != null)
            GLES20.glReadPixels(0, 0, mFrameWidth, mFrameHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer);

        GLES20.glDisableVertexAttribArray(mGLAttribPosition);
        GLES20.glDisableVertexAttribArray(mGLAttribTextureCoordinate);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, mOutputWidth, mOutputHeight);
        return mFrameBufferTextures[0];
    }

    public int onDrawToTexture(final int textureId) {
        if(mFrameBuffers == null)
            return OpenGlUtils.NO_TEXTURE;
        runPendingOnDrawTasks();
        GLES20.glViewport(0, 0, mFrameWidth, mFrameHeight);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffers[0]);
        GLES20.glUseProgram(mGLProgId);
        if(!isInitialized()) {
            return OpenGlUtils.NOT_INIT;
        }
        mGLCubeBuffer.position(0);
        GLES20.glVertexAttribPointer(mGLAttribPosition, 2, GLES20.GL_FLOAT, false, 0, mGLCubeBuffer);
        GLES20.glEnableVertexAttribArray(mGLAttribPosition);
        mGLTextureBuffer.position(0);
        GLES20.glVertexAttribPointer(mGLAttribTextureCoordinate, 2, GLES20.GL_FLOAT, false, 0, mGLTextureBuffer);
        GLES20.glEnableVertexAttribArray(mGLAttribTextureCoordinate);
        GLES20.glUniformMatrix4fv(mTextureTransformMatrixLocation, 1, false, mTextureTransformMatrix, 0);
        GLES20.glUniformMatrix4fv(mCustomTransformLocation, 1, false, mTextureMatrx, 0);

        if (updateTextureMatrx ) {
            GLES20.glUniform4fv(mMixColorLocation, 1, mMixColor);
            updateTextureMatrx = false;
        }

        if(textureId != OpenGlUtils.NO_TEXTURE){
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
            GLES20.glUniform1i(mGLUniformTexture, 0);
        }

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(mGLAttribPosition);
        GLES20.glDisableVertexAttribArray(mGLAttribTextureCoordinate);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, mOutputWidth, mOutputHeight);
        return mFrameBufferTextures[0];
    }

    public int frameBuffer() {
        return mFrameBuffers[0];
    }

    public void initCameraFrameBuffer(int width, int height) {
        if(mFrameBuffers != null && (mFrameWidth != width || mFrameHeight != height))
            destroyFramebuffers();
        if (mFrameBuffers == null) {
            mFrameWidth = width;
            mFrameHeight = height;
            mFrameBuffers = new int[1];
            mFrameBufferTextures = new int[1];

            GLES20.glGenFramebuffers(1, mFrameBuffers, 0);

            GLES20.glGenTextures(1, mFrameBufferTextures, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mFrameBufferTextures[0]);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
                    GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffers[0]);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                    GLES20.GL_TEXTURE_2D, mFrameBufferTextures[0], 0);

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        }
    }

    public void destroyFramebuffers() {
        if (mFrameBufferTextures != null) {
            GLES20.glDeleteTextures(1, mFrameBufferTextures, 0);
            mFrameBufferTextures = null;
        }
        if (mFrameBuffers != null) {
            GLES20.glDeleteFramebuffers(1, mFrameBuffers, 0);
            mFrameBuffers = null;
        }
        mFrameWidth = -1;
        mFrameHeight = -1;
    }
}
