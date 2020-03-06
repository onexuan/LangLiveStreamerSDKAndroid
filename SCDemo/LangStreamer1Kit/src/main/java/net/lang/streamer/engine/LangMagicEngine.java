package net.lang.streamer.engine;

import android.content.Context;
import android.hardware.Camera;

import com.github.faucamp.simplertmp.RtmpHandler;

import net.lang.gpuimage.helper.MagicFilterType;

import net.lang.streamer.camera.LangCameraEngine;
import net.lang.streamer.config.LangFaceuConfig;
import net.lang.streamer.config.LangWatermarkConfig;
import net.lang.streamer.engine.data.LangEngineParams;
import net.lang.streamer.faceu.LangFaceHandler;
import net.lang.streamer.widget.LangMagicCameraView;
import net.lang.streamer.widget.base.LangMagicBaseView;

public class LangMagicEngine {

    private static Context context;

    private final LangMagicBaseView baseView;

    private LangMediaPublisher mediaPublisher = null;

    private static LangEncodeHandler mEncodeHandler = null;
    private static RtmpHandler mRtmpHandler = null;
    private static LangRecordHandler mRecordHandler = null;
    private static LangCaptureHandler mCaptureHandler = null;
    private static LangFaceHandler mFaceHandler = null;

    private RtmpHandler.RtmpListener rtmpListener = null;
    private LangEncodeHandler.SnailEncodeListener encodeListener = null;
    private LangRecordHandler.SnailRecordListener recordListener = null;
    private LangCaptureHandler.SnailCaptureListener captureListener = null;
    private LangFaceHandler.SnailFaceListener faceListener = null;

    public LangMagicEngine(LangMagicBaseView magicBaseView){
        baseView = magicBaseView;
        context = baseView.getContext();
    }

    public static Context getContext() {
        return context;
    }

    // start rtmp streaming.
    public void startPublish(String rtmpUrl) {
        if (mediaPublisher != null) {
            ((LangMagicCameraView)baseView).setVideoEncoderCore(mediaPublisher.getVideoEncoder());
            mediaPublisher.setVideoResolution(LangEngineParams.vOutputWidth, LangEngineParams.vOutputHeight);
            mediaPublisher.setScreenOrientation(getCurrentorientation());
            mediaPublisher.startPublish(rtmpUrl);
            changeRecordingState(true);
        }
    }

    // start record camera and voice.
    public void startRecord(String path) {
        if (mediaPublisher != null) {
            mediaPublisher.startRecord(path);
        }
    }

    // stop record camera and voice
    public void stopRecord() {
        if (mediaPublisher != null) {
            mediaPublisher.stopRecord();
        }
    }

    // stop media streaming and recording.
    public void stop() {
        if (mediaPublisher != null) {
            changeRecordingState(false);
            mediaPublisher.stopPublish();
            mediaPublisher.stopRecord();
        }
    }

    // this method is invoked
    // 1:when start preview and stop preview, let audio capture thread running
    // 2:when rtc audio/video chat enabled, because rtc engine will handle audio input itself.
    public void enableAudio(boolean enable) {
        if (enable) {
            mediaPublisher.startAudio();
        } else {
            mediaPublisher.stopAudio();
        }
    }

    public int setMute(boolean mute) {
        if (mediaPublisher.getAudioEncoder() != null) {
            mediaPublisher.getAudioEncoder().mute(mute);
            return 0;
        }
        return -1;
    }

    // set camera horizontal mirror
    public void setCameraMirror(boolean enable) {
        ((LangMagicCameraView)baseView).flip(enable, false);
    }

    // switch camera front/back.
    public void switchCamera() {
        ((LangMagicCameraView)baseView).changeCamera();
    }

    // set camera torch enabled.
    public void setCameraToggleTorch(boolean enable) {
        LangCameraEngine.setCameraToggleTorch(enable);
    }

    // set camera focus position.
    public void setCameraFocusing(float x, float y, Camera.AutoFocusCallback autoFocusCallback) {
        LangCameraEngine.setCameraFocusing(x, y, baseView.getWidth(), baseView.getHeight(), autoFocusCallback);
    }

