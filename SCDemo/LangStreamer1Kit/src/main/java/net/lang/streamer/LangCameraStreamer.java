package net.lang.streamer;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.hardware.Camera;
import android.support.v4.app.ActivityCompat;
import android.view.SurfaceView;
import android.opengl.EGLContext;
import android.util.Log;
import android.os.Handler;

import com.yunfan.graphicbuffer.GraphicBufferWrapper;

import net.lang.gpuimage.filter.base.gpuimage.GPUImageFilter;
import net.lang.gpuimage.helper.MagicFilterType;

import net.lang.streamer.config.LangObjectSegmentationConfig;
import net.lang.streamer.camera.LangCameraEngine;
import net.lang.streamer.config.*;
import net.lang.streamer.engine.LangAudioEncoder;
import net.lang.streamer.engine.data.LangEngineParams;
import net.lang.streamer.rtc.IRtcSessionManager;
import net.lang.streamer.rtc.LangRtcSessionMgrCreator;
import net.lang.streamer.utils.Accelerometer;
import net.lang.streamer.engine.LangMagicEngine;
import net.lang.streamer.engine.LangMediaPublisher;
import net.lang.streamer.engine.LangVideoEncoder;
import net.lang.streamer.engine.LangVideoEncoderImpl;
import net.lang.streamer.utils.DebugLog;
import net.lang.streamer.video.LangTextureMovieEncoder;
import net.lang.streamer.widget.AnimationCallback;
import net.lang.streamer.widget.LangMagicCameraView;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by lichao on 17-5-2.
 */

