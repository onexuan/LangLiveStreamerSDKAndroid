package net.lang.gpuimage.utils;

import android.opengl.GLES20;

public class FBMHelper {
    private int mFbId;
    private int mTextureId;

    private int mFrameBufferWidth;
    private int mFrameBufferHeight;
    private int[] mFrameBufferTexture;
    private int[] mFrameBuffer;

    public FBMHelper() {
        mFbId = -1;
        mTextureId = -1;

        mFrameBufferWidth = 0;
        mFrameBufferHeight = 0;

        mFrameBuffer = new int[1];
        mFrameBufferTexture = new int[1];
    }

    public void initialize(int width, int height) throws IllegalArgumentException {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("input width or height illegal");
        }

        GLES20.glGenFramebuffers(1, mFrameBuffer, 0);
        GLES20.glGenTextures(1, mFrameBufferTexture, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mFrameBufferTexture[0]);


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

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffer[0]);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, mFrameBufferTexture[0], 0);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        mFrameBufferWidth = width;
        mFrameBufferHeight = height;

        mFbId = mFrameBuffer[0];
        mTextureId = mFrameBufferTexture[0];
    }

    public void deinitialize() {
        if (mFrameBufferTexture[0] > 0) {
            GLES20.glDeleteTextures(1, mFrameBufferTexture, 0);
            mTextureId = -1;
        }
        if (mFrameBuffer[0] > 0) {
            GLES20.glDeleteFramebuffers(1, mFrameBuffer, 0);
            mFbId = -1;
        }

        mFrameBufferWidth = mFrameBufferHeight = 0;
    }

    public int fbId() {
        return mFbId;
    }

    public final int textureID() {
        return mTextureId;
    }

    public int textureWidth() {
        return mFrameBufferWidth;
    }

    public int textureHeight() {
        return mFrameBufferHeight;
    }
}
