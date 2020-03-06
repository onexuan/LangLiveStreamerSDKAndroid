package net.lang.streamer.rtc.io.agora.avcapture.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.SurfaceView;
//import io.agora.propeller.Constant;
//import io.agora.openvcall.R;

import com.yunfan.graphicbuffer.GraphicBufferWrapper;

import net.lang.streamer.config.LangRtcConfig;
import net.lang.streamer.engine.LangVideoEncoderImpl;
import net.lang.streamer.utils.DebugLog;

import io.agora.rtc.Constants;
import io.agora.rtc.RtcEngine;
import io.agora.rtc.video.AgoraVideoFrame;
import io.agora.rtc.video.VideoCanvas;

import java.lang.ref.WeakReference;
import java.util.LinkedList;
import java.util.Queue;
import java.io.File;

/**
 * Created by lang on 2017/9/1.
 */

public class WorkerThread extends Thread {
    private final String TAG = this.getClass().getSimpleName();

    private final WeakReference<Context> mContextRef; //private final Context mContext;

    private static final int ACTION_WORKER_THREAD_QUIT = 0X1010; // quit this thread

    private static final int ACTION_WORKER_JOIN_CHANNEL = 0X2010;

    private static final int ACTION_WORKER_LEAVE_CHANNEL = 0X2011;

    private static final int ACTION_WORKER_CONFIG_ENGINE = 0X2012;

    private static final int ACTION_WORKER_PREVIEW = 0X2014;

    private static final int ACTION_WORKER_PUSH_VIDEO_BUFFER = 0x2015;

    private static final int ACTION_WORKER_PUSH_RAW_BUFFER = 0x2016;

    private static final int ACTION_WORKER_PUSH_GRAPHIC_BUFFER = 0x2017;

    private static final class WorkerThreadHandler extends Handler {

        private WorkerThread mWorkerThread;

        WorkerThreadHandler(WorkerThread thread) {
            this.mWorkerThread = thread;
        }

        public void release() {
            mWorkerThread = null;
        }

        @Override
        public void handleMessage(Message msg) {
            if (this.mWorkerThread == null) {
                DebugLog.w("WorkerThreadHandler", "handler is already released! " + msg.what);
                return;
            }

            switch (msg.what) {
                case ACTION_WORKER_THREAD_QUIT:
                    mWorkerThread.exit();
                    break;
                case ACTION_WORKER_JOIN_CHANNEL:
                    Object[] joinData = (Object[]) msg.obj; //String[] data = (String[]) msg.obj;
                    mWorkerThread.joinChannel((String)joinData[0], (int)joinData[1], (boolean)joinData[2]);//mWorkerThread.joinChannel(data[0], msg.arg1);
                    break;
                case ACTION_WORKER_LEAVE_CHANNEL:
                    String channel = (String) msg.obj;
                    mWorkerThread.leaveChannel(channel);
                    break;
                case ACTION_WORKER_CONFIG_ENGINE:
                    Object[] configData = (Object[]) msg.obj;
                    mWorkerThread.configEngine((int) configData[0], (String) configData[1], (String) configData[2]);
                    break;
                case ACTION_WORKER_PREVIEW:
                    Object[] previewData = (Object[]) msg.obj;
                    mWorkerThread.preview((boolean) previewData[0], (SurfaceView) previewData[1], (int) previewData[2]);
                    break;
                /*
                case ACTION_WORKER_PUSH_RAW_BUFFER:
                    Object[] frame = (Object[])msg.obj;
                    mWorkerThread.pushRawBuffer((AgoraVideoFrame)frame[0]);
                    break;
                 */
                case ACTION_WORKER_PUSH_GRAPHIC_BUFFER:
                    Object[] refData = (Object[])msg.obj;
                    mWorkerThread.pushGraphicBuffer((GraphicBufferWrapper)refData[0], (long)refData[1]);
                    break;
            }
        }
    }

    private WorkerThreadHandler mWorkerHandler;

    private boolean mReady;

