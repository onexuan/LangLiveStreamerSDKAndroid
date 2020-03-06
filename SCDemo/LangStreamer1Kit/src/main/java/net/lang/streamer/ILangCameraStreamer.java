package net.lang.streamer;

import android.view.SurfaceView;

import net.lang.streamer.config.LangObjectSegmentationConfig;
import net.lang.streamer.widget.AnimationCallback;
import net.lang.streamer.widget.LangMagicCameraView;
import net.lang.streamer.config.*;

import java.io.InputStream;

/**
 * Created by lichao on 17-5-2.
 */

public interface ILangCameraStreamer {
    enum LangStreamerEvent {
        LANG_EVENT_PUSH_CONNECTING          (1000, "LANG_EVENT_PUSH_CONNECTING"),
        LANG_EVENT_PUSH_RECONNECTING        (1001, "LANG_EVENT_PUSH_RECONNECTING"),
        LANG_EVENT_PUSH_CONNECT_SUCC        (1002, "LANG_EVENT_PUSH_CONNECT_SUCC"),
        LANG_EVENT_PUSH_DISCONNECT          (1003, "LANG_EVENT_PUSH_DISCONNECT"),
        LANG_EVENT_RECORD_BEGIN             (1004, "LANG_EVENT_RECORD_BEGIN"),
        LANG_EVENT_RECORD_END               (1005, "LANG_EVENT_RECORD_END"),
        LANG_EVENT_SCREENSHOT_BEGIN         (1006, "LANG_EVENT_SCREENSHOT_BEGIN"),
        LANG_EVENT_SCREENSHOT_END           (1007, "LANG_EVENT_SCREENSHOT_END"),
        LANG_EVENT_FACE_DETECTED            (1008, "LANG_EVENT_FACE_DETECTED"),
        LANG_EVENT_FACE_LOST                (1009, "LANG_EVENT_FACE_LOST"),
        LANG_EVENT_HAND_DETECTED            (1010, "LANG_EVENT_HAND_DETECTED"),
        LANG_EVENT_HAND_LOST                (1011, "LANG_EVENT_HAND_LOST"),
        LANG_EVENT_RTC_CONNECTING           (1012, "LANG_EVENT_RTC_CONNECTING"),
        LANG_EVENT_RTC_CONNECT_SUCC         (1013, "LANG_EVENT_RTC_CONNECT_SUCC"),
        LANG_EVENT_RTC_DISCONNECT           (1014, "LANG_EVENT_RTC_DISCONNECT"),
        LANG_EVENT_RTC_USER_JOINED          (1015, "LANG_EVENT_RTC_USER_JOINED"),
        LANG_EVENT_RTC_USER_OFFLINE         (1016, "LANG_EVENT_RTC_USER_OFFLINE"),
        LANG_EVENT_RTC_USER_AUDIO_MUTED     (1017, "LANG_EVENT_RTC_USER_AUDIO_MUTED"),
        LANG_EVENT_RTC_USER_VIDEO_MUTED     (1018, "LANG_EVENT_RTC_USER_VIDEO_MUTED"),
        LANG_EVENT_RTC_AUDIO_ROUTE_CHANGE   (1019, "LANG_EVENT_RTC_AUDIO_ROUTE_CHANGE"),
        LANG_EVENT_RTC_STATS_UPDATE         (1020, "LANG_EVENT_RTC_STATS_UPDATE"),
        LANG_EVENT_RTC_USER_VIDEO_RENDERED  (1021, "LANG_EVENT_RTC_USER_VIDEO_RENDERED"),
        LANG_EVENT_RTC_VIDEO_SIZE_CHANGED   (1022, "LANG_EVENT_RTC_VIDEO_SIZE_CHANGED"),
        LANG_EVENT_RTC_NETWORK_LOST         (1023, "LANG_EVENT_RTC_NETWORK_LOST"),
        LANG_EVENT_RTC_NETWORK_TIMEOUT      (1024, "LANG_EVENT_RTC_NETWORK_TIMEOUT"),
        LANG_WARNNING_NETWORK_WEAK          (2001, "LANG_WARNNING_NETWORK_WEAK"),
        LANG_WARNNING_RECONNECTING          (2002, "LANG_WARNNING_RECONNECTING"),
        LANG_WARNNING_HW_ACCELERATION_FAIL  (2003, "LANG_WARNNING_HW_ACCELERATION_FAIL");

