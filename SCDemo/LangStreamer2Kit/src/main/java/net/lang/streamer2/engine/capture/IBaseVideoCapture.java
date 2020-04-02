package net.lang.streamer2.engine.capture;

import android.util.Size;

import com.yunfan.graphicbuffer.GraphicBufferWrapper;

import net.lang.streamer2.engine.data.LangVideoConfiguration;

public abstract class IBaseVideoCapture {
    private Size mVideoSize;
    IVideoCaptureListener mListener;

    public IBaseVideoCapture(LangVideoConfiguration videoConfiguration) {
        if (videoConfiguration.getLandscape()) {
            mVideoSize = new Size(videoConfiguration.getWidth(), videoConfiguration.getHeight());
        } else {
            mVideoSize = new Size(videoConfiguration.getHeight(), videoConfiguration.getWidth());
        }
    }

    public final void setCaptureListener(IVideoCaptureListener listener) {
        mListener = listener;
    }

    public final Size getVideoSize() {
        return mVideoSize;
    }

    public abstract double getPreviewFps();

    public abstract void start();

    public abstract void stop();

    public abstract void release();

    public interface IVideoCaptureListener {
        boolean skip(long timestampNs);
        void onCapturedVideoFrame(GraphicBufferWrapper gb, long timestampNs);
        void onCapturedImageFrame(byte[] i420Frame, int width, int height, long timestampNs);
    }
}
