package net.lang.animation;

public interface IDecodeActionListener {
    void onParseProgress(IAnimationDecoder decoder, int currentFrame);
    void onParseComplete(IAnimationDecoder decoder, boolean parseStatus, int frameIndex);
}
