package net.lang.streamer2.camera;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Build;
import android.util.Log;

import net.lang.streamer2.utils.DebugLog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class LangCamera extends ICameraInterface {
    private static final String TAG = LangCamera.class.getSimpleName();
    private static volatile LangCamera sCameraInstance;

    private static final int STATE_OPENED = 1;
    private static final int STATE_PREVIEWING = 2;
    private static final int STATE_RELEASED = -1;
    private static final int INVALID_CAMERA_ID = -1;

    private int mCameraId;
    private int mLastCameraId = -1;
    private int mCameraState = -1;

    private boolean mAutoFocus;
    private int mFacing = Camera.CameraInfo.CAMERA_FACING_FRONT;

    private Camera mCamera;
    private Camera.Parameters mCamParameters;
    private final Camera.CameraInfo mCameraInfo = new Camera.CameraInfo();

    private ArrayList<Camera.Area> mAreas;

    public synchronized static LangCamera getInstance() {
        if (sCameraInstance == null) {
            sCameraInstance = new LangCamera();
        }
        return sCameraInstance;
    }

    @Override
    public boolean openCamera(CameraParams p) {
        chooseCamera();
        if (mCameraId == INVALID_CAMERA_ID) {
            mFacing = mFacing == Camera.CameraInfo.CAMERA_FACING_FRONT ? Camera.CameraInfo.CAMERA_FACING_BACK : Camera.CameraInfo.CAMERA_FACING_FRONT;
            chooseCamera();
        }
        return openCameraInternal(p);
    }

    private void chooseCamera() {
        int count = Camera.getNumberOfCameras();
        DebugLog.d(TAG, "camera numbers:" + Camera.getNumberOfCameras() + ", want facing:" + mFacing + ", " + Camera.CameraInfo.CAMERA_FACING_FRONT);
        for (int i = 0; i < count; i++) {
            Camera.getCameraInfo(i, mCameraInfo);
            DebugLog.d(TAG, "camera facing:" + mCameraInfo.facing);
            if (mCameraInfo.facing == mFacing) {
                mCameraId = i;
                return;
            }
        }
        mCameraId = INVALID_CAMERA_ID;
    }

    private boolean openCameraInternal(CameraParams p) {
        if (mCameraId == INVALID_CAMERA_ID) {
            mCallback.onCameraError(CAM_ER_INVALID_CAMERA_ID);
            return false;
        }
        if (mCamera != null) {
            DebugLog.w(TAG, "camera has already been opened:" + mCamera);
            mCallback.onCameraError(CAM_ER_INVALID_CAMERA_ID);
            return false;
        }
        try {
            mCamera = Camera.open(mCameraId);
            mCamParameters = mCamera.getParameters();
            DebugLog.d(TAG, "open camera:" + mCameraId + "___" + mCamParameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO));
            initPreviewParams(p);
            setCameraParams(mCamera, mCamParameters);
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            Camera.getCameraInfo(mCameraId, cameraInfo);
            p.setOrientation(cameraInfo.orientation);
            mCamera.cancelAutoFocus();
            if(!p.isLandscape()){
                final int rotation = getCameraDisplayOrientation(p.isLandscape() ? 90 : 0, p.getOrientation(), usingFrontCamera());
                Log.d(TAG, "final camera rotation:" + p.getOrientation()+","+rotation);
                mCamera.setDisplayOrientation(rotation);
            }
            mCallback.onCameraOpened(this, mLastCameraId == -1);
            updateCameraState(STATE_OPENED);
            return true;
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
        mCallback.onCameraError(CAM_ER_INVALID_PARAMETERS);
        closeCamera();
        return false;
    }

    private void initPreviewParams(CameraParams p) {
        mCamParameters.setPreviewFormat(p.getFrameFormat()); // 原始数据格式
        //mCamParameters.setRecordingHint(true);
        boolean setPreSizeSuccess = false;
        List<Camera.Size> previewSizeList = mCamParameters.getSupportedPreviewSizes();
        for (int i = 0; i < previewSizeList.size(); i++) {
            //DebugLog.d(TAG, previewSizeList.get(i).width + "---------------" + previewSizeList.get(i).height);
            if (previewSizeList.get(i).width == p.getPreviewWidth() && previewSizeList.get(i).height == p.getPreviewHeight()) {
                mCamParameters.setPreviewSize(p.getPreviewWidth(), p.getPreviewHeight());// 图片大小
                DebugLog.i(TAG, "setPreSizeSuccess:" + p.getPreviewWidth() + ",---" + p.getPreviewHeight());
                setPreSizeSuccess = true;
                break;
            }
        }

        if (!setPreSizeSuccess) {
            /*从列表中选取合适的分辨率*/
//            Size preSize = getSpecificRatioSize(previewSizeList, ((float) p2.getPreviewWidth()) / p2.getPreviewHeight());
            Camera.Size preSize = getSpecificRatioSize(mCamParameters.getSupportedPreviewSizes(), p.getPreviewWidth(), p.getPreviewHeight());
            DebugLog.d(TAG, preSize.width + "," + preSize.height + p.getPreviewWidth() + ","+ p.getPreviewHeight());
            mCamParameters.setPreviewSize(preSize.width, preSize.height); // 视频大小
//            p1.setPictureSize(preSize.width, preSize.height);// 图片大小
            p.setPreviewWidth(preSize.width);
            p.setPreviewHeight(preSize.height);
        }

        if (p.getFocusMode() != null && mCamParameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO))
            mCamParameters.setFocusMode(p.getFocusMode());//连续自动对焦
        List<int[]> range = mCamParameters.getSupportedPreviewFpsRange();
        setFrameRate(p.getFrameRate());//mCamParameters.setPreviewFrameRate(p2.getFrameRate()); // 帧率
//        mCamParameters.setPreviewFpsRange(p2.getFrameRate() * 1000, p2.getFrameRate() * 1000);

        if (!"honor".equalsIgnoreCase(Build.BRAND)
                && !"huawei".equalsIgnoreCase(Build.BRAND)
                && !"MI 2".equalsIgnoreCase(Build.MODEL)) {
            // 华为荣耀、米2(4.1)不支持該方法，三星画面变暗
            // p.setPreviewFpsRange(FRAME_RATE * 1000, FRAME_RATE * 1000);
        }

        // 是否支持视频防抖
//        if (mCamParameters.isVideoStabilizationSupported()) {
//            mCamParameters.setVideoStabilization(true);
//        }

        List<String> vals = mCamParameters.getSupportedWhiteBalance();
        if (vals != null && vals.contains(Camera.Parameters.WHITE_BALANCE_AUTO)) {
            mCamParameters.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
        }

        vals = mCamParameters.getSupportedAntibanding();
        if (vals != null && vals.contains(Camera.Parameters.ANTIBANDING_AUTO)) {
            mCamParameters.setAntibanding(Camera.Parameters.ANTIBANDING_AUTO);
        }

        //fix set params failed pro.
//        List<String> supportedSceneModes = mCamParameters.getSupportedSceneModes();
//        if (supportedSceneModes != null && supportedSceneModes.contains(Camera.Parameters.SCENE_MODE_AUTO)) {
//            mCamParameters.setSceneMode(Camera.Parameters.SCENE_MODE_BARCODE);
//        }

        List<Integer> formatsList = mCamParameters.getSupportedPreviewFormats();    //获取设备支持的预览format
        if (formatsList.contains(ImageFormat.NV21)) {
            mCamParameters.setPreviewFormat(ImageFormat.NV21);
        } else if (formatsList.contains(ImageFormat.YV12)) {
            mCamParameters.setPreviewFormat(ImageFormat.YV12);
        }

        //DebugLog.d(TAG, "getMaxExposureCompensation: " + mCamParameters.getMaxExposureCompensation());
        //DebugLog.d(TAG, "getMinExposureCompensation: " + mCamParameters.getMinExposureCompensation());
        //DebugLog.d(TAG, "initFocusParams: " + mCamParameters.getExposureCompensation());


        if ("GT-N7100".equals(Build.MODEL) || "GT-I9308".equals(Build.MODEL)
                || "GT-I9300".equals(Build.MODEL)) {
            mCamParameters.set("cam_mode", 1);
        }
    }

    private static Camera.Size getSpecificRatioSize(List<Camera.Size> sizes, int target_w, int target_h){
        Camera.Size temp = sizes.get(0).width > sizes.get(sizes.size() - 1).width ? sizes.get(0) : sizes.get(sizes.size() - 1);
        int score = temp.width * temp.height;
        int target_score = target_h * target_w;
        int diff = score - target_score;
        int idx = 0;
        for(int i = 1; i < sizes.size();i ++){
            Camera.Size t = sizes.get(i);
            //rayman: workaround for preview size incorrect.
            if (t.width == t.height)
                continue;

            int ts = t.width * t.height - target_score;
            if (ts < 0) {
                continue;
            }else {
                if (ts == 0 || ts < diff) {
                    idx = i;
                    diff = ts;
                    continue;
                }
            }
        }
        return sizes.get(idx);
    }

    private boolean setCameraParams(Camera c, Camera.Parameters p) {
        try {
            c.setParameters(p);
            return true;
        } catch (Throwable e) {
            e.printStackTrace();
        }
//CURRENT_SRC_FORMAT
        return false;
    }

    private static int getCameraDisplayOrientation(int degree, int orientation, boolean frontCamera) {
        int result;
        if (frontCamera) {
            result = (orientation + degree) % 360;
            result = (360 - result) % 360;   // compensate the mirror
        } else {
            // back-facing
            result = (orientation - degree + 360) % 360;
        }
        return result;
    }

    private void updateCameraState(int state) {
        DebugLog.d(TAG, "camera state:" + state);
        mCameraState = state;
    }

    @Override
    public boolean isCameraClosed() {
        return mCamera == null || mCamParameters == null || mCameraState == STATE_RELEASED;
    }

    @Override
    public boolean closeCamera() {
        if (!checkCameraState(false)) {
            return false;
        }
        mCamera.stopPreview();
        mCallback.onCameraClosed();
        try {
            mCamera.setPreviewCallback(null);
            mCamera.setPreviewTexture(null);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mCamera.release();
        DebugLog.v(TAG, "close camera:" + mCameraId);
        mCamera = null;
        updateCameraState(STATE_RELEASED);
        mCamParameters = null;
        mLastCameraId = mCameraId;
        mCameraId = -1;
        return true;
    }

    @Override
    public boolean switchCamera(CameraParams p) {
        closeCamera();
        mFacing = mFacing == Camera.CameraInfo.CAMERA_FACING_FRONT ? Camera.CameraInfo.CAMERA_FACING_BACK : Camera.CameraInfo.CAMERA_FACING_FRONT;
        return openCamera(p);
    }

    @Override
    public void setDefaultCamera(boolean frontCamera) {
        mFacing = frontCamera ? Camera.CameraInfo.CAMERA_FACING_FRONT : Camera.CameraInfo.CAMERA_FACING_BACK;
    }

    @Override
    public boolean isFlashOn() {
        if (!checkCameraState(true)) {
            return false;
        }
        final String mode = mCamParameters.getFlashMode();
        return Camera.Parameters.FLASH_MODE_TORCH.equals(mode);
    }

    @Override
    public boolean supportFlash() {
        final List<String> modes = mCamParameters.getSupportedFlashModes();
        return modes != null && modes.contains(Camera.Parameters.FLASH_MODE_OFF) && modes.contains(Camera.Parameters.FLASH_MODE_TORCH) ? true : false;
    }

    @Override
    public boolean setFlash(boolean on) {
        if (!checkCameraState(true)) {
            return false;
        }
        try {
            String flash = Camera.Parameters.FLASH_MODE_OFF;
            if (on) {
                flash = Camera.Parameters.FLASH_MODE_TORCH;
            }
            mCamParameters.setFlashMode(flash);
            return setCameraParams(mCamera, mCamParameters);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public boolean autoFocus() {
        if (!checkCameraState(true)) {
            return false;
        }
        try {
            initFocusParams(true, null, mCamParameters);
            setCameraParams(mCamera, mCamParameters);
            mCamera.cancelAutoFocus();
//            c.autoFocus(mFocusCallback);
            return true;
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return false;
    }

    @Override
    public boolean manualZoom(int zoomValue) {
        if (!checkCameraState(true)) {
            return false;
        }
        try {
            mCamera.cancelAutoFocus();
            if (initZoomParams(mCamParameters, zoomValue)) {
                setCameraParams(mCamera, mCamParameters);
            }
            mCamera.autoFocus(mFocusCallback);
            return true;
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return false;
    }

    @Override
    public boolean manualFocus(Rect r) {
        if (!checkCameraState(true)) {
            return false;
        }
        if (usingFrontCamera()) return false;
        if (r == null || r.isEmpty()) {
            r = new Rect(-200, -200, 200, 200);
        }
        try {
            mCamera.cancelAutoFocus();
            initFocusParams(false, r, mCamParameters);
            setCameraParams(mCamera, mCamParameters);
            mCamera.autoFocus(mFocusCallback);
            return true;
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return false;
    }

    @Override
    public boolean setFrameRate(int fps) {
        if (!checkCameraState(true)) {
            return false;
        }

        List<int []> fps_ranges = mCamParameters.getSupportedPreviewFpsRange();
        int [] selected_fps = matchPreviewFpsToVideo(fps_ranges, fps * 1000);
        setPreviewFpsRange(selected_fps[0], selected_fps[1]);
        return true;
    }

    private static int [] matchPreviewFpsToVideo(List<int []> fps_ranges, int video_frame_rate) {
        int selected_min_fps = -1, selected_max_fps = -1, selected_diff = -1;
        for(int [] fps_range : fps_ranges) {

            int min_fps = fps_range[0];
            int max_fps = fps_range[1];
            if( min_fps <= video_frame_rate && max_fps >= video_frame_rate ) {
                int diff = max_fps - min_fps;
                if( selected_diff == -1 || diff < selected_diff ) {
                    selected_min_fps = min_fps;
                    selected_max_fps = max_fps;
                    selected_diff = diff;
                }
            }
        }
        if( selected_min_fps != -1 ) {
            DebugLog.d(TAG, "    chosen fps range: " + selected_min_fps + " to " + selected_max_fps);
        }
        else {
            selected_diff = -1;
            int selected_dist = -1;
            for(int [] fps_range : fps_ranges) {
                int min_fps = fps_range[0];
                int max_fps = fps_range[1];
                int diff = max_fps - min_fps;
                int dist;
                if( max_fps < video_frame_rate )
                    dist = video_frame_rate - max_fps;
                else
                    dist = min_fps - video_frame_rate;
                if( selected_dist == -1 || dist < selected_dist || ( dist == selected_dist && diff < selected_diff ) ) {
                    selected_min_fps = min_fps;
                    selected_max_fps = max_fps;
                    selected_dist = dist;
                    selected_diff = diff;
                }
            }
        }
        return new int[]{selected_min_fps, selected_max_fps};
    }

    private void setPreviewFpsRange(int min, int max) {
        try {
            mCamParameters.setPreviewFpsRange(min, max);
            setCameraParams(mCamera, mCamParameters);
        }
        catch(RuntimeException e) {
            // can get RuntimeException from getParameters - we don't catch within that function because callers may not be able to recover,
            // but here it doesn't really matter if we fail to set the fps range
            DebugLog.e(TAG, "setPreviewFpsRange failed to get parameters");
            e.printStackTrace();
        }
    }

    @Override
    public int getMaxZoom() {
        if (!checkCameraState(true)) {
            return 0;
        }
        return mCamParameters.getMaxZoom();
    }

    @Override
    public int getCurrentZoom() {
        if (!checkCameraState(true)) {
            return 0;
        }
        return mCamParameters.getZoom();
    }

    @Override
    public boolean usingFrontCamera() {
        return mFacing == Camera.CameraInfo.CAMERA_FACING_FRONT;
    }

    @Override
    public void startPreview(SurfaceTexture surfaceTexture) {
        if (!checkCameraState(false) || mCameraState == STATE_PREVIEWING) {//已经在预览状态就不需要预览了~
            return;
        }
        try {
            mCamera.setPreviewTexture(surfaceTexture);
            mCamera.startPreview();
        } catch (Exception e) {
            e.printStackTrace();
        }
        updateCameraState(STATE_PREVIEWING);
    }

    @Override
    public int[] getFinalPreviewSize() {
        if (!checkCameraState(true)) {
            return new int[2];
        }
        int[] size = new int[2];
        Camera.Size temp = mCamParameters.getPreviewSize();
        size[0] = temp.width;
        size[1] = temp.height;
        return size;
    }

    @Override
    public int[] getExposureCompensationValue() {
        if (!checkCameraState(true)) return new int[]{};
        return new int[]{mCamParameters.getMinExposureCompensation(),
                mCamParameters.getMaxExposureCompensation()};
    }

    @Override
    public boolean manualExposureCompensation(int value) {
        if (!checkCameraState(true)) return false;
        DebugLog.d(TAG, "value:  " + value);
        DebugLog.d(TAG, "getMinExposureCompensation: " + mCamParameters.getMinExposureCompensation());
        DebugLog.d(TAG, "getMaxExposureCompensation: " + mCamParameters.getMaxExposureCompensation());
        if (value >= mCamParameters.getMinExposureCompensation() &&
                value <= mCamParameters.getMaxExposureCompensation()) {
            Log.d(TAG, "manualExposureCompensation: " + value);
            mCamParameters.setExposureCompensation(value);
            setCameraParams(mCamera, mCamParameters);
        } else {
            return false;
        }
        return false;
    }

    private boolean initZoomParams(Camera.Parameters p, int zoom) {
        DebugLog.v(TAG, "zoom supported: " + p.isZoomSupported());
        DebugLog.v(TAG, "smooth zoom supported: " + p.isSmoothZoomSupported());
        if (p.isZoomSupported()) {
            DebugLog.v(TAG, "max zoom: " + p.getMaxZoom());
            DebugLog.v(TAG, "current zoom: " + p.getZoom());

            final int maxZoom = p.getMaxZoom();
            zoom = Math.min(zoom, maxZoom);
            zoom = Math.max(0, zoom);

            DebugLog.v(TAG, "new current zoom: " + p.getZoom());
            // FIXME: 2016/9/12 startSmoothZoom待优化
            p.setZoom(zoom);
            return true;
        }
        return false;
    }

    private void initFocusParams(boolean auto, Rect r, Camera.Parameters p) {
        mAutoFocus = auto;
        if (auto) {
            if (p.getMaxNumFocusAreas() > 0) {
                p.setFocusAreas(null);
            }

            String val = null;
            List<String> focusModes = p.getSupportedFocusModes();
            if (focusModes != null) {
                if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                    val = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO;
                } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                    val = Camera.Parameters.FOCUS_MODE_AUTO;
                }
            }

            DebugLog.d(TAG, "auto focus mode:" + val);
            if (val != null) {
                p.setFocusMode(val);
            }
        } else {
            mAreas = new ArrayList<Camera.Area>();
            mAreas.add(new Camera.Area(r, 1000));
            if (p.getMaxNumFocusAreas() > 0) {//对焦
                Log.d(TAG, "[setAFArea]: " + r.toString());
                p.setFocusAreas(mAreas);
            }
            if (p.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                p.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            }

//            p.setExposureCompensation(0);//设置默认曝光补偿
            if (p.getMaxNumMeteringAreas() > 0) {//测光
                p.setMeteringAreas(mAreas);
            }

            DebugLog.d(TAG, "initFocusParams: " + Arrays.toString(getExposureCompensationValue()));
            DebugLog.d(TAG, "initFocusParams: " +p.getExposureCompensation());

//            String val = RecorderUtil.getManualFocusMode(p);
//            if (val != null) {
//                android.util.Log.d(TAG, "setAFArea setFocusMode: " + val);
//                p.setFocusMode(val);
//            }
        }
    }

    private Camera.AutoFocusCallback mFocusCallback = new Camera.AutoFocusCallback() {
        @Override
        public void onAutoFocus(boolean success, Camera camera) {
            DebugLog.v(TAG, "setAFArea focus result:" + success + " " + camera);
            if (!mAutoFocus) {
                if (!usingFrontCamera()) {
                    mCamParameters.setFocusAreas(mAreas);
                    mCamParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                    setCameraParams(mCamera, mCamParameters);
                    if (success)
                        camera.cancelAutoFocus();
                }
            }
        }
    };

    private boolean checkCameraState(boolean needParameters) {
        return needParameters ? mCamera != null && mCamParameters != null : mCamera != null;
    }
}
