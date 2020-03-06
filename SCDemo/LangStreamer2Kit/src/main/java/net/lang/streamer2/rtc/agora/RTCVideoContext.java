package net.lang.streamer2.rtc.agora;

import android.content.Context;
import android.graphics.PixelFormat;
import android.opengl.GLES20;

import com.yunfan.graphicbuffer.GraphicBufferWrapper;

import net.lang.gpuimage.utils.OpenGlUtils;
import net.lang.gpuimage.utils.Rotation;
import net.lang.gpuimage.utils.TextureRotationUtil;
import net.lang.rtclib.LangRTCVideoSession;
import net.lang.streamer2.config.LangRtcConfig;
import net.lang.streamer2.engine.data.LangRtcConfiguration;
import net.lang.streamer2.gles.LangTexture2dProgram;
import net.lang.streamer2.gles.LangYuv2RgbProgram;
import net.lang.streamer2.utils.DebugLog;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.List;

public class RTCVideoContext {
    private static final String TAG = RTCVideoContext.class.getSimpleName();

    private GraphicBufferWrapper mPushGraphicBuffer = null;
    private GraphicBufferWrapper mMixGraphicBuffer = null;
    private int[] mGraphicBufferFrameBufferID = null;

    private FloatBuffer mGLCubeBuffer;
    private FloatBuffer mGLTextureBuffer;

    private float[] mVertices = new float[8];
    private float[] mVerticesFlip = new float[8];

    private FloatBuffer mGLRtcLocalCubeBuffer;
    private FloatBuffer mGLRtcLocalTextureBuffer;

    private LangTexture2dProgram mTex2dProgram = null;
    private LangYuv2RgbProgram mCCPrograme = null;

    public RTCVideoContext() {
        mGLCubeBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.CUBE.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mGLCubeBuffer.put(TextureRotationUtil.CUBE).position(0);
        mGLTextureBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.CUBE.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mGLTextureBuffer.put(TextureRotationUtil.getRotation(Rotation.NORMAL, false, true)).position(0);

        mGLRtcLocalCubeBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.CUBE.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mGLRtcLocalCubeBuffer.put(TextureRotationUtil.CUBE).position(0);
        mGLRtcLocalTextureBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.CUBE.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mGLRtcLocalTextureBuffer.put(TextureRotationUtil.getRotation(Rotation.NORMAL, false, true)).position(0);
    }

    public GraphicBufferWrapper currentPushGraphicBuffer() {
        return mPushGraphicBuffer;
    }

    public GraphicBufferWrapper currentMixGraphicBuffer() {
        return mMixGraphicBuffer;
    }

