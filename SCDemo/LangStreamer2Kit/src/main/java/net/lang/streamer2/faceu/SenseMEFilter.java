package net.lang.streamer2.faceu;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.util.Log;

import net.lang.gpuimage.filter.base.gpuimage.GPUImageFilter;

import com.sensetime.stmobile.STBeautifyNative;
import com.sensetime.stmobile.STBeautyParamsType;
import com.sensetime.stmobile.STCommon;
import com.sensetime.stmobile.STHumanActionParamsType;
import com.sensetime.stmobile.STMobileFaceAttributeNative;
import com.sensetime.stmobile.STMobileHumanActionNative;
import com.sensetime.stmobile.STMobileObjectTrackNative;
import com.sensetime.stmobile.STMobileStickerNative;
import com.sensetime.stmobile.STMobileStreamFilterNative;
import com.sensetime.stmobile.STRotateType;
import com.sensetime.stmobile.model.STFaceAttribute;
import com.sensetime.stmobile.model.STHumanAction;

import com.sensetime.stmobile.model.STMobile106;
import com.sensetime.stmobile.model.STStickerInputParams;
import com.sensetime.utils.Accelerometer;
import com.sensetime.utils.STLicenseUtils;

import net.lang.gpuimage.utils.OpenGlUtils;
import com.yunfan.graphicbuffer.GraphicBufferWrapper;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

public class SenseMEFilter extends GPUImageFilter {
    private static final String TAG = SenseMEFilter.class.getSimpleName();

    private static final boolean VERBOSE = false;

    private static final String MODEL_NAME_ACTION= "M_SenseME_Action_5.5.1.model";
    private static final String MODEL_NAME_FACE_ATTRIBUTE = "M_SenseME_Attribute_1.0.1.model";
    private static final String MODEL_NAME_EYEBALL_CENTER = "M_Eyeball_Center.model";
    private static final String MODEL_NAME_EYEBALL_CONTOUR = "M_SenseME_Iris_2.0.0.model";
    private static final String MODEL_NAME_FACE_EXTRA = "M_SenseME_Face_Extra_5.22.0.model";

    private static final String MODEL_NAME_BODY_FOURTEEN = "M_SenseME_Body_Fourteen_1.2.0.model";
    private static final String MODEL_NAME_HAND = "M_SenseME_Hand_5.4.0.model";
    private static final String MODEL_NAME_AVATAR_CORE = "M_SenseME_Avatar_Core_2.0.0.model";
    private static final String MODEL_NAME_BODY_73_POINTS = "M_SenseME_Body_Contour_73_1.2.0.model";

    private Context mContext;
    private Accelerometer mAccelerometer;
    private Status mStatus;
    private boolean mLicenseOk;

    private int[] mFaceuFrameBufferID;
    private int mFaceuBufferTexID = -1;
    private GraphicBufferWrapper mFaceuBuffer;
    private byte[] mImageData;

    private int[] mBeautifyTextureId;
    private int[] mStickerTextureId;

    private String mCurrentSticker;
    private FaceuConfig mFaceuConfig;

    private STMobileStickerNative mStStickerNative;
    private STBeautifyNative mStBeautifyNative;
    private STMobileHumanActionNative mSTHumanActionNative;
    private STHumanAction mHumanActionBeautyOutput;

    private STMobileFaceAttributeNative mSTFaceAttributeNative;
    private STMobileObjectTrackNative mSTMobileObjectTrackNative;

    private boolean mIsCreateHumanActionHandleSucceeded = false;
    private boolean mIsCreateObjectTrackHandleSucceeded = false;

    private boolean mNeedBeautify = false;
    private boolean mNeedBodyBeautify = false;
    private boolean mNeedFaceAttribute = false;
    private boolean mNeedSticker = false;

    private static float[] mBeautifyParams = {0.36f, 0.74f, 0.02f, 0.13f, 0.11f, 0.1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0.5f, 0f, 0f};

    //++ Rayan
    private float mHumanActionRatio = 1.0f;
    private int mDetectImageHeight;
    private int mDetectImageWidth;
    private ByteBuffer mRGBABuffer;
    private int[] mOutputTextureId;
    //++ Rayan

    public static int[] beautyTypes = {
            STBeautyParamsType.ST_BEAUTIFY_REDDEN_STRENGTH,
            STBeautyParamsType.ST_BEAUTIFY_SMOOTH_STRENGTH,
            STBeautyParamsType.ST_BEAUTIFY_WHITEN_STRENGTH,
            STBeautyParamsType.ST_BEAUTIFY_ENLARGE_EYE_RATIO,
            STBeautyParamsType.ST_BEAUTIFY_SHRINK_FACE_RATIO,
            STBeautyParamsType.ST_BEAUTIFY_SHRINK_JAW_RATIO,
            STBeautyParamsType.ST_BEAUTIFY_CONSTRACT_STRENGTH,
            STBeautyParamsType.ST_BEAUTIFY_SATURATION_STRENGTH,
            STBeautyParamsType.ST_BEAUTIFY_DEHIGHLIGHT_STRENGTH,
            STBeautyParamsType.ST_BEAUTIFY_NARROW_FACE_STRENGTH
    };

    private STHumanAction mHumanAction = null;
    private long mDetectConfig = 0;

    private int mHumanActionCreateConfig = STCommon.ST_MOBILE_TRACKING_MULTI_THREAD
            | STCommon.ST_MOBILE_TRACKING_ENABLE_DEBOUNCE
            | STCommon.ST_MOBILE_TRACKING_ENABLE_FACE_ACTION
            | STMobileHumanActionNative.ST_MOBILE_ENABLE_FACE_DETECT
            | STMobileHumanActionNative.ST_MOBILE_ENABLE_HAND_DETECT
            | STMobileHumanActionNative.ST_MOBILE_DETECT_MODE_VIDEO
            | STMobileHumanActionNative.ST_MOBILE_ENABLE_FACE_EXTRA_DETECT;

