package net.lang.streamer.rtc.io.agora.ex;

/**
 * Created by lang on 2017/9/1.
 */

public class AudioVideoPreProcessing {

    private IAgoraNativeDataHandler mHandlerClient;

    public void setNativeDataHandler(IAgoraNativeDataHandler client) {
        mHandlerClient = client;
    }

    public final void registerPreProcessing() {
        if (mHandlerClient == null) {
            throw new IllegalStateException("should call setStreamingClient first");
        }
        doRegisterPreProcessing();
    }

    public final void deregisterPreProcessing() {

        doDeregisterPreProcessing();
    }

    @SuppressWarnings("native call")
    private void VM_onMixedAudioData(final byte[] data) {

        mHandlerClient.onGetMixedPCMData(data);
    }

    @SuppressWarnings("native call")
    private void VM_onVideoData(final byte[] data, int width, int height, int rotation, int pid) {

        mHandlerClient.onGetRemoteYUVData(pid, data, width, height, rotation);
    }

    public native void doRegisterPreProcessing();

    public native void doDeregisterPreProcessing();

    static {
        System.loadLibrary("apm-plugin-audio-video-preprocessing");
    }
}
