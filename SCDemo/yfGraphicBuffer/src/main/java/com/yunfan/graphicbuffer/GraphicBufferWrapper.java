package com.yunfan.graphicbuffer;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.util.concurrent.locks.ReentrantLock;

import dalvik.system.BaseDexClassLoader;

public class GraphicBufferWrapper {
    private static final String TAG = GraphicBufferWrapper.class.getSimpleName();
    private static final String mNativeLibName = "yf-graphic";
    private static volatile boolean mIsLibLoaded = false;
    private long mNativeObject;
    private Context mContext;
    private int mWidth;
    private int mHeight;
    private int mColorFormat;

    private int mFrameBufferId;
    private int mTextureId;

    private int mInstalledSdk;

    private byte[] mI420Data;
    private byte[] mNv12Data;
    private byte[] mRGBAData;

    private ReentrantLock mLock = new ReentrantLock();
    /*
    static {
        System.loadLibrary(mNativeLibName);
    }
    */
    private static void loadLibrariesOnce() {
        synchronized (GraphicBufferWrapper.class) {
            if (!mIsLibLoaded) {
                System.loadLibrary(mNativeLibName);
                mIsLibLoaded = true;
            }
        }
    }

    public static GraphicBufferWrapper createInstance(Context context, int width, int height, int colorFormat) {
        loadLibrariesOnce();
        GraphicBufferWrapper bufferWrapper = new GraphicBufferWrapper(context, width, height, colorFormat);
        int result = bufferWrapper.loadWithSdk();
        if (result < 0) {
            bufferWrapper = null;
        }
        return bufferWrapper;
    }

    public int createTexture(int framebuffer) {
        int texture = _createFrameBufferAndBind(mWidth, mHeight, mColorFormat, framebuffer);
        if (texture > 0) {
            mTextureId = texture;
            mFrameBufferId = framebuffer;
        }
        return mTextureId;
    }

    public int width() {
        return mWidth;
    }

    public int height() {
        return mHeight;
    }

    public int stride() {
        return _stride();
    }

    public int textureId() {
        return mTextureId;
    }

    public int framebufferId() {
        return mFrameBufferId;
    }

    public boolean tryLockGB() {
        return mLock.tryLock();
    }

    public void unlockGB() {
        mLock.unlock();
    }

    public long lock() {
        return _lock();
    }

    public int unlock() {
        return _unlock();
    }

    public int installedSdk() {
        return mInstalledSdk;
    }

    public void destroy() {
        if (mTextureId > 0) {
            _destroyFrameBuffer();
            mTextureId = -1;
            mFrameBufferId = -1;
            mContext = null;
            Log.d(TAG, "destroy(): release graphic buffer context");
        }
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        destroy();
    }

    public byte[] getVideoData(boolean isI420) {
        if (mI420Data == null) {
            mI420Data = new byte[width() * height() * 3/2];
        }
        if (mNv12Data == null) {
            mNv12Data = new byte[width() * height() * 3/2];
        }
        if (framebufferId() > 0 && textureId() > 0) {
            final long bufAddr = lock();

            final int bufStride = stride();
            final int colorType = (int)'A' | (int)'B' << 8 | (int)'G' << 16 | (int)'R' << 24;
            _RgbaToI420(bufAddr, mI420Data, width(), height(), bufStride, false, 0, colorType);

            unlock();

            if (!isI420) {
                _I420ToNv12(mI420Data, mNv12Data, width(), height());
                return mNv12Data;
            } else {
                return mI420Data;
            }
        }
        return null;
    }

    public byte[] getRgbaVideoData() {
        if (mRGBAData == null) {
            mRGBAData= new byte[4* width() * height()];
        }
        if (framebufferId() > 0 && textureId() > 0) {
            final long bufAddr = lock();
            synchronized (mRGBAData) {
                int stride = _stride();
                _CopyRgbaData(width(), height(), bufAddr, stride, mRGBAData);
            }
            unlock();
            return mRGBAData;
        }
        return null;
    }

    //default constructor
    private GraphicBufferWrapper(Context context, int width, int height, int colorFormat) {
        mContext = context;
        mWidth = width;
        mHeight = height;
        mColorFormat = colorFormat;
    }

    private int loadWithSdk() {
        synchronized (GraphicBufferWrapper.class) {
            int sdk = 0;
            BaseDexClassLoader classLoader = (BaseDexClassLoader)mContext.getClassLoader();
            File file = new File(classLoader.findLibrary(mNativeLibName));
            String path = file.getParent();
            Log.d(TAG, "loadWithSdk(): " + "Install shared library path [" + path + "] ...");
            // Using dlopen when system sdk less than 19
            if (Build.VERSION.SDK_INT < 19) {
                sdk = Build.VERSION.SDK_INT;
            }
            mInstalledSdk = sdk;
            return _load(sdk, path);
        }
    }

    private native int _load(int sdk, String path);
    private native void _unload();
    private native int _createFrameBufferAndBind(int width, int height, int colorFormat, int fbId);
    private native int _destroyFrameBuffer();
    private native long _lock();
    private native int _unlock();
    private native int _stride();
    private native void _CopyRgbaData(int width, int height, long addr, int addrStride, byte[] mRbgaData);

    public static native void _RgbaToI420(long addr, byte[] i420Frame, int width, int height, int stride, boolean flip, int rotate, int srcType);
    public static native void _I420ToNv12(byte[] i420Frame, byte[] nv12Frame, int width, int height);
}
