package net.lang.rtclib;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.SurfaceView;

import com.yunfan.graphicbuffer.GraphicBufferWrapper;

import java.io.File;
import java.lang.ref.WeakReference;

import io.agora.rtc.Constants;
import io.agora.rtc.RtcEngine;
import io.agora.rtc.video.AgoraVideoFrame;
import io.agora.rtc.video.VideoCanvas;

public final class LangRTCThread extends Thread {
    private static final String TAG = LangRTCThread.class.getSimpleName();

    private static final int MSG_THREAD_QUIT = 0X1010; // quit this thread
    private static final int MSG_JOIN_CHANNEL = 0X2010;
    private static final int MSG_LEAVE_CHANNEL = 0X2011;
    private static final int MSG_CONFIG_ENGINE = 0X2012;
    private static final int MSG_PREVIEW = 0X2014;
    private static final int MSG_PUSH_GRAPHIC_BUFFER = 0x2017;

    private final WeakReference<Context> mContextRef; //private final Context mContext;
    private LangRTCEventHandler mEngineEventHandler;
    private LangRTCThreadHandler mWorkerHandler;
    private boolean mReady;
    private RtcEngine mRtcEngine;
    private AgoraVideoFrame mVideoFrame = null;

    public LangRTCThread(Context context) {
        this.mContextRef = new WeakReference<Context>(context);
        this.mEngineEventHandler = new LangRTCEventHandler();
    }

    public LangRTCEventHandler eventHandler() {
        return mEngineEventHandler;
    }

    public RtcEngine rtcEngine() {
        return mRtcEngine;
    }

    public final void configEngine(int vProfile, int audioSampleRate, int audioChannel, String encryptionKey, String encryptionMode) {
        if (Thread.currentThread() != this) {
            Log.w(TAG, "configEngine() - worker thread asynchronously " + vProfile + " " + encryptionMode);
            Message envelop = new Message();
            envelop.what = MSG_CONFIG_ENGINE;
            envelop.obj = new Object[]{vProfile, audioSampleRate, audioChannel, encryptionKey, encryptionMode};
            mWorkerHandler.sendMessage(envelop);
            return;
        }

        ensureRtcEngineReadyLock();

        if (!TextUtils.isEmpty(encryptionKey)) {
            mRtcEngine.setEncryptionMode(encryptionMode);

            mRtcEngine.setEncryptionSecret(encryptionKey);
        }

        // set video profile
        mRtcEngine.enableVideo();
        mRtcEngine.setExternalVideoSource(true, false, true);
        mRtcEngine.setVideoProfile(vProfile, true);

        // set audio params.
        mRtcEngine.setAudioProfile(Constants.AUDIO_PROFILE_MUSIC_HIGH_QUALITY, Constants.AUDIO_SCENARIO_GAME_STREAMING);
        // High quality audio parameters
        mRtcEngine.setParameters("{\"che.audio.specify.codec\":\"HEAAC_2ch\"}");
        // Enable stereo
        mRtcEngine.setParameters("{\"che.audio.stereo\":true}");

        mRtcEngine.setRecordingAudioFrameParameters(audioSampleRate, audioChannel, 0, 1024);
        mRtcEngine.setPlaybackAudioFrameParameters(audioSampleRate, audioChannel, 0, 1024);
        mRtcEngine.setMixedAudioFrameParameters(audioSampleRate, 1024);

        mRtcEngine.enableAudioVolumeIndication(200, 3, false); // 200 ms

        Log.d(TAG, "configEngine " + vProfile + " " + encryptionMode);
    }

