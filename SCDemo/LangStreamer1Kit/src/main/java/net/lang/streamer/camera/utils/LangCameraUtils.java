package net.lang.streamer.camera.utils;

import android.hardware.Camera;

import java.util.List;

/**
 * Created by rayman.lee on 2017/3/09.
 */
public class LangCameraUtils {

    public static Camera.Size getLargePictureSize(Camera camera){
        if(camera != null){
            List<Camera.Size> sizes = camera.getParameters().getSupportedPictureSizes();
            Camera.Size temp = sizes.get(0);
            for(int i = 1;i < sizes.size();i ++){
                float scale = (float)(sizes.get(i).height) / sizes.get(i).width;
                if(temp.width < sizes.get(i).width && scale < 0.6f && scale > 0.5f)
                    temp = sizes.get(i);
            }
            return temp;
        }
        return null;
    }

    public static Camera.Size getLargePreviewSize(Camera camera){
        if(camera != null){
            List<Camera.Size> sizes = camera.getParameters().getSupportedPreviewSizes();
            Camera.Size temp = sizes.get(0);
            for(int i = 1;i < sizes.size();i ++){
                if(temp.width < sizes.get(i).width)
                    temp = sizes.get(i);
            }
            return temp;
        }
        return null;
    }

    public static Camera.Size getTargetPreviewSize(Camera camera, int target_w, int target_h){
        if(camera != null){
            List<Camera.Size> sizes = camera.getParameters().getSupportedPreviewSizes();
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
        return null;
    }

    public static int[] getSupportedFpsRange(Camera camera, int expectedFps) {
        if(camera != null) {
            List<int[]> fpsRanges = camera.getParameters().getSupportedPreviewFpsRange();
            expectedFps *= 1000;
            int[] closestRange = fpsRanges.get(0);
            int measure = Math.abs(closestRange[0] - expectedFps) + Math.abs(closestRange[1] - expectedFps);
            for (int[] range : fpsRanges) {
                int curMeasure = Math.abs(range[0] - expectedFps) + Math.abs(range[1] - expectedFps);
                if (curMeasure < measure) {
                    closestRange = range;
                    measure = curMeasure;
                }
            }
            return closestRange;
        }
        return null;
    }

    public static int[] getBestFpsRange(Camera camera, int expectedFps) {
        if(camera != null) {
            List<int[]> fpsRanges = camera.getParameters().getSupportedPreviewFpsRange();
            expectedFps *= 1000;
            int[] closestRange = null;
            int[] tempClosesRange = fpsRanges.get(0);
            //int measure = Math.abs(closestRange[0] - expectedFps) + Math.abs(closestRange[1] - expectedFps);
            for (int[] range : fpsRanges) {
                if (closestRange == null && range[0] >= expectedFps) {
                    closestRange = range;
                }else if (range[0] >= expectedFps && closestRange[1] > range[0]) {
                    closestRange = range;
                }

                if (range[0] > tempClosesRange[0]) {
                        tempClosesRange = range;
                }
            }

            return closestRange == null ? tempClosesRange : closestRange;
        }
        return null;
    }

    public static int getCameraDisplayOrientation(Camera camera, int cameraId, android.content.Context context) {
        if (camera != null) {
            try {
                Camera.CameraInfo info = new Camera.CameraInfo();
                Camera.getCameraInfo(cameraId, info);
                int degrees = 0;
                int rotation = ((android.app.Activity)context).getWindowManager().getDefaultDisplay().getRotation();
                switch (rotation) {
                    case android.view.Surface.ROTATION_0:
                        degrees = 0;
                        break;
                    case android.view.Surface.ROTATION_90:
                        degrees = 90;
                        break;
                    case android.view.Surface.ROTATION_180:
                        degrees = 180;
                        break;
                    case android.view.Surface.ROTATION_270:
                        degrees = 270;
                        break;
                }
                int result;
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    result = (info.orientation + degrees) % 360;
                    result = (360 - result) % 360;  // compensate the mirror
                } else {  // back-facing
                    result = (info.orientation - degrees + 360) % 360;
                }
                return result;

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return -1;
    }

    public static int getCameraOrientation(Camera camera, int cameraId) {
        int result = 0;
        if (camera != null) {
            try {
                Camera.CameraInfo info = new Camera.CameraInfo();
                Camera.getCameraInfo(cameraId, info);
                result = info.orientation;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return result;
    }
}