    private STFaceAttribute[] mArrayFaceAttribute = null;

    private int mFrameCount = 0;
    private final Object mLock = new Object();

    //----------------------------------------------------------------------------------
    private IFaceuListener mListener;
    private int mLastDetectedFaceCount = 0;
    private int mLastDetectedHandCount = 0;
    private long mLastHandAction = 0;
    private boolean enableGraphicBuffer = true;

    int processWidth, processHeight;

    public SenseMEFilter(Context context) {
        mContext = context;

        mStStickerNative = new STMobileStickerNative();
        mStBeautifyNative = new STBeautifyNative();
        mSTHumanActionNative = new STMobileHumanActionNative();
        mHumanActionBeautyOutput = new STHumanAction();
        mSTFaceAttributeNative = new STMobileFaceAttributeNative();
        mSTMobileObjectTrackNative = new STMobileObjectTrackNative();

        mAccelerometer = new Accelerometer(mContext);
        mLicenseOk = STLicenseUtils.checkLicense(mContext);
        mFaceuConfig = new FaceuConfig();
        mStatus = Status.kUnInit;
    }

    @Override
    public void init() {
        synchronized (mLock) {
            super.init();

            if (!mLicenseOk) {
                Log.e(TAG, "SenseME license file check failed!");
                return;
            }
//            initHumanAction(); //因为人脸模型加载较慢，建议异步调用
            initFaceAttribute();
            initObjectTrack();

            updateStatus(Status.kReady);
        }
        initHandlerManager();
        mSubModelsHandler.post(initHumanAction);

        if(mFaceuConfig.stickerItem != null){
            faceExtraModelAdded = true;
            synchronized (mLock) {
                mSubModelsHandler.post(addFaceExtraModel);
            }
        }
    }
    private boolean faceExtraModelAdded = false;
    private HandlerThread mSubModelsHandlerThread;
    private Handler mSubModelsHandler;

    private synchronized void initHandlerManager() {
        mSubModelsHandlerThread = new HandlerThread("SubModelsHandlerThread");
        mSubModelsHandlerThread.start();
        mSubModelsHandler = new Handler(mSubModelsHandlerThread.getLooper());
    }

    private synchronized void deinitHandlerManager() {
        mSubModelsHandlerThread.quitSafely();
        mSubModelsHandlerThread = null;
    }

    private Runnable addFaceExtraModel = new Runnable() {
        public void run() {
            do {
                int result = mSTHumanActionNative.addSubModelFromAssetFile(getFaceExtraModelName(), mContext.getAssets());
                if (VERBOSE)
                    Log.i(TAG, "add sub model result: " + result);

                if (result == 0) {
                    mDetectConfig |= STMobileHumanActionNative.ST_MOBILE_DETECT_EXTRA_FACE_POINTS;
                }
            } while (!mIsCreateHumanActionHandleSucceeded);
        }
    };

    private Runnable removeFaceExtraModel = new Runnable() {
        public void run() {
            int result = mSTHumanActionNative.removeSubModelByConfig(STMobileHumanActionNative.ST_MOBILE_ENABLE_FACE_EXTRA_DETECT);
            Log.i(TAG, "remove sub model result: " + result);

            if (result == 0) {
                mDetectConfig &= ~STMobileHumanActionNative.ST_MOBILE_DETECT_EXTRA_FACE_POINTS;
            }
        }
    };

    public void enableFaceExtraPoint(boolean enable){
        if (enable) {
            if (mIsInitialized && mFaceuConfig.stickerItem != null && !faceExtraModelAdded) {
                faceExtraModelAdded = true;
                mSubModelsHandler.post(addFaceExtraModel);
            }
        } else {
            mSubModelsHandler.post(removeFaceExtraModel);
            faceExtraModelAdded = false;
        }
    }

    @Override
    protected void onInit() {
        super.onInit();

        //初始化GL相关的句柄，包括美颜，贴纸，滤镜
        initBeauty();
        initSticker();

        mAccelerometer.start();

        mCurrentSticker = null;
        mFaceuConfig.defaultBeautyParams();
    }

    @Override
    public void onInputSizeChanged(int width, int height) {
        super.onInputSizeChanged(width, height);

        //++ Rayan
        mHumanActionRatio = 2.f;
        if (width >= height) {
            mDetectImageWidth = (int) (width / mHumanActionRatio);
            mDetectImageHeight = height * mDetectImageWidth / width;
        } else {
            mDetectImageHeight = (int) (height / mHumanActionRatio);
            mDetectImageWidth = width * mDetectImageHeight / height;
        }
        //++ Rayan
    }

    @Override
    public int onDrawFrame(final int textureId, final FloatBuffer cubeBuffer, final FloatBuffer textureBuffer) {
        synchronized (mLock) {
            int processedTextureId = textureId;

            int[] prevoiusFb = new int[1];
            GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, prevoiusFb, 0);
            int[] previousViewport = new int[4];
            GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, previousViewport, 0);

            if (prepared()) {
                synchronized(this) {
                    if (processWidth != getInputWidth() || processHeight != getInputHeight()) {
                        releaseEffectTexturesInternal();
                        processWidth = getInputWidth();
                        processHeight = getInputHeight();
                    }

                    prepareEffectTexturesInternal(processWidth, processHeight);
                    if (enableGraphicBuffer)
                        preprocessFaceuBuffer(textureId, processWidth, processHeight, cubeBuffer, textureBuffer);
                    else
                        preprocessFaceuBuffer(textureId, mDetectImageWidth, mDetectImageHeight, cubeBuffer, textureBuffer);
                }
                processedTextureId = processTexture(processedTextureId, processWidth, processHeight);
            }

