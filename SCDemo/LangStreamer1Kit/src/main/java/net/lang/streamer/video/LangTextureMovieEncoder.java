/*
 * Copyright 2013 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.lang.streamer.video;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import android.graphics.SurfaceTexture;
import android.opengl.EGLContext;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import net.lang.gpuimage.filter.base.gpuimage.GPUImageFilter;
import net.lang.gpuimage.filter.advanced.MagicBeautyFilter;
import net.lang.gpuimage.utils.*;

import net.lang.streamer.LangTexture;
import net.lang.streamer.camera.utils.LangCameraInfo;
import net.lang.streamer.engine.LangVideoEncoder;
import net.lang.streamer.engine.LangVideoEncoderImpl;
import net.lang.streamer.engine.data.LangEngineParams;
import net.lang.streamer.utils.DebugLog;
import net.lang.streamer.video.gles.EglCore;
import net.lang.streamer.video.gles.WindowSurface;
import net.lang.streamer.widget.LangMagicCameraView;
import com.yunfan.graphicbuffer.GraphicBufferWrapper;

/**
 * Encode a movie from frames rendered from an external texture image.
 * <p>
 * The object wraps an encoder running on a dedicated thread.  The various control messages
 * may be sent from arbitrary threads (typically the app UI thread).  The encoder thread
 * manages both sides of the encoder (feeding and draining); the only external input is
 * the GL texture.
 * <p>
 * The design is complicated slightly by the need to create an EGL context that shares state
 * with a view that gets restarted if (say) the device orientation changes.  When the view
 * in question is a GLSurfaceView, we don't have full control over the EGL context creation
 * on that side, so we have to bend a bit backwards here.
 * <p>
 * To use:
 * <ul>
 * <li>create TextureMovieEncoder object
 * <li>create an EncoderConfig
 * <li>call TextureMovieEncoder#startRecording() with the config
 * <li>call TextureMovieEncoder#setTextureId() with the texture object that receives frames
 * <li>for each frame, after latching it with SurfaceTexture#updateTexImage(),
 *     call TextureMovieEncoder#frameAvailable().
 * </ul>
 *
 * TODO: tweak the API (esp. textureId) so it's less awkward for simple use cases.
 */
public class LangTextureMovieEncoder implements Runnable {

    private static final String TAG = "LangTextureMovieEncoder";
    private static final boolean VERBOSE = false;

    private static final int MSG_START_RECORDING = 0;
    private static final int MSG_STOP_RECORDING = 1;
    private static final int MSG_FRAME_AVAILABLE = 2;
    private static final int MSG_SET_TEXTURE_ID = 3;
    private static final int MSG_UPDATE_SHARED_CONTEXT = 4;
    private static final int MSG_QUIT = 5;
    private static final int MSG_ENABLE_BEAUTY = 6;
    private static final int MSG_SET_FILTER = 7;
    private static final int MSG_UPDATE_TEXTURECOORD = 8;

    // ----- accessed exclusively by encoder thread -----
    private WindowSurface mInputWindowSurface;
    private EglCore mEglCore;
    private GPUImageFilter mInput;
    private LangTexture mTextureId;

    private LangVideoEncoder mVideoEncoder;
    private byte[] mLastFrame = null;
    private long mTimestampNanos = 0;

    // ----- accessed by multiple threads -----
    private volatile EncoderHandler mHandler;

    private Object mReadyFence = new Object();      // guards ready/running
    private boolean mReady;
    private boolean mRunning;
    private GPUImageFilter mBeautyFilter; // auto beauty filter.
    private int mBeautyLevel;
    private GPUImageFilter filter; // user specified filter.
    private static FloatBuffer gLCubeBuffer;
    private static FloatBuffer gLTextureBuffer;
    private Thread mDrainEncodeBuffer;
    private boolean mEndOfStream = false;

    public LangTextureMovieEncoder() {
        gLCubeBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.CUBE.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        gLCubeBuffer.put(TextureRotationUtil.CUBE).position(0);

        gLTextureBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        gLTextureBuffer.put(TextureRotationUtil.TEXTURE_NO_ROTATION).position(0);
    }