    /*
     * called in opengl-es context thread
     */
    public boolean drawLocalRTCVideoFrame(final int srcTextureId) {
        if (mPushGraphicBuffer == null) {
            DebugLog.w(TAG, "local rtc gl context not prepared");
            return false;
        }

        if (mPushGraphicBuffer.tryLockGB()) {
            int gbWidth = mPushGraphicBuffer.width();
            int gbHeight = mPushGraphicBuffer.height();

            GLES20.glViewport(0, 0, gbWidth, gbHeight);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mPushGraphicBuffer.framebufferId());
            OpenGlUtils.checkGlError("glBindFramebuffer");

            mTex2dProgram.draw(mGLCubeBuffer, mGLTextureBuffer, srcTextureId);

            mPushGraphicBuffer.unlockGB();

            return true;
        } else {
            DebugLog.w(TAG, "internal rtc graphic buffer is busy!");
            return false;
        }
    }

    /*
     * called in opengl-es context thread
     */
    public boolean mixLocalWithRemoteVideoFrame(final int srcTextureId,
                                                final List<LangRTCVideoSession> rtcSessions,
                                                final LangRtcConfig.RtcDisplayParams rtcDisplayParams) {
        if (mMixGraphicBuffer == null) {
            DebugLog.w(TAG, "local rtc gl context not prepared");
            return false;
        }

        int bufferWidth = mMixGraphicBuffer.width();
        int bufferHeight = mMixGraphicBuffer.height();

        float rtcSubWinOffset_x, rtcSubWinOffset_y;
        float rtcSubWinSize_x, rtcSubWinSize_y;

        int users = rtcSessions.size();
        if (users <= 2) {
            float kTopOffsetPercent = rtcDisplayParams.topOffsetPercent;//0.10f;
            float kSubWinWidthPercent = rtcDisplayParams.subWidthPercentOnTwoWins;//0.5f;
            float kSubWinHeightPercent = rtcDisplayParams.subHeightPercentOnTwoWins;//0.5f;
            // draw local canvas to framebuffer.
            rtcSubWinOffset_x = 0;
            rtcSubWinOffset_y = (float)bufferHeight * kTopOffsetPercent;
            rtcSubWinSize_x =  (float)bufferWidth * kSubWinWidthPercent;
            rtcSubWinSize_y = (float)bufferHeight * kSubWinHeightPercent;

            LangRTCVideoSession.convertToOpenglesVertexCoordinates(
                    rtcSubWinOffset_x,
                    rtcSubWinOffset_y,
                    rtcSubWinSize_x,
                    rtcSubWinSize_y,
                    bufferWidth,
                    bufferHeight,
                    mVertices);
            LangRTCVideoSession.flipVertexCoordinates(mVerticesFlip, mVertices);

            mGLRtcLocalCubeBuffer.put(mVerticesFlip).position(0);

            GLES20.glViewport(0, 0, bufferWidth, bufferHeight);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mMixGraphicBuffer.framebufferId());
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

            mTex2dProgram.draw(mGLRtcLocalCubeBuffer, mGLRtcLocalTextureBuffer, srcTextureId);

            // draw remote canvas to framebuffer
            for (int i = 1; i < users; i++) {
                LangRTCVideoSession session = rtcSessions.get(i);
                if (!session.texturesMatch()) {
                    session.destoryYUVTextures();
                    session.generateYUVTextures();
                }

                session.updateYUVTextures();

                rtcSubWinOffset_x = (float)bufferWidth * kSubWinWidthPercent;
                rtcSubWinOffset_y = (float)bufferHeight * kTopOffsetPercent;
                rtcSubWinSize_x =  (float)bufferWidth * kSubWinWidthPercent;
                rtcSubWinSize_y = (float)bufferHeight * kSubWinHeightPercent;

                LangRTCVideoSession.convertToOpenglesVertexCoordinates(
                        rtcSubWinOffset_x,
                        rtcSubWinOffset_y,
                        rtcSubWinSize_x,
                        rtcSubWinSize_y,
                        bufferWidth,
                        bufferHeight,
                        mVertices);
                LangRTCVideoSession.flipVertexCoordinates(mVerticesFlip, mVertices);

                mGLRtcLocalCubeBuffer.put(mVerticesFlip).position(0);

                FloatBuffer glTextureCoordBuffer = session.getTextureCoordFloatBuffer();
                mCCPrograme.draw(mGLRtcLocalCubeBuffer, glTextureCoordBuffer,
                        session.yTextureId(), session.uTextureId(), session.vTextureId());
            }
        } else {
            float kTopOffsetPercent = rtcDisplayParams.topOffsetPercent;//0.10f;
            float kSubWinWidthPercent = rtcDisplayParams.subWidthPercentAboveTwoWins;//0.333f;
            float kSubWinHeightPercent = rtcDisplayParams.subHeightPercentAboveTwoWins;//0.333f;
            // draw local canvas to framebuffer.
            rtcSubWinOffset_x = 0;
            rtcSubWinOffset_y = (float)bufferHeight * kTopOffsetPercent;
            rtcSubWinSize_x =  (float)bufferWidth * kSubWinWidthPercent;
            rtcSubWinSize_y = (float)bufferHeight * kSubWinHeightPercent;

            LangRTCVideoSession.convertToOpenglesVertexCoordinates(
                    rtcSubWinOffset_x,
                    rtcSubWinOffset_y,
                    rtcSubWinSize_x,
                    rtcSubWinSize_y,
                    bufferWidth,
                    bufferHeight,
                    mVertices);
            LangRTCVideoSession.flipVertexCoordinates(mVerticesFlip, mVertices);

            mGLRtcLocalCubeBuffer.put(mVerticesFlip).position(0);

            GLES20.glViewport(0, 0, bufferWidth, bufferHeight);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mMixGraphicBuffer.framebufferId());
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

            mTex2dProgram.draw(mGLRtcLocalCubeBuffer, mGLRtcLocalTextureBuffer, srcTextureId);

            // draw remote canvas to framebuffer
            for (int i = 1; i < users; i++) {
                LangRTCVideoSession session = rtcSessions.get(i);
                if (!session.texturesMatch()) {
                    session.destoryYUVTextures();
                    session.generateYUVTextures();
                }

                session.updateYUVTextures();

                int indexColumn = i % 3;
                int indexRow = i / 3;

                rtcSubWinOffset_x = (float)bufferWidth * kSubWinWidthPercent * indexColumn;
                rtcSubWinOffset_y = (float)bufferHeight * kTopOffsetPercent * indexRow + bufferHeight * kTopOffsetPercent;
                rtcSubWinSize_x =  (float)bufferWidth * kSubWinWidthPercent;
                rtcSubWinSize_y = (float)bufferHeight * kSubWinHeightPercent;


                LangRTCVideoSession.convertToOpenglesVertexCoordinates(
                        rtcSubWinOffset_x,
                        rtcSubWinOffset_y,
                        rtcSubWinSize_x,
                        rtcSubWinSize_y,
                        bufferWidth,
                        bufferHeight,
                        mVertices);
                LangRTCVideoSession.flipVertexCoordinates(mVerticesFlip, mVertices);

                mGLRtcLocalCubeBuffer.put(mVerticesFlip).position(0);

                FloatBuffer glTextureCoordBuffer = session.getTextureCoordFloatBuffer();
                mCCPrograme.draw(mGLRtcLocalCubeBuffer, glTextureCoordBuffer,
                        session.yTextureId(), session.uTextureId(), session.vTextureId());
            }
        }

        return true;
    }

    /*
     * called in opengl-es context thread
     */
    public void setupLocalRTCGLContext(Context context, LangRtcConfiguration rtcConfig) {
        if (mGraphicBufferFrameBufferID == null) {
            mGraphicBufferFrameBufferID = new int[2];
            GLES20.glGenFramebuffers(2, mGraphicBufferFrameBufferID, 0);
        }

        if (mPushGraphicBuffer == null) {
            int rtcPushWidth = 0, rtcPushHeight = 0;
            if (rtcConfig.getRtcVideoProfile() == LangRtcConfig.RTC_VIDEO_PROFILE_120P) {
                rtcPushWidth = 120;
                rtcPushHeight = 160;
            } else if (rtcConfig.getRtcVideoProfile() == LangRtcConfig.RTC_VIDEO_PROFILE_180P) {
                rtcPushWidth = 180;
                rtcPushHeight = 360;
            } else if (rtcConfig.getRtcVideoProfile() == LangRtcConfig.RTC_VIDEO_PROFILE_240P) {
                rtcPushWidth = 240;
                rtcPushHeight = 320;
            } else if (rtcConfig.getRtcVideoProfile() == LangRtcConfig.RTC_VIDEO_PROFILE_360P) {
                rtcPushWidth = 360;
                rtcPushHeight = 640;
            } else if (rtcConfig.getRtcVideoProfile() == LangRtcConfig.RTC_VIDEO_PROFILE_480P) {
                rtcPushWidth = 480;
                rtcPushHeight = 640;
            } else if (rtcConfig.getRtcVideoProfile() == LangRtcConfig.RTC_VIDEO_PROFILE_720P) {
                rtcPushWidth = 720;
                rtcPushHeight = 1280;
            }
            mPushGraphicBuffer = GraphicBufferWrapper.createInstance(context,
                    rtcPushWidth, rtcPushHeight, PixelFormat.RGBA_8888);
            mPushGraphicBuffer.createTexture(mGraphicBufferFrameBufferID[0]);
            if (mPushGraphicBuffer.textureId() <= 0) {
                throw new RuntimeException("rtc: create push shared texture failed in GraphicBuffer");
            }
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                    GLES20.GL_TEXTURE_2D, mPushGraphicBuffer.textureId(), 0);
        }
        if (mMixGraphicBuffer == null) {
            mMixGraphicBuffer = GraphicBufferWrapper.createInstance(context,
                    rtcConfig.getVideoWidth(), rtcConfig.getVideoHeight(), PixelFormat.RGBA_8888);
            mMixGraphicBuffer.createTexture(mGraphicBufferFrameBufferID[1]);
            if (mMixGraphicBuffer.textureId() <= 0) {
                throw new RuntimeException("rtc: create mix shared texture failed in GraphicBuffer");
            }
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                    GLES20.GL_TEXTURE_2D, mMixGraphicBuffer.textureId(), 0);
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        if (mTex2dProgram == null) {
            mTex2dProgram = new LangTexture2dProgram();
        }
        if (mCCPrograme == null) {
            mCCPrograme = new LangYuv2RgbProgram();
        }
    }

    /*
     * called in opengl-es context thread
     */
    public void cleanupLocalRTCGLContext() {
        if (mPushGraphicBuffer != null) {
            mPushGraphicBuffer.destroy();
            mPushGraphicBuffer = null;
        }
        if (mMixGraphicBuffer != null) {
            mMixGraphicBuffer.destroy();
            mMixGraphicBuffer = null;
        }
        if (mTex2dProgram != null) {
            mTex2dProgram.release();
            mTex2dProgram = null;
        }
        if (mCCPrograme != null) {
            mCCPrograme.release();
            mCCPrograme = null;
        }
        if (mGraphicBufferFrameBufferID != null) {
            GLES20.glDeleteFramebuffers(2, mGraphicBufferFrameBufferID, 0);
            mGraphicBufferFrameBufferID = null;
        }
    }
}
