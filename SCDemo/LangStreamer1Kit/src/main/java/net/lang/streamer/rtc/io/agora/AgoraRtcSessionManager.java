package net.lang.streamer.rtc.io.agora;

import android.content.Context;
import android.graphics.PixelFormat;
import android.opengl.GLES20;
import android.view.SurfaceView;
import android.opengl.EGLContext;
import android.util.Log;

import com.yunfan.graphicbuffer.GraphicBufferWrapper;

import net.lang.gpuimage.filter.base.gpuimage.GPUImageFilter;
import net.lang.gpuimage.utils.*;

import net.lang.streamer.engine.data.LangEngineParams;
import net.lang.streamer.rtc.io.agora.ex.MediaDataAudioObserver;
import net.lang.streamer.rtc.io.agora.ex.MediaDataObserverPlugin;
import net.lang.streamer.rtc.io.agora.ex.MediaDataVideoObserver;
import net.lang.streamer.rtc.io.agora.ex.MediaPreProcessing;
import net.lang.streamer.LangRtcInfo;
import net.lang.streamer.config.LangRtcConfig;
import net.lang.streamer.engine.LangMagicEngine;
import net.lang.streamer.engine.LangAudioEncoder;
import net.lang.streamer.engine.LangMediaPublisher;
import net.lang.streamer.rtc.IRtcSession;
import net.lang.streamer.rtc.LangRtcMessageHandler;
import net.lang.streamer.rtc.io.agora.avcapture.model.*;
import net.lang.streamer.rtc.IRtcSessionManager;
import net.lang.streamer.rtc.io.agora.ex.IAgoraNativeDataHandler;
import net.lang.streamer.utils.DebugLog;
import net.lang.streamer.video.LangTextureMovieEncoder;
import net.lang.streamer.video.gles.GlUtil;
import net.lang.streamer.video.gles.Yuv2RgbProgram;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import io.agora.rtc.Constants;
import io.agora.rtc.IRtcEngineEventHandler;
import io.agora.rtc.RtcEngine;
import io.agora.rtc.live.LiveTranscoding;
import io.agora.rtc.video.AgoraVideoFrame;
import io.agora.rtc.video.VideoCanvas;

/**
 * Created by lang on 2017/9/4.
 */

