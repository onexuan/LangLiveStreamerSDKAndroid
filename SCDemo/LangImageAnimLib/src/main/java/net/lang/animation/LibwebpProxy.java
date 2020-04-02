package net.lang.animation;

public final class LibwebpProxy {
    private static final String mNativeLibName = "lang_webpanim";
    private static volatile boolean mIsLibLoaded = false;

    public LibwebpProxy() {
        loadLibrariesOnce();
    }

    public int init() {
        return WebPAnimInit();
    }

    public int decodeRGBA(String path, int[] width, int[] height, int[] nbFrames, int scaledWidth, int scaledHeight) {
        return WebPAnimDecodeRGBA(path, width, height, nbFrames, scaledWidth, scaledHeight);
    }

    public void getDecodedFrame(int index, byte[] imageData) {
        WebPGetDecodedFrame(index, imageData);
    }

    public int getFrameDuration(int index) {
        return WebPGetFrameDuration(index);
    }

    public int saveImage(String dumpFolder) {
        return WebPSaveImage(dumpFolder);
    }

    public int release() {
        return WebPAnimRelease();
    }

    private static void loadLibrariesOnce() {
        synchronized (LibwebpProxy.class) {
            if (!mIsLibLoaded) {
                System.loadLibrary(mNativeLibName);
                mIsLibLoaded = true;
            }
        }
    }

    private static native int WebPAnimInit();

    private static native int WebPAnimDecodeRGBA(String path, int[] width, int[] height, int[] nbFrames, int scaledWidth, int scaledHeight);

    private static native void WebPGetDecodedFrame(int index, byte[] imageData);

    private static native int WebPGetFrameDuration(int index);

    private static native int WebPSaveImage(String dumpFolder);

    private static native int WebPAnimRelease();
}
