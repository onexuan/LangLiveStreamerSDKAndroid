package net.lang.streamer.faceu.image.impl

import android.graphics.Bitmap
import android.os.Environment

import net.lang.streamer.utils.LibraryHelper
import net.lang.streamer.faceu.image.AnimationDecoder
import net.lang.streamer.faceu.image.DecodeAction
import net.lang.streamer.faceu.image.ImageFrame
import net.lang.streamer.utils.DebugLog

import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer

/**
 * Created by Rayan on 2019/7/17.
 */

class WebpDecoder(private var inPath: String,
                  private var degree: Float,
                  private var action: DecodeAction) : AnimationDecoder(), Runnable {

    private val TAG = "WebpDecoder"

    private val saveImage = false

    private val webpJni = libwebpAnimJNI()
    private var imageSource: ByteArray? = null

    private var defaultWidth = 0
    private var defaultHeight = 0

    init {
        LibraryHelper.loadWebpLibOnce()
    }

    fun setSize(width: Int, height: Int) {
        defaultWidth = width
        defaultHeight = height
    }

    override fun run() {
        init()
        try {
            val width = intArrayOf(0)
            val height = intArrayOf(0)
            val nbFrames = intArrayOf(0)

            val err = webpJni._WebPAnimDecodeRGBA(inPath, width, height, nbFrames, defaultWidth, defaultHeight)
            if (saveImage)
                webpJni._WebPSaveImage(Environment.getExternalStorageDirectory().toString())
            if (0 == err) {
                DebugLog.i(TAG, "width: ${width[0]}")
                DebugLog.i(TAG, "height: ${height[0]}")
                DebugLog.i(TAG, "nbFrames: ${nbFrames[0]}")

                frameCount = nbFrames[0]
                totalLength = frameCount * webpJni._WebGetFrameDuration(0)
                if (imageSource == null)
                    imageSource = ByteArray(width[0] * height[0] * 4)
                val tempImage = Bitmap.createBitmap(width[0], height[0], Bitmap.Config.ARGB_8888)
                for (i in 0 until frameCount) {
                    webpJni._WebPGetDecodedFrame(i, imageSource!!)
                    tempImage.copyPixelsFromBuffer(ByteBuffer.wrap(imageSource))
                    val image = Bitmap.createBitmap(tempImage, 0, 0, defaultWidth, defaultHeight)
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
                }
                tempImage.recycle()
            } else {
                status = when (err) {
                    -1 -> STATUS_NOT_INIT_YET
                    -2 -> STATUS_DECODE_ERROR
                    else -> STATUS_UNKNOWN_ERROR
                }
            }

            if (!err()) {
                if (frameCount < 0) {
                    status = STATUS_FORMAT_ERROR
                    action.parseOk(false, -1)
                } else {
                    DebugLog.i(TAG, "Finished..")
                    status = STATUS_FINISH
                    action.parseOk(true, -1)
                }
            } else {
                DebugLog.e(TAG, "Webp decode failed, status=$status")
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            release()
        }
    }

    private fun init() {
        status = STATUS_PARSING
        frameCount = 0
        imageFrame = null
        imageSource = null

        webpJni._WebPAnimInit()
    }

    private fun release() {
        webpJni._WebPAnimRelease()
    }
}