package net.lang.streamer2.faceu;

public interface IFaceuListener {
    enum FaceuGestureType{
        FACEU_GESTURE_NONE           (-1, "FACEU_GESTURE_NONE"),
        FACEU_GESTURE_PALM           (0, "FACEU_GESTURE_PALM"),       ///<手掌
        FACEU_GESTURE_GOOD           (1, "FACEU_GESTURE_GOOD"),       ///<大拇哥
        FACEU_GESTURE_OK             (2, "FACEU_GESTURE_OK"),         ///<OK手势
        FACEU_GESTURE_PISTOL         (3, "FACEU_GESTURE_PISTOL"),     ///<手枪手势
        FACEU_GESTURE_FINGER_INDEX   (4, "FACEU_GESTURE_FINGER_INDEX"), ///<食指指尖
        FACEU_GESTURE_FINGER_HEART   (5, "FACEU_GESTURE_FINGER_HEART"), ///<单手比爱心
        FACEU_GESTURE_LOVE           (6, "FACEU_GESTURE_LOVE"),       ///<爱心
        FACEU_GESTURE_SCISSOR        (7, "FACEU_GESTURE_SCISSOR"),    ///<剪刀手
        FACEU_GESTURE_CONGRATULATE   (8, "FACEU_GESTURE_CONGRATULATE"), ///<恭贺（抱拳）
        FACEU_GESTURE_HOLDUP         (9, "FACEU_GESTURE_HOLDUP"),     ///<托手
        FACEU_GESTURE_FIST           (10, "FACEU_GESTURE_FIST");       ///<拳頭
        int value;
        String name;
        FaceuGestureType(int v, String s) {
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

    void onHumanFaceDetected(int faceCount);
    void onHumanHandDetected(int handCount, FaceuGestureType gesture);
}
