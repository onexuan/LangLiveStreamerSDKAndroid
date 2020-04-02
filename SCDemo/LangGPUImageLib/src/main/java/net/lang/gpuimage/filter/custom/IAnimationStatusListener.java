package net.lang.gpuimage.filter.custom;

public interface IAnimationStatusListener {
    void onAnimationDecoding(String animPath, float progressPercentage);
    /**
     * !!Important!!
     * To add the motion sticker success, MUST wait until this callback function invoked
     */
    void onAnimationDecodeSuccess(String animPath);

    void onAnimationDecodeError(String animPath);

    void onAnimationPlaying(String animPath, int frameIndex);

    void onAnimationPlayFinish(String animPath);
}
