package net.lang.streamer.faceu.image

import android.graphics.Bitmap

/**
 * Created by Rayan on 2019/7/17.
 */

abstract class AnimationDecoder {

    val STATUS_PARSING = 0
    val STATUS_FORMAT_ERROR = 1
    val STATUS_OPEN_ERROR = 2
    val STATUS_FINISH = -1
    val STATUS_NOT_INIT_YET = -2
    val STATUS_DECODE_ERROR = -3
    val STATUS_UNKNOWN_ERROR = -4

    protected var imageFrame: ImageFrame? = null // frames read from current file
    protected var currentFrame: ImageFrame? = null
    protected var status = 0
    internal var frameCount = 0
    internal var totalLength = 0
    protected var delay = 0 // delay in milliseconds

    fun parseOk(): Boolean {
        return status == STATUS_FINISH
    }

    /**
     * the delay time to get the nth frame
     * @param n no. of frame
     * @return delay time in ms
     */
    fun getDelay(n: Int): Int {
        delay = -1
        if (n in 0 until frameCount) {
            // delay = ((ImageFrame) frames.elementAt(n)).delay;
            val f = getFrame(n)
            if (f != null)
                delay = f.delay
        }
        return delay
    }

    /**
     * the delay time to get all frames
     * @return
     */
    fun getDelays(): IntArray {
        var f = imageFrame
        val d = IntArray(frameCount)
        var i = 0
        while (f != null && i < frameCount) {
            d[i] = f.delay
            f = f.nextFrame
            i++
        }
        return d
    }

    /**
     * get the first frame
     * @return
     */
    fun getImage(): Bitmap? {
        return getFrameImage(0)
    }

    /**
     * get the nth bitmap
     * @param n
     * @return the nth bitmap or null if no bitmap or error occurred
     */
    fun getFrameImage(n: Int): Bitmap? {
        val frame = getFrame(n)
        return frame?.image
    }

    /**
     * get the nth ImageFrame
     * @param n
     * @return
     */
    fun getFrame(n: Int): ImageFrame? {
        var frame = imageFrame
        var i = 0
        while (frame != null) {
            if (i == n) {
                return frame
            } else {
                frame = frame.nextFrame
            }
            i++
        }
        return null
    }

    /**
     * reset to the first frame
     */
    fun reset() {
        currentFrame = imageFrame
    }

    fun err(): Boolean {
        return status != STATUS_PARSING
    }
}