    public final void waitForReady() {
        while (!mReady) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            RTCLog.d(TAG, "wait for " + LangRTCThread.class.getSimpleName());
        }
    }

    public void joinChannel(final String channel, int uid, boolean audience) {
        if (Thread.currentThread() != this) {
            Log.w(TAG, "joinChannel() - worker thread asynchronously " + channel + " " + uid);
            Message envelop = new Message();
            envelop.what = MSG_JOIN_CHANNEL;
            envelop.obj = new Object[]{channel, uid, audience};//envelop.obj = new String[]{channel};
            //envelop.arg1 = uid;
            mWorkerHandler.sendMessage(envelop);
            return;
        }

        int clientRole = Constants.CLIENT_ROLE_BROADCASTER;
        if (audience) {
            clientRole = Constants.CLIENT_ROLE_AUDIENCE;
        }

        ensureRtcEngineReadyLock();

        mRtcEngine.setClientRole(clientRole);
        mRtcEngine.joinChannel(null, channel, "OpenVCall", uid);

        RTCLog.d(TAG, "joinChannel " + channel + " " + uid);
    }

    public final void leaveChannel(String channel) {
        if (Thread.currentThread() != this) {
            Log.w(TAG, "leaveChannel() - worker thread asynchronously " + channel);
            Message envelop = new Message();
            envelop.what = MSG_LEAVE_CHANNEL;
            envelop.obj = channel;
            mWorkerHandler.sendMessage(envelop);
            return;
        }

        if (mRtcEngine != null) {
            mRtcEngine.leaveChannel();
            mRtcEngine.enableVideo();
        }

        RTCLog.d(TAG, "leaveChannel " + channel);
    }

    public final void preview(boolean start, SurfaceView view, int uid) {
        if (Thread.currentThread() != this) {
            Log.w(TAG, "preview() - worker thread asynchronously " + start + " " + view + " " + (uid & 0XFFFFFFFFL));
            Message envelop = new Message();
            envelop.what = MSG_PREVIEW;
            envelop.obj = new Object[]{start, view, uid};
            mWorkerHandler.sendMessage(envelop);
            return;
        }

        ensureRtcEngineReadyLock();
        if (start) {
            mRtcEngine.setupLocalVideo(new VideoCanvas(view, VideoCanvas.RENDER_MODE_ADAPTIVE, uid));
            mRtcEngine.startPreview();
        } else {
            mRtcEngine.stopPreview();
        }
    }

    public final void pushGraphicBuffer(GraphicBufferWrapper gb, long timestampMs) {
        if (Thread.currentThread() != this) {
            RTCLog.d(TAG, "pushExternalVideo() - worker thread asynchronously "  + timestampMs);
            Message envelop = new Message();
            envelop.what = MSG_PUSH_GRAPHIC_BUFFER;
            envelop.obj = new Object[]{gb, timestampMs};
            mWorkerHandler.sendMessage(envelop);
            return;
        }

        if (gb.tryLockGB()) {
            ensureRtcEngineReadyLock();

            if (mVideoFrame == null) {
                mVideoFrame = new AgoraVideoFrame();
                mVideoFrame.format = AgoraVideoFrame.FORMAT_I420;
                mVideoFrame.stride = gb.width();
                mVideoFrame.height = gb.height();
                mVideoFrame.buf = new byte[1920 * 1080 * 4];
            }

            final long gbAddr = gb.lock();
            final int gbColorType = (int)'A' | (int)'B' << 8 | (int)'G' << 16 | (int)'R' << 24;
            GraphicBufferWrapper._RgbaToI420(gbAddr, mVideoFrame.buf, gb.width(), gb.height(), gb.stride(), false, 0, gbColorType);
            gb.unlock();

            mVideoFrame.timeStamp = timestampMs;

            boolean result = mRtcEngine.pushExternalVideoFrame(mVideoFrame);
            Log.d(TAG, "buffer data:" + mVideoFrame.buf +
                    " stride = " + mVideoFrame.stride +
                    " height = " + mVideoFrame.height +
                    " format = " + mVideoFrame.format +
                    " result = " + result);

            gb.unlockGB();
        } else {
            Log.w(TAG, "pushGraphicBuffer graphic buffer is busy now!");
        }
    }

    /**
     * call this method to exit
     * should ONLY call this method when this thread is running
     */
    public final void exit() {
        if (Thread.currentThread() != this) {
            RTCLog.w(TAG, "exit() - exit app thread asynchronously");
            mWorkerHandler.sendEmptyMessage(MSG_THREAD_QUIT);
            return;
        }

        mReady = false;

        RtcEngine.destroy();

        // TODO should remove all pending(read) messages

        RTCLog.d(TAG, "exit() > start");

        // exit thread looper
        Looper.myLooper().quit();

        mWorkerHandler.release();

        RTCLog.d(TAG, "exit() > end");
    }

    @Override
    public void run() {
        RTCLog.d(TAG, "start to run");
        Looper.prepare();

        mWorkerHandler = new LangRTCThreadHandler(this);

        ensureRtcEngineReadyLock();

        mReady = true;

        // enter thread looper
        Looper.loop();
    }

    private RtcEngine ensureRtcEngineReadyLock() {
        if (mRtcEngine == null) {
            String appId = mContextRef.get().getString(R.string.app_rtc_id);
            if (TextUtils.isEmpty(appId)) {
                throw new RuntimeException("NEED TO use your App ID, get your own ID at https://dashboard.agora.io/");
            }
            try {
                mRtcEngine = RtcEngine.create(mContextRef.get(), appId, mEngineEventHandler.mRtcEventHandler);
            } catch (Exception e) {
                RTCLog.e(TAG, Log.getStackTraceString(e));
                throw new RuntimeException("NEED TO check rtc sdk init fatal error\n" + Log.getStackTraceString(e));
            }
            mRtcEngine.setChannelProfile(Constants.CHANNEL_PROFILE_LIVE_BROADCASTING);
            mRtcEngine.setClientRole(Constants.CLIENT_ROLE_BROADCASTER);
            mRtcEngine.setLogFile(Environment.getExternalStorageDirectory()
                    + File.separator + mContextRef.get().getPackageName() + "/log/agora-rtc.log");
        }
        return mRtcEngine;
    }

    private static final class LangRTCThreadHandler extends Handler {
        private LangRTCThread mWorkerThread;

        LangRTCThreadHandler(LangRTCThread thread) {
            mWorkerThread = thread;
        }

        public void release() {
            mWorkerThread = null;
        }

        @Override
        public void handleMessage(Message msg) {
            if (this.mWorkerThread == null) {
                RTCLog.w("WorkerThreadHandler", "handler is already released! " + msg.what);
                return;
            }

            switch (msg.what) {
                case MSG_THREAD_QUIT:
                    mWorkerThread.exit();
                    break;
                case MSG_JOIN_CHANNEL:
                    Object[] joinData = (Object[]) msg.obj;
                    mWorkerThread.joinChannel((String)joinData[0], (int)joinData[1], (boolean)joinData[2]);
                    break;
                case MSG_LEAVE_CHANNEL:
                    String channel = (String) msg.obj;
                    mWorkerThread.leaveChannel(channel);
                    break;
                case MSG_CONFIG_ENGINE:
                    Object[] configData = (Object[]) msg.obj;
                    mWorkerThread.configEngine((int)configData[0], (int)configData[1], (int)configData[2], (String)configData[3], (String)configData[4]);
                    break;
                case MSG_PREVIEW:
                    Object[] previewData = (Object[]) msg.obj;
                    mWorkerThread.preview((boolean) previewData[0], (SurfaceView) previewData[1], (int) previewData[2]);
                    break;
                case MSG_PUSH_GRAPHIC_BUFFER:
                    Object[] refData = (Object[])msg.obj;
                    mWorkerThread.pushGraphicBuffer((GraphicBufferWrapper)refData[0], (long)refData[1]);
                    break;
            }
        }
    }

}