public class AgoraRtcSessionManager extends IRtcSessionManager
        implements IAgoraNativeDataHandler, AGEventHandler, MediaDataAudioObserver, MediaDataVideoObserver {
    private static final String TAG = AgoraRtcSessionManager.class.getSimpleName();

    private static final int STATE_UNINITIALIZED = 0;
    private static final int STATE_INITIALIZED   = 1;

    private static final int AGRTCSTATE_DID_STOPPED  = 2;
    private static final int AGRTCSTATE_WILL_STOPPED = 3;
    private static final int AGRTCSTATE_DID_ACTIVE   = 4;
    private static final int AGRTCSTATE_WILL_ACTIVE  = 5;

    private int mState = STATE_UNINITIALIZED;
    private int mRtcState = AGRTCSTATE_DID_STOPPED;

    private Context mContext;
    private LangMagicEngine mEngine;

    private LangRtcConfig mRtcConfig;
    private LangRtcConfig.RtcDisplayParams mRtcDisplayParams;

    private LangRtcMessageHandler mHandler;

    private List<AgoraRtcSession> mRtcSessions = null;
//    private AudioVideoPreProcessing mPreProcessing = null;//new AudioVideoPreProcessing();

    private boolean mIsRtmpStreaming = false;

    private boolean mVideoMuted = false;
    private boolean mAudioMuted = false;

    private final Object mRtcFence = new Object();
    private boolean mRtcActive = false;

    private WorkerThread mWorkerThread;

    private GraphicBufferWrapper mGraphicBuffer = null;
    private int[] mGraphicBufferFrameBufferID = null;
    private int mGraphicBufferTexID = -1;

    private Yuv2RgbProgram mCCPrograme = null;

    protected FloatBuffer mGLCubeBuffer;
    protected FloatBuffer mGLTextureBuffer;

    protected FloatBuffer mGLRtcLocalCubeBuffer;
    protected FloatBuffer mGLRtcLocalTextureBuffer;

    //private byte[] mLastFrameRef = null;

    public AgoraRtcSessionManager(LangRtcMessageHandler handler, Context context, LangMagicEngine engine) {
        mRtcState = AGRTCSTATE_DID_STOPPED;
        if (handler == null) {
            throw new IllegalArgumentException("should pass a valid LangRtcMessageHandler instance first");
        }
        if (context == null) {
            throw new IllegalArgumentException("should pass a valid Context instance first");
        }
        //if (engine == null) {
        //    throw new IllegalArgumentException("should pass a valid LangMagicEngine instance first");
        //}

        mHandler = handler;
        mContext = context;
        mEngine = engine;

        mRtcConfig = null;
        mRtcDisplayParams = new LangRtcConfig.RtcDisplayParams();
        mRtcSessions = new java.util.ArrayList<>();

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

//        mPreProcessing = new AudioVideoPreProcessing();

        initWorkerThread();

        mState = STATE_INITIALIZED;
    }

    @Override
    public SurfaceView createRtcRenderView() {
        SurfaceView surfaceV = RtcEngine.CreateRendererView(mContext);
        return surfaceV;
    }

    @Override
    public void setDisplayParamsWhenStreaming(LangRtcConfig.RtcDisplayParams displayParams) {
        if (mState != STATE_INITIALIZED) {
            throw new IllegalStateException("joinChannel() called on an "
                    + "uninitialized AgoraRtcSessionManager.");
        }

        synchronized (mRtcFence) {
            // now check rtc display layout params.
            if (displayParams.topOffsetPercent + displayParams.subHeightPercentOnTwoWins > 0.7f) {
                DebugLog.w(TAG, "setDisplayParamsWhenStreaming() watch out for LangRtcConfig.RtcDisplayParams: " +
                        " topOffsetPercent " + displayParams.topOffsetPercent +
                        " subHeightPercentOnTwoWins " + displayParams.subHeightPercentOnTwoWins);
            }
            if (displayParams.topOffsetPercent + displayParams.subHeightPercentAboveTwoWins * 2 > 0.9f) {
                DebugLog.w(TAG, "setDisplayParamsWhenStreaming() watch out for LangRtcConfig.RtcDisplayParams: " +
                        " topOffsetPercent " + displayParams.topOffsetPercent +
                        " subHeightPercentAboveTwoWins " + displayParams.subHeightPercentOnTwoWins);
            }

            mRtcDisplayParams = displayParams.dup();
        }
    }

    @Override
    public boolean joinChannel(LangRtcConfig config, SurfaceView localView) {
        if (mState != STATE_INITIALIZED) {
            throw new IllegalStateException("joinChannel() called on an "
                    + "uninitialized AgoraRtcSessionManager.");
        }

        mRtcConfig = config; // Todo: deep copy class
        synchronized (mRtcFence) {
            if (mRtcState != AGRTCSTATE_DID_STOPPED) {
                DebugLog.w(TAG, "joinChannel() called in invalid state: " + rtsStatusString());
                return false;
            }
            joinChannel_l(mRtcConfig, localView);
        }

        return true;
    }

    @Override
    public void leaveChannel() {
        if (mState != STATE_INITIALIZED) {
            throw new IllegalStateException("leaveChannel() called " +
                    "in invalid state STATE_UNINITIALIZED");
        }

        synchronized (mRtcFence) {
            if (mRtcState != AGRTCSTATE_DID_ACTIVE && mRtcState != AGRTCSTATE_WILL_ACTIVE) {
                DebugLog.w(TAG, "leaveChannel() called in invalid state: " + rtsStatusString());
                return;
            }

            //if (mRtcState == AGRTCSTATE_DID_ACTIVE) {
                leaveChannel_l();
            //}
        }
    }

    @Override
    public boolean setVoiceChat(boolean voiceOnly) {
        if (mState != STATE_INITIALIZED) {
            throw new IllegalStateException("setVoiceChat() called " +
                    "in invalid state STATE_UNINITIALIZED");
        }

        synchronized (mRtcFence) {
            if (mRtcState != AGRTCSTATE_DID_ACTIVE) {
                DebugLog.w(TAG, "setVoiceChat() called in invalid state: " + rtsStatusString());
                return false;
            }

            setVoiceChat_l(voiceOnly);
        }

        return true;
    }

    @Override
    public boolean muteLocalVoice(boolean mute) {
        if (mState != STATE_INITIALIZED) {
            throw new IllegalStateException("muteLocalVoice() called " +
                    "in invalid state STATE_UNINITIALIZED");
        }

        synchronized (mRtcFence) {
            if (mRtcState != AGRTCSTATE_DID_ACTIVE) {
                DebugLog.w(TAG, "muteLocalVoice() called in invalid state: " + rtsStatusString());
                return false;
            }

            muteLocalVoice_l(mute);
        }

        return true;
    }

    @Override
    public boolean muteRemoteVoice(final int uid, boolean mute) {
        if (mState != STATE_INITIALIZED) {
            throw new IllegalStateException("muteRemoteVoice() called" +
                    "in invalid state STATE_UNINITIALIZED");
        }

        boolean muted = true;
        synchronized (mRtcFence) {
            if (mRtcState != AGRTCSTATE_DID_ACTIVE) {
                DebugLog.w(TAG, "muteRemoteVoice() called in invalid state: " + rtsStatusString());
                return false;
            }

            int result = muteRemoteVoice_l(uid, mute);
            if (result != 0) {
                DebugLog.w(TAG, "muteRemoteVoice_l() failed: uid = " + uid);
                muted = false;
            }
        }

        return muted;
    }

    @Override
    public boolean setupRemoteUser(final int uid, SurfaceView remoteView) {
        if (mState != STATE_INITIALIZED) {
            throw new IllegalStateException("setupRemoteUser() called " +
                    "in invalid state STATE_UNINITIALIZED");
        }

        synchronized (mRtcFence) {
            if (mRtcState != AGRTCSTATE_DID_ACTIVE) {
                DebugLog.w(TAG, "setupRemoteUser() called in invalid state: " + rtsStatusString());
                return false;
            }

            setupRemoteUser_l(uid, remoteView);
        }

        return true;
    }

    @Override
    public boolean pushExternalVideoFrame(EGLContext eglContext, int textureId, int stride, int height, long timestamp) {
        AgoraVideoFrame vf = new AgoraVideoFrame();
        vf.format = AgoraVideoFrame.FORMAT_TEXTURE_2D;
        vf.timeStamp = timestamp;
        vf.stride = stride;
        vf.height = height;
        vf.textureID = textureId;
        vf.syncMode = true;
        vf.eglContext14 = eglContext;
        vf.transform = new float[]{
                1.0f, 0.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f, 0.0f,
                0.0f, 0.0f, 1.0f, 0.0f,
                0.0f, 0.0f, 0.0f, 1.0f
        };
        boolean result = false;
        if (mRtcActive) {
            if (rtcEngine() != null) {
                result = rtcEngine().pushExternalVideoFrame(vf);
                Log.i(TAG, "pushExternalVideoFrame() " +
                        " eglContext: " + eglContext +
                        " textureId: " + textureId +
                        " stride: " + stride +
                        " height: " + height +
                        " timestamp: " + timestamp +
                        " result: " + result);
            }
        }
        return result;
    }

    @Override
    public boolean pushExternalVideoFrame(GPUImageFilter filter, int texId, long timestamp) {
        if (mState != STATE_INITIALIZED) {
            throw new IllegalStateException("pushExternalVideoFrame() called in invalid state STATE_UNINITIALIZED");
        }

        synchronized (mRtcFence) {
            if (mRtcState != AGRTCSTATE_DID_ACTIVE) {
                return false;
            }

            if (mGraphicBuffer == null) {
                int rtcPushWidth = 360;
                int rtcPushHeight = 640;

                if (mRtcConfig != null) {
                    if (mRtcConfig.videoProfile == Constants.VIDEO_PROFILE_120P) {
                        rtcPushWidth = 120;
                        rtcPushHeight = 160;
                    } else if (mRtcConfig.videoProfile == Constants.VIDEO_PROFILE_180P) {
                        rtcPushWidth = 180;
                        rtcPushHeight = 360;
                    } else if (mRtcConfig.videoProfile == Constants.VIDEO_PROFILE_240P) {
                        rtcPushWidth = 240;
                        rtcPushHeight = 320;
                    } else if (mRtcConfig.videoProfile == Constants.VIDEO_PROFILE_360P) {
                        rtcPushWidth = 360;
                        rtcPushHeight = 640;
                    } else if (mRtcConfig.videoProfile == Constants.VIDEO_PROFILE_480P) {
                        rtcPushWidth = 480;
                        rtcPushHeight = 640;
                    } else if (mRtcConfig.videoProfile == Constants.VIDEO_PROFILE_720P) {
                        rtcPushWidth = 720;
                        rtcPushHeight = 1280;
                    }
                }

                setupLocalRtcGLContext(rtcPushWidth, rtcPushHeight);
            }

            if (mGraphicBuffer != null) {

                if (mGraphicBuffer.tryLockGB()) {
                    int gbWidth = mGraphicBuffer.width();
                    int gbHeight = mGraphicBuffer.height();


                    GLES20.glViewport(0, 0, gbWidth, gbHeight);
                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mGraphicBufferFrameBufferID[0]);
                    GlUtil.checkGlError("glBindFramebuffer");

                    filter.onDrawFrame(texId, mGLCubeBuffer, mGLTextureBuffer);
                    GlUtil.checkGlError("onDrawFrame");

                    getWorkerThread().pushGraphicBuffer(mGraphicBuffer, timestamp);
                /*
                AgoraVideoFrame currentFrame = getWorkerThread().getLocalRtcPushBuffer();
                if (currentFrame != null) {
                    int bufLength = mGraphicBuffer.lockAndCopySafe(currentFrame.buf);
                    mGraphicBuffer.unlock();
                    currentFrame.stride = gbWidth;
                    currentFrame.height = gbHeight;
                    currentFrame.timeStamp = timestamp;
                    getWorkerThread().pushRawBuffer(currentFrame);
                }
                */
                    mGraphicBuffer.unlockGB();
                } else {
                    Log.w(TAG, "pushExternalVideoFrame graphic buffer is busy!");
                }
            }
        }

        return true;
    }

    @Override
    public boolean mixLocalWithRemoteVideoFrame(GPUImageFilter filter, int texId, GraphicBufferWrapper gb, LangTextureMovieEncoder encoder, long timestamp) {

        if (mState != STATE_INITIALIZED) {
            throw new IllegalStateException("pushExternalVideoFrame() called in invalid state STATE_UNINITIALIZED");
        }

        synchronized (mRtcFence) {
            if (mRtcState != AGRTCSTATE_DID_ACTIVE) {
                return false;
            }

            if (!LangRtcConfig.localMixed) {
                return false;
            }

            int bufferWidth = gb.width();
            int bufferHeight = gb.height();

            float rtcSubWinOffset_x, rtcSubWinOffset_y;
            float rtcSubWinSize_x, rtcSubWinSize_y;

            float[] glViewVertices = new float[8];
            float[] glViewVerticesFlip = new float[8];

            int users = mRtcSessions.size();
            if (users <= 2) {

                float kTopOffsetPercent = mRtcDisplayParams.topOffsetPercent;//0.10f;
                float kSubWinWidthPercent = mRtcDisplayParams.subWidthPercentOnTwoWins;//0.5f;
                float kSubWinHeightPercent = mRtcDisplayParams.subHeightPercentOnTwoWins;//0.5f;

                // draw local canvas to framebuffer.
                rtcSubWinOffset_x = 0;
                rtcSubWinOffset_y = (float)bufferHeight * kTopOffsetPercent;
                rtcSubWinSize_x =  (float)bufferWidth * kSubWinWidthPercent;
                rtcSubWinSize_y = (float)bufferHeight * kSubWinHeightPercent;

                IRtcSession.convertToOpenglesVertexCoordinates(
                        rtcSubWinOffset_x,
                        rtcSubWinOffset_y,
                        rtcSubWinSize_x,
                        rtcSubWinSize_y,
                        bufferWidth,
                        bufferHeight,
                        glViewVertices);
                IRtcSession.flipVertexCoordinates(glViewVerticesFlip, glViewVertices);

                mGLRtcLocalCubeBuffer.put(glViewVerticesFlip).position(0);

                GLES20.glViewport(0, 0, bufferWidth, bufferHeight);
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, gb.framebufferId());
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

                filter.onDrawFrame(texId, mGLRtcLocalCubeBuffer, mGLRtcLocalTextureBuffer);

                // draw remote canvas to framebuffer
                for (int i = 1; i < users; i++) {
                    AgoraRtcSession session = mRtcSessions.get(i);
                    if (!session.texturesMatch()) {
                        session.destoryYUVTextures();
                        session.generateYUVTextures();
                    }

                    session.updateYUVTextures();

                    rtcSubWinOffset_x = (float)bufferWidth * kSubWinWidthPercent;
                    rtcSubWinOffset_y = (float)bufferHeight * kTopOffsetPercent;
                    rtcSubWinSize_x =  (float)bufferWidth * kSubWinWidthPercent;
                    rtcSubWinSize_y = (float)bufferHeight * kSubWinHeightPercent;

                    IRtcSession.convertToOpenglesVertexCoordinates(
                            rtcSubWinOffset_x,
                            rtcSubWinOffset_y,
                            rtcSubWinSize_x,
                            rtcSubWinSize_y,
                            bufferWidth,
                            bufferHeight,
                            glViewVertices);
                    IRtcSession.flipVertexCoordinates(glViewVerticesFlip, glViewVertices);

                    mGLRtcLocalCubeBuffer.put(glViewVerticesFlip).position(0);

                    FloatBuffer glTextureCoordBuffer = session.getTextureCoordFloatBuffer();
                    mCCPrograme.draw(mGLRtcLocalCubeBuffer, glTextureCoordBuffer, 0, 1, 2);
                }

                encoder.tryreadFrameFBO(gb, timestamp);

            } else {
                float kTopOffsetPercent = mRtcDisplayParams.topOffsetPercent;//0.10f;
                float kSubWinWidthPercent = mRtcDisplayParams.subWidthPercentAboveTwoWins;//0.333f;
                float kSubWinHeightPercent = mRtcDisplayParams.subHeightPercentAboveTwoWins;//0.333f;

                // draw local canvas to framebuffer.
                rtcSubWinOffset_x = 0;
                rtcSubWinOffset_y = (float)bufferHeight * kTopOffsetPercent;
                rtcSubWinSize_x =  (float)bufferWidth * kSubWinWidthPercent;
                rtcSubWinSize_y = (float)bufferHeight * kSubWinHeightPercent;

                IRtcSession.convertToOpenglesVertexCoordinates(
                        rtcSubWinOffset_x,
                        rtcSubWinOffset_y,
                        rtcSubWinSize_x,
                        rtcSubWinSize_y,
                        bufferWidth,
                        bufferHeight,
                        glViewVertices);
                IRtcSession.flipVertexCoordinates(glViewVerticesFlip, glViewVertices);

                mGLRtcLocalCubeBuffer.put(glViewVerticesFlip).position(0);

                GLES20.glViewport(0, 0, bufferWidth, bufferHeight);
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, gb.framebufferId());
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

                filter.onDrawFrame(texId, mGLRtcLocalCubeBuffer, mGLRtcLocalTextureBuffer);

                // draw remote canvas to framebuffer
                for (int i = 1; i < users; i++) {
                    AgoraRtcSession session = mRtcSessions.get(i);
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


                    IRtcSession.convertToOpenglesVertexCoordinates(
                            rtcSubWinOffset_x,
                            rtcSubWinOffset_y,
                            rtcSubWinSize_x,
                            rtcSubWinSize_y,
                            bufferWidth,
                            bufferHeight,
                            glViewVertices);
                    IRtcSession.flipVertexCoordinates(glViewVerticesFlip, glViewVertices);

                    mGLRtcLocalCubeBuffer.put(glViewVerticesFlip).position(0);

                    FloatBuffer glTextureCoordBuffer = session.getTextureCoordFloatBuffer();
                    mCCPrograme.draw(mGLRtcLocalCubeBuffer, glTextureCoordBuffer, 0, 1, 2);
                }

                encoder.tryreadFrameFBO(gb, timestamp);
            }
        }

        return true;
    }

    @Override
    public void changeRtmpStatus(boolean isStreaming) {
        synchronized (mRtcFence) {
            if (mIsRtmpStreaming != isStreaming) {
                mIsRtmpStreaming = isStreaming;
            }
        }
    }

    @Override
    public void release() {
        if (mState != STATE_INITIALIZED) {
            throw new IllegalStateException("release() called on an " +
                    "uninitialized AgoraRtcSessionManager.");
        }

        if (mRtcState == AGRTCSTATE_DID_ACTIVE) {
            Log.w(TAG, "leave channel before release");
            mIsRtmpStreaming = false;
            leaveChannel_l();
        }

        synchronized (mRtcFence) {
            if (mRtcState == AGRTCSTATE_WILL_STOPPED) {
                try {
                    Log.w(TAG, "wait leave signal");
                    mRtcFence.wait(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            Log.w(TAG, "deinit worker thread");
            deInitWorkerThread();
        }

        mHandler.removeCallbacksAndMessages(null);
        mHandler = null;

        mContext = null;
        mEngine = null;
//        mPreProcessing = null;

        mState = STATE_UNINITIALIZED;
    }

    // implements IAgoraNativeDataHandler
    @Override
    public void onGetMixedPCMData(final byte[] pcm) {

        synchronized (mRtcFence) {
            if (mIsRtmpStreaming && mRtcState == AGRTCSTATE_DID_ACTIVE) {
                LangMediaPublisher publisher = mEngine.getMediaPublisher();
                LangAudioEncoder audioEncoder = publisher.getAudioEncoder();
                if (audioEncoder != null && publisher.isAudioStart()) {
                    if (pcm == null || pcm.length < 1024) {
                        DebugLog.w(TAG, "onGetMixedPCMData audio buffer invalid");
                        return;
                    }
                    audioEncoder.onGetPcmFrame(pcm, pcm.length);
                }
            }
        }

    }

    @Override
    public void onGetLocalYUVData(final byte[] yuv, final int width, final int height) {

    }

    @Override
    public void onGetRemoteYUVData(final int pid, final byte[] yuv, final int width, final int height, final int rotation) {
        Log.d(TAG, "onGetRemoteYUVData(): pid: " + pid + " width: " + width + " height: " + height + "rotation: " + rotation);

        synchronized (mRtcFence) {
            if (mRtcState == AGRTCSTATE_DID_ACTIVE) {
                for (int i = 0; i < mRtcSessions.size(); i++) {
                    AgoraRtcSession rtcSession = mRtcSessions.get(i);
                    if (pid == rtcSession.uid()) {
                        rtcSession.sendYUVData(yuv, width, height);
                    }
                }
            }
        }
    }

    // implements AGEventHandler
    @Override
    public void onJoinChannelSuccess(String channel, final int uid, int elapsed) {
        Log.i(TAG, "onJoinChannelSuccess(): local uid = " + uid);

        synchronized (mRtcFence) {
            onJoinChannelSuccess_l(channel, uid, elapsed);
        }
    }

    @Override
    public void onRejoinChannelSuccess(String channel, final int uid, int elapsed) {
        Log.i(TAG, "onRejoinChannelSuccess(): local uid = " + uid);

        synchronized (mRtcFence) {
            onRejoinChannelSuccess_l(channel, uid, elapsed);
        }
    }

    @Override
    public void onLeaveChannel() {
        Log.i(TAG, "onLeaveChannel()");

        synchronized (mRtcFence) {
            onLeaveChannel_l();
        }
    }

    @Override
    public void onUserJoined(int uid, int elapsed) {
        Log.i(TAG, "onUserJoined(): uid = " + uid);

        synchronized (mRtcFence) {
            onUserJoined_l(uid, elapsed);
        }
    }

    @Override
    public void onFirstRemoteVideoDecoded(int uid, int width, int height, int elapsed) {
        Log.i(TAG, "onFirstRemoteVideoDecoded(): uid = " + uid + " width = " + width + " height = " + height);

        synchronized (mRtcFence) {
            onFirstRemoteVideoDecoded_l(uid, width, height, elapsed);
        }
    }

    @Override
    public void onUserOffline(int uid, int reason) {
        Log.i(TAG, "onUserOffline(): uid =" + uid);

        synchronized (mRtcFence) {
            onUserOffline_l(uid, reason);
        }
    }

    @Override
    public void onExtraCallback(final int type, final Object... data) {
        int peerUid;
        boolean muted;

        synchronized (mRtcFence) {
            switch (type) {
                case AGEventHandler.EVENT_TYPE_ON_USER_AUDIO_MUTED: {
                    peerUid = (Integer) data[0];
                    muted = (boolean) data[1];
                    onRemoteUserAudioMuted_l(peerUid, muted);
                    break;
                }
                case AGEventHandler.EVENT_TYPE_ON_USER_VIDEO_MUTED: {
                    peerUid = (Integer) data[0];
                    muted = (boolean) data[1];
                    onRemoteUserVideoMuted_l(peerUid, muted);
                    break;
                }
                case AGEventHandler.EVENT_TYPE_ON_APP_ERROR: {
                    int subType = (int) data[0];
                    //if (subType == ConstantApp.AppError.NO_NETWORK_CONNECTION) {
                    //    onRtcConnectionError_l();
                    //}
                    if (subType == ConstantApp.AppError.NETWORK_CONNECTION_LOST) {
                        onRtcConnectionLost_l();
                    } else if (subType == ConstantApp.AppError.NETWORK_CONNECTION_TIMEOUT) {
                        onRtcConnectionTimeout_l();
                    }
                    break;
                }
                case AGEventHandler.EVENT_TYPE_ON_RTC_STATS: {
                    IRtcEngineEventHandler.RtcStats stats = (IRtcEngineEventHandler.RtcStats)data[0];
                    onRtcStats_l(stats);
                    break;
                }
                case AGEventHandler.EVENT_TYPE_ON_AGORA_MEDIA_ERROR: {
                    int error = (int) data[0];
                    String description = (String) data[1];
                    onRtcFatalError_l(error, description);
                    break;
                }
                case AGEventHandler.EVENT_TYPE_ON_AUDIO_ROUTE_CHANGED: {
                    onRtcLocalAudioRouteChanged_l((int) data[0]);
                    break;
                }
            }
        }
    }

    //
    // internal API implementation.
    //
    private void joinChannel_l(LangRtcConfig config, SurfaceView localView) {

        getWorkerThread().eventHandler().addEventHandler(this);
        doConfigEngine(config.videoProfile, config.encryptionKey, config.encryptionMode);

        rtcEngine().setAudioProfile(Constants.AUDIO_PROFILE_MUSIC_HIGH_QUALITY, Constants.AUDIO_SCENARIO_GAME_STREAMING);
        // High quality audio parameters
        rtcEngine().setParameters("{\"che.audio.specify.codec\":\"HEAAC_2ch\"}");
        // Enable stereo
        rtcEngine().setParameters("{\"che.audio.stereo\":true}");

        rtcEngine().setRecordingAudioFrameParameters(config.audioSampleRate, config.audioChannel, 0, 1024);
        rtcEngine().setPlaybackAudioFrameParameters(config.audioSampleRate, config.audioChannel, 0, 1024);
        rtcEngine().setMixedAudioFrameParameters(config.audioSampleRate, 1024);

        getWorkerThread().preview(true, localView, 0);
        getWorkerThread().joinChannel(config.channelName, 0, config.audience);

        if (LangRtcConfig.localMixed && !config.audience) {
//            mPreProcessing.setNativeDataHandler(this);
//            mPreProcessing.registerPreProcessing();
            registerRtcMediaDataObserver();
        }

            mRtcState = AGRTCSTATE_WILL_ACTIVE;
    }


    MediaDataObserverPlugin mediaDataObserverPlugin;
    private void registerRtcMediaDataObserver() {
        mediaDataObserverPlugin = MediaDataObserverPlugin.the();
        MediaPreProcessing.setCallback(mediaDataObserverPlugin);
//        MediaPreProcessing.setVideoCaptureByteBuffer(mediaDataObserverPlugin.byteBufferCapture);
        MediaPreProcessing.setAudioRecordByteBuffer(mediaDataObserverPlugin.byteBufferAudioRecord);
//        MediaPreProcessing.setAudioPlayByteBuffer(mediaDataObserverPlugin.byteBufferAudioPlay);
//        MediaPreProcessing.setBeforeAudioMixByteBuffer(mediaDataObserverPlugin.byteBufferBeforeAudioMix);
        MediaPreProcessing.setAudioMixByteBuffer(mediaDataObserverPlugin.byteBufferAudioMix);

        mediaDataObserverPlugin.addVideoObserver(this);
        mediaDataObserverPlugin.addAudioObserver(this);
    }


    private void unregisterRtcMediaDataObserver() {
        if (mediaDataObserverPlugin != null) {
            mediaDataObserverPlugin.removeAudioObserver(this);
            mediaDataObserverPlugin.removeVideoObserver(this);
            mediaDataObserverPlugin.removeAllBuffer();
        }
        MediaPreProcessing.releasePoint();
    }

    @Override
    public void onRecordAudioFrame(byte[] data, int audioFrameType, int samples, int bytesPerSample, int channels, int samplesPerSec, long renderTimeMs, int bufferLength) {

    }

    @Override
    public void onPlaybackAudioFrame(byte[] data, int audioFrameType, int samples, int bytesPerSample, int channels, int samplesPerSec, long renderTimeMs, int bufferLength) {

    }

    @Override
    public void onPlaybackAudioFrameBeforeMixing(int uid, byte[] data, int audioFrameType, int samples, int bytesPerSample, int channels, int samplesPerSec, long renderTimeMs, int bufferLength) {

    }

    byte [] mStereoData = null;
    @Override
    public void onMixedAudioFrame(byte[] data, int audioFrameType, int samples, int bytesPerSample, int channels, int samplesPerSec, long renderTimeMs, int bufferLength) {

        synchronized (mRtcFence) {
            if (mIsRtmpStreaming && mRtcState == AGRTCSTATE_DID_ACTIVE) {
                LangMediaPublisher publisher = mEngine.getMediaPublisher();
                LangAudioEncoder audioEncoder = publisher.getAudioEncoder();
                if (audioEncoder != null && publisher.isAudioStart()) {
                    if (data == null || data.length < 1024) {
                        DebugLog.w(TAG, "onGetMixedPCMData audio buffer invalid");
                        return;
                    }

                    if (mRtcConfig.audioChannel != channels) {
                        if (mStereoData == null) {
                            mStereoData = new byte[bufferLength * 2];
                        }
                        for (int i = 0; i < samples; i++) {
                            mStereoData[i * 4 + 0] = data[i * 2 + 0];
                            mStereoData[i * 4 + 1] = data[i * 2 + 1];
                            mStereoData[i * 4 + 2] = data[i * 2 + 0];
                            mStereoData[i * 4 + 3] = data[i * 2 + 1];
                        }
                        audioEncoder.onGetPcmFrame(mStereoData, mStereoData.length);
                    } else {
                        audioEncoder.onGetPcmFrame(data, data.length);
                    }
                }
            }
        }
    }

    @Override
    public void onCaptureVideoFrame(byte[] data, int frameType, int width, int height, int bufferLength, int yStride, int uStride, int vStride, int rotation, long renderTimeMs) {

    }

    @Override
    public void onRenderVideoFrame(int uid, byte[] data, int frameType, int width, int height, int bufferLength, int yStride, int uStride, int vStride, int rotation, long renderTimeMs) {
        synchronized (mRtcFence) {
            if (mRtcState == AGRTCSTATE_DID_ACTIVE) {
                for (int i = 0; i < mRtcSessions.size(); i++) {
                    AgoraRtcSession rtcSession = mRtcSessions.get(i);
                    if (uid == rtcSession.uid()) {
                        rtcSession.sendYUVData(data, width, height);
                    }
                }
            }
        }
    }




    private void doConfigEngine(int vProfile, String encryptionKey, String encryptionMode) {
        getWorkerThread().configEngine(vProfile, encryptionKey, encryptionMode);
    }

    private void setupRemoteUser_l(final int uid, SurfaceView remoteView) {
        rtcEngine().setupRemoteVideo(new VideoCanvas(remoteView, VideoCanvas.RENDER_MODE_HIDDEN, uid));
    }

    private void setVoiceChat_l(boolean voiceOnly) {
        mVideoMuted = voiceOnly;
        if (mVideoMuted) {
            rtcEngine().disableVideo();
        } else {
            rtcEngine().enableVideo();
        }
    }

    private void muteLocalVoice_l(boolean mute) {
        mAudioMuted = mute;
        rtcEngine().muteLocalAudioStream(mAudioMuted);
    }

    private int muteRemoteVoice_l(final int uid, boolean mute) {
        return rtcEngine().muteRemoteAudioStream(uid, mute);
    }

    private void leaveChannel_l() {
        stopStreamIfNeed();
//        mPreProcessing.deregisterPreProcessing();
        unregisterRtcMediaDataObserver();
        getWorkerThread().leaveChannel(config().mChannel);
        getWorkerThread().preview(false, null, 0);

        mRtcState = AGRTCSTATE_WILL_STOPPED;
    }


    private void publishStreamIfNeed() {
        if (LangRtcConfig.localMixed) return;
        rtcEngine().setLiveTranscoding(updateLiveTransCoding());
        rtcEngine().addPublishStreamUrl(mRtcConfig.pushStreamUrl, true);
    }

    private void stopStreamIfNeed() {
        if (LangRtcConfig.localMixed) return;
        rtcEngine().removePublishStreamUrl(mRtcConfig.pushStreamUrl);
    }

    private LiveTranscoding updateLiveTransCoding() {
        int number = mRtcSessions.size();
        float kTopOffsetPercent = mRtcDisplayParams.topOffsetPercent;//0.10f;
        float kSubWinWidthPercent = mRtcDisplayParams.subWidthPercentOnTwoWins;//0.5f;
        float kSubWinHeightPercent = mRtcDisplayParams.subHeightPercentOnTwoWins;//0.5f;

        ArrayList<LiveTranscoding.TranscodingUser> users = new ArrayList<>(number);


        LiveTranscoding.TranscodingUser localUser = new LiveTranscoding.TranscodingUser();
        localUser.uid = mRtcSessions.get(0).uid();
        localUser.x = 0;
        localUser.y = (int) (LangEngineParams.vOutputHeight * kTopOffsetPercent);
        localUser.width = (int) (LangEngineParams.vOutputWidth * kSubWinWidthPercent);
        localUser.height = (int) (LangEngineParams.vOutputHeight * kSubWinHeightPercent);

        localUser.zOrder = 1;
        localUser.audioChannel = 0;
        users.add(localUser);


        if (number > 1) {
            LiveTranscoding.TranscodingUser remoteUser = new LiveTranscoding.TranscodingUser();

            remoteUser.uid = mRtcSessions.get(1).uid(); // REMOTE USER

            remoteUser.x = (int) (LangEngineParams.vOutputWidth * kSubWinWidthPercent);
            remoteUser.y = (int) (LangEngineParams.vOutputHeight * kTopOffsetPercent);
            remoteUser.width = (int) (LangEngineParams.vOutputWidth * kSubWinWidthPercent);
            remoteUser.height = (int) (LangEngineParams.vOutputHeight * kSubWinHeightPercent);

            remoteUser.zOrder = 2;
            remoteUser.audioChannel = 0;

            users.add(remoteUser);
        }

        LiveTranscoding liveTranscoding = new LiveTranscoding();
        liveTranscoding.setUsers(users);

        liveTranscoding.width = LangEngineParams.vOutputWidth;
        liveTranscoding.height = LangEngineParams.vOutputHeight;

        liveTranscoding.videoBitrate = mRtcConfig.videoBitrate / 1000;
        liveTranscoding.videoFramerate = mRtcConfig.videoFPS;
        liveTranscoding.lowLatency = true;
        return liveTranscoding;
    }


    //
    // Internal API callbacks implementations.
    //
    private void onJoinChannelSuccess_l(String channel, final int uid, int elapsed) {
        //synchronized (mRtcFence) {
            if (mRtcState == AGRTCSTATE_WILL_ACTIVE) {
                mRtcState = AGRTCSTATE_DID_ACTIVE;

                AgoraRtcSession localSession = new AgoraRtcSession();
                localSession.setUid(uid);
                mRtcSessions.add(localSession);

                publishStreamIfNeed();
                mHandler.notifyRtcLocalUserJoined(uid);
            } else {
                //throw new IllegalStateException("onJoinChannelSuccess_l() called in invalid state.");
                DebugLog.e(TAG, "onJoinChannelSuccess_l() called in invalid state: " + rtsStatusString());
            }
        //}
    }

    private void onRejoinChannelSuccess_l(String channel, final int uid, int elapsed) {
        //synchronized (mRtcFence) {
            if (mRtcState == AGRTCSTATE_DID_ACTIVE) {
                int local_uid = mRtcSessions.get(0).uid();
                if (local_uid != uid) {
                    DebugLog.w(TAG, "onRejoinChannelSuccess_l(): local uid change from" + "(" + local_uid + ")"
                        + " to" + "(" + uid + ")");
                    mRtcSessions.get(0).setUid(uid);
                }

                publishStreamIfNeed();
                mHandler.notifyRtcLocalUserJoined(uid);
            }
        //}
    }

    private void onUserJoined_l(int uid, int elapsed) {
        //synchronized (mRtcFence) {
            if (mRtcState == AGRTCSTATE_DID_ACTIVE) {
                boolean uidExists = false;
                for (int i = 0; i < mRtcSessions.size(); i++) {
                    if (uid == mRtcSessions.get(i).uid()) {
                        uidExists = true;
                    }
                }

                if (!uidExists) {
                    AgoraRtcSession session = new AgoraRtcSession();
                    session.setUid(uid);
                    mRtcSessions.add(session);
                }

                if (mediaDataObserverPlugin != null) {
                    mediaDataObserverPlugin.addDecodeBuffer(uid);
                }

                publishStreamIfNeed();
                mHandler.notifyRtcRemoteUserJoined(uid);
            }
        //}
    }

    private void onFirstRemoteVideoDecoded_l(int uid, int width, int height, int elapsed) {
        //synchronized (mRtcFence) {
            if (mRtcState == AGRTCSTATE_DID_ACTIVE) {
                boolean uidExists = false;
                for (int i = 0; i < mRtcSessions.size(); i++) {
                    if (uid == mRtcSessions.get(i).uid()) {
                        uidExists = true;
                    }
                }

                if (!uidExists) {
                    DebugLog.w(TAG, "onUserJoined(int, int) callback not received, try add new user here.");
                    AgoraRtcSession session = new AgoraRtcSession();
                    session.setUid(uid);
                    mRtcSessions.add(session);
                    mHandler.notifyRtcRemoteUserJoined(uid);
                }


                if (mediaDataObserverPlugin != null) {
                    mediaDataObserverPlugin.addDecodeBuffer(uid);
                }

                publishStreamIfNeed();
                mHandler.notifyRtcRemoteUserVideoRendered(uid, width, height);
            }
        //}
    }

    private void onRemoteUserAudioMuted_l(int uid, boolean muted) {
        //synchronized (mRtcFence) {
            mHandler.notifyRtcRemoteUserAudioMuted(uid, muted);
        //}
    }

    private void onRemoteUserVideoMuted_l(int uid, boolean muted) {
        //synchronized (mRtcFence) {
            mHandler.notifyRtcRemoteUserVideoMuted(uid, muted);
        //}
    }

    private void onRtcLocalAudioRouteChanged_l(int data) {

        DebugLog.w(TAG, "onRtcLocalAudioRouteChanged_l = " + data);


        mHandler.notifyRtcLocalAudioRouteChanged(data);

    }

    private void onRtcConnectionLost_l() {
        //synchronized (mRtcFence) {
            mHandler.notifyRtcNetworkLost();
        //}
    }

    private void onRtcConnectionTimeout_l() {
        //synchronized (mRtcFence) {
            mHandler.notifyRtcNetworkTimeout();
        //}
    }

    private void onRtcStats_l(IRtcEngineEventHandler.RtcStats stats) {
        LangRtcInfo rtcInfo = new LangRtcInfo();
        rtcInfo.duration = stats.totalDuration;
        rtcInfo.txBytes = stats.txBytes;
        rtcInfo.rxBytes = stats.rxBytes;
        rtcInfo.rxAudioKBitrate = stats.rxAudioKBitRate;
        rtcInfo.rxVideoKBitrate = stats.rxVideoKBitRate;
        rtcInfo.txAudioKBitrate = stats.txAudioKBitRate;
        rtcInfo.txVideoKBitrate = stats.txVideoKBitRate;
        //rtcInfo.onlineUsers = stats.users;
        rtcInfo.onlineUsers = mRtcSessions.size();

        mHandler.notifyRtcStatsUpdate(rtcInfo);
    }

    private void onRtcFatalError_l(int error, String description) {
        //synchronized (mRtcFence) {
            mHandler.notifyRtcError(error, description);
        //}
    }

    private void onUserOffline_l(int uid, int reason) {
        //synchronized (mRtcFence) {
            if (mRtcState == AGRTCSTATE_DID_ACTIVE) {
                int indexTodelete = -1;
                for (int i = 0; i < mRtcSessions.size(); i++) {
                    AgoraRtcSession rtcSession = mRtcSessions.get(i);
                    if (rtcSession.uid() == uid) {
                        mRtcSessions.remove(i);
                        indexTodelete = i;
                        break;
                    }
                }
                if (indexTodelete == -1) {
                    DebugLog.w(TAG, "WARNING: onUserOffline() uid: " + uid + " not match in session list");
                }

                if (mediaDataObserverPlugin != null) {
                    mediaDataObserverPlugin.removeDecodeBuffer(uid);
                }
                publishStreamIfNeed();

                mHandler.notifyRtcRemoteUserOffline(uid, reason);
            }
        //}
    }

    private void onLeaveChannel_l() {
        //synchronized (mRtcFence) {
            if (mRtcState == AGRTCSTATE_WILL_STOPPED) {
                mRtcState = AGRTCSTATE_DID_STOPPED;

                if (mGraphicBuffer != null) {
                    cleanupLocalRtcGLContext();
                }

                //remove all session objects.
                for (int i = 0; i < mRtcSessions.size(); i++) {
                    AgoraRtcSession session = mRtcSessions.get(i);
                    Log.i(TAG, "remove session uid:" + session.uid());
                }
                mRtcSessions.clear();

                mHandler.notifyRtcLocalUserOffline();

                getWorkerThread().eventHandler().removeEventHandler(this);

                mRtcFence.notifyAll();
            } else {
                DebugLog.e(TAG, "onLeaveChannel_l() called in invalid state: " + rtsStatusString());
            }
        //}
    }

    private RtcEngine rtcEngine() {
        return getWorkerThread().getRtcEngine();
    }

    protected final EngineConfig config() {
        return getWorkerThread().getEngineConfig();
    }

    private synchronized void initWorkerThread() {
        if (mWorkerThread == null) {
            mWorkerThread = new WorkerThread(mContext);
            mWorkerThread.start();

            mWorkerThread.waitForReady();
        }
    }

    private synchronized WorkerThread getWorkerThread() {
        return mWorkerThread;
    }

    private synchronized void deInitWorkerThread() {
        mWorkerThread.exit();
        try {
            mWorkerThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mWorkerThread = null;
    }

    private String rtsStatusString() {
        String statusString = new String();
        if (mRtcState == AGRTCSTATE_DID_STOPPED)
            statusString += "AGRTCSTATE_DID_STOPPED";
        else if (mRtcState == AGRTCSTATE_WILL_STOPPED)
            statusString += "AGRTCSTATE_WILL_STOPPED";
        else if (mRtcState == AGRTCSTATE_DID_ACTIVE)
            statusString += "AGRTCSTATE_DID_ACTIVE";
        else
            statusString += "AGRTCSTATE_WILL_ACTIVE";

        return statusString;
    }

    //
    // Local push rtc video frames to remote server
    //
    private void setupLocalRtcGLContext(int width, int height) {
        if (mGraphicBuffer == null) {
            mGraphicBuffer = GraphicBufferWrapper.createInstance(mContext, width, height, PixelFormat.RGBA_8888);
            mGraphicBufferFrameBufferID = new int[1];
            GLES20.glGenFramebuffers(1, mGraphicBufferFrameBufferID, 0);
            mGraphicBufferTexID = mGraphicBuffer.createTexture(mGraphicBufferFrameBufferID[0]);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                    GLES20.GL_TEXTURE_2D, mGraphicBufferTexID, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        }

        if (mCCPrograme == null) {
            mCCPrograme = new Yuv2RgbProgram();
        }
    }

    private void cleanupLocalRtcGLContext() {
        if (mGraphicBuffer != null) {
            mGraphicBuffer.destroy();
            GLES20.glDeleteFramebuffers(1, mGraphicBufferFrameBufferID, 0);
            mGraphicBufferFrameBufferID = null;
            mGraphicBuffer = null;
        }

        if (mCCPrograme != null) {
            mCCPrograme.release();
            mCCPrograme = null;
        }
    }
}
