package net.lang.streamer.rtc.io.agora.ex;

/**
 * Created by lang on 2017/9/1.
 */

public interface IAgoraNativeDataHandler {

    void onGetMixedPCMData(final byte[] pcm);

    void onGetLocalYUVData(final byte[] yuv, final int width, final int height);

    void onGetRemoteYUVData(final int pid, final byte[] yuv, final int width, final int height, final int rotation);

}
