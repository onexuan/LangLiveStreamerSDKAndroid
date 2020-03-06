package net.lang.gpuimage.filter.custom;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLES20;

import net.lang.gpuimage.filter.base.gpuimage.GPUImageFilter;
import net.lang.gpuimage.utils.OpenGlUtils;
import net.lang.gpuimage.utils.TextureRotationUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class MagicWaterMarkFilter extends GPUImageFilter {

    private Context context;
    private int[] inputTextureHandles = {-1};
    private int[] inputTextureUniformLocations = {-1};
    private FloatBuffer mWaterMarkVertex;
    private int[] mFrameBuffer = null;
    private int mFrameBufferWidth = 0;
    private int mFrameBufferHeight = 0;
    private int[] mFrameBufferTexture = null;
    private int mSurfaceWidth = 0;
    private int mSurfaceHeight = 0;
    private int mX = 0;
    private int mY = 0;
    private int mW = 0;
    private int mH = 0;
    private boolean fullScreen = false;

    private boolean mUpdateRect = false;
    private String mUrl = "filter/snail.png";
    private static float sMatrx[] = {
            -1.0f, -1.0f, //左下
            1.0f, -1.0f,  // 右下
            -1.0f, 1.0f,    // 左上
            1.0f, 1.0f,   // 右上
    };

    private static float mVertexTranslateMatrx[] = {
            0.f, 0.f, 0.f, 0.f,
            0.f, 0.f, 0.f, 0.f,
            0.f, 0.f, 0.f, 0.f,
            0.f, 0.f, 0.f, 0.f
    };

    private boolean mEnable = false;

    public static final String NO_FILTER_VERTEX_SHADER2 = "" +
            "attribute vec4 position;\n" +
            "uniform mat4 vertexTranslateMatrix;\n" +
            "attribute vec4 inputTextureCoordinate;\n" +
            " \n" +
            "varying vec2 textureCoordinate;\n" +
            " \n" +
            "void main()\n" +
            "{\n" +
            "    gl_Position =  position;\n" +
            //"    textureCoordinate = vec2(inputTextureCoordinate.x, 1.0-inputTextureCoordinate.y);\n" +
            "    textureCoordinate = vec2(inputTextureCoordinate.x, inputTextureCoordinate.y);\n" +
            "}";
    public static final String NO_FILTER_FRAGMENT_SHADER2 = "" +
            "varying highp vec2 textureCoordinate;\n" +
            " \n" +
            "uniform sampler2D inputImageTexture;\n" +
            "void main()\n" +
            "{\n" +
            "     gl_FragColor = texture2D(inputImageTexture, textureCoordinate);\n" +
            //"	  gl_FragColor.a = 0.8;\n" +
            "}";

    public MagicWaterMarkFilter(Context context) {
        super(NO_FILTER_VERTEX_SHADER2, NO_FILTER_FRAGMENT_SHADER2);
        mWaterMarkVertex = ByteBuffer.allocateDirect(TextureRotationUtil.CUBE.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mWaterMarkVertex.put(sMatrx).position(0);
        this.context = context;
    }

    public boolean enable() {
        return mEnable;
    }

    public void setSurface(int w, int h) {
        mSurfaceWidth = w;
        mSurfaceHeight = h;
    }

    public void setUrl(String url) {
        if (mUrl == url) return;

        mUrl = url;
        runOnDraw(new Runnable() {
            @Override
            public void run() {
                GLES20.glDeleteTextures(1, inputTextureHandles, 0);
                for (int i = 0; i < inputTextureHandles.length; i++)
                    inputTextureHandles[i] = -1;

                inputTextureHandles[0] = OpenGlUtils.loadTexture(context, mUrl);
            }
        });
    }

    public void setBitmap(final Bitmap picture) {

        runOnDraw(new Runnable() {
            @Override
            public void run() {
                GLES20.glDeleteTextures(1, inputTextureHandles, 0);
                for (int i = 0; i < inputTextureHandles.length; i++)
                    inputTextureHandles[i] = -1;

                inputTextureHandles[0] = OpenGlUtils.loadTexture(picture, OpenGlUtils.NO_TEXTURE, false);
            }
        });
    }

    public int setRect(int x, int y, int w, int h) {
        mX = x;
        mY = y;
        mW = w;
        mH = h;
        mUpdateRect = true;
        return 0;
    }

    public void setEnable(boolean e) {
        mEnable = e;
    }

    public void setFullScreen(boolean fullScreen) {
        this.fullScreen = fullScreen;
    }

    public void onDestroy() {
        super.onDestroy();
        GLES20.glDeleteTextures(1, inputTextureHandles, 0);
        for (int i = 0; i < inputTextureHandles.length; i++)
            inputTextureHandles[i] = -1;
        getFrameBuffer(0, 0);
        context = null;
    }

    public int onDrawFrameWaterMark(int frameWidth, int frameHeight) {
        if (!mEnable) {
            return OpenGlUtils.ON_DRAWN;
        }
        if (!mIsInitialized) {
            return OpenGlUtils.ON_DRAWN;
        }

        if (fullScreen) {
            if (mH == 0)
                mH = mSurfaceHeight;
            if (mW == 0)
                mW = mSurfaceWidth;

            mX = 0;
            mY = 0;
        }

        float widthScaleFactor = (float) frameWidth / (float) mSurfaceWidth;
        float heightScaleFactor = (float) frameHeight / (float) mSurfaceHeight;
        float fx = (float) mX * widthScaleFactor;
        float fy = (float) mY * heightScaleFactor;
        float fw = (float) mW * widthScaleFactor;
        float fh = (float) mH * heightScaleFactor;

        GLES20.glViewport((int) fx, (int) (fy), (int) fw, (int) fh);
        GLES20.glUseProgram(mGLProgId);
        runPendingOnDrawTasks();

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        mWaterMarkVertex.position(0);
        GLES20.glVertexAttribPointer(mGLAttribPosition, 2, GLES20.GL_FLOAT, false, 0, mWaterMarkVertex);
        GLES20.glEnableVertexAttribArray(mGLAttribPosition);
        mGLTextureBuffer.position(0);
        GLES20.glVertexAttribPointer(mGLAttribTextureCoordinate, 2, GLES20.GL_FLOAT, false, 0,
                mGLTextureBuffer);
        GLES20.glEnableVertexAttribArray(mGLAttribTextureCoordinate);

        if (inputTextureHandles[0] != OpenGlUtils.NO_TEXTURE) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, inputTextureHandles[0]);
            GLES20.glUniform1i(mGLUniformTexture, 0);
        }
        onDrawArraysPre();
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(mGLAttribPosition);
        GLES20.glDisableVertexAttribArray(mGLAttribTextureCoordinate);
        onDrawArraysAfter();
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glDisable(GLES20.GL_BLEND);
        return OpenGlUtils.ON_DRAWN;
    }


    public int onDrawFrameWaterMark(int frameWidth, int frameHeight, FloatBuffer vertexBuffer, FloatBuffer textureBuffer) {
        if (!mEnable) {
            return OpenGlUtils.ON_DRAWN;
        }
        if (!mIsInitialized) {
            return OpenGlUtils.ON_DRAWN;
        }

        float widthScaleFactor = (float) frameWidth / (float) mSurfaceWidth;
        float heightScaleFactor = (float) frameHeight / (float) mSurfaceHeight;
        float fx = (float) mX * widthScaleFactor;
        float fy = (float) mY * heightScaleFactor;
        float fw = (float) mW * widthScaleFactor;
        float fh = (float) mH * heightScaleFactor;

        //GLES20.glViewport(mX, mY + frameHeight - mH, mW, mH);
        GLES20.glViewport((int) fx, (int) (fy + frameHeight - fh), (int) fw, (int) fh);
        GLES20.glUseProgram(mGLProgId);
        runPendingOnDrawTasks();


        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        vertexBuffer.position(0);
        GLES20.glVertexAttribPointer(mGLAttribPosition, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);
        GLES20.glEnableVertexAttribArray(mGLAttribPosition);
        textureBuffer.position(0);
        GLES20.glVertexAttribPointer(mGLAttribTextureCoordinate, 2, GLES20.GL_FLOAT, false, 0,
                textureBuffer);
        GLES20.glEnableVertexAttribArray(mGLAttribTextureCoordinate);

        if (inputTextureHandles[0] != OpenGlUtils.NO_TEXTURE) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, inputTextureHandles[0]);
            GLES20.glUniform1i(mGLUniformTexture, 0);
        }
        onDrawArraysPre();
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(mGLAttribPosition);
        GLES20.glDisableVertexAttribArray(mGLAttribTextureCoordinate);
        onDrawArraysAfter();
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glDisable(GLES20.GL_BLEND);
        return OpenGlUtils.ON_DRAWN;
    }

    public void onInitialized() {
        super.onInitialized();
        runOnDraw(new Runnable() {
            public void run() {
                inputTextureHandles[0] = OpenGlUtils.loadTexture(context, mUrl);
            }
        });
    }

    public int getFrameBuffer(int w, int h) {
        if (mFrameBufferWidth != w || mFrameBufferHeight != h) {
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

        }
        return mFrameBuffer != null ? mFrameBuffer[0] : OpenGlUtils.NO_TEXTURE;
    }

    public int getFrameBufferTexture() {
        return mFrameBufferTexture == null ? OpenGlUtils.NO_TEXTURE : mFrameBufferTexture[0];
    }
}