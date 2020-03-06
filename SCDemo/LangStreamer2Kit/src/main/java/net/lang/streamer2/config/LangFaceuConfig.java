package net.lang.streamer2.config;

/**
 * Created by lang on 2017/8/16.
 */

public class LangFaceuConfig {
    public boolean enable;             // face detect flag, set by user
    public boolean needBeautify;       // advanced beauty flag, set by user
    public boolean needBodyBeautify;   // advanced body beauty flag, set by user(not available now).
    public boolean needSticker;        // sticker attached to face flag, set by user.
    public boolean needSpecialSticker;

    // base beauty params
    public float reddenStrength;
    public float smoothStrength;
    public float whitenStrength;
    public float contrastStrength;
    public float saturationStrength;

    // advanced beauty params
    public float enlargeEyeRatio;       //ENLARGE_EYE_RATIO
    public float shrinkFaceRatio;       //SHRINK_FACE_RATIO
    public float shrinkJawRatio;        //SHRINK_JAW_RATIO
    public float narrowFaceStrength;

    public String beautyStickerPath = null;// set by user

    public LangFaceuConfig() {
        defaultBeautyParams();
    }

    public void defaultBeautyParams() {
        float[] defaultParams = {0.36f, 0.74f, 0.02f, 0.13f, 0.25f, 0.02f, 0f, 0f, 0f, 0f, 0f, 0f, 0.5f, 0f, 0f};
        needBeautify = true;
        needBodyBeautify = false;
        needSticker = true;
        needSpecialSticker = false;

        reddenStrength = defaultParams[0];
        smoothStrength = defaultParams[1];
        whitenStrength = defaultParams[2];
        contrastStrength = defaultParams[6];
        saturationStrength = defaultParams[7];

        enlargeEyeRatio = defaultParams[3];
        shrinkFaceRatio = defaultParams[4];
        shrinkJawRatio = defaultParams[5];
        narrowFaceStrength = defaultParams[9];

        beautyStickerPath = null;
    }

    public void copy(LangFaceuConfig faceuConfig) {
        this.enable = faceuConfig.enable;
        this.needBeautify = faceuConfig.needBeautify;
        this.needBodyBeautify = faceuConfig.needBodyBeautify;
        this.needSticker = faceuConfig.needSticker;

        this.reddenStrength = faceuConfig.reddenStrength;
        this.smoothStrength = faceuConfig.smoothStrength;
        this.whitenStrength = faceuConfig.whitenStrength;
        this.contrastStrength = faceuConfig.contrastStrength;
        this.saturationStrength = faceuConfig.saturationStrength;

        this.enlargeEyeRatio = faceuConfig.enlargeEyeRatio;
        this.shrinkFaceRatio = faceuConfig.shrinkFaceRatio;
        this.shrinkJawRatio = faceuConfig.shrinkJawRatio;
        this.narrowFaceStrength = faceuConfig.narrowFaceStrength;

        this.beautyStickerPath = faceuConfig.beautyStickerPath;
    }
}
