package com.snail.scdemo;

import android.app.usage.UsageEvents;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceView;

import net.lang.streamer.video.gles.EglCore;
import net.lang.streamer.video.gles.WindowSurface;

import java.lang.ref.WeakReference;

/**
 * Created by lichao on 17-6-9.
 */

public class NativeSurfaceTest   implements SurfaceTexture.OnFrameAvailableListener, Runnable{
    private Context ctx;
    private EGLContext eglContext;
    private  EglCore eglCore;
    WindowSurface windowSurface;
    private int[] texid = {-1};
    private boolean isInit = false;
    private SurfaceTexture surfaceTexture;
    private Handler mHandle;
    private Thread mThread;
    private Object mFence = new Object();
    protected final int MSG_INIT = 1;
    protected final int MSG_DRAW = 2;
    private String TAG = "lichao";
    private float mColor = 0f;
    private Surface mSurface = null;

    public class EventHandle extends Handler {
        private WeakReference<NativeSurfaceTest>  thiz;
        EventHandle(NativeSurfaceTest obj) {
            thiz = new WeakReference<NativeSurfaceTest>(obj);
        }


        @Override
        public void handleMessage(Message msg) {
            NativeSurfaceTest obj = thiz.get();
            switch (msg.what) {
                case MSG_DRAW:
                    obj.draw_();
                    break;
                case MSG_INIT:
                    obj.init_((EGLContext)msg.obj);
                    break;
                default:
                    break;
            }
        }
    }

    public NativeSurfaceTest(Context ctx) {
        this.ctx = ctx;
        mThread = new Thread(this);
        mThread.start();
        synchronized (mFence) {
            try {
                mFence.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void init_(EGLContext eglContext) {
        if (!isInit) {
            Log.d(TAG, "init");
            this.eglContext = eglContext;
            GLES20.glGenTextures(1, texid, 0);

            if (texid[0] < 0) new RuntimeException("texid create faild.");
            surfaceTexture = new SurfaceTexture(texid[0]);
            surfaceTexture.setOnFrameAvailableListener(this);
            eglCore = new EglCore(eglContext, EglCore.FLAG_RECORDABLE);
            windowSurface = new WindowSurface(eglCore, surfaceTexture);
            windowSurface.makeCurrent();
            mSurface = new Surface(surfaceTexture);
            isInit = true;
        }
    }

    private void draw_() {
        Log.d(TAG, "draw_");
        mColor += 0.2;
        GLES20.glClearColor(mColor, 0f, 0f, 0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        if (mColor > 1.0) {
            mColor = 0f;
        }
        windowSurface.swapBuffers();
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        Log.d(TAG,"onFrameAvailable");
        surfaceTexture.updateTexImage();
    }

    @Override
    public void run() {
        synchronized (mFence) {
            mFence.notifyAll();
        }
        Looper.prepare();
        mHandle = new EventHandle(this);
        Looper.loop();
    }

    public void init(EGLContext ctx) {
        if (mHandle != null) {
            mHandle.sendMessage(mHandle.obtainMessage(MSG_INIT, ctx));
        }
    }

    public void draw() {
        Log.d(TAG, "draw");
        if (mHandle != null) {
            mHandle.sendMessage(mHandle.obtainMessage(MSG_DRAW));
        }
    }
}
