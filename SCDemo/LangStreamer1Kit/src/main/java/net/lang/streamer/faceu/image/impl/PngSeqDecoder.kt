package net.lang.streamer.faceu.image.impl

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import net.lang.streamer.faceu.image.AnimationDecoder
import net.lang.streamer.faceu.image.DecodeAction
import net.lang.streamer.faceu.image.ImageFrame
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream


/**
 * Created by Rayan on 2019/9/4.
 */

class PngSeqDecoder(private var filePath: String,
                    private var degree: Float,
                    private var action: DecodeAction) : AnimationDecoder(), Runnable {
    val TAG = "PngSeqDecoder"

    private var defaultHeight = 0
    private var defaultWidth  = 0

    fun setSize(width: Int, height: Int) {
        defaultWidth = width
        defaultHeight = height
    }

    override fun run() {
        status = STATUS_PARSING

        val zipFile = ZipFile(filePath)
        val fis = FileInputStream(filePath)
        val zipIs = ZipInputStream(BufferedInputStream(fis))
        var ze = zipIs.nextEntry
        val byteBuffer = ByteArrayOutputStream()
        val bufferSize = 1024
        val buffer = ByteArray(bufferSize)

        while (ze != null) {
            if (ze.name.contains("__MACOSX/")) {
                ze = zipIs.nextEntry
                continue
            }
            byteBuffer.reset()
            var len: Int
            val inputStream = zipFile.getInputStream(ze)
            while (inputStream.read(buffer).also { len = it } != -1) {
                byteBuffer.write(buffer, 0, len)
                byteBuffer.flush()
            }

            val bytesArray = byteBuffer.toByteArray()
            val tempImage = BitmapFactory.decodeByteArray(bytesArray, 0, bytesArray.size)
            var image: Bitmap
            //scale image
            image = if(defaultHeight != 0 && defaultWidth != 0) {
                Bitmap.createScaledBitmap(tempImage, defaultWidth, defaultHeight, true)
            } else {
                tempImage
            }
            if (imageFrame == null) {
                imageFrame = ImageFrame(image, delay)
                currentFrame = imageFrame
            } else {
                var f = imageFrame
                while (f?.nextFrame != null) {
                    f = f.nextFrame
                }
                f?.nextFrame = ImageFrame(image, delay)
            }

            ze = zipIs.nextEntry
            frameCount++
        }
        zipIs.close()

        Log.i(TAG, "Finished..")
        status = STATUS_FINISH
        action.parseOk(true, -1)
    }
}