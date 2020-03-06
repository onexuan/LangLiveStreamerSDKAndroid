package net.lang.streamer;

/**
 * Created by lichao on 17-7-17.
 */

public class LangWhiteListCell {
    private String mModel;
    private int mCodecFlag = 0;
    private int mRenderFlag = 0;
    public LangWhiteListCell(String model, int codecFlag, int renderFlag) {
        mModel = model;
        mCodecFlag = codecFlag;
        mRenderFlag = renderFlag;
    }

    public int flagCodec() {
        return mCodecFlag;
    }

    public int flagRender() {
        return mRenderFlag;
    }

    public String name() {
        return mModel;
    }
}
