package net.lang.streamer.camera;

import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;

import net.lang.streamer.LangCameraStreamer;
import net.lang.streamer.camera.utils.LangCameraInfo;
import net.lang.streamer.camera.utils.LangCameraUtils;
import net.lang.streamer.engine.LangMagicEngine;
import net.lang.streamer.engine.data.LangEngineParams;
import net.lang.streamer.utils.DebugLog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LangCameraEngine {
    private static Camera camera = null;
    private static int cameraID = -1;
    private static int backCameraID = -1, frontCameraID = -1;

    public static Camera getCamera() {
        return camera;
    }

    public static int getCameraID() {
        return cameraID;
    }

    public static boolean isbackCameraID(int id) {
        return id == backCameraID;
    }

    public static void resetCamera() {
    	cameraID = -1;
    	backCameraID = -1;
    	frontCameraID = -1;
    }

    public static boolean openCamera() {
        if(camera == null) {
            try{
                listAllCameras();
                camera = Camera.open(cameraID);
                setDefaultParameters();
                return true;
            }catch(RuntimeException e) {
                return false;
            }
        }
        return false;
    }

    public static void releaseCamera() {
        if(camera != null){
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }

    public static void listAllCameras() {
        Camera.CameraInfo info = new Camera.CameraInfo();
        int numCameras = getAvailableCameras();
        for (int i = 0; i < numCameras; i++) {
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                backCameraID = i;
            } else if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                frontCameraID = i;
                break;
            }
        }

        // set default current camera Id.
        if (cameraID == -1) {
            cameraID = frontCameraID >=0 ? frontCameraID : backCameraID;
        }
    }

    public static int getAvailableCameras() {
        return Camera.getNumberOfCameras();
    }

    public static void switchCameraID() {
        if (cameraID == backCameraID) {
            cameraID = frontCameraID;
        } else if (cameraID == frontCameraID) {
            cameraID = backCameraID;
		}
    }

    public static boolean isFrontCamera() {
        if (cameraID == frontCameraID)
            return true;
        else
            return false;
    }

    public void resumeCamera() {
        openCamera();
    }

    public void setParameters(Parameters parameters){
        camera.setParameters(parameters);
    }

    public Parameters getParameters(){
        if(camera != null)
            camera.getParameters();
        return null;
    }

    private static void setDefaultParameters() {
        Parameters parameters = camera.getParameters();
        if (parameters.getSupportedFocusModes().contains(
                Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO/*FOCUS_MODE_CONTINUOUS_PICTURE*/)) {
            parameters.setFocusMode(Parameters.FOCUS_MODE_CONTINUOUS_VIDEO/*FOCUS_MODE_CONTINUOUS_PICTURE*/);
        }

        int desiredPreviewWidth = LangEngineParams.cameraPreveiwWidth;
        int desiredPreviewHeight = LangEngineParams.cameraPreviewHeight;
        if (desiredPreviewWidth < desiredPreviewHeight) {
            desiredPreviewWidth = LangEngineParams.cameraPreviewHeight;
            desiredPreviewHeight = LangEngineParams.cameraPreveiwWidth;
        }

        Size previewSize = LangCameraUtils.getTargetPreviewSize(camera, desiredPreviewWidth, desiredPreviewHeight);
        parameters.setPreviewSize(previewSize.width, previewSize.height);
        Size pictureSize = LangCameraUtils.getLargePictureSize(camera);
        parameters.setPictureSize(pictureSize.width, pictureSize.height);

        int fpsRange[] = LangCameraUtils.getBestFpsRange(camera, LangEngineParams.cameraFps);
        parameters.setPreviewFpsRange(fpsRange[0], fpsRange[1]);

        parameters.setPreviewFormat(android.graphics.ImageFormat.NV21);
        parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
        parameters.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
        parameters.setSceneMode(Camera.Parameters.SCENE_MODE_AUTO);

        //parameters.setRotation(90);
        camera.setParameters(parameters);

        /*
        int rotation = LangCameraUtils.getCameraDisplayOrientation(camera, cameraID, LangMagicEngine.getContext());
        camera.setDisplayOrientation(rotation);
        */
    }

    private static Size getPreviewSize(){
        return camera.getParameters().getPreviewSize();
    }

    private static Size getPictureSize(){
        return camera.getParameters().getPictureSize();
    }

    public static void setPreviewCallback(Camera.PreviewCallback cb) {
        if (camera != null) {
            camera.setPreviewCallback(cb);
        }
    }

    public static void startPreview(SurfaceTexture surfaceTexture){
        if(camera != null)
            try {
                camera.setPreviewTexture(surfaceTexture);
                camera.startPreview();
            } catch (IOException e) {
                e.printStackTrace();
            }
    }

    public static void startPreview(){
        if(camera != null)
            camera.startPreview();
    }

    public static void stopPreview(){
        camera.stopPreview();
    }

    public static void setRotation(int rotation){
        Camera.Parameters params = camera.getParameters();
        params.setRotation(rotation);
        camera.setParameters(params);
    }

    public static void setCameraToggleTorch(boolean enable) {
        //Camera camera = LangCameraEngine.getCamera();
        if (camera != null) {
            Camera.Parameters p = camera.getParameters();
            List<String> lists = p.getSupportedFlashModes();
            boolean find = false;
            if (lists != null) {
                for (String it : lists) {
                    if (it.startsWith(Camera.Parameters.FLASH_MODE_TORCH)) {
                        find = true;
                        break;
                    }
                }
            }
            if (find) {
                if (!enable) {
                    p.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                    camera.setParameters(p);
                    p.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
                } else {
                    p.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                }
                camera.setParameters(p);
            }
        }
    }

    public static void setCameraFocusing(float x, float y, float width, float height, Camera.AutoFocusCallback autoFocusCallback) {

        //android.hardware.Camera camera = LangCameraEngine.getCamera();
        if (camera != null) {
            //android.hardware.Camera.Parameters p = camera.getParameters();
            Rect focusRect = calculateTapArea(x, y, width, height, 1.0f);
            Rect meteringRect = calculateTapArea(x, y, width, height, 1.0f);

            Camera.Parameters parameters = camera.getParameters();
            List<String> list = parameters.getSupportedFocusModes();
            boolean supports = false;
            if (list != null) {
                supports = list.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            }

            if (!supports) return;

            if (parameters.getMaxNumFocusAreas() > 0) {
                List<Camera.Area> focusAreas = new ArrayList<Camera.Area>();
                focusAreas.add(new Camera.Area(focusRect, 1000));

                parameters.setFocusAreas(focusAreas);
            }

            if (parameters.getMaxNumMeteringAreas() > 0) {
                List<Camera.Area> meteringAreas = new ArrayList<Camera.Area>();
                meteringAreas.add(new Camera.Area(meteringRect, 1000));

                parameters.setMeteringAreas(meteringAreas);
            }
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            camera.setParameters(parameters);
            camera.autoFocus(autoFocusCallback);
        }
    }

    private static Rect calculateTapArea(float x, float y, float width, float height, float coefficient) {
        float focusAreaSize = 300;
        int areaSize = Float.valueOf(focusAreaSize * coefficient).intValue();

        int centerX = (int) (x / width * 2000 - 1000);
        int centerY = (int) (y / height * 2000 - 1000);

        int left = clamp(centerX - areaSize / 2, -1000, 1000);
        int right = clamp(left + areaSize, -1000, 1000);
        int top = clamp(centerY - areaSize / 2, -1000, 1000);
        int bottom = clamp(top + areaSize, -1000, 1000);

        return new Rect(left, top, right, bottom);
    }

    private static int clamp(int x, int min, int max) {
        if (x > max) {
            return max;
        }
        if (x < min) {
            return min;
        }
        return x;
    }

    public static void takePicture(Camera.ShutterCallback shutterCallback, Camera.PictureCallback rawCallback,
                                   Camera.PictureCallback jpegCallback){
        camera.takePicture(shutterCallback, rawCallback, jpegCallback);
    }

    public static LangCameraInfo getCameraInfo() {
        LangCameraInfo info = new LangCameraInfo();
        Size size = getPreviewSize();
        CameraInfo cameraInfo = new CameraInfo();
        Camera.getCameraInfo(cameraID, cameraInfo);
        info.previewWidth = size.width;
        info.previewHeight = size.height;
        info.orientation = cameraInfo.orientation;
        info.isFront = cameraID == frontCameraID ? true : false;
        size = getPictureSize();
        info.pictureWidth = size.width;
        info.pictureHeight = size.height;
        return info;
    }

    public static int getCameraAngle() {
        return LangCameraUtils.getCameraOrientation(camera, cameraID);
        //return LangCameraUtils.getCameraDisplayOrientation(camera, cameraID, LangMagicEngine.getContext());
    }
}