    // set gpu filter type.
    public void setFilter(MagicFilterType type) {
        ((LangMagicCameraView)baseView).setFilter(type, context);
    }

    public void enableAutoBeauty(boolean enable) {
        ((LangMagicCameraView)baseView).enableAutoBeautyFilter(enable);
    }

    public void setAutoBeautyLevel(int level) {
        ((LangMagicCameraView)baseView).setAutoBeautyLevel(level);
    }

    public void updateWaterMarkConfig(LangWatermarkConfig config) {
        ((LangMagicCameraView)baseView).updateWaterMarkConfig(config);
    }

    public void updateFaceuConfig(final LangFaceuConfig config) {
        ((LangMagicCameraView)baseView).updateFaceuConfig(config);
    }

    // screenshot to the given path.
    public void screenshot(String path) {
        ((LangMagicCameraView)baseView).screenshot(path);
    }

    public void setCameraListener(LangMagicCameraView.SnailMagicCameraListener listenner) {
        ((LangMagicCameraView)baseView).setCameraListing(listenner);
    }

    // set network status listener.
    public void setNetworkListener(RtmpHandler.RtmpListener listener) {
        rtmpListener = listener;
    }

    // set record status listener.
    public void setRecordListener(LangRecordHandler.SnailRecordListener listener) {
        recordListener = listener;
    }

    // set encoder status listener.
    public void setEncodeListener(LangEncodeHandler.SnailEncodeListener listener) {
        encodeListener = listener;
    }

    // set capture status listener.
    public void setCaptureListener(LangCaptureHandler.SnailCaptureListener listener) {
        captureListener = listener;
    }

    // set face detect listener.
    public void setFaceListener(LangFaceHandler.SnailFaceListener listener) {
        faceListener = listener;
    }

    public void cleanup() {
        if (mEncodeHandler != null) {
            mEncodeHandler.removeCallbacksAndMessages(null);
        }
        if (mRtmpHandler != null) {
            mRtmpHandler.removeCallbacksAndMessages(null);
        }
        if (mRecordHandler != null) {
            mRecordHandler.removeCallbacksAndMessages(null);
        }
        if (mCaptureHandler != null) {
            mCaptureHandler.removeCallbacksAndMessages(null);
        }
        if (mediaPublisher != null) {
            mediaPublisher.release();
        }
    }

    // callback for activity onCreate
    public void onCreate(LangVideoEncoderImpl.EncoderType type) {
        mediaPublisher = new LangMediaPublisher();

        mEncodeHandler = new LangEncodeHandler(encodeListener);
        mRtmpHandler = new RtmpHandler(rtmpListener);
        mRecordHandler = new LangRecordHandler(recordListener);
        mCaptureHandler = new LangCaptureHandler(captureListener);
        mFaceHandler = new LangFaceHandler(faceListener);

        mediaPublisher.setEncodeHandler(mEncodeHandler, type);
        mediaPublisher.setRtmpHandler(mRtmpHandler);
        mediaPublisher.setRecordHandler(mRecordHandler);
        ((LangMagicCameraView)baseView).setCaptureHandler(mCaptureHandler);
        ((LangMagicCameraView)baseView).setFaceDetectHandler(mFaceHandler);
    }

    // callback for activity onPause
    public void onPause() {
        if (mediaPublisher != null) {
            mediaPublisher.pauseRecord();
        }

        baseView.onPause();
    }

    // callback for activity onResume
    public void onResume() {
        if (mediaPublisher != null) {
            mediaPublisher.resumeRecord();
        }

        baseView.onResume();
    }

    // callback for activity onDestory
    public void onDestroy() {
        cleanup();
        ((LangMagicCameraView)baseView).onDestroy();
    }

    private void changeRecordingState(boolean isRecording) {
        ((LangMagicCameraView)baseView).changeRecordingState(isRecording);
    }

    private int getCurrentorientation() {
        android.content.res.Configuration mConfiguration = context.getResources().getConfiguration();
        return mConfiguration.orientation;
    }

    public LangMediaPublisher getMediaPublisher() {
        return mediaPublisher;
    }

}
