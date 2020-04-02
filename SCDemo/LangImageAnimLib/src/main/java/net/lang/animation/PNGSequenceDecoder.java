package net.lang.animation;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public final class PNGSequenceDecoder extends IAnimationDecoder {
    private static final String TAG = PNGSequenceDecoder.class.getSimpleName();

    private String mFilePath;
    private IDecodeActionListener mListener;

    private int mDefaultWidth  = 0;
    private int mDefaultHeight = 0;

    public PNGSequenceDecoder(String filePath, IDecodeActionListener listener) {
        mFilePath = filePath;
        mListener = listener;
    }

    public void setSize(int width, int height) {
        mDefaultWidth = width;
        mDefaultHeight = height;
    }

    @Override
    public void run() {
        mStatus = STATUS_PARSING;

        try {
            ZipFile zipFile = new ZipFile(mFilePath);
            FileInputStream fis = new FileInputStream(mFilePath);
            ZipInputStream zipIs = new ZipInputStream(new BufferedInputStream(fis));
            ZipEntry ze = zipIs.getNextEntry();
            ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
            int bufferSize = 1024;
            byte[] buffer = new byte[bufferSize];

            while (ze != null) {
                if (ze.getName().contains("__MACOSX/")) {
                    ze = zipIs.getNextEntry();
                    continue;
                }
                byteBuffer.reset();
                int len;
                InputStream inputStream = zipFile.getInputStream(ze);
                while ((len = inputStream.read(buffer)) != -1) {
                    byteBuffer.write(buffer, 0, len);
                    byteBuffer.flush();
                }

                byte[] bytesArray = byteBuffer.toByteArray();
                Bitmap tempImage = BitmapFactory.decodeByteArray(bytesArray, 0, bytesArray.length);
                Bitmap image;
                //scale image
                if (mDefaultWidth != 0 && mDefaultHeight != 0) {
                    image = Bitmap.createScaledBitmap(tempImage, mDefaultWidth, mDefaultHeight, true);
                } else {
                    image = tempImage;
                }
                if (mImageFrame == null) {
                    mImageFrame = new ImageFrame(image, mDelayMilliSecs);
                    mCurrentFrame = mImageFrame;
                } else {
                    ImageFrame f = mImageFrame;
                    while (f.getNextFrame() != null) {
                        f = f.getNextFrame();
                    }
                    f.setNextFrame(new ImageFrame(image, mDelayMilliSecs));
                }

                ze = zipIs.getNextEntry();
                mFrameCount++;
                if (mListener != null) {
                    mListener.onParseProgress(this, mFrameCount);
                }
            }
            zipIs.close();

            android.util.Log.i(TAG, "Finished..");
            mStatus = STATUS_FINISH;
            if (mListener != null) {
                mListener.onParseComplete(this, true, -1);
            }

        } catch (IOException ioe) {
            android.util.Log.e(TAG, ioe.getLocalizedMessage());
        }
    }
}