            if (prevoiusFb[0] != 0) {
                GLES20.glViewport(previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3]);
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, prevoiusFb[0]);
                super.onDrawFrame(processedTextureId, cubeBuffer, textureBuffer);
            }

            return processedTextureId;
        }
    }

    @Override
    public int onDrawFrame(int textureId) {
        return this.onDrawFrame(textureId, mGLCubeBuffer, mGLTextureBuffer);
    }


    @Override
    protected void onDestroy() {
        synchronized (mLock) {
            super.onDestroy();

            releaseEffectTexturesInternal();

//            deinitHumanAction();
            mSubModelsHandler.post(deinitHumanAction);
            deinitFaceAttribute();
            deinitObjectTrack();
            deinitBeauty();

            // TODO: for iM app, mark it out
//            deinitSticker();
            deinitHandlerManager();

            mAccelerometer.stop();

            mContext = null;
            updateStatus(Status.kRelease);
        }
    }

    public synchronized void enableGraphicBuffer(boolean enabled) {
        this.enableGraphicBuffer = enabled;
        if (enableGraphicBuffer) {
            destroyResizedFrameBuffer();
        } else {
            deinitFaceuBuffer();
        }
    }

    private int[] mResizedFrameBuffer;
    private int[] mResizedFrameBufferTextureId;

    private void initResizedFrameBuffer(int width, int height) {
        if (mResizedFrameBuffer == null) {
            mResizedFrameBuffer = new int[1];
            mResizedFrameBufferTextureId = new int[1];
            GLES20.glGenFramebuffers(1, mResizedFrameBuffer, 0);
            GLES20.glGenTextures(1, mResizedFrameBufferTextureId, 0);
            bindFrameBuffer(mResizedFrameBufferTextureId[0], mResizedFrameBuffer[0], width, height);
        }
    }

    private void bindFrameBuffer(int textureId, int frameBuffer, int width, int height) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffer);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D,textureId, 0);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }

    private void destroyResizedFrameBuffer() {
        if (mResizedFrameBufferTextureId != null) {
            GLES20.glDeleteTextures(1, mResizedFrameBufferTextureId, 0);
            mResizedFrameBufferTextureId = null;
        }
        if (mResizedFrameBuffer != null) {
            GLES20.glDeleteFramebuffers(1, mResizedFrameBuffer, 0);
            mResizedFrameBuffer = null;
        }
    }

    public void setDetectionListener(IFaceuListener listener) {
        mListener = listener;
    }

    public String getCurrentSticker(){return mCurrentSticker;}

    //set facuconfig to SenseMEFilter
    public void setFaceuConfig(FaceuConfig faceuConfig) {
        synchronized (mLock) {
            mFaceuConfig.enableBeauty = faceuConfig.enableBeauty;
            mFaceuConfig.enableBodyBeauty = faceuConfig.enableBodyBeauty;
            mFaceuConfig.enableSticker = faceuConfig.enableSticker;

            mFaceuConfig.reddenStrength = faceuConfig.reddenStrength;
            mFaceuConfig.smoothStrength = faceuConfig.smoothStrength;
            mFaceuConfig.whitenStrength = faceuConfig.whitenStrength;
            mFaceuConfig.contrastStrength = faceuConfig.contrastStrength;
            mFaceuConfig.saturationStrength = faceuConfig.saturationStrength;
            mFaceuConfig.enlargeEyeRatio = faceuConfig.enlargeEyeRatio;
            mFaceuConfig.shrinkFaceRatio = faceuConfig.shrinkFaceRatio;
            mFaceuConfig.shrinkJawRatio = faceuConfig.shrinkJawRatio;
            mFaceuConfig.narrowFaceStrength = faceuConfig.narrowFaceStrength;

            mFaceuConfig.stickerItem = faceuConfig.stickerItem;
        }
    }

    public void setFaceuSticker(String stickerItem) {
        synchronized (mLock) {
            mFaceuConfig.enableSticker = !TextUtils.isEmpty(stickerItem);
            mFaceuConfig.stickerItem = stickerItem;
        }
    }

    @Deprecated
    public void setEffect(String item) {
        mFaceuConfig.enableBeauty = false;
        mFaceuConfig.enableBodyBeauty = false;
        mFaceuConfig.enableSticker = true;
        mFaceuConfig.stickerItem = item;
        /*
        enableSticker(mFaceuConfig.enableSticker);
        */
    }

    public FaceuAttribute[] getCurrentFaceAttribute(int index) {
        synchronized (mLock) {
            if (status() != Status.kReady)
                return null;
            if (mArrayFaceAttribute == null)
                return null;
            if (index >= mArrayFaceAttribute.length)
                return null;
            STFaceAttribute stfaceAttribute = mArrayFaceAttribute[index];
            if (stfaceAttribute.attribute_count > 0) {
                java.util.List<FaceuAttribute> attributeList = new java.util.ArrayList<FaceuAttribute>();
                for(int i = 0; i < stfaceAttribute.arrayAttribute.length; i++){
                    FaceuAttribute attribute = new FaceuAttribute();
                    attribute.category = stfaceAttribute.arrayAttribute[i].category;
                    attribute.label = stfaceAttribute.arrayAttribute[i].label;
                    attribute.score = stfaceAttribute.arrayAttribute[i].score;
                    attributeList.add(attribute);
                }
                FaceuAttribute[] faceuAttribute = attributeList.toArray(new FaceuAttribute[attributeList.size()]);
                return faceuAttribute;
            }
            return null;
        }
    }

    private boolean prepared() {
        if (status() != Status.kReady) {
            Log.e(TAG, "SenseMe: processFromTexture failed due to invalid status:" + status().name());
            return false;
        }

        if (getInputWidth() == 0 || getInputHeight() == 0) {
            Log.e(TAG, "filter input size has not configured");
            return false;
        }

        return true;
    }

    private void preprocessFaceuBuffer(final int textureId, int width, int height, final FloatBuffer cubeBuffer, final FloatBuffer textureBuffer) {
        if (!enableGraphicBuffer) {
            if (mRGBABuffer == null) {
                mRGBABuffer = ByteBuffer.allocate(width * height * 4);
            }
            mRGBABuffer.rewind();
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mResizedFrameBuffer[0]);
            GLES20.glViewport(0, 0, width, height);
            OpenGlUtils.checkGlError("glBindFramebuffer");

            super.onDrawFrame(textureId, cubeBuffer, textureBuffer);

            GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, mRGBABuffer);
            performFaceDetection(mRGBABuffer.array(), width, height, STCommon.ST_PIX_FMT_RGBA8888);
        } else {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFaceuFrameBufferID[0]);
            GLES20.glViewport(0, 0, width, height);
            OpenGlUtils.checkGlError("glBindFramebuffer");

            super.onDrawFrame(textureId, cubeBuffer, textureBuffer);

            mImageData = mFaceuBuffer.getVideoData(true);
            performFaceDetection(mImageData, width, height, STCommon.ST_PIX_FMT_YUV420P);
        }
    }

    private int processTexture(final int textureId, int width, int height) {
        int outTextureId = textureId;
        STHumanAction humanAction = mHumanAction;

        if (mNeedBeautify != mFaceuConfig.enableBeauty) {
            enableBeautify(mFaceuConfig.enableBeauty);
        }
        if (mNeedBodyBeautify != mFaceuConfig.enableBodyBeauty) {
            enableBodyBeautify(mFaceuConfig.enableBodyBeauty);
        }
        if (mNeedSticker != mFaceuConfig.enableSticker) {
            enableSticker(mFaceuConfig.enableSticker);
        }

        if ((mNeedBeautify || mNeedSticker || mNeedFaceAttribute || mNeedBodyBeautify) && mIsCreateHumanActionHandleSucceeded) {
            int result = 0;
            int orientation = getCurrentOrientation();

            //美颜
            if (mNeedBeautify || mNeedBodyBeautify) {// do beautify

                updateBeautyParams();

                long beautyStartTime = System.currentTimeMillis();
                result = mStBeautifyNative.processTexture(textureId, width, height, orientation, humanAction, mBeautifyTextureId[0], mHumanActionBeautyOutput);
                long beautyEndTime = System.currentTimeMillis();
                if (VERBOSE) Log.i(TAG, "beautify cost time: " + (beautyEndTime-beautyStartTime));
                if (result == 0) {
                    outTextureId = mBeautifyTextureId[0];
                    humanAction = mHumanActionBeautyOutput;
                    if (VERBOSE) Log.i(TAG, "replace enlarge eye and shrink face action");
                }
                if (VERBOSE) Log.i(TAG, "beautify cost time: " + (System.currentTimeMillis()-beautyStartTime));
            }

            //调用贴纸API绘制贴纸
            if (mNeedSticker) {
                //humanAction = mHumanActionBeautyOutput != null ? mHumanActionBeautyOutput : humanAction;
                boolean cullFaceFlag = GLES20.glIsEnabled(GLES20.GL_CULL_FACE);
                if (cullFaceFlag) {
                    GLES20.glDisable(GLES20.GL_CULL_FACE);
                }

                updateSticker();

                /**
                 * 1.在切换贴纸时，调用STMobileStickerNative的changeSticker函数，传入贴纸路径(参考setShowSticker函数的使用)
                 * 2.切换贴纸后，使用STMobileStickerNative的getTriggerAction函数获取当前贴纸支持的手势和前后背景等信息，返回值为int类型
                 * 3.根据getTriggerAction函数返回值，重新配置humanActionDetect函数的config参数，使detect更高效
                 *
                 * 例：只检测人脸信息和当前贴纸支持的手势等信息时，使用如下配置：
                 * mDetectConfig = mSTMobileStickerNative.getTriggerAction()|STMobileHumanActionNative.ST_MOBILE_FACE_DETECT;
                 */
                long stickerStartTime = System.currentTimeMillis();
                int frontStickerRotation = STRotateType.ST_CLOCKWISE_ROTATE_0;
                STStickerInputParams inputEvent = new STStickerInputParams(new float[]{0,0,0,1}, false, 0);
                result = mStStickerNative.processTexture(outTextureId, humanAction, orientation, frontStickerRotation, width, height,
                        false, inputEvent, mStickerTextureId[0]);

                if (VERBOSE) {
                    Log.i(TAG, "processTexture result: " + result);
                    Log.i(TAG, "sticker cost time: " + (System.currentTimeMillis() - stickerStartTime));
                }
                if (result == 0) {
                    outTextureId = mStickerTextureId[0];
                }

                if (cullFaceFlag) {
                    GLES20.glEnable(GLES20.GL_CULL_FACE);
                }
            }
        }

        return outTextureId;
    }

    private void prepareEffectTexturesInternal(int width, int height) {
        //-- Rayan
        if (enableGraphicBuffer) {
            if (mFaceuBuffer == null)
                initFaceuBuffer(width, height);
        } else {
            initResizedFrameBuffer(mDetectImageWidth, mDetectImageHeight);
        }
        //-- Rayan

        if (mBeautifyTextureId == null) {
            mBeautifyTextureId = new int[1];
            initEffectTexture(width, height, mBeautifyTextureId, GLES20.GL_TEXTURE_2D);
        }

        if (mStickerTextureId == null) {
            mStickerTextureId = new int[1];
            initEffectTexture(width, height, mStickerTextureId, GLES20.GL_TEXTURE_2D);
        }

        if (mOutputTextureId == null) {
            mOutputTextureId = new int[1];
        }
    }

    private void initFaceuBuffer(int width, int height) {
        mFaceuBuffer = GraphicBufferWrapper.createInstance(mContext, width, height, PixelFormat.RGBA_8888);
        if (mFaceuBuffer != null) {
            mFaceuFrameBufferID = new int[1];
            GLES20.glGenFramebuffers(1, mFaceuFrameBufferID, 0);
            mFaceuBufferTexID = mFaceuBuffer.createTexture(mFaceuFrameBufferID[0]);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                    GLES20.GL_TEXTURE_2D, mFaceuBufferTexID, 0);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        }

        mImageData = new byte[width * height * 4];
    }

    private static void initEffectTexture(int width, int height, int[] textureId, int type) {
        int len = textureId.length;
        if (len > 0) {
            GLES20.glGenTextures(len, textureId, 0);
        }
        for (int aTextureId : textureId) {
            GLES20.glBindTexture(type, aTextureId);
            GLES20.glTexParameteri(type,
                    GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(type,
                    GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(type,
                    GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(type,
                    GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexImage2D(type, 0, GLES20.GL_RGBA, width, height, 0,
                    GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        }
    }

    private void releaseEffectTexturesInternal() {
        if (mFaceuBuffer != null) {
            deinitFaceuBuffer();
        }

        if (mResizedFrameBuffer != null) {
            destroyResizedFrameBuffer();
        }

        if (mBeautifyTextureId != null) {
            GLES20.glDeleteFramebuffers(1, mBeautifyTextureId, 0);
        }

        if (mStickerTextureId != null) {
            GLES20.glDeleteFramebuffers(1, mStickerTextureId, 0);
        }
    }

    private void deinitFaceuBuffer() {
        if (mFaceuBuffer != null) {
            mFaceuBuffer.destroy();
            GLES20.glDeleteFramebuffers(1, mFaceuFrameBufferID, 0);
            mFaceuFrameBufferID = null;
            mFaceuBuffer = null;
        }
    }

    private void performFaceDetection(final byte[] data, int width, int height, int format) {
        if(mIsCreateHumanActionHandleSucceeded) {
            int orientation = getCurrentOrientation();//getHumanActionOrientation();
            long humanActionCostTime = System.currentTimeMillis();
            STHumanAction humanAction = mSTHumanActionNative.humanActionDetect(data, format,
                    mDetectConfig, orientation, width, height);
            if (VERBOSE) Log.v(TAG, "human action cost time: " + (System.currentTimeMillis() - humanActionCostTime));

            if (!enableGraphicBuffer)
                humanAction = STHumanAction.humanActionResize(mHumanActionRatio, humanAction);

            mHumanAction = humanAction;

            if (VERBOSE) {
                if (mFrameCount % 20 == 0) {
                    faceAttributeDetection(data, width, height, STCommon.ST_PIX_FMT_RGBA8888, humanAction);
                }
                mFrameCount++;
                if (mFrameCount == 1000) mFrameCount = 0;
            }

            // post detection message to listener.
            if(mListener != null) {
                int faceCount = humanAction.faceCount;
                if (mLastDetectedFaceCount != faceCount) {
                    // face count changed.
                    mLastDetectedFaceCount = faceCount;
                    mListener.onHumanFaceDetected(mLastDetectedFaceCount);
                }

                int handCount = humanAction.handCount;
                if (mLastDetectedHandCount != handCount) {
                    // hand count changed.
                    mLastDetectedHandCount = handCount;
                    if (handCount > 0) {
                        mLastHandAction = humanAction.hands[0].handAction;
                    }
                    mListener.onHumanHandDetected(handCount, getGesture(humanAction));
                } else {
                    // hand count has not changed.
                    if (handCount > 0) {
                        long handAction = humanAction.hands[0].handAction;
                        if (mLastHandAction != handAction) {
                            mLastHandAction = handAction;
                            mListener.onHumanHandDetected(handCount, getGesture(humanAction));
                        }
                    }
                }

                /*
                if (mHandler != null) {
                    if (humanAction.faceCount > 0) {
                        if (!mLastFaceDetected) {
                            mHandler.notifyHumanFaceDetected();
                            mLastFaceDetected = true;
                        }
                    } else {
                        if (mLastFaceDetected) {
                            mHandler.notifyHumanFaceLost();
                            mLastFaceDetected = false;
                        }
                    }

                    if (humanAction.handCount > 0) {
                        long handAction = humanAction.hands[0].handAction;
                        if (!mLastHandDetected) {
                            mHandler.notifyHumanHandDetected(getGesture(humanAction));
                            mLastHandDetected = true;
                        } else if (mLastHandAction != handAction) {
                            mHandler.notifyHumanHandDetected(getGesture(humanAction));
                        }
                        mLastHandAction = handAction;
                    } else {
                        if (mLastHandDetected) {
                            mHandler.notifyHumanHandLost();
                            mLastHandDetected = false;
                            mLastHandAction = 0;
                        }
                    }
                }
                 */
            }
        }
    }

    private void faceAttributeDetection(final byte[] data, int width, int height, int format, STHumanAction humanAction) {
        if (data != null && humanAction != null) {
            STMobile106[] arrayFaces = humanAction.getMobileFaces();
            if (arrayFaces != null && arrayFaces.length != 0) { // face attribute
                STFaceAttribute[] arrayFaceAttribute = new STFaceAttribute[arrayFaces.length];
                long attributeCostTime = System.currentTimeMillis();
                int result = mSTFaceAttributeNative.detect(data, format, width, height, arrayFaces, arrayFaceAttribute);
                if (VERBOSE) Log.v(TAG, "attribute cost time: " + (System.currentTimeMillis() - attributeCostTime));
                if (result == 0) {
                    if (arrayFaceAttribute[0].attribute_count > 0) {
                        String faceAttribute = STFaceAttribute.getFaceAttributeString(arrayFaceAttribute[0]);
                        if (VERBOSE) Log.v(TAG, "face attribute :" + faceAttribute);
                    }
                }
                mArrayFaceAttribute = arrayFaceAttribute;
            }
        }
    }

    private int getCurrentOrientation() {
        int dir = Accelerometer.getDirection();
        int orientation = dir - 1;
        if (orientation < 0) {
            orientation = dir ^ 3;
        }
        return orientation;

    }

    private void initFaceAttribute() {
        String assetModelPath = "models" + File.separator + MODEL_NAME_FACE_ATTRIBUTE;
        int result = mSTFaceAttributeNative.createInstanceFromAssetFile(assetModelPath, mContext.getAssets());
        Log.i(TAG, "the result for createInstance for faceAttribute is " + result);
    }

    private void deinitFaceAttribute() {
        mSTFaceAttributeNative.destroyInstance();
    }

    private Runnable initHumanAction = new Runnable() {
        public void run() {
            //从asset资源文件夹读取model到内存，再使用底层st_mobile_human_action_create_from_buffer接口创建handle
            int result = mSTHumanActionNative.createInstanceFromAssetFile(getActionModelName(), mHumanActionCreateConfig, mContext.getAssets());
            Log.i(TAG, "the result for createInstance for human_action is " + result);

            if (result == 0) {
                mIsCreateHumanActionHandleSucceeded = true;
                mSTHumanActionNative.setParam(STHumanActionParamsType.ST_HUMAN_ACTION_PARAM_BACKGROUND_BLUR_STRENGTH, 0.35f);

                //for test avatar
                result = mSTHumanActionNative.addSubModelFromAssetFile(getEyeballContourModelName(), mContext.getAssets());
                Log.i(TAG, "add eyeball contour model result: " + result);

                result = mSTHumanActionNative.addSubModelFromAssetFile(getBody73PointsModelName(), mContext.getAssets());
                Log.i(TAG, "add body contour model result: " + result);
            }
        }
    };

    @Deprecated
    private void initHumanAction() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                //从asset资源文件夹读取model到内存，再使用底层st_mobile_human_action_create_from_buffer接口创建handle
                int result = mSTHumanActionNative.createInstanceFromAssetFile(getActionModelName(), mHumanActionCreateConfig, mContext.getAssets());
                Log.i(TAG, "the result for createInstance for human_action is " + result);

                if (result == 0) {
                    mIsCreateHumanActionHandleSucceeded = true;
                    mSTHumanActionNative.setParam(STHumanActionParamsType.ST_HUMAN_ACTION_PARAM_BACKGROUND_BLUR_STRENGTH, 0.35f);

                    //for test face morph
//                        if(mFaceuConfig.stickerItem != null) {
//                            result = mSTHumanActionNative.addSubModelFromAssetFile(getFaceExtraModelName(), mContext.getAssets());
//                            Log.i(TAG, "add face extra model result: " + result);
//                        }else{
//                            result = mSTHumanActionNative.removeSubModelByConfig(STMobileHumanActionNative.ST_MOBILE_ENABLE_FACE_EXTRA_DETECT);
//                            Log.i(TAG, "remove face extra model result: " + result);
//                        }

                    //for test avatar
                    result = mSTHumanActionNative.addSubModelFromAssetFile(getEyeballContourModelName(), mContext.getAssets());
                    Log.i(TAG, "add eyeball contour model result: " + result);

                    result = mSTHumanActionNative.addSubModelFromAssetFile(getBody73PointsModelName(), mContext.getAssets());
                    Log.i(TAG, "add body contour model result: " + result);
                }
            }
        }).start();
    }

    private Runnable deinitHumanAction = new Runnable() {
        @Override
        public void run() {
            if (mIsCreateHumanActionHandleSucceeded) {
                mSTHumanActionNative.destroyInstance();
                mIsCreateHumanActionHandleSucceeded = false;
            }
        }
    };

    @Deprecated
    private void deinitHumanAction() {
        if (mIsCreateHumanActionHandleSucceeded) {
            mSTHumanActionNative.destroyInstance();
            mIsCreateHumanActionHandleSucceeded = false;
        }
    }

    private void initSticker() {
        int result = mStStickerNative.createInstance(mContext);

        //从资源文件加载Avatar模型
        mStStickerNative.loadAvatarModelFromAssetFile(getAvatarCoreModelName(), mContext.getAssets());
    }

    public void deinitSticker() {
        mStStickerNative.destroyInstance();
    }

    private void initBeauty() {
        int result = mStBeautifyNative.createInstance();
        Log.i(TAG, "the result is for initBeautify " + result);
        if (result == 0) {
            mStBeautifyNative.setParam(STBeautyParamsType.ST_BEAUTIFY_REDDEN_STRENGTH, mBeautifyParams[0]);
            mStBeautifyNative.setParam(STBeautyParamsType.ST_BEAUTIFY_SMOOTH_STRENGTH, mBeautifyParams[1]);
            mStBeautifyNative.setParam(STBeautyParamsType.ST_BEAUTIFY_WHITEN_STRENGTH, mBeautifyParams[2]);
            mStBeautifyNative.setParam(STBeautyParamsType.ST_BEAUTIFY_ENLARGE_EYE_RATIO, mBeautifyParams[3]);
            mStBeautifyNative.setParam(STBeautyParamsType.ST_BEAUTIFY_SHRINK_FACE_RATIO, mBeautifyParams[4]);
            mStBeautifyNative.setParam(STBeautyParamsType.ST_BEAUTIFY_SHRINK_JAW_RATIO, mBeautifyParams[5]);
            mStBeautifyNative.setParam(STBeautyParamsType.ST_BEAUTIFY_CONSTRACT_STRENGTH, mBeautifyParams[6]);
            mStBeautifyNative.setParam(STBeautyParamsType.ST_BEAUTIFY_SATURATION_STRENGTH, mBeautifyParams[7]);
            mStBeautifyNative.setParam(STBeautyParamsType.ST_BEAUTIFY_DEHIGHLIGHT_STRENGTH, mBeautifyParams[8]);
            mStBeautifyNative.setParam(STBeautyParamsType.ST_BEAUTIFY_NARROW_FACE_STRENGTH, mBeautifyParams[9]);
        }
    }

    private void deinitBeauty() {
        mStBeautifyNative.destroyBeautify();
    }

    private void initObjectTrack() {
        int result = mSTMobileObjectTrackNative.createInstance();
        if (result == 0) {
            mIsCreateObjectTrackHandleSucceeded = true;
        }
    }

    private void deinitObjectTrack() {
        if (mIsCreateObjectTrackHandleSucceeded) {
            mSTMobileObjectTrackNative.destroyInstance();
        }
    }

    private void enableBeautify(boolean needBeautify) {
        mNeedBeautify = needBeautify;
        setHumanActionDetectConfig(mNeedBeautify|mNeedSticker|mNeedFaceAttribute, mStStickerNative.getTriggerAction());
    }

    private void enableBodyBeautify(boolean needBodyBeautify) {
        mNeedBodyBeautify = needBodyBeautify;
        if(mNeedBodyBeautify){
            mDetectConfig |= STMobileHumanActionNative.ST_MOBILE_BODY_KEYPOINTS;
            mDetectConfig |= STMobileHumanActionNative.ST_MOBILE_BODY_DETECT_FULL;
        }else{
            mDetectConfig &= ~STMobileHumanActionNative.ST_MOBILE_BODY_KEYPOINTS;
            mDetectConfig &= ~STMobileHumanActionNative.ST_MOBILE_BODY_DETECT_FULL;
        }
    }

    private void enableSticker(boolean needSticker){
        mNeedSticker = needSticker;
        setHumanActionDetectConfig(mNeedBeautify|mNeedSticker|mNeedFaceAttribute, mStStickerNative.getTriggerAction());
    }

    private void updateBeautyParams() {
        if (mBeautifyParams[0] != mFaceuConfig.reddenStrength) {
            mBeautifyParams[0] = mFaceuConfig.reddenStrength;
            mStBeautifyNative.setParam(beautyTypes[0], mBeautifyParams[0]);
        }
        if (mBeautifyParams[1] != mFaceuConfig.smoothStrength) {
            mBeautifyParams[1] = mFaceuConfig.smoothStrength;
            mStBeautifyNative.setParam(beautyTypes[1], mBeautifyParams[1]);
        }
        if (mBeautifyParams[2] != mFaceuConfig.whitenStrength) {
            mBeautifyParams[2] = mFaceuConfig.whitenStrength;
            mStBeautifyNative.setParam(beautyTypes[2], mBeautifyParams[2]);
        }
        if (mBeautifyParams[6] != mFaceuConfig.contrastStrength) {
            mBeautifyParams[6] = mFaceuConfig.contrastStrength;
            mStBeautifyNative.setParam(beautyTypes[6], mBeautifyParams[6]);
        }
        if (mBeautifyParams[7] != mFaceuConfig.saturationStrength) {
            mBeautifyParams[7] = mFaceuConfig.saturationStrength;
            mStBeautifyNative.setParam(beautyTypes[7], mBeautifyParams[7]);
        }
        if (mBeautifyParams[3] != mFaceuConfig.enlargeEyeRatio) {
            mBeautifyParams[3] = mFaceuConfig.enlargeEyeRatio;
            mStBeautifyNative.setParam(beautyTypes[3], mBeautifyParams[3]);
        }
        if (mBeautifyParams[4] != mFaceuConfig.shrinkFaceRatio) {
            mBeautifyParams[4] = mFaceuConfig.shrinkFaceRatio;
            mStBeautifyNative.setParam(beautyTypes[4], mBeautifyParams[4]);
        }
        if (mBeautifyParams[5] != mFaceuConfig.shrinkJawRatio) {
            mBeautifyParams[5] = mFaceuConfig.shrinkJawRatio;
            mStBeautifyNative.setParam(beautyTypes[5], mBeautifyParams[5]);
        }
        if (mBeautifyParams[9] != mFaceuConfig.narrowFaceStrength) {
            mBeautifyParams[9] = mFaceuConfig.narrowFaceStrength;
            mStBeautifyNative.setParam(beautyTypes[9], mBeautifyParams[9]);
        }
    }

    private void updateSticker() {
        if (mCurrentSticker != mFaceuConfig.stickerItem) {
            mCurrentSticker = mFaceuConfig.stickerItem;
            mStStickerNative.changeSticker(mCurrentSticker);
        }
    }

    /**
     * human action detect的配置选项,根据Sticker的TriggerAction和是否需要美颜配置
     *
     * @param needFaceDetect  是否需要开启face detect
     * @param config  sticker的TriggerAction
     */
    private void setHumanActionDetectConfig(boolean needFaceDetect, long config) {

        if (needFaceDetect) {
            mDetectConfig = config | STMobileHumanActionNative.ST_MOBILE_FACE_DETECT
                    | STMobileHumanActionNative.ST_MOBILE_HAND_DETECT_FULL
                    | STMobileHumanActionNative.ST_MOBILE_FACE_DETECT_FULL
//                    | STMobileHumanActionNative.ST_MOBILE_DETECT_EXTRA_FACE_POINTS
            ;
        } else {
            mDetectConfig = config;
        }
    }

    //---------------------------------------------------------------------
    private IFaceuListener.FaceuGestureType getGesture(STHumanAction humanAction) {
        if (humanAction.handCount > 0) {
            long handAction = humanAction.hands[0].handAction;
            if ((handAction & STMobileHumanActionNative.ST_MOBILE_HAND_PALM) > 0)
                return IFaceuListener.FaceuGestureType.FACEU_GESTURE_PALM;
            else if ((handAction & STMobileHumanActionNative.ST_MOBILE_HAND_GOOD) > 0)
                return IFaceuListener.FaceuGestureType.FACEU_GESTURE_GOOD;
            else if ((handAction & STMobileHumanActionNative.ST_MOBILE_HAND_OK) > 0)
                return IFaceuListener.FaceuGestureType.FACEU_GESTURE_OK;
            else if ((handAction & STMobileHumanActionNative.ST_MOBILE_HAND_PISTOL) > 0)
                return IFaceuListener.FaceuGestureType.FACEU_GESTURE_PISTOL;
            else if ((handAction & STMobileHumanActionNative.ST_MOBILE_HAND_FINGER_INDEX) > 0)
                return IFaceuListener.FaceuGestureType.FACEU_GESTURE_FINGER_INDEX;
            else if ((handAction & STMobileHumanActionNative.ST_MOBILE_HAND_FINGER_HEART) > 0)
                return IFaceuListener.FaceuGestureType.FACEU_GESTURE_FINGER_HEART;
            else if ((handAction & STMobileHumanActionNative.ST_MOBILE_HAND_LOVE) > 0)
                return IFaceuListener.FaceuGestureType.FACEU_GESTURE_LOVE;
            else if ((handAction & STMobileHumanActionNative.ST_MOBILE_HAND_SCISSOR) > 0)
                return IFaceuListener.FaceuGestureType.FACEU_GESTURE_SCISSOR;
            else if ((handAction & STMobileHumanActionNative.ST_MOBILE_HAND_CONGRATULATE) > 0)
                return IFaceuListener.FaceuGestureType.FACEU_GESTURE_CONGRATULATE;
            else if ((handAction & STMobileHumanActionNative.ST_MOBILE_HAND_HOLDUP) > 0)
                return IFaceuListener.FaceuGestureType.FACEU_GESTURE_HOLDUP;
            else if ((handAction & STMobileHumanActionNative.ST_MOBILE_HAND_FIST) > 0) {
                return IFaceuListener.FaceuGestureType.FACEU_GESTURE_FIST;
            }
        }
        return IFaceuListener.FaceuGestureType.FACEU_GESTURE_NONE;
    }

    //---------------------------------------------------------------------
    private static String getActionModelName() {
        return "models" + File.separator + MODEL_NAME_ACTION;
    }

    private static String getFaceExtraModelName() {
        return "models" + File.separator + MODEL_NAME_FACE_EXTRA;
    }

    private static String getEyeballContourModelName() {
        return "models" + File.separator + MODEL_NAME_EYEBALL_CONTOUR;
    }

    private static String getBody73PointsModelName() {
        return "models" + File.separator + MODEL_NAME_BODY_73_POINTS;
    }

    private static String getAvatarCoreModelName() {
        return "models" + File.separator + MODEL_NAME_AVATAR_CORE;
    }

    enum Status{
        kUnInit("UnInit"),
        kReady("Ready"),
        kRelease("Release");
        String mName;
        Status(String name) {
            mName = name;
        }
    }

    private Status status() {
        return mStatus;
    }

    private void updateStatus(Status status) {
        Log.d(TAG, "SenseMe tracker change status " + mStatus.mName + " -> " + status.mName);
        mStatus = status;
    }

    //----------------------------------------------------------------
    public final static class FaceuConfig {
        public boolean enableBeauty;
        public boolean enableBodyBeauty;
        public boolean enableSticker;
        public boolean enableFilter;

        // base beauty params
        public float reddenStrength;
        public float smoothStrength;
        public float whitenStrength;
        public float contrastStrength;   //CONSTRACT_STRENGTH
        public float saturationStrength; //SATURATION_STRENGTH

        // advanced beauty params
        public float enlargeEyeRatio;    //ENLARGE_EYE_RATIO
        public float shrinkFaceRatio;    //SHRINK_FACE_RATIO
        public float shrinkJawRatio;     //SHRINK_JAW_RATIO
        public float narrowFaceStrength;

        // sticker item
        public String stickerItem;

        public FaceuConfig() {
            defaultBeautyParams();
        }

        public void defaultBeautyParams() {
            float[] defaultParams = {0.36f, 0.74f, 0.02f, 0.13f, 0.25f, 0.02f, 0f, 0f, 0f, 0f, 0f, 0f, 0.5f, 0f, 0f};
            enableBeauty = true;
            enableBodyBeauty = false;
            enableSticker = true;
            reddenStrength = defaultParams[0];
            smoothStrength = defaultParams[1];
            whitenStrength = defaultParams[2];
            contrastStrength = defaultParams[6];
            saturationStrength = defaultParams[7];
            enlargeEyeRatio = defaultParams[3];
            shrinkFaceRatio = defaultParams[4];
            shrinkJawRatio = defaultParams[5];
            narrowFaceStrength = defaultParams[9];
        }

        public void setBasicParams(float beautyLevel, float redLevel, float whiteLevel) {
            smoothStrength = beautyLevel;
            reddenStrength = redLevel;
            whitenStrength = whiteLevel;
        }

        public void setAdvancedParams(float eyeLevel, float faceLevel, float jawLevel) {
            enlargeEyeRatio = eyeLevel;
            shrinkFaceRatio = faceLevel;
            shrinkJawRatio = jawLevel;
        }
    }

    public static class FaceuAttribute {
        public String category; // 属性描述, 如"age", "gender", "attractive"
        public String label; //属性标签描述， 如"male", "female"，"35"等
        public float score; //该属性标签的置信度
    }
}

