package net.lang.streamer.faceu;

import android.os.Handler;
import android.os.Message;

import java.lang.ref.WeakReference;

/**
 * Created by lang on 2017/11/28.
 */

public class LangFaceHandler extends Handler {

    private static final int MSG_FACE_DETECTED = 0;
    private static final int MSG_FACE_LOST = 1;
    private static final int MSG_HAND_DETECTED = 2;
    private static final int MSG_HAND_LOST = 3;

    public enum GestureType{
        GESTURE_TYPE_NONE           (-1, "GESTURE_TYPE_NONE"),
        GESTURE_TYPE_PALM           (10000, "GESTURE_TYPE_PALM"),       ///<手掌
        GESTURE_TYPE_GOOD           (10001, "GESTURE_TYPE_GOOD"),       ///<大拇哥
        GESTURE_TYPE_OK             (10002, "GESTURE_TYPE_OK"),         ///<OK手势
        GESTURE_TYPE_PISTOL         (10003, "GESTURE_TYPE_PISTOL"),     ///<手枪手势
        GESTURE_TYPE_FINGER_INDEX   (10004, "GESTURE_TYPE_FINGER_INDEX"), ///<食指指尖
        GESTURE_TYPE_FINGER_HEART   (10005, "GESTURE_TYPE_FINGER_HEART"), ///<单手比爱心
        GESTURE_TYPE_LOVE           (10006, "GESTURE_TYPE_LOVE"),       ///<爱心
        GESTURE_TYPE_SCISSOR        (10007, "GESTURE_TYPE_SCISSOR"),    ///<剪刀手
        GESTURE_TYPE_CONGRATULATE   (10008, "GESTURE_TYPE_CONGRATULATE"), ///<恭贺（抱拳）
        GESTURE_TYPE_HOLDUP         (10009, "GESTURE_TYPE_HOLDUP"),     ///<托手
        GESTURE_TYPE_FIST           (10010, "GESTURE_TYPE_HOLDUP");     ///<拳頭
        int value;
        String name;
        GestureType(int v, String s) {
            this.value = v;
            this.name = s;
        }

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
        }
    }


    private WeakReference<SnailFaceListener> mWeakListener;

    public LangFaceHandler(SnailFaceListener listener) {
        mWeakListener = new WeakReference<>(listener);
    }

    public void notifyHumanFaceDetected() {
        sendEmptyMessage(MSG_FACE_DETECTED);
    }

    public void notifyHumanFaceLost() {
        sendEmptyMessage(MSG_FACE_LOST);
    }

    public void notifyHumanHandDetected(LangFaceHandler.GestureType gesture) {
        obtainMessage(MSG_HAND_DETECTED, new Object[]{gesture}).sendToTarget();
    }

    public void notifyHumanHandLost() {
        sendEmptyMessage(MSG_HAND_LOST);
    }

    @Override  // runs on UI thread
    public void handleMessage(Message msg) {
        SnailFaceListener listener = mWeakListener.get();
        if (listener == null)
            return;

        switch (msg.what) {
            case MSG_FACE_DETECTED: {
                listener.onHumanFaceDetected();
                break;
            }
            case MSG_FACE_LOST: {
                listener.onHumanFaceLost();
                break;
            }
            case MSG_HAND_DETECTED: {
                Object[] data = (Object[])msg.obj;
                listener.onHumanHandDetected((LangFaceHandler.GestureType)data[0]);
                break;
            }
            case MSG_HAND_LOST: {
                listener.onHumanHandLost();
                break;
            }
            default:
                throw new RuntimeException("unknown msg " + msg.what);
        }
    }

    public interface SnailFaceListener {

        void onHumanFaceDetected();

        void onHumanFaceLost();

        void onHumanHandDetected(LangFaceHandler.GestureType gesture);

        void onHumanHandLost();
    }
}