public class LangCameraStreamer extends LangCameraStreamerListener
        implements android.hardware.Camera.AutoFocusCallback, LangMagicCameraView.SnailMagicCameraListener {
    public static final String TAG = LangCameraStreamer.class.getName();
    public final static int RTMP_UNCONNECT = 0;
    public final static int RTMP_CONNECTING = 1;
    public final static int RTMP_CONNECTED = 2;
    private Accelerometer mAccelerometer;
    private LangMagicEngine mEngine;
    private LangMediaPublisher mPublisher;
    private LangAudioEncoder mAudioEncoder;
    private LangVideoEncoder mVideoEncoder;
    private LangStreamerConfig mConfig;
    private LangMagicCameraView mView;
    private IRtcSessionManager mRtcManager;
    private LangCameraFilter mFilterType = LangCameraFilter.LANG_FILTER_NONE;
    private LangCameraBeauty mBeautyLevel = LangCameraBeauty.LANG_BEAUTY_NONE;
    private boolean mMute = false;
    private boolean mAutoBitrate = false;
    private int mReconnectCnt = 8;
    private int mReconnectTimes = 0;
    private int mReconnectIntervalMs = 5000;
    private Status mStatus = Status.kUnInit;
    private static int mLevel = LangStreamerLogLevel.LANG_LOG_DEBUG;
    private boolean mStopStream = true;
    private boolean mStopRecord = true;
    private FloatBuffer mCameraEffectValue = null;
    private LangLiveInfo mPlayerInfo = null;
    private double mAudioBitrateBps = 0;
    private double mVideoBitrateBps = 0;
    private final String mLocalPkg = "com.lang.lang";
    private long mDropLastFrameTimestamp = 0;
    private String pushStreamUrl = null;

    private boolean mReconnecting = false;
    private static Handler mReconnectHandler = new Handler();
    private final Object mReconnectRence = new Object();

    public static LangStreamerConfig fromQuality(LangAudioQuality a, LangVideoQuality v) {
        LangStreamerConfig config = new LangStreamerConfig();
        switch (a) {
            case LANG_AUDIO_QUALITY_LOW:
                config.audioBitrate = 64 * 1000;
                config.audioSampleRate = 16000;
                break;
            case LANG_AUDIO_QUALITY_MEDIUM:
                config.audioBitrate = 96 * 1000;
                config.audioSampleRate = 44100;
                break;
            case LANG_AUDIO_QUALITY_HIGH:
                config.audioBitrate = 128 * 1000;
                config.audioSampleRate = 48000;
            default:
                config.audioBitrate = 96 * 1000;
                config.audioSampleRate = 44100;
                break;
        }

        switch (v) {
            case LANG_VIDEO_QUALITY_LOW_1:
                config.videoResolution = LangVideoResolution.LANG_VIDEO_RESOLUTION_360P;
                config.videoFPS = 15;
                config.videoBitrate = 400 * 1000;
                break;
            case LANG_VIDEO_QUALITY_LOW_2:
            case LANG_VIDEO_QUALITY_DEFAULT:
                config.videoResolution = LangVideoResolution.LANG_VIDEO_RESOLUTION_360P;
                config.videoFPS = 24;
                config.videoBitrate = 500 * 1000;
                break;
            case LANG_VIDEO_QUALITY_LOW_3:
                config.videoResolution = LangVideoResolution.LANG_VIDEO_RESOLUTION_360P;
                config.videoFPS = 30;
                config.videoBitrate = 600 * 1000;
                break;
            case LANG_VIDEO_QUALITY_MEDIUM_1:
                config.videoResolution = LangVideoResolution.LANG_VIDEO_RESOLUTION_480P;
                config.videoFPS = 15;
                config.videoBitrate = 700 * 1000;
                break;
            case LANG_VIDEO_QUALITY_MEDIUM_2:
                config.videoResolution = LangVideoResolution.LANG_VIDEO_RESOLUTION_480P;
                config.videoFPS = 24;
                config.videoBitrate = 800 * 1000;
                break;
            case LANG_VIDEO_QUALITY_MEDIUM_3:
                config.videoResolution = LangVideoResolution.LANG_VIDEO_RESOLUTION_540P;
                config.videoFPS = 24;
                config.videoBitrate = 900 * 1000;
                break;
            case LANG_VIDEO_QUALITY_HIGH_1:
                config.videoResolution = LangVideoResolution.LANG_VIDEO_RESOLUTION_540P;
                config.videoFPS = 30;
                config.videoBitrate = 1000 * 1000;
                break;
            case LANG_VIDEO_QUALITY_HIGH_2:
                config.videoResolution = LangVideoResolution.LANG_VIDEO_RESOLUTION_720P;
                config.videoFPS = 24;
                config.videoBitrate = 1100 * 1000;
                break;
            case LANG_VIDEO_QUALITY_HIGH_3:
                config.videoResolution = LangVideoResolution.LANG_VIDEO_RESOLUTION_720P;
                config.videoFPS = 30;
                config.videoBitrate = 1200 * 1000;
                break;
            default:
                return null;
        }

        config.audioChannel = 2;
        config.videoMaxKeyframeInterval = 75;
        return config;
    }

    @Override
    public void onResume() {
        startPreview();
        if (pushStreamUrl != null)
            startStreaming(null);
    }

    @Override
    public void onPause() {
        stopPreview();
        if (pushStreamUrl != null)
            stopStreaming();
    }

    @Override
    public boolean skip(long timems) {
        boolean skipFrame = false;

        if (mDropLastFrameTimestamp == 0) {
            mDropLastFrameTimestamp = timems;
        } else {
            final long targetdiff = 1000 / mConfig.videoFPS;
            if (timems - mDropLastFrameTimestamp < targetdiff) {
                if (mPublisher.getPushVideoFps() > mConfig.videoFPS)
                    skipFrame = true;
            } else {
                mDropLastFrameTimestamp = timems;
            }
        }
        return skipFrame;
    }

    enum Status {
        kUnInit("UnInit"),
        kInit("Init"),
        kStartPreview("StartPreview"),
        kStopPreview("StopPreview"),
        kRelease("Release");
        String mName;

        Status(String name) {
            mName = name;
        }
    }

    private void updateStatus(Status status) {
        DebugLog.dfmt(TAG, "Change status " + mStatus.mName + " -> " + status.mName);
        mStatus = status;
        if (Status.kInit == status) {
            setFilter(mFilterType);
            setBeauty(mBeautyLevel);
            setMute(mMute);
        } else if (Status.kStartPreview == status) {
            setAutoAdjustBitrate(mAutoBitrate);
        }
    }

    private Status status() {
        return mStatus;
    }

    public static ILangCameraStreamer create() {
        LangCameraStreamer streamer = null;
        streamer = new LangCameraStreamer();
        return streamer;
    }

    private LangCameraStreamer() {
        mPlayerInfo = new LangLiveInfo();
    }

    @Override
    public void onRtmpConnecting(String msg) {
        super.onRtmpConnecting(msg);
        mPlayerInfo.connectStatus = RTMP_CONNECTING;
    }

    @Override
    public void onRtmpConnected(String msg) {
        //++
        Log.w(TAG, "onRtmpConnected called");
        mReconnectTimes = 0;
        //--
        super.onRtmpConnected(msg);
        mPlayerInfo.connectStatus = RTMP_CONNECTED;
    }

    @Override
    public void onRtmpDisconnected() {
        super.onRtmpDisconnected();
        mPlayerInfo.connectStatus = RTMP_UNCONNECT;
    }

    @Override
    public void onRtmpVideoFpsChanged(double fps) {
        super.onRtmpVideoFpsChanged(fps);
        mPlayerInfo.videoEncodeFrameCountPerSecond = (int) fps;
    }

    @Override
    public void onRtmpVideoBitrateChanged(double bitrate) {
        super.onRtmpVideoBitrateChanged(bitrate);
        mVideoBitrateBps = (int) bitrate;
        mPlayerInfo.uploadSpeed = (int) ((mVideoBitrateBps + mAudioBitrateBps) / 8);
    }

    @Override
    public void onRtmpAudioBitrateChanged(double bitrate) {
        super.onRtmpAudioBitrateChanged(bitrate);
        mAudioBitrateBps = (int) bitrate;
        mPlayerInfo.uploadSpeed = (int) ((mVideoBitrateBps + mAudioBitrateBps) / 8);
    }

    @Override
    public void onRtmpSocketException(SocketException e) {
        Log.e(TAG, "onRtmpSocketException called");
        //++
        if (mReconnectTimes < mReconnectCnt) {
            if (!mReconnecting) {
                internalReconnect();
                mReconnectTimes++;
            }
            return;
        }
        //--
        super.onRtmpSocketException(e);
        mPlayerInfo.connectStatus = RTMP_UNCONNECT;
        mPlayerInfo.uploadSpeed = 0;
    }

    @Override
    public void onRtmpIOException(IOException e) {
        Log.e(TAG, "onRtmpIOException called");
        //++
        if (mReconnectTimes < mReconnectCnt) {
            if (!mReconnecting) {
                internalReconnect();
                mReconnectTimes++;
            }
            return;
        }
        //--
        super.onRtmpIOException(e);
        mPlayerInfo.connectStatus = RTMP_UNCONNECT;
        mPlayerInfo.uploadSpeed = 0;
    }

    @Override
    public void onRtmpIllegalArgumentException(IllegalArgumentException e) {
        Log.e(TAG, "onRtmpIllegalArgumentException called");
        super.onRtmpIllegalArgumentException(e);
        mPlayerInfo.connectStatus = RTMP_UNCONNECT;
    }

    @Override
    public void onRtmpIllegalStateException(IllegalStateException e) {
        Log.e(TAG, "onRtmpIllegalStateException called");
        super.onRtmpIllegalStateException(e);
        mPlayerInfo.connectStatus = RTMP_UNCONNECT;
    }

    private void internalReconnect() {
        mReconnecting = true;

        if (mEventListener != null) {
            mEventListener.onEvent(this, LangStreamerEvent.LANG_EVENT_PUSH_RECONNECTING, mReconnectTimes);
        }
        // try re-connect after mReconnectIntervalMs.
        mReconnectHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.w(TAG, "try reconnect now, times = " + mReconnectTimes);
                synchronized (mReconnectRence) {
                    if (mPublisher != null) {
                        mPublisher.stopPublish();
                        mPublisher.startPublish(pushStreamUrl);
                    }
                    mReconnecting = false;
                }
            }
        }, mReconnectIntervalMs);

    }

    private int internalInit(LangMagicCameraView view) {
        mView = view;
        mEngine = new LangMagicEngine(view);
        mEngine.setEncodeListener(this);
        mEngine.setNetworkListener(this);
        mEngine.setRecordListener(this);
        mEngine.setCaptureListener(this);
        mEngine.setFaceListener(this);
        mEngine.setCameraListener(this);
        LangVideoEncoderImpl.EncoderType t = view.encoderType();
        mEngine.onCreate(t);

        mPublisher = mEngine.getMediaPublisher();
        mPublisher.setPublisherListener(this);

        mVideoEncoder = mPublisher.getVideoEncoder();
        mAudioEncoder = mPublisher.getAudioEncoder();
        mAccelerometer = new Accelerometer(mView.getContext());
        mRtcManager = LangRtcSessionMgrCreator.createRtcSessionManager(this, mView.getContext(), mEngine);
        updateStatus(Status.kInit);
        return 0;
    }

    @Override
    public int init(LangAudioQuality acfg, LangVideoQuality vcfg, LangMagicCameraView view) {
        LangStreamerConfig config = fromQuality(acfg, vcfg);
        if (config != null) {
            return init(config, view);
        }
        return -1;
    }

    @Override
    public int init(LangStreamerConfig config, LangMagicCameraView view) {
        boolean invalid = config.audioBitrate < 0 ||
                config.audioChannel < 1 ||
                config.audioSampleRate < 8000 ||
                config.videoBitrate < 1 ||
                config.videoFPS < 1 ||
                config.videoMaxKeyframeInterval < 1;

        if (invalid) {
            DebugLog.e(TAG, "Invalid configure parameters.");
            return -1;
        }

        if (view == null) {
            return -1;
        }

        mConfig = config;
        setupParameters();
        internalInit(view);
        return 0;
    }

    @Override
    public int startPreview() {
        if (status() == Status.kInit || status() == Status.kStopPreview) {
            if (!checkPermissions()) {
                sendError(LangStreamerError.LANG_ERROR_NO_PERMISSIONS, 0);
                return -1;
            }
            mAccelerometer.start();
            mEngine.enableAudio(true); //set audio capture thread running.
            mEngine.onResume();
            if (!checkCamera())
                return -1;
            updateStatus(Status.kStartPreview);
            super.startPreview();
        }
        return 0;
    }

    private int alignOf(int v, int align) {
        return ((v + align - 1) / align) * align;
    }

    void setupParameters() {
        LangEngineParams.aOutputBitrateKbps = mConfig.audioBitrate / 1000;
        LangEngineParams.aSamplerate = mConfig.audioSampleRate;
        LangEngineParams.cameraPreveiwWidth = 720;
        LangEngineParams.cameraPreviewHeight = 1280;
        LangEngineParams.cameraFps = mConfig.videoFPS;
        LangEngineParams.vOutKeyFrameIntervalSec = mConfig.videoMaxKeyframeInterval / mConfig.videoFPS;
        LangEngineParams.vOutputBitrateKbps = mConfig.videoBitrate / 1000;
        LangEngineParams.vOutputFps = mConfig.videoFPS;
        if (mConfig.videoResolution == LangVideoResolution.LANG_VIDEO_RESOLUTION_360P) {

            LangEngineParams.vOutputWidth = alignOf(360, 32);
            LangEngineParams.vOutputHeight = alignOf(640, 32);
        } else if (mConfig.videoResolution == LangVideoResolution.LANG_VIDEO_RESOLUTION_480P) {

            LangEngineParams.vOutputWidth = alignOf(480, 32);
            LangEngineParams.vOutputHeight = alignOf(854, 32);
        } else if (mConfig.videoResolution == LangVideoResolution.LANG_VIDEO_RESOLUTION_540P) {

            LangEngineParams.vOutputWidth = alignOf(540, 32);
            LangEngineParams.vOutputHeight = alignOf(960, 32);
        } else if (mConfig.videoResolution == LangVideoResolution.LANG_VIDEO_RESOLUTION_720P) {

            LangEngineParams.vOutputWidth = alignOf(720, 32);
            LangEngineParams.vOutputHeight = alignOf(1280, 32);
        }

        LangEngineParams.aChannel = mConfig.audioChannel;
    }

    private boolean checkCamera() {
        if (LangCameraEngine.getCamera() == null) {
            sendError(LangStreamerError.LANG_ERROR_OPEN_CAMERA_FAIL, 0);
            mPlayerInfo.cameraPresent = LangCameraEngine.isbackCameraID(LangCameraEngine.getCameraID()) ? 1 : 0;
            return false;
        }
        return true;
    }

    @Override
    public int stopPreview() {
        if (status() == Status.kStartPreview) {
            updateStatus(Status.kStopPreview);
            stopStreaming();
            stopRecording();
            mView.onPause();
            mAccelerometer.stop();
            mEngine.enableAudio(false); //set audio capture thread stop.
            super.stopPreview();
        }
        return 0;
    }

    @Override
    public int startStreaming(String url) {
        /*
        if (!LangPushAuthentication.getInstance().isAuthenticateSucceed()) {
            int rev = -1;
            sendError(LangStreamerError.LANG_ERROR_AUTH_FAIL, rev);
            return rev;
        }
        */

        if (url == null) {
            url = pushStreamUrl;
        } else {
            pushStreamUrl = url;
        }

        if (status() == Status.kStartPreview) {
            if (mStopStream) {
                mStopStream = false;

                //++
                mReconnectHandler.removeCallbacksAndMessages(null);
                synchronized (mReconnectRence) {
                    mEngine.startPublish(url);
                }
                //--
                //mEngine.startPublish(url);
            }
        }
        return 0;
    }

    @Override
    public int stopStreaming() {
        if (!mStopStream) {
            mView.changeRecordingState(false);

            mReconnecting = false;
            mReconnectHandler.removeCallbacksAndMessages(null);
            synchronized (mReconnectRence) {
                mPublisher.stopPublish();
            }
            mReconnectTimes = 0;

            mStopStream = true;
        }
        return 0;
    }

    @Override
    public boolean isStreaming() {
        if (mPlayerInfo != null && !mStopStream)
            return mPlayerInfo.connectStatus == RTMP_CONNECTED;

        return super.isStreaming();
    }

    @Override
    public int startRecording(String url) {
        if (mStopRecord && !mStopStream) {
            mStopRecord = false;
            mView.setVideoEncoderCore(mVideoEncoder);
            //mView.changeRecordingState(true);
            mEngine.startRecord(url);//mPublisher.startRecord(url);
        }
        return 0;
    }

    @Override
    public int stopRecording() {
        if (!mStopRecord && !mStopStream) {
            mStopRecord = true;
            //mView.changeRecordingState(false);
            mEngine.stopRecord();//mPublisher.stopRecord();
        }
        return 0;
    }

    @Override
    public int screenshot(String url) {
        if (status() == Status.kStartPreview) {
            mEngine.screenshot(url);
            return 0;
        } else {
            sendError(LangStreamerError.LANG_ERROR_SCREENSHOT_FAIL, -1);
            return -1;
        }
    }

    @Override
    public int release() {
        if (status() != Status.kUnInit) {
            if (mRtcManager != null) {
                mRtcManager.release();
                mRtcManager = null;
            }
            stopPreview();
            mEngine.onDestroy();
            //mView.onDestroy();
            mView = null;
            mEngine = null;
            mPublisher = null;
            LangCameraEngine.resetCamera();
        }
        return 0;
    }

    @Override
    public int switchCamera() {
        if (status() == Status.kStartPreview) {
            mEngine.switchCamera();
            checkCamera();
        }
        return 0;
    }

    @Override
    public void setCameraBrightLevel(float level) {
        if (mCameraEffectValue == null) {
            mCameraEffectValue = FloatBuffer.allocate(4);
            mCameraEffectValue.position(0);
            mCameraEffectValue.put(1.0f);
            mCameraEffectValue.put(1.0f);
            mCameraEffectValue.put(1.0f);
            mCameraEffectValue.put(1.0f);
            mCameraEffectValue.position(0);
        }
        mCameraEffectValue.position(0);
        float v = level > 1.0f ? 1.0f : level;
        mCameraEffectValue.array()[0] = v;
        if (mView != null) {
            mView.setMixColor(mCameraEffectValue);
        }
    }

    @Override
    public void setCameraToggleTorch(boolean enable) {
        mEngine.setCameraToggleTorch(enable);
    }

    @Override
    public void setCameraFocusing(float x, float y) {
        if (status() != Status.kStartPreview)
            return;

        mEngine.setCameraFocusing(x, y, this);
    }

    @Override
    public void onAutoFocus(boolean b, android.hardware.Camera camera) {
        if (b)
            camera.cancelAutoFocus();
        DebugLog.dfmt(TAG, "Focus %s", b ? "ok..." : "faild...");
    }

    @Override
    public int setFilter(LangCameraFilter filter) {
        MagicFilterType type = MagicFilterType.NONE;
        switch (filter) {
            case LANG_FILTER_FAIRYTALE:
                type = MagicFilterType.FAIRYTALE;
                break;
            case LANG_FILTER_SUNRISE:
                type = MagicFilterType.SUNRISE;
                break;
            case LANG_FILTER_SUNSET:
                type = MagicFilterType.SUNSET;
                break;
            case LANG_FILTER_WHITECAT:
                type = MagicFilterType.WHITECAT;
                break;
            case LANG_FILTER_BLACKCAT:
                type = MagicFilterType.BLACKCAT;
                break;
            case LANG_FILTER_SKINWHITEN:
                type = MagicFilterType.SKINWHITEN;
                break;
            case LANG_FILTER_HEALTHY:
                type = MagicFilterType.HEALTHY;
                break;
            case LANG_FILTER_SWEETS:
                type = MagicFilterType.SWEETS;
                break;
            case LANG_FILTER_ROMANCE:
                type = MagicFilterType.ROMANCE;
                break;
            case LANG_FILTER_SAKURA:
                type = MagicFilterType.SAKURA;
                break;
            case LANG_FILTER_WARM:
                type = MagicFilterType.WARM;
                break;
            case LANG_FILTER_ANTIQUE:
                type = MagicFilterType.ANTIQUE;
                break;
            case LANG_FILTER_NOSTALGIA:
                type = MagicFilterType.NOSTALGIA;
                break;
            case LANG_FILTER_CALM:
                type = MagicFilterType.CALM;
                break;
            case LANG_FILTER_LATTE:
                type = MagicFilterType.LATTE;
                break;
            case LANG_FILTER_TENDER:
                type = MagicFilterType.TENDER;
                break;
            case LANG_FILTER_COOL:
                type = MagicFilterType.COOL;
                break;
            case LANG_FILTER_EMERALD:
                type = MagicFilterType.EMERALD;
                break;
            case LANG_FILTER_EVERGREEN:
                type = MagicFilterType.EVERGREEN;
                break;
            case LANG_FILTER_CRAYON:
                type = MagicFilterType.CRAYON;
                break;
            case LANG_FILTER_SKETCH:
                type = MagicFilterType.SKETCH;
                break;
            case LANG_FILTER_AMARO:
                type = MagicFilterType.AMARO;
                break;
            case LANG_FILTER_BRANNAN:
                type = MagicFilterType.BRANNAN;
                break;
            case LANG_FILTER_BROOKLYN:
                type = MagicFilterType.BROOKLYN;
                break;
            case LANG_FILTER_EARLYBIRD:
                type = MagicFilterType.EARLYBIRD;
                break;
            case LANG_FILTER_FREUD:
                type = MagicFilterType.FREUD;
                break;
            case LANG_FILTER_HEFE:
                type = MagicFilterType.HEFE;
                break;
            case LANG_FILTER_HUDSON:
                type = MagicFilterType.HUDSON;
                break;
            case LANG_FILTER_INKWELL:
                type = MagicFilterType.INKWELL;
                break;
            case LANG_FILTER_KEVIN:
                type = MagicFilterType.KEVIN;
                break;
            case LANG_FILTER_LOMO:
                type = MagicFilterType.LOMO;
                break;
            case LANG_FILTER_N1977:
                type = MagicFilterType.N1977;
                break;
            case LANG_FILTER_NASHVILLE:
                type = MagicFilterType.NASHVILLE;
                break;
            case LANG_FILTER_PIXAR:
                type = MagicFilterType.PIXAR;
                break;
            case LANG_FILTER_RISE:
                type = MagicFilterType.RISE;
                break;
            case LANG_FILTER_SIERRA:
                type = MagicFilterType.SIERRA;
                break;
            case LANG_FILTER_SUTRO:
                type = MagicFilterType.SUTRO;
                break;
            case LANG_FILTER_TOASTER2:
                type = MagicFilterType.TOASTER2;
                break;
            case LANG_FILTER_WALDEN:
                type = MagicFilterType.WALDEN;
                break;
            default:
                break;
        }

        mEngine.setFilter(type);

        mFilterType = filter;
        return 0;
    }

    @Override
    public int setBeauty(LangCameraBeauty info) {
        mBeautyLevel = info;
        if (/*mView != null &&*/ mStatus == Status.kStartPreview) {
            if (info == LangCameraBeauty.LANG_BEAUTY_NONE) {
                mEngine.enableAutoBeauty(false);//mView.enableAutoBeautyFilter(false);
            } else {
                mEngine.enableAutoBeauty(true);//mView.enableAutoBeautyFilter(true);
                mEngine.setAutoBeautyLevel(info.value);//mView.setAutoBeautyLevel(info.value);
            }
        }
        return 0;
    }

    @Override
    public int setWatermark(LangWatermarkConfig config) {
        if (config.url.isEmpty() && config.picture == null && config.enable) {
            return -1;
        }

        if (config.x < 0 || config.y < 0) {
            return -1;
        }

        if (!config.fullScreen && (config.w < 1 || config.h < 1))
            return -1;

        mEngine.updateWaterMarkConfig(config);
        return 0;
    }

    @Override
    public int setFaceu(LangFaceuConfig config) {
        mEngine.updateFaceuConfig(config);
        return 0;
    }

    @Override
    public int setGiftAnimation(LangObjectSegmentationConfig params, InputStream inputStream, InputStream giftStream) {
        if (mView != null) {
            mView.updateMattingConfig(params, inputStream, giftStream);
        }
        return 0;
    }

    @Override
    public int setMattingAnimation(LangObjectSegmentationConfig params, String inputPath, String giftPath, AnimationCallback animationCallback){
        if (mView != null) {
            mView.updateMattingConfig(params, inputPath, giftPath, animationCallback);
        }
        return 0;
    }

    @Override
    public int setHairColors(LangObjectSegmentationConfig params) {
        if (mView != null) {
            mView.updateBeautyHairConfig(params);
        }
        return 0;
    }

    @Override
    public int setMute(boolean mute) {
        mMute = mute;
        mEngine.setMute(mute);
        return 0;
    }

    @Override
    public void setReconnectOption(int count, int interval) {
        mReconnectIntervalMs = interval;
        mReconnectCnt = count;
    }

    @Override
    public void setAutoAdjustBitrate(boolean enable) {
        mAutoBitrate = enable;
        if (mVideoEncoder != null)
            mVideoEncoder.autoBitrate(enable);
    }

    @Override
    public void setDebugLevel(int level) {
        mLevel = level;
    }

    public static String getVersion() {
        return LangStreamerVersion.getVersion().version();
    }

    private int getDisplayOrientation(int degrees, int cameraId) {
        // See android.hardware.Camera.setDisplayOrientation for
        // documentation.
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360; // compensate the mirror
        } else { // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        return result;
    }

    @Override
    public void setMirror(boolean enable) {
        mEngine.setCameraMirror(enable);
    }

    @Override
    public void setZoom(float scale) {
        if (scale < 1.0) scale = 1.0f;
        if (scale > 3.0) scale = 3.0f;
        float zoom = (float) (scale * (-0.335) + 1.335);
        if (mCameraEffectValue == null) {
            mCameraEffectValue = FloatBuffer.allocate(4);
            mCameraEffectValue.position(0);
            mCameraEffectValue.put(0.f);
            mCameraEffectValue.put(1.0f);
            mCameraEffectValue.put(1.0f);
            mCameraEffectValue.put(1.0f);
            mCameraEffectValue.position(0);
        }
        mCameraEffectValue.position(0);
        mCameraEffectValue.array()[1] = zoom;
        if (mView != null) {
            mView.setMixColor(mCameraEffectValue);
        }
    }

    @Override
    public LangLiveInfo getLiveInfo() {
        mPlayerInfo.localBufferAudioCount = mPublisher.getAudioFrameCacheNumber();
        mPlayerInfo.localBufferVideoCount = mPublisher.getVideoFrameCacheNumber();
        mPlayerInfo.pushVideoFrameCount = mPublisher.getPushVideoFrameCounts();
        mPlayerInfo.encodeVideoFrameCount = mPlayerInfo.pushVideoFrameCount;
        mPlayerInfo.videoPushFrameCountPerSecond = (int) mPublisher.getPushVideoFps();
        mPlayerInfo.previewFrameCountPerSecond = (int) ((LangMagicCameraView) mView).getPreviewFps();
        mPlayerInfo.videoDiscardFrameCount = mVideoEncoder == null ? 0 : mVideoEncoder.getDiscardFrameCounts();
        mPlayerInfo.videoDropFrameCountPerSencond = mVideoEncoder == null ? 0 : (int) mVideoEncoder.getDiscardFps();
        return mPlayerInfo;
    }


    @Override
    public boolean rtcAvailable(LangRtcConfig config) {
        if (status() != Status.kStartPreview) {
            return false;
        }

        if (config.deviceInBlackLists()) {
            return false;
        }

        if (mView.encoderType() == LangVideoEncoderImpl.EncoderType.kHardwarePipeline) {
            //Todo: we have not implement push surface texture on agora rtc sdk.
            DebugLog.w(TAG, "rtc sdk will not work in hardware pipeline mode.");
            return false;
        }
        if (!LangEngineParams.enableGraphicBuffer) {
            // Todo: we will use android graphic buffer mapping RGBA memory to CPU and glReadPixels
            // Todo: is too slow to grab data, so if no graphic buffer available, return immediately.
            DebugLog.w(TAG, "graphic buffer is disabled, so rtc sdk will not work.");
            return false;
        }
        return true;
    }

    @Override
    public SurfaceView createRtcRenderView() {
        if (mRtcManager != null) {
            return mRtcManager.createRtcRenderView();
        }
        return null;
    }

    @Override
    public void setRtcDisplayLayoutParams(LangRtcConfig.RtcDisplayParams displayParams) {
        if (mRtcManager != null) {
            mRtcManager.setDisplayParamsWhenStreaming(displayParams);
        }
    }

    @Override
    public int joinRtcChannel(LangRtcConfig config, SurfaceView localView) {
        if (!rtcAvailable(config)) {
            return -1;
        }
        super.joinRtcChannel(config, localView);

        if (!mStopStream) {
            if (!LangRtcConfig.localMixed) {
                stopStreaming();
            }
            mEngine.enableAudio(false);
            mRtcManager.changeRtmpStatus(true);

        }

        // key rtc audio/video config is same with rtmp streamer.
        config.audioSampleRate = mConfig.audioSampleRate;
        config.audioChannel = mConfig.audioChannel;
        config.videoBitrate = mConfig.videoBitrate;
        config.videoFPS = mConfig.videoFPS;
        config.pushStreamUrl = pushStreamUrl;
        mRtcManager.joinChannel(config, localView);

        return 0;
    }

    @Override
    public void leaveRtcChannel() {
        if (!mStopStream) {
            mRtcManager.changeRtmpStatus(false);
        }
        mRtcManager.leaveChannel();
        if (!LangRtcConfig.localMixed) {
            startStreaming(null);
        }
    }

    @Override
    public void onRtcLocalUserJoined(int uid) {
        super.onRtcLocalUserJoined(uid);
    }

    @Override
    public void onRtcLocalUserOffline() {
        super.onRtcLocalUserOffline();

        if (!mStopStream) {
            mEngine.enableAudio(true);
        }
    }

    @Override
    public int setRtcVoiceChat(boolean voiceOnly) {
        if (mRtcManager != null) {
            boolean result = mRtcManager.setVoiceChat(voiceOnly);
            return result ? 0 : -1;
        }
        return -1;
    }

    @Override
    public int muteRtcLocalVoice(boolean mute) {
        if (mRtcManager != null) {
            boolean result = mRtcManager.muteLocalVoice(mute);
            return result ? 0 : -1;
        }
        return -1;
    }

    @Override
    public int muteRtcRemoteVoice(final int uid, boolean mute) {
        if (mRtcManager != null) {
            boolean result = mRtcManager.muteRemoteVoice(uid, mute);
            return result ? 0 : -1;
        }
        return -1;
    }

    @Override
    public int setupRtcRemoteUser(final int uid, SurfaceView remoteView) {
        if (mRtcManager != null) {
            boolean result = mRtcManager.setupRemoteUser(uid, remoteView);
            return result ? 0 : -1;
        }
        return -1;
    }

    @Override
    public boolean pushExternalVideoFrame(EGLContext eglContext, int textureId, int stride, int height, long timestamp) {
        return mRtcManager.pushExternalVideoFrame(eglContext, textureId, stride, height, timestamp);
    }

    @Override
    public boolean pushExternalVideoFrame(GPUImageFilter filter, int texId, long timestamp) {
        if (mRtcManager != null) {
            return mRtcManager.pushExternalVideoFrame(filter, texId, timestamp);
        }
        return false;
    }

    @Override
    public boolean mixLocalWithRemoteVideoFrame(GPUImageFilter filter, int texId, GraphicBufferWrapper gb, LangTextureMovieEncoder encoder, long timestamp) {
        if (mRtcManager != null) {
            return mRtcManager.mixLocalWithRemoteVideoFrame(filter, texId, gb, encoder, timestamp);
        }
        return false;
    }

    public static void print(String tag, int leve, String msg) {
        if (leve >= mLevel) {
            switch (leve) {
                case LangStreamerLogLevel.LANG_LOG_INFO:
                    Log.i(tag, msg);
                    break;
                case LangStreamerLogLevel.LANG_LOG_DEBUG:
                    Log.d(tag, msg);
                    break;
                case LangStreamerLogLevel.LANG_LOG_WARNING:
                    Log.w(tag, msg);
                    break;
                case LangStreamerLogLevel.LANG_LOG_ERROR:
                    Log.e(tag, msg);
                    break;
                case LangStreamerLogLevel.LANG_LOG_NONE:
                    break;
                default:
                    break;
            }
        }
    }

    // check the specified permission to avoid sdk crash.
    private boolean checkPermissions() {
        if (android.os.Build.VERSION.SDK_INT < 23) {
            return true;
        }

        Activity currentActivity = null;
        Context context = mView.getContext();
        while (context instanceof android.content.ContextWrapper) {
            if (context instanceof Activity) {
                currentActivity = (Activity) context;
                break;
            }
            context = ((android.content.ContextWrapper) context).getBaseContext();
        }
        if (context == null) {
            Log.e(TAG, "checkPermissions: cannot find activity from current view!");
            return false;
        }

        String[] requestPermissions = {
                android.Manifest.permission.RECORD_AUDIO,
                android.Manifest.permission.CAMERA,
                //android.Manifest.permission.READ_EXTERNAL_STORAGE,
                //android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        };

        for (int i = 0; i < requestPermissions.length; i++) {
            String requestPermission = requestPermissions[i];
            int checkSelfPermission = -1;
            try {
                checkSelfPermission = ActivityCompat.checkSelfPermission(currentActivity, requestPermission);
            } catch (RuntimeException e) {
                DebugLog.e(TAG, "RuntimeException: " + e.getMessage());
                return false;
            }

            if (checkSelfPermission != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "checkPermissions: no " + requestPermission + "permission!");
                return false;
            }
        }

        return true;
    }

    @Override
    public void enableMakeups(boolean enable) {
        if (mView != null) {
            mView.enableMakeups(enable);
        }
    }

    @Override
    public void enablePushMatting(boolean enable) {
        if (mView != null) {
            mView.enablePushMatting(enable);
        }
    }
}
