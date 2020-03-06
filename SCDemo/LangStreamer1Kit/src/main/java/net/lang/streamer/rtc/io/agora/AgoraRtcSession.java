package net.lang.streamer.rtc.io.agora;

import android.opengl.GLES20;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import net.lang.gpuimage.utils.*;
import net.lang.streamer.rtc.IRtcSession;

/**
 * Created by lang on 2017/9/8.
 */

public class AgoraRtcSession extends IRtcSession {

    private static final int kMaxVideoWidth = 1920;
    private static final int kMaxVideoHeight = 1080;

    private int[] mYuvTextures;
    private int mLumaTextureWidth;
    private int mLumaTextureHeight;

    private int mVideoWidth;
    private int mVideoHeight;

    private byte[] mYdata;
    private byte[] mUdata;
    private byte[] mVData;

    protected FloatBuffer mGLTextureBuffer;

    public AgoraRtcSession() {

        mYuvTextures = new int[3];

        mYdata = new byte[kMaxVideoWidth * kMaxVideoHeight];
        mUdata = new byte[kMaxVideoWidth/2 * kMaxVideoHeight/2];
        mVData = new byte[kMaxVideoWidth/2 * kMaxVideoHeight/2];

        mLumaTextureWidth = 0;
        mLumaTextureHeight = 0;

        mGLTextureBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
    }

    //
    // called from native thread.
    //
    @Override
    public void sendYUVData(final byte[] yuv, final int width, final int height) {

        mVideoWidth = width;
        mVideoHeight = height;

        int yDataOffset = 0;
        int uDataOffset = width * height;
        int vDataOffset = 5 * width * height / 4;

        int yLength = width * height;
        int uvLength = width * height / 4;

        System.arraycopy(yuv, yDataOffset, mYdata, 0, yLength);
        System.arraycopy(yuv, uDataOffset, mUdata, 0, uvLength);
        System.arraycopy(yuv, vDataOffset, mVData, 0, uvLength);
    }

    //
    // called from opengl thread.
    //
    @Override
    public void generateYUVTextures() {
        GLES20.glGenTextures(3, mYuvTextures, 0);

        for (int i = 0; i < 3; i++) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mYuvTextures[i]);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

            if (i > 0) {
                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, mVideoWidth/2, mVideoHeight/2, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, null);
            } else {
                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, mVideoWidth, mVideoHeight, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, null);
            }
        }

        mLumaTextureWidth = mVideoWidth;
        mLumaTextureHeight = mVideoHeight;
    }

    @Override
    public void destoryYUVTextures() {
        if (mYuvTextures[0] != 0) {
            GLES20.glDeleteTextures(3, mYuvTextures, 0);
            mYuvTextures[0] = 0;
        }
    }

    @Override
    public void updateYUVTextures() {
        ByteBuffer yBuffer = ByteBuffer.wrap(mYdata);
        ByteBuffer uBuffer = ByteBuffer.wrap(mUdata);
        ByteBuffer vBuffer = ByteBuffer.wrap(mVData);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mYuvTextures[0]);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, mVideoWidth, mVideoHeight, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, yBuffer);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mYuvTextures[1]);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, mVideoWidth/2, mVideoHeight/2, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, uBuffer);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mYuvTextures[2]);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, mVideoWidth/2, mVideoHeight/2, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, vBuffer);
    }

    @Override
    public boolean texturesMatch() {
        if (mLumaTextureWidth == mVideoWidth && mLumaTextureHeight == mVideoHeight) {
            return true;
        } else {
            return false;
        }
    }

    public FloatBuffer getTextureCoordFloatBuffer() {

        mGLTextureBuffer.put(TextureRotationUtil.getRotation(Rotation.NORMAL, false, true)).position(0);
        return  mGLTextureBuffer;
    }
}
