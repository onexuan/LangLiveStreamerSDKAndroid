package net.lang.streamer.engine;

import android.os.Handler;
import android.os.Message;

import java.io.IOException;
import java.lang.ref.WeakReference;

/**
 * Created by lang on 2017/10/31.
 */

public class LangCaptureHandler extends Handler {

    private static final int MSG_CAPTURE_STARTED = 0;
    private static final int MSG_CAPTURE_FINISHED = 1;

    private static final int MSG_CAPTURE_IO_EXCEPTION = 2;

    private WeakReference<SnailCaptureListener> mWeakListener;

    public LangCaptureHandler(SnailCaptureListener listener) {
        mWeakListener = new WeakReference<>(listener);
    }

    public void notifyCaptureStarted(String msg) {
        obtainMessage(MSG_CAPTURE_STARTED, msg).sendToTarget();
    }

    public void notifyCaptureFinished(String msg) {
        obtainMessage(MSG_CAPTURE_FINISHED, msg).sendToTarget();
    }

    public void notifyCaptureIOException(IOException e) {
        obtainMessage(MSG_CAPTURE_IO_EXCEPTION, e).sendToTarget();
    }

    @Override  // runs on UI thread
    public void handleMessage(Message msg) {
        SnailCaptureListener listener = mWeakListener.get();
        if (listener == null) {
            return;
        }

        switch (msg.what) {
            case MSG_CAPTURE_STARTED:
                listener.onCaptureStarted((String) msg.obj);
                break;
            case MSG_CAPTURE_FINISHED:
                listener.onCaptureFinished((String) msg.obj);
                break;
            case MSG_CAPTURE_IO_EXCEPTION:
                listener.onCaptureIOException((IOException) msg.obj);
                break;
            default:
                throw new RuntimeException("unknown msg " + msg.what);
        }
    }

    public interface SnailCaptureListener {

        void onCaptureStarted(String msg);

        void onCaptureFinished(String msg);

        void onCaptureIOException(IOException e);
    }
}