    public final void waitForReady() {
        while (!mReady) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            DebugLog.d(TAG, "wait for " + WorkerThread.class.getSimpleName());
        }
    }

    @Override
    public void run() {
        DebugLog.d(TAG, "start to run");
        Looper.prepare();

        mWorkerHandler = new WorkerThreadHandler(this);

        ensureRtcEngineReadyLock();

        mReady = true;

        // enter thread looper
        Looper.loop();
    }

    private RtcEngine mRtcEngine;

    public final void joinChannel(final String channel, int uid, boolean audience) {
        if (Thread.currentThread() != this) {
            Log.w(TAG, "joinChannel() - worker thread asynchronously " + channel + " " + uid);
            Message envelop = new Message();
            envelop.what = ACTION_WORKER_JOIN_CHANNEL;
            envelop.obj = new Object[]{channel, uid, audience};//envelop.obj = new String[]{channel};
            //envelop.arg1 = uid;
            mWorkerHandler.sendMessage(envelop);
            return;
        }

        if (audience) {
            mEngineConfig.mClientRole = Constants.CLIENT_ROLE_AUDIENCE;
        } else {
            mEngineConfig.mClientRole = Constants.CLIENT_ROLE_BROADCASTER;
        }

        ensureRtcEngineReadyLock();

        mRtcEngine.setClientRole(mEngineConfig.mClientRole);
        mRtcEngine.joinChannel(null, channel, "OpenVCall", uid);

        mEngineConfig.mChannel = channel;

        DebugLog.d(TAG, "joinChannel " + channel + " " + uid);
    }

    public final void leaveChannel(String channel) {
        if (Thread.currentThread() != this) {
            Log.w(TAG, "leaveChannel() - worker thread asynchronously " + channel);
            Message envelop = new Message();
            envelop.what = ACTION_WORKER_LEAVE_CHANNEL;
            envelop.obj = channel;
            mWorkerHandler.sendMessage(envelop);
            return;
        }

        if (mRtcEngine != null) {
            mRtcEngine.leaveChannel();
            mRtcEngine.enableVideo();
        }

        mEngineConfig.reset();
        Log.d(TAG, "leaveChannel " + channel);
    }

    private EngineConfig mEngineConfig;

    public final EngineConfig getEngineConfig() {
        return mEngineConfig;
    }

    private final AgoraEngineEventHandler mEngineEventHandler;

    public final void configEngine(int vProfile, String encryptionKey, String encryptionMode) {
        if (Thread.currentThread() != this) {
            Log.w(TAG, "configEngine() - worker thread asynchronously " + vProfile + " " + encryptionMode);
            Message envelop = new Message();
            envelop.what = ACTION_WORKER_CONFIG_ENGINE;
            envelop.obj = new Object[]{vProfile, encryptionKey, encryptionMode};
            mWorkerHandler.sendMessage(envelop);
            return;
        }

        ensureRtcEngineReadyLock();
        mEngineConfig.mVideoProfile = vProfile;

        if (!TextUtils.isEmpty(encryptionKey)) {
            mRtcEngine.setEncryptionMode(encryptionMode);

            mRtcEngine.setEncryptionSecret(encryptionKey);
        }

        mRtcEngine.setVideoProfile(mEngineConfig.mVideoProfile, true);

        Log.d(TAG, "configEngine " + mEngineConfig.mVideoProfile + " " + encryptionMode);
    }

    public final void preview(boolean start, SurfaceView view, int uid) {
        if (Thread.currentThread() != this) {
            Log.w(TAG, "preview() - worker thread asynchronously " + start + " " + view + " " + (uid & 0XFFFFFFFFL));
            Message envelop = new Message();
            envelop.what = ACTION_WORKER_PREVIEW;
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


    private Queue<AgoraVideoFrame> mFillQueue = new LinkedList<>();
    private Queue<AgoraVideoFrame> mEmptyQueue = new LinkedList<>();
    private final Object mRtcPushBufferLock = new Object();
    private final int kMaxBufferCnt = 10;
    private int mBuffercnt = 0;

    public final AgoraVideoFrame getLocalRtcPushBuffer() {
        AgoraVideoFrame buffer;
        synchronized (mRtcPushBufferLock) {
            buffer = mEmptyQueue.poll();
        }
        if (buffer == null) {
            if(mBuffercnt < kMaxBufferCnt){
                buffer = new AgoraVideoFrame();
                buffer.format = AgoraVideoFrame.FORMAT_RGBA;
                buffer.buf = new byte[1920 * 1080 * 4];

                mBuffercnt++;
            }
        }
        return buffer;
    }

    public final void pushRawBuffer(AgoraVideoFrame frame) {
        if (Thread.currentThread() != this) {
            Log.w(TAG, "pushRawBuffer() - worker thread asynchronously "  + frame.timeStamp);

            synchronized (mRtcPushBufferLock) {
                mFillQueue.add(frame);
                mRtcPushBufferLock.notifyAll();
            }

            Message envelop = new Message();
            envelop.what = ACTION_WORKER_PUSH_RAW_BUFFER;
            envelop.obj = new Object[]{frame};
            mWorkerHandler.sendMessage(envelop);
            return;
        }

        ensureRtcEngineReadyLock();

        synchronized (mRtcPushBufferLock) {
            AgoraVideoFrame packet = mFillQueue.poll();
            if (packet == null) {
                try {
                    mRtcPushBufferLock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                packet = mFillQueue.poll();
            }

            if (packet != null) {
                boolean result = mRtcEngine.pushExternalVideoFrame(packet);
                Log.d(TAG, "buffer data:" + packet.buf + " stride = " + packet.stride + " height = " + packet.height + " format = " + packet.format + " result = " + result);

                mEmptyQueue.add(packet);
                mRtcPushBufferLock.notifyAll();
            }
        }
    }


    private AgoraVideoFrame mVf = null;

    public final void pushGraphicBuffer(GraphicBufferWrapper gb, long timestamp) {
        if (Thread.currentThread() != this) {
            DebugLog.d(TAG, "pushExternalVideo() - worker thread asynchronously "  + timestamp);
            Message envelop = new Message();
            envelop.what = ACTION_WORKER_PUSH_GRAPHIC_BUFFER;
            envelop.obj = new Object[]{gb, timestamp};
            mWorkerHandler.sendMessage(envelop);
            return;
        }

        if (gb.tryLockGB()) {
            ensureRtcEngineReadyLock();

            if (mVf == null) {
                mVf = new AgoraVideoFrame();
                mVf.format = AgoraVideoFrame.FORMAT_I420;
                mVf.stride = gb.width();
                mVf.height = gb.height();
                mVf.buf = new byte[1920 * 1080 * 4];
            }

            final int type = LangVideoEncoderImpl.handleSpecialDeviceFormat();
            final long gbAddr = gb.lock();
            LangVideoEncoderImpl.RGBAToI420Fast(null, gbAddr, mVf.buf, gb.width(), gb.height(), gb.stride(), false, 0, type);
            gb.unlock();

            mVf.timeStamp = timestamp;
            //int bufLength = gb.lockAndCopySafe(mRGBA);
            //gb.unlock();
            //mVf.buf = mRGBA;

            boolean result = mRtcEngine.pushExternalVideoFrame(mVf);
            Log.d(TAG, "buffer data:" + mVf.buf + " stride = " + mVf.stride + " height = " + mVf.height + " format = " + mVf.format + " result = " + result);

            gb.unlockGB();
        } else {
            Log.w(TAG, "pushGraphicBuffer graphic buffer is busy now!");
        }
    }

    public static String getDeviceID(Context context) {
        // XXX according to the API docs, this value may change after factory reset
        // use Android id as device id
        return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    private RtcEngine ensureRtcEngineReadyLock() {
        if (mRtcEngine == null) {
            String appId = LangRtcConfig.rtcAppId;
            if (TextUtils.isEmpty(appId)) {
                throw new RuntimeException("NEED TO use your App ID, get your own ID at https://dashboard.agora.io/");
            }
            try {
                mRtcEngine = RtcEngine.create(mContextRef.get(), appId, mEngineEventHandler.mRtcEventHandler);
            } catch (Exception e) {
                DebugLog.e(TAG, Log.getStackTraceString(e));
                throw new RuntimeException("NEED TO check rtc sdk init fatal error\n" + Log.getStackTraceString(e));
            }
            mRtcEngine.setChannelProfile(Constants.CHANNEL_PROFILE_LIVE_BROADCASTING);
            mRtcEngine.setClientRole(Constants.CLIENT_ROLE_BROADCASTER);
            mRtcEngine.enableVideo();
            mRtcEngine.setExternalVideoSource(true, false, true);

            mRtcEngine.enableAudioVolumeIndication(200, 3, false); // 200 ms
            mRtcEngine.setLogFile(Environment.getExternalStorageDirectory()
                    + File.separator + mContextRef.get().getPackageName() + "/log/agora-rtc.log");
        }
        return mRtcEngine;
    }

    public AgoraEngineEventHandler eventHandler() {
        return mEngineEventHandler;
    }

    public RtcEngine getRtcEngine() {
        return mRtcEngine;
    }

    /**
     * call this method to exit
     * should ONLY call this method when this thread is running
     */
    public final void exit() {
        if (Thread.currentThread() != this) {
            DebugLog.w(TAG, "exit() - exit app thread asynchronously");
            mWorkerHandler.sendEmptyMessage(ACTION_WORKER_THREAD_QUIT);
            return;
        }

        mReady = false;

        RtcEngine.destroy();

        // TODO should remove all pending(read) messages

        DebugLog.d(TAG, "exit() > start");

        // exit thread looper
        Looper.myLooper().quit();

        mWorkerHandler.release();

        DebugLog.d(TAG, "exit() > end");
    }

    public WorkerThread(Context context) {
        this.mContextRef = new WeakReference<Context>(context); //this.mContext = context;

        this.mEngineConfig = new EngineConfig();
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        this.mEngineConfig.mUid = pref.getInt(ConstantApp.PrefManager.PREF_PROPERTY_UID, 0);

        this.mEngineEventHandler = new AgoraEngineEventHandler(mContextRef.get(), this.mEngineConfig);
    }
}