        int value;
        String name;
        LangStreamerEvent(int v, String string) {
            this.value = v;
            this.name = string;
        }
    }

    enum LangStreamerError {
        LANG_ERROR_OPEN_CAMERA_FAIL     (3001, "LANG_ERROR_OPEN_CAMERA_FAIL"),
        LANG_ERROR_OPEN_MIC_FAIL        (3002, "LANG_ERROR_OPEN_MIC_FAIL"),
        LANG_ERROR_VIDEO_ENCODE_FAIL    (3003, "LANG_ERROR_VIDEO_ENCODE_FAIL"),
        LANG_ERROR_AUDIO_ENCODE_FAIL    (3004, "LANG_ERROR_AUDIO_ENCODE_FAIL"),
        LANG_ERROR_PUSH_CONNECT_FAIL    (3005, "LANG_ERROR_PUSH_CONNECT_FAIL"),
        LANG_ERROR_RECORD_FAIL          (3006, "LANG_ERROR_RECORD_FAIL"),
        LANG_ERROR_SCREENSHOT_FAIL      (3007, "LANG_ERROR_SCREENSHOT_FAIL"),
        LANG_ERROR_UNSUPPORTED_FORMAT   (3008, "LANG_ERROR_UNSUPPORTED_FORMAT"),
        LANG_ERROR_NO_PERMISSIONS       (3009, "LANG_ERROR_NO_PERMISSIONS"),
        LANG_ERROR_AUTH_FAIL            (3010, "LANG_ERROR_AUTH_FAIL"),
        LANG_ERROR_RTC_EXCEPTION        (3020, "LANG_ERROR_RTC_EXCEPTION");
        int value;
        String name;
        LangStreamerError(int v, String string) {
            this.value = v;
            this.name = string;
        }
    }

    enum LangCameraFilter {
        LANG_FILTER_NONE                (0, "LANG_FILTER_NONE"),
        LANG_FILTER_FAIRYTALE           (1, "LANG_FILTER_FAIRYTALE"),
        LANG_FILTER_SUNRISE             (2, "LANG_FILTER_SUNRISE"),
        LANG_FILTER_SUNSET              (3, "LANG_FILTER_SUNSET"),
        LANG_FILTER_WHITECAT            (4, "LANG_FILTER_WHITECAT"),
        LANG_FILTER_BLACKCAT            (5, "LANG_FILTER_BLACKCAT"),
        LANG_FILTER_SKINWHITEN          (6, "LANG_FILTER_SKINWHITEN"),
        LANG_FILTER_HEALTHY             (7, "LANG_FILTER_HEALTHY"),
        LANG_FILTER_SWEETS              (8, "LANG_FILTER_SWEETS"),
        LANG_FILTER_ROMANCE             (9, "LANG_FILTER_ROMANCE"),
        LANG_FILTER_SAKURA              (10, "LANG_FILTER_SAKURA"),
        LANG_FILTER_WARM                (11, "LANG_FILTER_WARM"),
        LANG_FILTER_ANTIQUE             (12, "LANG_FILTER_ANTIQUE"),
        LANG_FILTER_NOSTALGIA           (13, "LANG_FILTER_NOSTALGIA"),
        LANG_FILTER_CALM                (14, "LANG_FILTER_CALM"),
        LANG_FILTER_LATTE               (15, "LANG_FILTER_LATTE"),
        LANG_FILTER_TENDER              (16, "LANG_FILTER_TENDER"),
        LANG_FILTER_COOL                (17, "LANG_FILTER_COOL"),
        LANG_FILTER_EMERALD             (18, "LANG_FILTER_EMERALD"),
        LANG_FILTER_EVERGREEN           (19, "LANG_FILTER_EVERGREEN"),
        LANG_FILTER_CRAYON              (20, "LANG_FILTER_CRAYON"),
        LANG_FILTER_SKETCH              (21, "LANG_FILTER_SKETCH"),
        LANG_FILTER_AMARO               (22, "LANG_FILTER_AMARO"),
        LANG_FILTER_BRANNAN             (23, "LANG_FILTER_BRANNAN"),
        LANG_FILTER_BROOKLYN            (24, "LANG_FILTER_BROOKLYN"),
        LANG_FILTER_EARLYBIRD           (25, "LANG_FILTER_EARLYBIRD"),
        LANG_FILTER_FREUD               (26, "LANG_FILTER_FREUD"),
        LANG_FILTER_HEFE                (27, "LANG_FILTER_HEFE"),
        LANG_FILTER_HUDSON              (28, "LANG_FILTER_HUDSON"),
        LANG_FILTER_INKWELL             (29, "LANG_FILTER_INKWELL"),
        LANG_FILTER_KEVIN               (30, "LANG_FILTER_KEVIN"),
        LANG_FILTER_LOMO                (31, "LANG_FILTER_LOMO"),
        LANG_FILTER_N1977               (32, "LANG_FILTER_N1977"),
        LANG_FILTER_NASHVILLE           (33, "LANG_FILTER_NASHVILLE"),
        LANG_FILTER_PIXAR               (34, "LANG_FILTER_PIXAR"),
        LANG_FILTER_RISE                (35, "LANG_FILTER_RISE"),
        LANG_FILTER_SIERRA              (36, "LANG_FILTER_SIERRA"),
        LANG_FILTER_SUTRO               (37, "LANG_FILTER_SUTRO"),
        LANG_FILTER_TOASTER2            (38, "LANG_FILTER_TOASTER2"),
        LANG_FILTER_WALDEN              (39, "LANG_FILTER_WALDEN");
        int value;
        String name;

