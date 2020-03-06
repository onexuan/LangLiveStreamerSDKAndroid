package net.lang.streamer.faceu.image.impl

/**
 * Created by Rayan on 2019/7/17.
 */

class libwebpAnimJNI {

    fun _WebPAnimInit(): Int {
        return WebPAnimInit()
    }

    fun _WebPAnimDecodeRGBA(inPath: String, width: IntArray, height: IntArray, nbFrames: IntArray, scaledWidth: Int, scaledHeight: Int): Int {
        return WebPAnimDecodeRGBA(inPath, width, height, nbFrames, scaledWidth, scaledHeight)
    }

    fun _WebPGetDecodedFrame(index: Int, imageData: ByteArray) {
        WebPGetDecodedFrame(index, imageData)
    }

    fun _WebGetFrameDuration(index: Int): Int {
        return WebGetFrameDuration(index)
    }

    fun _WebPSaveImage(dump_folder: String): Int {
        return WebPSaveImage(dump_folder)
    }

    fun _WebPAnimRelease(): Int {
        return WebPAnimRelease()
    }

    private external fun WebPAnimInit(): Int

    private external fun WebPAnimDecodeRGBA(var0: String, var1: IntArray, var2: IntArray, var3: IntArray, var4: Int, var5: Int): Int

    private external fun WebPGetDecodedFrame(var0: Int, imageData: ByteArray)

    private external fun WebGetFrameDuration(var0: Int): Int

    private external fun WebPSaveImage(var0: String): Int

    private external fun WebPAnimRelease(): Int
}