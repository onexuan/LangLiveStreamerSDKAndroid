package net.lang.streamer2.utils;

import android.opengl.GLES20;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class TextureUtils {
    private int id;
    private int texture;
    private boolean valide;
    ReentrantLock lock;
    private int mFrameBufferWidth;
    private int mFrameBufferHeight;
    private int[] mFrameBufferTexture;
    private int[] mFrameBuffer;
    public TextureUtils() {
        lock = new ReentrantLock();
        valide = false;
    }

    public void initTexture_l(int w, int h) {
        if (mFrameBufferWidth != w || mFrameBufferHeight != h){
            if (mFrameBufferTexture != null) {
                GLES20.glDeleteTextures(1, mFrameBufferTexture, 0);
                mFrameBufferTexture = null;
            }
            if (mFrameBuffer != null) {
                GLES20.glDeleteFramebuffers(1, mFrameBuffer, 0);
                mFrameBuffer = null;
            }
        }

        if (mFrameBuffer == null && w > 0 && h > 0) {
            mFrameBuffer = new int[1];
            mFrameBufferTexture = new int[1];

            GLES20.glGenFramebuffers(1, mFrameBuffer, 0);
            GLES20.glGenTextures(1, mFrameBufferTexture, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mFrameBufferTexture[0]);


            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0,
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

            mFrameBufferWidth = w;
            mFrameBufferHeight = h;

            id = mFrameBuffer[0];
            texture = mFrameBufferTexture[0];
        }
    }

    public void setValid_l(boolean v) {
        valide = v;
    }

    public boolean isValid_l() {
        return valide;
    }

    public int textureID() {
        return texture;
    }

    public int id() {
        return id;
    }

    public void lock() {
        lock.lock();
    }

    public void unlock() {
        lock.unlock();
    }

    public boolean tryLock() {
        return lock.tryLock();
    }

    public boolean tryLock(long timeMS) {
        try {
            return lock.tryLock(timeMS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            return false;
        }
    }

    public boolean isLock() {
        return lock.isLocked();
    }

    public int textureWidth() {
        return mFrameBufferWidth;
    }

    public int textureHeight() {
        return mFrameBufferHeight;
    }
}