       LangCameraFilter(int v, String string) {
            this.value = v;
            this.name = string;
        }

        String getName() {
            return name;
        }

        int getValue() {
            return value;
        }
    }

    enum LangCameraBeauty {
        LANG_BEAUTY_NONE            (0, "LANG_BEAUTY_NONE"),
        LANG_BEAUTY_LEVEL_1         (1, "LANG_BEAUTY_LEVEL_1"),
        LANG_BEAUTY_LEVEL_2         (2, "LANG_BEAUTY_LEVEL_2"),
        LANG_BEAUTY_LEVEL_3         (3, "LANG_BEAUTY_LEVEL_3"),
        LANG_BEAUTY_LEVEL_4         (4, "LANG_BEAUTY_LEVEL_4"),
        LANG_BEAUTY_LEVEL_5(5, "LANG_BEAUTY_LEVEL_5");
        int value;
        String name;
        LangCameraBeauty(int v, String string) {
            this.value = v;
            this.name = string;
        }
    }

    enum LangAudioQuality {
        LANG_AUDIO_QUALITY_LOW(0),
        LANG_AUDIO_QUALITY_MEDIUM(1),
        LANG_AUDIO_QUALITY_HIGH(2),
        LANG_AUDIO_QUALITY_DEFAULT(LANG_AUDIO_QUALITY_MEDIUM.getValue());
        private int value;
        LangAudioQuality(int v) {
            value = v;
        }

        int getValue() {
            return value;
        }
    }

    enum LangVideoResolution {
        LANG_VIDEO_RESOLUTION_360P(0),
        LANG_VIDEO_RESOLUTION_480P(1),
        LANG_VIDEO_RESOLUTION_540P(3),
        LANG_VIDEO_RESOLUTION_720P(4);
        private int value;
        LangVideoResolution(int v) {
            value = v;
        }

        int getValue() {
            return value;
        }

    }

