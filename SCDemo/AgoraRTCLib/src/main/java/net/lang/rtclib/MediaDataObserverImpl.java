package net.lang.rtclib;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class MediaDataObserverImpl implements IRTCFrameListener {

    private static final int VIDEO_DEFAULT_BUFFER_SIZE = 3240 * 1080; // default maximum video size Full HD+
    private static final int AUDIO_DEFAULT_BUFFER_SIZE = 2048;

    private final List<IMediaAudioObserver> audioObserverList = new ArrayList<>();
    private final List<IMediaVideoObserver> videoObserverList = new ArrayList<>();

    public ByteBuffer byteBufferCapture = ByteBuffer.allocateDirect(VIDEO_DEFAULT_BUFFER_SIZE);
    public ByteBuffer byteBufferRender = ByteBuffer.allocateDirect(VIDEO_DEFAULT_BUFFER_SIZE);
    public ByteBuffer byteBufferAudioRecord = ByteBuffer.allocateDirect(AUDIO_DEFAULT_BUFFER_SIZE);
    public ByteBuffer byteBufferAudioPlay = ByteBuffer.allocateDirect(AUDIO_DEFAULT_BUFFER_SIZE);
    public ByteBuffer byteBufferBeforeAudioMix = ByteBuffer.allocateDirect(AUDIO_DEFAULT_BUFFER_SIZE);
    public ByteBuffer byteBufferAudioMix = ByteBuffer.allocateDirect(AUDIO_DEFAULT_BUFFER_SIZE);

    private final List<MediaDataBuffer> decodeBufferList = new ArrayList<>();

    private boolean beCaptureVideoShot = false;
    private boolean beRenderVideoShot = false;
    private String captureFilePath = null;
    private String renderFilePath = null;
    private int renderVideoShotUid;

    private static MediaDataObserverImpl myAgent = null;
    public static MediaDataObserverImpl the() {
        if (myAgent == null) {
            synchronized (MediaDataObserverImpl.class) {
                if (myAgent == null)
                    myAgent = new MediaDataObserverImpl();
            }
        }
        return myAgent;
    }

    public void addVideoObserver(IMediaVideoObserver observer) {
        videoObserverList.add(observer);
    }

    public void removeVideoObserver(IMediaVideoObserver observer) {
        videoObserverList.remove(observer);
    }

    public void addAudioObserver(IMediaAudioObserver observer) {
        audioObserverList.add(observer);
    }

    public void removeAudioObserver(IMediaAudioObserver observer) {
        audioObserverList.remove(observer);
    }

    public void saveCaptureVideoSnapshot(String filePath) {
        beCaptureVideoShot = true;
        captureFilePath = filePath;
    }

    public void saveRenderVideoSnapshot(String filePath, int uid) {
        beRenderVideoShot = true;
        renderFilePath = filePath;
        renderVideoShotUid = uid;
    }

    public void addDecodeBuffer(int uid) {
        int index = getDecodeIndex(uid);
        if (index == -1) {
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(VIDEO_DEFAULT_BUFFER_SIZE);
            decodeBufferList.add(new MediaDataBuffer(uid, byteBuffer));
            MediaPreProcessingNative.setVideoDecodeByteBuffer(uid, byteBuffer);
        }
    }

    public void removeDecodeBuffer(int uid) {
        int index = getDecodeIndex(uid);
        if (index != -1) {
            decodeBufferList.remove(index);
        }

        MediaPreProcessingNative.setVideoDecodeByteBuffer(uid, null);
    }

    private int getDecodeIndex(int uid) {
        for (int i = 0; i < decodeBufferList.size(); i++) {
            if (decodeBufferList.get(i).getUid() == uid) {
                return i;
            }
        }
        return -1;
    }

    public void removeAllBuffer() {
        decodeBufferList.removeAll(decodeBufferList);
        releaseBuffer();
    }

    @Override
    public void onCaptureVideoFrame(int videoFrameType, int width, int height, int bufferLength, int yStride, int uStride, int vStride, int rotation, long renderTimeMs) {

        byte[] buf = new byte[bufferLength];
        byteBufferCapture.limit(bufferLength);
        byteBufferCapture.get(buf);
        byteBufferCapture.flip();

        for (IMediaVideoObserver observer : videoObserverList) {
            observer.onCaptureVideoFrame(buf, videoFrameType, width, height, bufferLength, yStride, uStride, vStride, rotation, renderTimeMs);
        }

        byteBufferCapture.put(buf);
        byteBufferCapture.flip();

        if (beCaptureVideoShot) {
            beCaptureVideoShot = false;

            getVideoSnapshot(width, height, rotation, bufferLength, buf, captureFilePath, yStride, uStride, vStride);
        }
    }

    @Override
    public void onRenderVideoFrame(int uid, int videoFrameType, int width, int height, int bufferLength, int yStride, int uStride, int vStride, int rotation, long renderTimeMs) {

        for (IMediaVideoObserver observer : videoObserverList) {
            Iterator<MediaDataBuffer> it = decodeBufferList.iterator();
            while (it.hasNext()) {
                MediaDataBuffer tmp = it.next();
                if (tmp.getUid() == uid) {
                    byte[] buf = new byte[bufferLength];
                    tmp.getByteBuffer().limit(bufferLength);
                    tmp.getByteBuffer().get(buf);
                    tmp.getByteBuffer().flip();

                    observer.onRenderVideoFrame(uid, buf, videoFrameType, width, height, bufferLength, yStride, uStride, vStride, rotation, renderTimeMs);

                    tmp.getByteBuffer().put(buf);
                    tmp.getByteBuffer().flip();

                    if (beRenderVideoShot) {
                        if (uid == renderVideoShotUid) {
                            beRenderVideoShot = false;

                            getVideoSnapshot(width, height, rotation, bufferLength, buf, renderFilePath, yStride, uStride, vStride);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onRecordAudioFrame(int audioFrameType, int samples, int bytesPerSample, int channels, int samplesPerSec, long renderTimeMs, int bufferLength) {
        byte[] buf = new byte[bufferLength];
        byteBufferAudioRecord.limit(bufferLength);
        byteBufferAudioRecord.get(buf);
        byteBufferAudioRecord.flip();

        for (IMediaAudioObserver observer : audioObserverList) {
            observer.onRecordAudioFrame(buf, audioFrameType, samples, bytesPerSample, channels, samplesPerSec, renderTimeMs, bufferLength);
        }

        byteBufferAudioRecord.put(buf);
        byteBufferAudioRecord.flip();
    }

    @Override
    public void onPlaybackAudioFrame(int audioFrameType, int samples, int bytesPerSample, int channels, int samplesPerSec, long renderTimeMs, int bufferLength) {
        byte[] buf = new byte[bufferLength];
        byteBufferAudioPlay.limit(bufferLength);
        byteBufferAudioPlay.get(buf);
        byteBufferAudioPlay.flip();

        for (IMediaAudioObserver observer : audioObserverList) {
            observer.onPlaybackAudioFrame(buf, audioFrameType, samples, bytesPerSample, channels, samplesPerSec, renderTimeMs, bufferLength);
        }

        byteBufferAudioPlay.put(buf);
        byteBufferAudioPlay.flip();
    }

    @Override
    public void onPlaybackAudioFrameBeforeMixing(int uid, int audioFrameType, int samples, int bytesPerSample, int channels, int samplesPerSec, long renderTimeMs, int bufferLength) {
        byte[] buf = new byte[bufferLength];
        byteBufferBeforeAudioMix.limit(bufferLength);
        byteBufferBeforeAudioMix.get(buf);
        byteBufferBeforeAudioMix.flip();

        for (IMediaAudioObserver observer : audioObserverList) {
            observer.onPlaybackAudioFrameBeforeMixing(uid, buf, audioFrameType, samples, bytesPerSample, channels, samplesPerSec, renderTimeMs, bufferLength);
        }

        byteBufferBeforeAudioMix.put(buf);
        byteBufferBeforeAudioMix.flip();
    }

    @Override
    public void onMixedAudioFrame(int audioFrameType, int samples, int bytesPerSample, int channels, int samplesPerSec, long renderTimeMs, int bufferLength) {
        byte[] buf = new byte[bufferLength];
        byteBufferAudioMix.limit(bufferLength);
        byteBufferAudioMix.get(buf);
        byteBufferAudioMix.flip();

        for (IMediaAudioObserver observer : audioObserverList) {
            observer.onMixedAudioFrame(buf, audioFrameType, samples, bytesPerSample, channels, samplesPerSec, renderTimeMs, bufferLength);
        }

        byteBufferAudioMix.put(buf);
        byteBufferAudioMix.flip();
    }

    private void getVideoSnapshot(int width, int height, int rotation, int bufferLength, byte[] buffer, String filePath, int yStride, int uStride, int vStride) {
        File file = new File(filePath);

        byte[] NV21 = new byte[bufferLength];
        swapYU12toYUV420SemiPlanar(buffer, NV21, width, height, yStride, uStride, vStride);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        int[] strides = {yStride, yStride};
        YuvImage image = new YuvImage(NV21, ImageFormat.NV21, width, height, strides);

        image.compressToJpeg(
                new Rect(0, 0, image.getWidth(), image.getHeight()),
                100, baos);

        // rotate picture when saving to file
        Matrix matrix = new Matrix();
        matrix.postRotate(rotation);
        byte[] bytes = baos.toByteArray();
        try {
            baos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        Bitmap target = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

        File fileParent = file.getParentFile();
        if (!fileParent.exists()) {
            fileParent.mkdirs();
        }
        if (file.exists()) {
            file.delete();
        }

        try {
            file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        target.compress(Bitmap.CompressFormat.JPEG, 100, fos);

        target.recycle();
        bitmap.recycle();

        try {
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void swapYU12toYUV420SemiPlanar(byte[] yu12bytes, byte[] i420bytes, int width, int height, int yStride, int uStride, int vStride) {
        System.arraycopy(yu12bytes, 0, i420bytes, 0, yStride * height);
        int startPos = yStride * height;
        int yv_start_pos_u = startPos;
        int yv_start_pos_v = startPos + startPos / 4;
        for (int i = 0; i < startPos / 4; i++) {
            i420bytes[startPos + 2 * i + 0] = yu12bytes[yv_start_pos_v + i];
            i420bytes[startPos + 2 * i + 1] = yu12bytes[yv_start_pos_u + i];
        }
    }

    public void releaseBuffer() {
        byteBufferCapture.clear();
        byteBufferRender.clear();
        byteBufferAudioRecord.clear();
        byteBufferAudioPlay.clear();
        byteBufferBeforeAudioMix.clear();
        byteBufferAudioMix.clear();
    }
}
