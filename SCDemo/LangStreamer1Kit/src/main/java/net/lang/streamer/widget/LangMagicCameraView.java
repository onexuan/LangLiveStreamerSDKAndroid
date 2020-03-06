package net.lang.streamer.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.EGL14;
import android.opengl.GLES20;
import android.opengl.EGLContext;
import android.util.AttributeSet;
import android.util.Log;

import com.yunfan.graphicbuffer.GraphicBufferWrapper;

import net.lang.gpuimage.filter.base.gpuimage.GPUImageFilter;
import net.lang.gpuimage.filter.base.MagicCameraInputFilter;
import net.lang.gpuimage.filter.custom.MagicWaterMarkFilter;
import net.lang.gpuimage.helper.*;
import net.lang.gpuimage.utils.*;

import net.lang.streamer.faceu.impl.BeautyHairTracker;
import net.lang.streamer.faceu.impl.MattingTracker;
import net.lang.streamer.LangWhiteList;
import net.lang.streamer.NonPIPEMagicFilter;
import net.lang.streamer.LangTexture;
import net.lang.streamer.LangWhiteListCell;
import net.lang.streamer.camera.LangCameraEngine;
import net.lang.streamer.camera.utils.LangCameraInfo;
import net.lang.streamer.config.*;
import net.lang.streamer.engine.LangCaptureHandler;
import net.lang.streamer.engine.LangVideoEncoderImpl;
import net.lang.streamer.engine.data.LangEngineParams;
import net.lang.streamer.faceu.*;
import net.lang.streamer.utils.DebugLog;
import net.lang.streamer.utils.SpeedStatistics;
import net.lang.streamer.engine.LangVideoEncoder;
import net.lang.streamer.video.LangTextureMovieEncoder;
import net.lang.streamer.widget.base.LangMagicBaseView;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class LangMagicCameraView extends LangMagicBaseView implements Camera.PreviewCallback, IAnimationListener {

    public interface SnailMagicCameraListener {
        boolean skip(long timems);

        boolean pushExternalVideoFrame(EGLContext eglContext, int textureId, int stride, int height, long timestamp);

        boolean pushExternalVideoFrame(GPUImageFilter filter, int texId, long timestamp);

        boolean mixLocalWithRemoteVideoFrame(GPUImageFilter filter, int texId, GraphicBufferWrapper gb, LangTextureMovieEncoder encoder, long timestamp);
    }

    private MagicCameraInputFilter cameraInputFilter;
    private MagicWaterMarkFilter mWaterMarkFilter;
    private GPUImageFilter beautyFilter = null; // rayman add
    private GPUImageFilter mDraw = null;
    private GPUImageFilter mNonPIPEDraw = null;
    private int mLevel; // rayman add (beauty filter level)
    private SnailMagicCameraListener magicCameraListenner = null;
    private LangCaptureHandler mCaptureHandler = null;

    private SurfaceTexture surfaceTexture;
    private LangVideoEncoderImpl.EncoderType mEncoderType = LangVideoEncoderImpl.EncoderType.kHardwarePipeline;

    public LangMagicCameraView(Context context) {
        this(context, null);
    }

    private boolean recordingEnabled;
    private int recordingStatus;

    private static final int RECORDING_OFF = 0;
    private static final int RECORDING_ON = 1;
    private static final int RECORDING_RESUMED = 2;
    private boolean mFlipHorizontal = false;
    private boolean mFlipVertical = false;
    private Rotation mRotation = Rotation.NORMAL;
    private boolean cameraSizeChange = false;
    private boolean mScreenshot = false;
    private String mScreenshotPath = null;
    private static LangTextureMovieEncoder videoEncoder = new LangTextureMovieEncoder();
    private LangTexture mSwapTexture = null;
    private LangTexture mEncoderTexture = null;
    private SpeedStatistics mCameraPreviewFps = null;
    private SpeedStatistics mSkipFrameFps = null;
    private GraphicBufferWrapper mGraphicBuffer = null;
    private int[] mGraphicBufferFrameBufferID = null;
    private int mGraphicBufferTexID = -1;

    private SenseMEFilter.FaceuConfig internalFaceuConfig = null;
    private SenseMEFilter mFaceTracker = null;
    private FloatBuffer gLCameraTextureBuffer = null;
    private FloatBuffer glEncoderTextureBuffer = null;

    private MattingTracker mMattingTracker = null;
    private LangObjectSegmentationConfig tfMattingParams = null;
    private BeautyHairTracker mBeautyHairTracker = null;
    private LangObjectSegmentationConfig tfHairParams = null;
    private AnimationCallback animationCallback = null;

    private boolean faceUMakeups = false;
    private boolean pushMatting = false;

    private int mEncoderWidth, mEncoderHeight;

    private static final String TAG = LangMagicCameraView.class.getName();

    public LangMagicCameraView(Context context, AttributeSet attrs) {
        super(context, attrs);
        cameraInputFilter = new MagicCameraInputFilter(getContext());
        mDraw = new GPUImageFilter();
        mNonPIPEDraw = new NonPIPEMagicFilter();
        mWaterMarkFilter = new MagicWaterMarkFilter(context);
        mWaterMarkFilter.setRect(0, 0, 300, 200);
        recordingStatus = -1;
        recordingEnabled = false;
        mSwapTexture = new LangTexture();
        mEncoderTexture = new LangTexture();
        mCameraPreviewFps = new SpeedStatistics();
        mSkipFrameFps = new SpeedStatistics();
        setEncoderType(LangVideoEncoderImpl.EncoderType.kHardwarePipeline);
        enableGraphicBuffer(false);
        gLCameraTextureBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.length * 4)
                .order(java.nio.ByteOrder.nativeOrder())
                .asFloatBuffer();
        glEncoderTextureBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.length * 4)
                .order(java.nio.ByteOrder.nativeOrder())
                .asFloatBuffer();

        mFaceTracker = new SenseMEFilter(this.getContext());//new LangSenseMeTracker(mFaceuConfig, this.getContext());
        internalFaceuConfig = new SenseMEFilter.FaceuConfig();
        internalFaceuConfig.defaultBeautyParams();

        tfMattingParams = new LangObjectSegmentationConfig(LangObjectSegmentationConfig.Companion.getMATTING(), 1.f);
        mMattingTracker = new MattingTracker(this.getContext(), tfMattingParams);

        tfHairParams = new LangObjectSegmentationConfig(LangObjectSegmentationConfig.Companion.getHAIR(), 0.5f);
        mBeautyHairTracker = new BeautyHairTracker(this.getContext(), tfHairParams);
    }

    public void setAnimationCallback(AnimationCallback animationCallback){
        mMattingTracker.setAnimationCallback(animationCallback);
    }

    public void enableGraphicBuffer(boolean gb_enable) {
        DebugLog.w(TAG, "enableGraphicBuffer=" + gb_enable);
        boolean enable = gb_enable;
        LangWhiteListCell cell = LangWhiteList.cell(getContext());
        if (cell != null) {
            if ((cell.flagCodec() & LangWhiteList.kCodecEnableGraphicBuffer) == 0) {
                enable = false;
                DebugLog.w(TAG, "Disable GraphicBuffer accelerate.");
            }
        }
        LangEngineParams.enableGraphicBuffer = enable;
    }

    public void setCameraListing(SnailMagicCameraListener listenner) {
        magicCameraListenner = listenner;
    }

    public void setCaptureHandler(LangCaptureHandler handler) {
        mCaptureHandler = handler;
    }

    public void setFaceDetectHandler(LangFaceHandler handler) {
        mFaceTracker.setDetectionHandler(handler);//((LangSenseMeTracker) mFaceTracker).setFaceDetectHandler(handler);
    }

    public static void glCheckError(String append) {
        int rev = GLES20.glGetError();
        String str = "non - " + append;
        if (rev != GLES20.GL_NO_ERROR) {
            switch (rev) {
                case GLES20.GL_INVALID_ENUM:
                    str = "GL_INVALID_ENUM";
                    break;
                case GLES20.GL_INVALID_FRAMEBUFFER_OPERATION:
                    str = "GL_INVALID_FRAMEBUFFER_OPERATION";
                    break;
                case GLES20.GL_INVALID_OPERATION:
                    str = "GL_INVALID_OPERATION";
                    break;
                case GLES20.GL_INVALID_VALUE:
                    str = "GL_INVALID_VALUE";
                    break;
                case GLES20.GL_INVERT:
                    str = "GL_INVERT";
                    break;
                default:
                    str = "Unknown";
                    break;
            }
        }
        DebugLog.i(LangMagicCameraView.class.getName(), str);
    }

    public void setEncoderType(LangVideoEncoderImpl.EncoderType type) {
        LangWhiteListCell cell = LangWhiteList.cell(getContext());
        LangVideoEncoderImpl.EncoderType t = type;
        if (cell != null) {
            t = LangWhiteList.chooseType(cell.flagCodec(), type);
        }

        if (t != type) {
            DebugLog.w(TAG, "Using codec type " + t.name() + " instead of " + type.name());
        }
        mEncoderType = t;
    }

    public LangVideoEncoderImpl.EncoderType encoderType() {
        return mEncoderType;
    }

    public void flip(boolean flipHorizontal, boolean flipVertical) {
        mFlipHorizontal = flipHorizontal;
        //mFlipVertical = flipVertical;
        glEncoderTextureBuffer.clear();
        glEncoderTextureBuffer.put(TextureRotationUtil.getRotation(mRotation, mFlipHorizontal, mFlipVertical)).position(0);
        //mUpdateTextureCoord = true;
    }

    public void updateWaterMarkConfig(LangWatermarkConfig config) {
        mWaterMarkFilter.setEnable(config.enable);
        if (config.picture != null) {
            mWaterMarkFilter.setBitmap(config.picture);
        } else {
            mWaterMarkFilter.setUrl(config.url);
        }

        mWaterMarkFilter.setFullScreen(config.fullScreen);
        mWaterMarkFilter.setRect(config.x, config.y, config.w, config.h);
    }

    public void setMixColor(FloatBuffer rgba) {
        cameraInputFilter.setMixColor(rgba);
    }

    public void enableMakeups(boolean enable) {
        faceUMakeups = enable;
    }

    public void enablePushMatting(boolean enable) {
        pushMatting = enable;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        super.onSurfaceCreated(gl, config);
        recordingEnabled = videoEncoder.isRecording();
        if (recordingEnabled) {
            recordingStatus = RECORDING_RESUMED;
        } else {
            recordingStatus = RECORDING_OFF;
        }
        mDraw.init();
        mNonPIPEDraw.init();
        mWaterMarkFilter.init();
        cameraInputFilter.init();
        //++rayman
        mFaceTracker.init();
        //--

        if (mEncoderType == LangVideoEncoderImpl.EncoderType.kHardwarePipeline) {
            enableGraphicBuffer(false);
            DebugLog.d(TAG, "Disable GraphicBuffer supports on pipe mode.");
        }

        if (textureId == OpenGlUtils.NO_TEXTURE) {
            textureId = OpenGlUtils.getExternalOESTextureID();
            if (textureId != OpenGlUtils.NO_TEXTURE) {
                surfaceTexture = new SurfaceTexture(textureId);
                surfaceTexture.setOnFrameAvailableListener(onFrameAvailableListener);
                LangCameraEngine.startPreview(surfaceTexture);
            }
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        super.onSurfaceChanged(gl, width, height);
        if (mWaterMarkFilter != null)
            mWaterMarkFilter.setSurface(width, height);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (beautyFilter != null) {
            beautyFilter.destroy();
            beautyFilter = null;
        }

        if (mFaceTracker != null) {
            mFaceTracker.destroy();//mFaceTracker.unloadFaceTracker();
            mFaceTracker = null;
        }

        if (mMattingTracker != null) {
            mMattingTracker.unloadFaceTracker();
            mMattingTracker = null;
        }

        if (mBeautyHairTracker != null) {
            mBeautyHairTracker.unloadFaceTracker();
            mBeautyHairTracker = null;
        }

        if (filter != null) {
            filter.destroy();
            filter = null;
        }

        mDraw.destroy();
        mDraw = null;

        mNonPIPEDraw.destroy();
        mNonPIPEDraw = null;

        cameraInputFilter.destroy();
        cameraInputFilter = null;

        mSwapTexture.lock();
        mSwapTexture.initTexture_l(0, 0);
        mSwapTexture.unlock();
        mSwapTexture = null;

        mEncoderTexture.lock();
        mEncoderTexture.initTexture_l(0, 0);
        mEncoderTexture.unlock();
        mEncoderTexture = null;

        mWaterMarkFilter.onDestroy();
        mWaterMarkFilter = null;

        if (LangEngineParams.enableGraphicBuffer) {
            if (mGraphicBuffer != null) {
                mGraphicBuffer.destroy();
                GLES20.glDeleteFramebuffers(1, mGraphicBufferFrameBufferID, 0);
                mGraphicBufferFrameBufferID = null;
                mGraphicBuffer = null;
            }
        }

        magicCameraListenner = null;

        DebugLog.w(TAG, "onDestroy");
    }

    private Boolean mattingEnabled = false;
    private Boolean beautyHairEnabled = false;

    @Override
    public void onDrawFrame(GL10 gl) {

        super.onDrawFrame(gl);
        if (imageWidth <= 0 || imageHeight <= 0)
            return;
        if (surfaceTexture == null)
            return;
        surfaceTexture.updateTexImage();
        if (recordingEnabled) {
            switch (recordingStatus) {
                case RECORDING_OFF:
                    LangCameraInfo info = LangCameraEngine.getCameraInfo();
                    videoEncoder.startRecording(new LangTextureMovieEncoder.EncoderConfig(EGL14.eglGetCurrentContext(),
                            info));
                    recordingStatus = RECORDING_ON;
                    break;
                case RECORDING_RESUMED:
                    videoEncoder.updateSharedContext(EGL14.eglGetCurrentContext());
                    recordingStatus = RECORDING_ON;
                    break;
                case RECORDING_ON:
                    break;
                default:
                    throw new RuntimeException("unknown status " + recordingStatus);
            }
        } else {
            switch (recordingStatus) {
                case RECORDING_ON:
                case RECORDING_RESUMED:
                    videoEncoder.stopRecording();
                    recordingStatus = RECORDING_OFF;
                    break;
                case RECORDING_OFF:
                    break;
                default:
                    throw new RuntimeException("unknown status " + recordingStatus);
            }
        }

        float[] mtx = new float[16];
        surfaceTexture.getTransformMatrix(mtx);
        float[] mIdentityMatrx = {
                1.0f, 0.0f, 0.f,0f,
                0.0f, 1.0f, 0.f,0f,
                0.0f, 0.0f, 1.f,0f,
                0.0f, 0.0f, 0.f,0f,
        };
        cameraInputFilter.setTextureTransformMatrix(mIdentityMatrx);
        int id = textureId;

        if (cameraSizeChange) {
            cameraInputFilter.initCameraFrameBuffer(imageWidth, imageHeight);
            cameraSizeChange = false;
            if (beautyFilter != null) {
                beautyFilter.onInputSizeChanged(imageWidth, imageHeight);
            }

            //++rayman
            mSwapTexture.lock();
            mSwapTexture.initTexture_l(imageWidth, imageHeight);
            mSwapTexture.unlock();
            //--

            mEncoderTexture.lock();
            mEncoderTexture.setValid_l(false);
            mEncoderTexture.initTexture_l(mEncoderWidth, mEncoderHeight);
            mEncoderTexture.unlock();

            //++rayman
            if (mEncoderType == LangVideoEncoderImpl.EncoderType.kHardwarePipeline) {
                mFlipVertical = false;
            } else {
                mFlipVertical = true;
            }

            glEncoderTextureBuffer.clear();
            glEncoderTextureBuffer.put(TextureRotationUtil.getRotation(mRotation, false, mFlipVertical)).position(0);

            Rotation rotation;
            try {
                rotation = Rotation.fromInt(LangCameraEngine.getCameraAngle());
            } catch (IllegalStateException e) {
                DebugLog.e(TAG, "get camera angle failed! " + e.getMessage());
                rotation = Rotation.NORMAL;
            }
            boolean flipHorizontal = LangCameraEngine.isFrontCamera();
            float[] textureCords = TextureRotationUtil.getRotation(rotation, true, flipHorizontal);
            gLCameraTextureBuffer.clear();
            gLCameraTextureBuffer.put(textureCords).position(0);

            // face tracker need an un-rotated texture.
            mFaceTracker.onInputSizeChanged(imageWidth, imageHeight);//mFaceTracker.loadFaceTracker(imageWidth, imageHeight);
            mBeautyHairTracker.loadFaceTracker(imageWidth, imageHeight);
            mMattingTracker.loadFaceTracker(imageWidth, imageHeight);
            mMattingTracker.setListener(this);
            //--rayman
        }

        int cam_id_tex = cameraInputFilter.onDrawToTexture(textureId, gLCubeBuffer, gLCameraTextureBuffer, null);
        int cam_id = cameraInputFilter.frameBuffer();

        // process matting
        if (pushMatting) {
            synchronized (this) {
                if (mattingEnabled && mMattingTracker != null) {
                    cam_id_tex = mMattingTracker.processFromTexture(mNonPIPEDraw, cam_id, cam_id_tex, null);
                }
            }
        }

        // process beauty hair
        synchronized (this) {
            if (beautyHairEnabled && mBeautyHairTracker != null) {
                cam_id_tex = mBeautyHairTracker.processFromTexture(mNonPIPEDraw, cam_id, cam_id_tex, null);
            }
        }

        // process faceu.
        mFaceTracker.enableGraphicBuffer(!faceUMakeups);
        mFaceTracker.enableFaceExtraPoint(faceUMakeups);
        cam_id_tex = mFaceTracker.onDrawFrame(cam_id_tex);//mFaceTracker.processFromTexture(mNonPIPEDraw, cam_id, cam_id_tex, rgbaBuffer);

        int bind_id = -1;
        int bind_id_tex = -1;

        int draw_id = -1;
        int draw_id_tex = -1;

        bind_id = mSwapTexture.id();
        bind_id_tex = mSwapTexture.textureID();
        draw_id = cam_id;
        draw_id_tex = cam_id_tex;

        if (beautyFilter != null) {
            GLES20.glViewport(0, 0, imageWidth, imageHeight);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, bind_id);
            beautyFilter.onDrawFrame(draw_id_tex);
            // swap bind/draw buffer
            bind_id = bind_id + draw_id;
            bind_id_tex = bind_id_tex + draw_id_tex;

            draw_id = bind_id - draw_id;
            bind_id = bind_id - draw_id;

            draw_id_tex = bind_id_tex - draw_id_tex;
            bind_id_tex = bind_id_tex - draw_id_tex;
        }

        if (filter != null) {
            GLES20.glViewport(0, 0, imageWidth, imageHeight);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, bind_id);
            filter.onDrawFrame(draw_id_tex);
            // swap bind/draw buffer
            bind_id = bind_id + draw_id;
            bind_id_tex = bind_id_tex + draw_id_tex;
            draw_id = bind_id - draw_id;
            bind_id = bind_id - draw_id;
            draw_id_tex = bind_id_tex - draw_id_tex;
            bind_id_tex = bind_id_tex - draw_id_tex;
        }
        //--rayman

        boolean skipFrame = false;
        boolean noSkip = true;
        if (magicCameraListenner != null) {
            noSkip = !magicCameraListenner.skip(surfaceTexture.getTimestamp() / 1000000);
        }

        if (!noSkip) {
            if (!pushMatting) {
                synchronized (this) {
                    if (mattingEnabled && mMattingTracker != null) {
                        draw_id_tex = mMattingTracker.processFromTexture(mNonPIPEDraw, draw_id, draw_id_tex, null);
                    }
                }
            }
            GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            mDraw.onDrawFrame(draw_id_tex, gLCubeBuffer, gLTextureBuffer);//mDraw.onDrawFrame(mRotateTexture.textureID());
            mCameraPreviewFps.add();
            return;
        }

        if (mGraphicBuffer == null && LangEngineParams.enableGraphicBuffer) {
            mGraphicBuffer = GraphicBufferWrapper.createInstance(getContext(), LangEngineParams.vOutputWidth, LangEngineParams.vOutputHeight, PixelFormat.RGBA_8888);
            if (mGraphicBuffer != null) {
                mGraphicBufferFrameBufferID = new int[1];
                GLES20.glGenFramebuffers(1, mGraphicBufferFrameBufferID, 0);
                mGraphicBufferTexID = mGraphicBuffer.createTexture(mGraphicBufferFrameBufferID[0]);
                GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                        GLES20.GL_TEXTURE_2D, mGraphicBufferTexID, 0);
                if (mGraphicBufferTexID <= 0) {
                    mGraphicBuffer = null;
                    GLES20.glDeleteFramebuffers(1, mGraphicBufferFrameBufferID, 0);
                    mGraphicBufferFrameBufferID = null;
                    enableGraphicBuffer(false);
                }
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
            }
        }

        if (mEncoderType == LangVideoEncoderImpl.EncoderType.kHardwarePipeline) {
            if (mEncoderTexture.tryLock()) {
                GLES20.glViewport(0,0, mEncoderTexture.textureWidth(), mEncoderTexture.textureHeight());
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mEncoderTexture.id());
                mDraw.onDrawFrame(draw_id_tex, gLCubeBuffer, glEncoderTextureBuffer);
                glCheckError(this.getClass().getName());
                mEncoderTexture.setValid_l(true);
                mEncoderTexture.unlock();
                videoEncoder.frameAvailable(surfaceTexture, mEncoderTexture);
            } else {
                skipFrame = true;
            }
        } else {
            // for rtc push video buffer
            boolean mixRtcSuccess = false;
            if (magicCameraListenner != null) {
                if (LangEngineParams.enableGraphicBuffer) {
                    magicCameraListenner.pushExternalVideoFrame(
                            mNonPIPEDraw,
                            draw_id_tex,
                            System.currentTimeMillis());

                    mixRtcSuccess = magicCameraListenner.mixLocalWithRemoteVideoFrame(
                            mNonPIPEDraw,
                            draw_id_tex,
                            mGraphicBuffer,
                            videoEncoder,
                            System.currentTimeMillis());
                }
            }

            if (!mixRtcSuccess) {
                GLES20.glViewport(0, 0, LangEngineParams.vOutputWidth, LangEngineParams.vOutputHeight);
                int fbid = LangEngineParams.enableGraphicBuffer ? mGraphicBufferFrameBufferID[0] : mEncoderTexture.id();
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbid);
                glCheckError("GraphicBuffer ");

                mNonPIPEDraw.onDrawFrame(draw_id_tex, gLCubeBuffer, glEncoderTextureBuffer);
                glCheckError("GraphicBuffer 2");

                boolean watermark = mWaterMarkFilter.enable();
                if (watermark) {
                    mWaterMarkFilter.onDrawFrameWaterMark(mEncoderWidth, mEncoderHeight);
                }

                if (LangEngineParams.enableGraphicBuffer) {
                    skipFrame = videoEncoder.tryreadFrameFBO(mGraphicBuffer, surfaceTexture.getTimestamp());
                } else {
                    skipFrame = videoEncoder.tryReadFrameFBO(surfaceTexture.getTimestamp(), LangEngineParams.vOutputWidth, LangEngineParams.vOutputHeight, fbid);
                }
            }
        }

        if (skipFrame) {
            double rate = mSkipFrameFps.rate();
            if (rate > 8)
                DebugLog.d(this.getClass().getName(), "Maybe the encoder of device is low and skip frame on camera (skip rate " + rate + "fps)");
            mSkipFrameFps.add();
        }

        if (!pushMatting) {
            synchronized (this) {
                if (mattingEnabled && mMattingTracker != null) {
                    draw_id_tex = mMattingTracker.processFromTexture(mNonPIPEDraw, draw_id, draw_id_tex, null);
                }
            }
        }

        GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        mDraw.onDrawFrame(draw_id_tex, gLCubeBuffer, gLTextureBuffer);
        mCameraPreviewFps.add();

        if (mScreenshot) {
            savePictureFromDisplay(mScreenshotPath);
            mScreenshot = false;
        }
    }

    @Override
    public synchronized void onFinish() {
        mattingEnabled = false;
    }

    @Override
    public void onPreviewFrame(final byte[] data, final Camera camera) {
        //use single input mode.
//        if (LangCameraEngine.getCamera() == null) return;
//        if (mFaceTracker != null) {
//            mFaceTracker.onDetectFrame(data);
//        }
    }

    public double getSkipFFps() {
        return mSkipFrameFps.rate();
    }

    public double getPreviewFps() {
        return mCameraPreviewFps.rate();
    }

    private SurfaceTexture.OnFrameAvailableListener onFrameAvailableListener = new SurfaceTexture.OnFrameAvailableListener() {

        @Override
        public void onFrameAvailable(SurfaceTexture surfaceTexture) {
            requestRender();
        }
    };

    @Override
    public void setFilter(MagicFilterType type, Context context) {
        super.setFilter(type, context);
        //videoEncoder.setFilter(type);
    }

    private void openCamera() {
        boolean cameraReady = true;
        if (LangCameraEngine.getCamera() == null)
            cameraReady = LangCameraEngine.openCamera();
        if (!cameraReady) {
            DebugLog.e(TAG, "openCamera failed due to RuntimeException!");
            return;
        }

        LangCameraInfo info = LangCameraEngine.getCameraInfo();

        if(info.orientation == 90 || info.orientation == 270){
            if (imageHeight != info.previewWidth || imageWidth != info.previewHeight) {
                cameraSizeChange = true;
            }
            imageWidth = info.previewHeight;
            imageHeight = info.previewWidth;
        }else{
            if (imageHeight != info.previewHeight || imageWidth != info.previewWidth) {
                cameraSizeChange = true;
            }
            imageWidth = info.previewWidth;
            imageHeight = info.previewHeight;
        }

        updateEncoderOutputSize();

        cameraInputFilter.onInputSizeChanged(imageWidth, imageHeight);
        //boolean flipHorizontal = LangCameraEngine.getCameraInfo().isFront;
        float[] textureCords = TextureRotationUtil.getRotation(Rotation.NORMAL, false, false);
        gLTextureBuffer.clear();
        gLTextureBuffer.put(textureCords).position(0);
        videoEncoder.setPreviewSize(imageWidth, imageHeight);
//        LangCameraEngine.setPreviewCallback(this);
        if (surfaceTexture != null)
            LangCameraEngine.startPreview(surfaceTexture);

        cameraSizeChange = true;
    }

    private void closeCamera() {
//        LangCameraEngine.setPreviewCallback(null);
        LangCameraEngine.releaseCamera();
    }

    public void changeRecordingState(boolean isRecording) {
        recordingEnabled = isRecording;
    }

    public void setVideoEncoderCore(LangVideoEncoder encoder) {
        videoEncoder.setVideoEncoderCore(encoder);
    }

    protected void onFilterChanged() {
        super.onFilterChanged();
        cameraInputFilter.onDisplaySizeChanged(surfaceWidth, surfaceHeight);
    }

    public void onResume() {
        super.onResume();
        openCamera();
    }

    public void onPause() {
        super.onPause();
        closeCamera();
    }

    public void changeCamera() {
        closeCamera();
        LangCameraEngine.switchCameraID();
        openCamera();
    }

    //++rayman
    public void enableAutoBeautyFilter(final boolean enable) {
        queueEvent(new Runnable() {
            @Override
            public void run() {
                internalFaceuConfig.enableBeauty = enable;
                mFaceTracker.setFaceuConfig(internalFaceuConfig);
                /*
                if (enable) {
                    if (beautyFilter == null) {
                        beautyFilter = MagicFilterFactory.initFilters(MagicFilterType.BEAUTY);
                        beautyFilter.init();
                        mLevel = 5;
                        beautyFilter.onDisplaySizeChanged(surfaceWidth, surfaceHeight);
                        beautyFilter.onInputSizeChanged(imageWidth, imageHeight);
                    }
                } else {
                    if (beautyFilter != null) {
                        beautyFilter.destroy();
                        beautyFilter = null;
                    }
                    mLevel = 0;
                }
                 */
            }
        });
        requestRender();
    }

    public void setAutoBeautyLevel(final int level) {
        queueEvent(new Runnable() {
            @Override
            public void run() {
                if (level < 1 || level > 5) {
                    return;
                }

                mLevel = level;
                if (mLevel == 1) {
                    internalFaceuConfig.setBasicParams(0.18f, 0.09f, 0.01f);
                } else if (mLevel == 2) {
                    internalFaceuConfig.setBasicParams(0.36f, 0.18f, 0.02f);
                } else if (mLevel == 3) {
                    internalFaceuConfig.setBasicParams(0.54f, 0.27f, 0.03f);
                } else if (mLevel == 4) {
                    internalFaceuConfig.setBasicParams(0.72f, 0.36f, 0.04f);
                } else if (mLevel == 5) {
                    internalFaceuConfig.setBasicParams(0.90f, 0.45f, 0.05f);
                }
                mFaceTracker.setFaceuConfig(internalFaceuConfig);
                /*
                if (beautyFilter != null) {
                    ((MagicBeautySmoothingFilter)beautyFilter).setBeautyLevel(mLevel);
                }
                 */
            }
        });
    }

    public void updateFaceuConfig(final LangFaceuConfig config) {
        queueEvent(new Runnable() {
            @Override
            public void run() {
                //++rayman
                internalFaceuConfig.enableBeauty = config.needBeautify;
                internalFaceuConfig.enableBodyBeauty = config.needBodyBeautify;
                internalFaceuConfig.enableSticker = config.needSticker;
                internalFaceuConfig.reddenStrength = config.reddenStrength;
                internalFaceuConfig.smoothStrength = config.smoothStrength;
                internalFaceuConfig.whitenStrength = config.whitenStrength;
                internalFaceuConfig.contrastStrength = config.contrastStrength;
                internalFaceuConfig.saturationStrength = config.saturationStrength;
                internalFaceuConfig.enlargeEyeRatio = config.enlargeEyeRatio;
                internalFaceuConfig.shrinkFaceRatio = config.shrinkFaceRatio;
                internalFaceuConfig.shrinkJawRatio = config.shrinkJawRatio;
                internalFaceuConfig.narrowFaceStrength = config.narrowFaceStrength;
                internalFaceuConfig.stickerItem = config.beautyStickerPath;
                mFaceTracker.setFaceuConfig(internalFaceuConfig);
                if (config.needSpecialSticker) {
                    mFaceTracker.enableGraphicBuffer(false);
                    mFaceTracker.enableFaceExtraPoint(true);
                } else {
                    mFaceTracker.enableGraphicBuffer(true);
                    mFaceTracker.enableFaceExtraPoint(false);
                }
                //--rayman
            }
        });
    }

    public void updateMattingConfig(final LangObjectSegmentationConfig params, final InputStream stream, final InputStream giftStream) {
        queueEvent(new Runnable() {
            @Override
            public void run() {
                synchronized(LangMagicCameraView.class) {
                    mattingEnabled = stream != null;
                    if (stream != null)
                        mMattingTracker.setAnimationData(stream, giftStream);
                }
            }
        });
    }

    public void updateMattingConfig(final LangObjectSegmentationConfig params, final String inputPath, final String giftPath, final AnimationCallback animationCallback) {
        this.animationCallback = animationCallback;
        queueEvent(new Runnable() {
            @Override
            public void run() {
                synchronized(LangMagicCameraView.class) {
                    mattingEnabled = inputPath != null;
                    if (inputPath != null)
                        mMattingTracker.setAnimationData(inputPath, giftPath);
                }
            }
        });
    }

    public void updateBeautyHairConfig(final LangObjectSegmentationConfig params) {
        queueEvent(new Runnable() {
            @Override
            public void run() {
                synchronized(LangMagicCameraView.class) {
                    beautyHairEnabled = params != null;
                    if (params != null)
                        mBeautyHairTracker.switchParams(params);
                }
            }
        });
    }

    public void screenshot(final String folder) {
        mScreenshot = true;
        mScreenshotPath = folder;
        if (mCaptureHandler != null) {
            mCaptureHandler.notifyCaptureStarted(folder);
        }
    }

    private void savePictureFromDisplay(final String folder) {
        int pixelsBuffer[] = new int[surfaceWidth * surfaceHeight];
        int pixelsSource[] = new int[surfaceWidth * surfaceHeight];
        IntBuffer buffer = IntBuffer.wrap(pixelsBuffer);
        if (buffer != null) {
            buffer.position(0);
            GLES20.glReadPixels(0, 0, surfaceWidth, surfaceHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer);
            int offset1, offset2;
            for (int i = 0; i < surfaceHeight; i++) {
                offset1 = i * surfaceWidth;
                offset2 = (surfaceHeight - i - 1) * surfaceWidth;
                for (int j = 0; j < surfaceWidth; j++) {
                    int texturePixel = pixelsBuffer[offset1 + j];
                    int blue = (texturePixel >> 16) & 0xff;
                    int red = (texturePixel << 16) & 0x00ff0000;
                    int pixel = (texturePixel & 0xff00ff00) | red | blue;
                    pixelsSource[offset2 + j] = pixel;
                }
            }
            Bitmap bitmap = Bitmap.createBitmap(pixelsSource, surfaceWidth, surfaceHeight, Bitmap.Config.ARGB_8888);

            String file = null;
            try {
                java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS");
                file = folder + format.format(new java.util.Date()) + ".png";
                FileOutputStream stream = new FileOutputStream(new java.io.File(file));
                bitmap.compress(Bitmap.CompressFormat.PNG, 80, stream);
                try {
                    stream.close();
                } catch (IOException e) {
                    if (mCaptureHandler != null) {
                        mCaptureHandler.notifyCaptureIOException(e);
                    }
                    return;
                }
            } catch (FileNotFoundException e) {
                if (mCaptureHandler != null) {
                    mCaptureHandler.notifyCaptureIOException(e);
                }
                return;
            }
            if (mCaptureHandler != null) {
                mCaptureHandler.notifyCaptureFinished(file);
            }
        }
    }

    private void updateEncoderOutputSize() {
        if (mEncoderType == LangVideoEncoderImpl.EncoderType.kHardwarePipeline) {
            // make sure imageWidth > imageHeight
            if (LangEngineParams.vOutputWidth > LangEngineParams.vOutputHeight) {
                mEncoderWidth = imageWidth;
                mEncoderHeight = imageHeight;
            } else {
                mEncoderWidth = imageHeight;
                mEncoderHeight = imageWidth;
            }
        } else {
            mEncoderWidth = LangEngineParams.vOutputWidth;
            mEncoderHeight = LangEngineParams.vOutputHeight;
        }
    }
    //--

}