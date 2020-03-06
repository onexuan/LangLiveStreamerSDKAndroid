package net.lang.streamer.faceu;

import net.lang.gpuimage.filter.base.gpuimage.GPUImageFilter;

import java.nio.ByteBuffer;

/**
 * Created by lang on 2017/8/15.
 */

public interface IFaceTracker {

    boolean loadFaceTracker(int imageWidth, int imageHeight);

    int processFromTexture(GPUImageFilter filter, int cameraFboId, int cameraTextureId, ByteBuffer rgbaBuffer);

    void unloadFaceTracker();

    void onDetectFrame(final byte[] data);
}