    enum LangVideoQuality {
        LANG_VIDEO_QUALITY_LOW_1(0),
        LANG_VIDEO_QUALITY_LOW_2(1),
        LANG_VIDEO_QUALITY_LOW_3(3),
        LANG_VIDEO_QUALITY_MEDIUM_1(4),
        LANG_VIDEO_QUALITY_MEDIUM_2(5),
        LANG_VIDEO_QUALITY_MEDIUM_3(6),
        LANG_VIDEO_QUALITY_HIGH_1(7),
        LANG_VIDEO_QUALITY_HIGH_2(8),
        LANG_VIDEO_QUALITY_HIGH_3(9),
        LANG_VIDEO_QUALITY_DEFAULT(LANG_VIDEO_QUALITY_LOW_2.getValue());
        private int value;
        LangVideoQuality(int v) {
            value = v;
        }

        int getValue() {
            return value;
        }
    }

    class LangStreamerLogLevel {
        public static final int LANG_LOG_INFO = 0;
        public static final int LANG_LOG_DEBUG = 1;
        public static final int LANG_LOG_WARNING = 2;
        public static final int LANG_LOG_ERROR = 3;
        public static final int LANG_LOG_NONE = 4;
    }

    //初始化，带配置参数
    int init(LangStreamerConfig config, LangMagicCameraView view);
    int init(LangAudioQuality acfg, LangVideoQuality vcfg, LangMagicCameraView view);

    //生命周期
    void onResume();
    void onPause();

    //预览控制
    int startPreview();
    int stopPreview();

    //推流控制
    int startStreaming(String  url);
    int stopStreaming();
    boolean isStreaming();

    //录制控制
    int startRecording(String  url);
    int stopRecording();

    //截屏
    int screenshot(String  url);

    //释放资源
    int release();

    //camera control
    int switchCamera();
    void setCameraBrightLevel(float level);
    void setCameraToggleTorch(boolean enable);
    void setCameraFocusing(float x, float y);

    //设置滤镜，美颜，水印
    int setFilter(LangCameraFilter filter);
    int setBeauty(LangCameraBeauty info);
    int setWatermark(LangWatermarkConfig config);
    int setFaceu(LangFaceuConfig config);

    // 禮物動畫，美髮等(Object segmentation相關)
    int setGiftAnimation(LangObjectSegmentationConfig params, InputStream inputStream, InputStream giftStream);
    int setMattingAnimation(LangObjectSegmentationConfig params, String inputPath, String giftPath, AnimationCallback animationCallback);
    int setHairColors(LangObjectSegmentationConfig params);

    //静音
    int setMute(boolean mute);

    //混音
    int enableAudioPlay(boolean enableAudioPlay);
    //事件，错误侦听
    void setOnEventListener(ILangCameraStreamerOnEventListener listener);
    void setOnErrorListener(ILangCameraStreamerOnErrorListener listener);

    //重连配置，次数，间隔
    void setReconnectOption(int count, int interval);

    //开启码率动态调整策略
    void setAutoAdjustBitrate(boolean enable);


    // debug 日志等级
    void setDebugLevel(int level);
    
    //翻转

    void setMirror(boolean enable);
    void setZoom(float scale);


    //获取实时推流信息
    LangLiveInfo getLiveInfo();

    //rtc api control
    boolean rtcAvailable(LangRtcConfig config);
    SurfaceView createRtcRenderView();
    void setRtcDisplayLayoutParams(LangRtcConfig.RtcDisplayParams displayParams);
    int joinRtcChannel(LangRtcConfig config, SurfaceView localView);
    void leaveRtcChannel();
    int setRtcVoiceChat(boolean voiceOnly);
    int muteRtcLocalVoice(boolean mute);
    int muteRtcRemoteVoice(final int uid, boolean mute);
    int setupRtcRemoteUser(final int uid, SurfaceView remoteView);

    void enableMakeups(boolean enable);
    void enablePushMatting(boolean enable);

    interface ILangCameraStreamerOnEventListener {
        void onEvent(ILangCameraStreamer streamer, LangStreamerEvent event, int what);
        void onEvent(ILangCameraStreamer streamer, LangStreamerEvent event, Object obj);
    }

    interface ILangCameraStreamerOnErrorListener {
        void onError(ILangCameraStreamer streamer, LangStreamerError event, int what);
    }

}