    public boolean tryreadFrameFBO(GraphicBufferWrapper buffer, long pts) {
        if (mVideoEncoder == null)
            return false;

        if(mVideoEncoder.shouldSkipInputWhenStreaming()) {
            return false;
        }

        LangVideoEncoderImpl.EncoderBuffer in = mVideoEncoder.getBuffer();
        if (in != null) {
            in.gb = buffer;
            in.pts = pts;
            in.handle.release(in);
            mLastFrame = in.data;
            return true;
        }
        return false;
    }

    public boolean tryReadFrameFBO(long pts, int w, int h, int texid) {
        if (mVideoEncoder == null)
            return false;

        if(mVideoEncoder.shouldSkipInputWhenStreaming()) {
            return false;
        }

        LangVideoEncoderImpl.EncoderBuffer in = mVideoEncoder.getBuffer();
        if (in != null) {
            in.buffer.position(0);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, texid);
            GLES20.glReadPixels(0, 0, w, h,
                    GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, in.buffer);
            LangMagicCameraView.glCheckError("glReadPixels");
            in.pts = pts;
            in.data = in.buffer.array();
            in.handle.release(in);
            return true;
        }
        return false;
    }

    public byte[] readLastFrame() {
        return mLastFrame;
    }

    public int readBufferFormat() {
        if (mVideoEncoder != null) {
            return mVideoEncoder.getColorFormat();
        }
        return -1;
    }

    public void setVideoEncoderCore(LangVideoEncoder encoder) {
        if (encoder != null) {
            mVideoEncoder = encoder;
        } else {
            throw new RuntimeException("setVideoEncoderCore param illegal!!!");
        }
    }
    /**
     * Encoder configuration.
     * <p>
     * Object is immutable, which means we can safely pass it between threads without
     * explicit synchronization (and don't need to worry about it getting tweaked out from
     * under us).
     * <p>
     * TODO: make frame rate and iframe interval configurable?  Maybe use builder pattern
     *       with reasonable defaults for those and bit rate.
     */
    public static class EncoderConfig {
        final EGLContext mEglContext;

        public EncoderConfig(EGLContext sharedEglContext, LangCameraInfo info) {

            mEglContext = sharedEglContext;
            gLTextureBuffer.clear();
            gLTextureBuffer.put(TextureRotationUtil.getRotation(/*Rotation.fromInt(info.orientation)*/Rotation.NORMAL,
                    false, true)).position(0);
        }
    }

    /**
     * Tells the video recorder to start recording.  (Call from non-encoder thread.)
     * <p>
     * Creates a new thread, which will create an encoder using the provided configuration.
     * <p>
     * Returns after the recorder thread has started and is ready to accept Messages.  The
     * encoder may not yet be fully configured.
     */
    public void startRecording(EncoderConfig config) {
        Log.d(TAG, "Encoder: startRecording()");
        synchronized (mReadyFence) {
            if (mRunning) {
                Log.w(TAG, "Encoder thread already running");
                return;
            }
            mRunning = true;
            new Thread(this, "LangTextureMovieEncoder").start();
            while (!mReady) {
                try {
                    mReadyFence.wait();
                } catch (InterruptedException ie) {
                    // ignore
                }
            }
        }

        mHandler.sendMessage(mHandler.obtainMessage(MSG_START_RECORDING, config));
    }

    /**
     * Tells the video recorder to stop recording.  (Call from non-encoder thread.)
     * <p>
     * Returns immediately; the encoder/muxer may not yet be finished creating the movie.
     * <p>
     * TODO: have the encoder thread invoke a callback on the UI thread just before it shuts down
     * so we can provide reasonable status UI (and let the caller know that movie encoding
     * has completed).
     */
    public void stopRecording() {
        Log.d(TAG, "Encoder: stopRecording()");
        synchronized (mReadyFence) {
            if (!mReady) {
                // if we call stop in case of hander not ready, we just directly call
                // handleStopRecording() without sending message.
                handleStopRecording();
                return;
            }
        }
        // rayman note:
        // We have found that in some low-performence devices, when enable recording
        // from surface, MSG_FRAME_AVAILABLE may occupy too much resource in looper thread.
        // If we just simply invoke sendMessage(), looper may not received MSG_STOP_RECORDING
        // immediately, thus will result in video encoder blocked and make total pipline in
        // wrong status.

        // removeCallbacksAndMessages: is a temp solution for low-performence devices.
        mHandler.removeCallbacksAndMessages(null);
        mHandler.sendMessage/*AtFrontOfQueue*/(mHandler.obtainMessage(MSG_STOP_RECORDING));

        mHandler.sendMessage(mHandler.obtainMessage(MSG_QUIT));
        // We don't know when these will actually finish (or even start).  We don't want to
        // delay the UI thread though, so we return immediately.
    }

    /**
     * Returns true if recording has been started.
     */
    public boolean isRecording() {
        synchronized (mReadyFence) {
            return mRunning;
        }
    }

    /**
     * Tells the video recorder to refresh its EGL surface.  (Call from non-encoder thread.)
     */
    public void updateSharedContext(EGLContext sharedContext) {
        mHandler.sendMessage(mHandler.obtainMessage(MSG_UPDATE_SHARED_CONTEXT, sharedContext));
    }

    /**
     * Tells the video recorder that a new frame is available.  (Call from non-encoder thread.)
     * <p>
     * This function sends a message and returns immediately.  This isn't sufficient -- we
     * don't want the caller to latch a new frame until we're done with this one -- but we
     * can get away with it so long as the input frame rate is reasonable and the encoder
     * thread doesn't stall.
     * <p>
     * TODO: either block here until the texture has been rendered onto the encoder surface,
     * or have a separate "block if still busy" method that the caller can execute immediately
     * before it calls updateTexImage().  The latter is preferred because we don't want to
     * stall the caller while this thread does work.
     */
    public void frameAvailable(SurfaceTexture st, LangTexture texture) {
        synchronized (mReadyFence) {
            if (!mReady) {
                return;
            }
        }
        //Log.d(TAG, "Encoder: frameAvailable()");
        //float[] transform = new float[16];      // TODO - avoid alloc every frame
        //st.getTransformMatrix(transform);
        long timestamp = st.getTimestamp();
        if (timestamp == 0) {
            // Seeing this after device is toggled off/on with power button.  The
            // first frame back has a zero timestamp.
            //
            // MPEG4Writer thinks this is cause to abort() in native code, so it's very
            // important that we just ignore the frame.
            Log.w(TAG, "HEY: got SurfaceTexture with timestamp of zero");
            return;
        }

        mHandler.sendMessage(mHandler.obtainMessage(MSG_FRAME_AVAILABLE,
                (int) (timestamp >> 32), (int) timestamp, texture));
    }

    /**
     * Tells the video recorder what texture name to use.  This is the external texture that
     * we're receiving camera previews in.  (Call from non-encoder thread.)
     * <p>
     * TODO: do something less clumsy
     */
    public void setTextureId(LangTexture id) {
        synchronized (mReadyFence) {
            if (!mReady) {
                return;
            }
        }
        mHandler.sendMessage(mHandler.obtainMessage(MSG_SET_TEXTURE_ID, 0, 0, id));
    }

    /**
     * Encoder thread entry point.  Establishes Looper/Handler and waits for messages.
     * <p>
     * @see Thread#run()
     */
    @Override
    public void run() {
        // Establish a Looper for this thread, and define a Handler for it.
        Looper.prepare();
        synchronized (mReadyFence) {
            mHandler = new EncoderHandler(this);
            mReady = true;
            mReadyFence.notify();
        }
        Looper.loop();

        Log.d(TAG, "Encoder thread exiting");
        synchronized (mReadyFence) {
            mReady = mRunning = false;
            mHandler = null;
        }
    }


    /**
     * Handles encoder state change requests.  The handler is created on the encoder thread.
     */
    private static class EncoderHandler extends Handler {
        private WeakReference<LangTextureMovieEncoder> mWeakEncoder;

        public EncoderHandler(LangTextureMovieEncoder encoder) {
            mWeakEncoder = new WeakReference<LangTextureMovieEncoder>(encoder);
        }

        @Override  // runs on encoder thread
        public void handleMessage(Message inputMessage) {
            int what = inputMessage.what;
            Object obj = inputMessage.obj;

            LangTextureMovieEncoder encoder = mWeakEncoder.get();
            if (encoder == null) {
                Log.w(TAG, "EncoderHandler.handleMessage: encoder is null");
                return;
            }

            switch (what) {
                case MSG_START_RECORDING:
                    Log.d(TAG, "looper handleStartRecording.");
                    encoder.handleStartRecording((EncoderConfig) obj);
                    break;
                case MSG_STOP_RECORDING:
                    Log.d(TAG, "looper handleStopRecording.");
                    encoder.handleStopRecording();
                    break;
                case MSG_FRAME_AVAILABLE:
                    //Log.d(TAG, "looper handleFrameAvailable.");
                    long timestamp = (((long) inputMessage.arg1) << 32) |
                            (((long) inputMessage.arg2) & 0xffffffffL);
                    encoder.handleFrameAvailable((LangTexture) obj, timestamp);
                    break;
                case MSG_SET_TEXTURE_ID:
                    //Log.d(TAG, "looper handleSetTexture.");
                    encoder.handleSetTexture((LangTexture) inputMessage.obj);
                    break;
                case MSG_UPDATE_SHARED_CONTEXT:
                    Log.d(TAG, "looper handleUpdateSharedContext.");
                    encoder.handleUpdateSharedContext((EGLContext) inputMessage.obj);
                    break;
                case MSG_QUIT:
                    Log.d(TAG, "looper quit.");
                    Looper.myLooper().quit();
                    break;
                case MSG_ENABLE_BEAUTY:
                    encoder.handleEnableAutoBeauty(inputMessage.arg1);
                    break;
                case MSG_UPDATE_TEXTURECOORD:
                    encoder.handleUpdateTextureCoord((Rotation)obj, inputMessage.arg1 != 0, inputMessage.arg2 != 0);
                    break;
                default:
                    throw new RuntimeException("Unhandled msg what=" + what);
            }
        }
    }

    private void handleUpdateTextureCoord(Rotation r, boolean flipHor, boolean flipVer) {
        gLTextureBuffer.position(0);
        gLTextureBuffer.put(TextureRotationUtil.getRotation(r, flipHor, flipVer)).position(0);
        Log.d("lichao",  (r == Rotation.NORMAL ? " NORMAL " : "Unknown ") + (flipHor ? "true " : "false ") + (flipVer ? "true " : "false "));
    }

    /**
     * Starts recording.
     */
    private void handleStartRecording(EncoderConfig config) {
        Log.d(TAG, "handleStartRecording " + config);
        prepareEncoder(config.mEglContext);

        // encoder create failure.
        if (mInput == null) return;

        mEndOfStream = false;

            mDrainEncodeBuffer = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (!mEndOfStream) {
                        mVideoEncoder.onProcessedSurfaceFrame(mEndOfStream);
                        try {
                            Thread.sleep( 1000 / LangEngineParams.vOutputFps);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    DebugLog.d(TAG, "Exit DrainEncodeBuffer");
                }
            });
        mDrainEncodeBuffer.setName("DrainEncodeBuffer");
        mDrainEncodeBuffer.start();
    }

    /**
     * Handles notification of an available frame.
     * <p>
     * The texture is rendered onto the encoder's input surface, along with a moving
     * box (just because we can).
     * <p>
     * @param transform The texture transform, from SurfaceTexture.
     * @param timestampNanos The frame's timestamp, from SurfaceTexture.
     */
    private void handleFrameAvailable(LangTexture texture, long timestampNanos) {
        //if (VERBOSE) Log.d(TAG, "handleFrameAvailable tr=" );
        if (mVideoEncoder.shouldSkipInputWhenStreaming()) {
            return;
        }
        mTextureId = texture;
        if (!mTextureId.tryLock()) {
            DebugLog.d(this.getClass().getName(), "Try lock failed and skip frame ...");
            return;
        }

        if (!mTextureId.isValid_l()) {
            DebugLog.d(this.getClass().getName(), "Current texture is invalid and skip frame ...");
            texture.unlock();
            return;
        }

        int targetTexture = texture.textureID();
        //mVideoEncoder.onProcessedSurfaceFrame(false);
        //mInput.setTextureTransformMatrix(transform);
        if(filter == null && mBeautyLevel == 0)
            mInput.onDrawFrame(targetTexture, gLCubeBuffer, gLTextureBuffer);
        else if (filter == null && mBeautyLevel > 0)
            mBeautyFilter.onDrawFrame(targetTexture, gLCubeBuffer, gLTextureBuffer);
        else
            filter.onDrawFrame(targetTexture, gLCubeBuffer, gLTextureBuffer);

        if (mTimestampNanos == 0) {
            mTimestampNanos = timestampNanos;
        }

        mTextureId.unlock();
        long surfaceTimeNanos = timestampNanos - mTimestampNanos;

        mInputWindowSurface.setPresentationTime(surfaceTimeNanos);
        mInputWindowSurface.swapBuffers();
        LangMagicCameraView.glCheckError(TAG);
    }

    /**
     * Handles a request to stop encoding.
     */
    private void handleStopRecording() {
        Log.d(TAG, "handleStopRecording");
        if (mDrainEncodeBuffer != null) {
            mEndOfStream = true;
            while (mDrainEncodeBuffer.isAlive()) try {
                Thread.sleep(33);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mDrainEncodeBuffer = null;
        }
        mVideoEncoder.onProcessedSurfaceFrame(mEndOfStream);
        releaseEncoder();
    }

    /**
     * Sets the texture name that SurfaceTexture will use when frames are received.
     */
    private void handleSetTexture(LangTexture id) {
        //Log.d(TAG, "handleSetTexture " + id);
        mTextureId = id;
    }

    private void handleEnableAutoBeauty(int level) {
        mBeautyLevel = level;
        if (level > 0) {
            ((MagicBeautyFilter)mBeautyFilter).setBeautyLevel(level);
        }
    }

    /**
     * Tears down the EGL surface and context we've been using to feed the MediaCodec input
     * surface, and replaces it with a new one that shares with the new context.
     * <p>
     * This is useful if the old context we were sharing with went away (maybe a GLSurfaceView
     * that got torn down) and we need to hook up with the new one.
     */
    private void handleUpdateSharedContext(EGLContext newSharedContext) {
        Log.d(TAG, "handleUpdatedSharedContext " + newSharedContext);

        // Release the EGLSurface and EGLContext.
        mInputWindowSurface.releaseEglSurface();
        mInput.destroy();
        mEglCore.release();

        // Create a new EGLContext and recreate the window surface.
        mEglCore = new EglCore(newSharedContext, EglCore.FLAG_RECORDABLE);
        mInputWindowSurface.recreate(mEglCore);
        mInputWindowSurface.makeCurrent();

        // Create new programs and such for the new context.
        mInput = new GPUImageFilter();
        mInput.init();
    }

    private void prepareEncoder(EGLContext sharedContext) {
        if (!mVideoEncoder.onStartAsync())
            return;

        mVideoWidth = mVideoEncoder.getOutputWidth();
        mVideoHeight = mVideoEncoder.getOutputHeight();
        mEglCore = new EglCore(sharedContext, EglCore.FLAG_RECORDABLE);
        mInputWindowSurface = new WindowSurface(mEglCore, mVideoEncoder.getInputSurface(), true);
        mInputWindowSurface.makeCurrent();
        mInput = new GPUImageFilter();
        mInput.init();
    }

    private void releaseEncoder() {
        mVideoEncoder.onStopAsync();
        if (mInputWindowSurface != null) {
            mInputWindowSurface.release();
            mInputWindowSurface = null;
        }
        if (mInput != null) {
            mInput.destroy();
            mInput = null;
        }
        if (mEglCore != null) {
            mEglCore.release();
            mEglCore = null;
        }

        mTimestampNanos = 0;
    }

    private int mPreviewWidth = -1;
    private int mPreviewHeight = -1;
    private int mVideoWidth = -1;
    private int mVideoHeight = -1;

    public void setPreviewSize(int width, int height) {
        mPreviewWidth = width;
        mPreviewHeight = height;
    }

    public void waitDrawCamTexture() {

    }

}