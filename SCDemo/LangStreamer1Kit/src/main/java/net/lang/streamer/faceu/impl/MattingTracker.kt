package net.lang.streamer.faceu.impl

import android.content.Context
import android.graphics.*
import android.opengl.GLES20
import android.os.SystemClock

import com.langlive.LangAIKit.TFLiteInterpreterPortrait
import net.lang.gpuimage.filter.advanced.MagicBlurFilter
import net.lang.gpuimage.filter.advanced.MagicMattingBlendFilter

import net.lang.gpuimage.filter.base.gpuimage.GPUImageFilter
import net.lang.gpuimage.utils.*
import net.lang.streamer.LangTexture

import net.lang.streamer.camera.LangCameraEngine
import net.lang.streamer.config.LangObjectSegmentationConfig
import net.lang.streamer.faceu.image.AnimationDecoder
import net.lang.streamer.faceu.image.DecodeAction
import net.lang.streamer.faceu.image.impl.PngSeqDecoder
import net.lang.streamer.faceu.image.impl.WebpDecoder
import net.lang.streamer.utils.DebugLog
import net.lang.streamer.widget.AnimationCallback
import net.lang.streamer.video.gles.GlUtil
import java.lang.RuntimeException

import java.nio.ByteBuffer
import java.nio.FloatBuffer

class MattingTracker(ctx: Context,
                     params: LangObjectSegmentationConfig) : LangObjectSegmentationTracker(ctx, params) {

    private val VERBOSE = false
    private val TAG = this.javaClass.simpleName

    private var decoder: AnimationDecoder? = null
    private var giftDecoder: AnimationDecoder? = null
    private var giftTexId = OpenGlUtils.NO_TEXTURE
    private var giftTextureIndex = 0
    private var giftTextureIds: IntArray? = null
    private var mode = Mode.kBackground
    private var animationCallback: AnimationCallback? = null
    internal enum class Mode constructor(var mName: String) {
        kBackground("Background"),
        kGift("Gift"),
    }

    private var finishLoadingGift = false
    private var drawGiftInSdk = false //test for draw gift in demo app.
    private var textureIndex = 0

    private var webpTexId = OpenGlUtils.NO_TEXTURE

    private var magicHorizontalBlurFilter: MagicBlurFilter? = null
    private var magicVerticalBlurFilter: MagicBlurFilter? = null
    private var mattingBlendFilter: MagicMattingBlendFilter? = null

    private var blurTextureH: LangTexture? = null
    private var blurTextureV: LangTexture? = null

    private var rgbaArray: ByteArray? = null

    private var framesToInsert = 0
    private var prevFramesToInsert = 0
    private var remaining = -1

    init {
        // tensor-flow ctx allocation
        tfLite = TFLiteInterpreterPortrait(ctx)
        fgMaskCeilingArray = IntArray(tfLite.maskWidth) {tfLite.maskHeight}

        magicHorizontalBlurFilter = MagicBlurFilter(ctx, true, 4f)
        magicVerticalBlurFilter = MagicBlurFilter(ctx, false, 4f)
        mattingBlendFilter = MagicMattingBlendFilter(ctx)
    }

    override fun loadFaceTracker(imageWidth: Int, imageHeight: Int): Boolean {
        blurTextureH = LangTexture()
        blurTextureH!!.initTexture_l(imageWidth, imageHeight)
        blurTextureV = LangTexture()
        blurTextureV!!.initTexture_l(imageWidth, imageHeight)

        magicHorizontalBlurFilter?.init()
        magicHorizontalBlurFilter?.onInputSizeChanged(imageWidth, imageHeight)

        magicVerticalBlurFilter?.init()
        magicVerticalBlurFilter?.onInputSizeChanged(imageWidth, imageHeight)

        mattingBlendFilter?.init()
        mattingBlendFilter?.onInputSizeChanged(imageWidth, imageHeight)

        return super.loadFaceTracker(imageWidth, imageHeight)
    }

    fun setAnimationCallback(callback: AnimationCallback){
        animationCallback = callback
    }

    @Synchronized
    override fun setAnimationData(inputPath: String, giftPath: String?) {
        synchronized(this) {
            when {
                "webp" == inputPath.substringAfterLast(".") -> {
                    decoder = WebpDecoder(inputPath, 0f, object : DecodeAction {
                        override fun parseOk(parseStatus: Boolean, frameIndex: Int) {

                            if (parseStatus) {
                                animationCallback?.onDecodeSuccess()
                            } else {
                                animationCallback?.onDecodeError()
                            }
                        }
                    }).apply {
                        setSize(tfLite.maskWidth, tfLite.maskHeight)
                    }
                }
                "zip" == inputPath.substringAfterLast(".") -> {
                    decoder = PngSeqDecoder(inputPath, 0f, object : DecodeAction {
                        override fun parseOk(parseStatus: Boolean, frameIndex: Int) {
                            if (parseStatus)
                                animationCallback?.onDecodeSuccess()
                            else
                                animationCallback?.onDecodeError()
                        }
                    }).apply {
                        setSize(tfLite.maskWidth, tfLite.maskHeight)
                    }
                }
                else -> {
                    throw RuntimeException("Unsupported type")
                }
            }
            runInBackground(decoder as Runnable)

            if (giftPath != null) {
                when {
                    "webp" == giftPath.substringAfterLast(".") -> {
                        giftDecoder = WebpDecoder(inputPath, 0f, object : DecodeAction {
                            override fun parseOk(parseStatus: Boolean, frameIndex: Int) {

                                if (parseStatus) {
                                    giftTextureIds = IntArray(giftDecoder!!.frameCount)
                                    animationCallback?.onDecodeSuccess()
                                } else
                                    animationCallback?.onDecodeError()
                            }
                        }).apply {
                            setSize(tfLite.maskWidth, tfLite.maskHeight)
                        }
                    }
                    "zip" == giftPath.substringAfterLast(".") -> {
                        giftDecoder = PngSeqDecoder(giftPath, 0f, object : DecodeAction {
                            override fun parseOk(parseStatus: Boolean, frameIndex: Int) {
                                if (parseStatus) {
                                    giftTextureIds = IntArray(giftDecoder!!.frameCount)
                                    animationCallback?.onDecodeSuccess()
                                } else
                                    animationCallback?.onDecodeError()
                            }
                        }).apply {
                            setSize(tfLite.maskWidth, tfLite.maskHeight)
                        }
                    }
                    else -> {
                        throw RuntimeException("Unsupported type")
                    }
                }
                runInBackground(giftDecoder as Runnable)
            }

        }
    }

    override fun processFromTexture(filter: GPUImageFilter?, cameraFboId: Int, cameraTextureId: Int, rgbaBuffer: ByteBuffer?): Int {
        srcTextureBuffer.position(0)

        synchronized(this) {
            if ((decoder != null && decoder!!.parseOk()) ||
                    (giftDecoder != null && giftDecoder!!.parseOk())) {
                when (getCurrentOrientation()) {
                    0 -> {
                        updateTextureBuffer(srcTextureBuffer, Rotation.NORMAL)
                    }
                    1 -> if (LangCameraEngine.isFrontCamera()) {
                        updateTextureBuffer(srcTextureBuffer, Rotation.ROTATION_90, flipHorizontal = false, flipVertical = false)
                    } else {
                        updateTextureBuffer(srcTextureBuffer, Rotation.ROTATION_270, true)
                    }
                    2 -> {
                        updateTextureBuffer(srcTextureBuffer, Rotation.ROTATION_180)
                    }
                    3 -> if (LangCameraEngine.isFrontCamera()) {
                        updateTextureBuffer(srcTextureBuffer, Rotation.ROTATION_270, flipHorizontal = false, flipVertical = false)
                    } else {
                        updateTextureBuffer(srcTextureBuffer, Rotation.ROTATION_90, true)
                    }
                }

                getPixelData(filter!!, cameraTextureId, mGLCubeBuffer, srcTextureBuffer)
                if (graphicBuffer!!.tryLockGB()) {
                    rgbaArray = graphicBuffer!!.rgbaVideoData

                    val bufAddr = graphicBuffer!!.lock()
                    val bufStride = graphicBuffer!!.stride()

                    onDetectFrame_l(bufAddr, bufStride)

                    graphicBuffer!!.unlockGB()
                }

                if (pixelClasses != null)
                    handleSegmentation(pixelClasses!!, tfLite.maskWidth, tfLite.maskHeight)
                else {
                    DebugLog.e(TAG, "Buffer error...")
                    return OpenGlUtils.NO_TEXTURE
                }

                applyOverlayToTexture(filter, cameraTextureId, mGLCubeBuffer, srcTextureBuffer)

                return processedTexture!!.textureID()
            } else
                return cameraTextureId
        }
    }

    override fun unloadFaceTracker() {
        synchronized(this) {
            if (null != giftTextureIds) {
                GLES20.glDeleteTextures(giftTextureIds!!.size, giftTextureIds, 0)
                giftTextureIds = null
            }

            if (decoder != null) {
                for (i in 0 until decoder!!.frameCount) {
                    if (!decoder!!.getFrameImage(i)!!.isRecycled)
                        decoder!!.getFrameImage(i)!!.recycle()
                }
                decoder = null
            }
            if (giftDecoder != null) {
                for (i in 0 until giftDecoder!!.frameCount) {
                    if (!giftDecoder!!.getFrameImage(i)!!.isRecycled)
                        giftDecoder!!.getFrameImage(i)!!.recycle()
                }
                giftDecoder = null
            }
        }

        super.unloadFaceTracker()
    }

    override fun getPixelData(filter: GPUImageFilter?, cameraTextureId: Int, cubeBuffer: FloatBuffer, textureBuffer: FloatBuffer) {
        val previousViewport = IntArray(4)
        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, previousViewport, 0)
        GLES20.glViewport(0, 0, tfLite.inputWidth, tfLite.inputHeight)

        if (graphicBuffer != null) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, graphicBufferID!![0])
            GlUtil.checkGlError("glBindFramebuffer")

            filter!!.onDrawFrame(cameraTextureId, cubeBuffer, textureBuffer)
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_NONE)
        GLES20.glViewport(previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3])
    }

    override fun handleSegmentation(resultPixels: ByteArray, maskWidth: Int, maskHeight: Int) {
        super.handleSegmentation(resultPixels, maskWidth, maskHeight)

        if (Mode.kBackground == mode) {
            if (prevFps == 0L) {
                prevFps = System.currentTimeMillis()
            } else {
                val currentTsMs = System.currentTimeMillis()
                val time = currentTsMs - prevFps
                fps = 1000f / time
                prevFps = currentTsMs
            }

            framesToInsert = ((decoder!!.totalLength / 1000f * fps) / decoder!!.frameCount).toInt()
            if (framesToInsert != prevFramesToInsert || remaining < 0)
                remaining = framesToInsert
            prevFramesToInsert = framesToInsert

            val index = textureIndex % decoder!!.frameCount
            if (remaining > 1) {
                --remaining
            } else {
                remaining = framesToInsert
                textureIndex++
            }

            val image = decoder!!.getFrameImage(index)!!
            webpTexId = OpenGlUtils.loadTexture(image, webpTexId, false)
            mattingBlendFilter?.setWebpTextureId(webpTexId, srcTextureBuffer)

            // load prediction from tflite as alpha
            for (i in 0 until tfLite.maskWidth) {
                for (j in 0 until tfLite.maskHeight) {
                    val currentIndex = j * tfLite.maskHeight + i
                    if (resultPixels[currentIndex] > 0) {
                        maskPixels!![currentIndex] = Color.argb(
                                rgbaArray!![currentIndex * 4 + 3].toInt(),
                                rgbaArray!![currentIndex * 4 + 0].toInt(),
                                rgbaArray!![currentIndex * 4 + 1].toInt(),
                                rgbaArray!![currentIndex * 4 + 2].toInt())
                    } else {
                        maskPixels!![currentIndex] = Color.TRANSPARENT
                    }
                }
            }

            maskBmp!!.setPixels(maskPixels, 0, tfLite.maskHeight, 0, 0, tfLite.maskWidth, tfLite.maskHeight)
            maskTexId = OpenGlUtils.loadTexture(maskBmp!!, maskTexId, false)
        } else {
            val index = giftTextureIndex % giftDecoder!!.frameCount
            giftTextureIndex++
            if(!finishLoadingGift)
                giftTextureIds!![index] = OpenGlUtils.loadTexture(giftDecoder!!.getFrameImage((index)), OpenGlUtils.NO_TEXTURE, true)
            giftTexId = giftTextureIds!![index]
        }
    }

    override fun applyOverlayToTexture(filter: GPUImageFilter, srcTexId: Int, cubeBuffer: FloatBuffer, textureBuffer: FloatBuffer) {
        GLES20.glViewport(0, 0, previewWidth, previewHeight)

        if (Mode.kBackground == mode) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, blurTextureH!!.id())
            magicHorizontalBlurFilter!!.onDrawFrame(maskTexId, cubeBuffer, mGLTextureBuffer)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, blurTextureV!!.id())
            magicVerticalBlurFilter!!.onDrawFrame(blurTextureH!!.textureID(), cubeBuffer, mGLTextureBuffer)
            mattingBlendFilter!!.setBackgroundTextureId(blurTextureV!!.textureID(), textureBuffer)

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, processedTexture!!.id())
            mattingBlendFilter!!.onDrawFrame(srcTexId, cubeBuffer, mGLTextureBuffer)

            if (0 == textureIndex % decoder!!.frameCount) {
                animationCallback?.onAnimationPlayFinish()
                if(drawGiftInSdk) {
                    mode = Mode.kGift
                } else {
//                    animationListener!!.onFinish()
                }
            }
        } else {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, processedTexture!!.id())
            filter.onDrawFrame(giftTexId, cubeBuffer, textureBuffer)
            if (0 == (giftTextureIndex % giftDecoder!!.frameCount)) {
                if (null != animationListener)
                    animationListener!!.onFinish()
                mode = Mode.kBackground
                if (!finishLoadingGift)
                    finishLoadingGift = true
            }
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_NONE)
    }

    private fun setGradientColors(
            maskPixels: IntArray,
            maskBmpWidth: Int,
            maskBmpHeight: Int,
            resultPixels: ByteArray,
            threshold: Int,
            background: Bitmap
    ) {
        val startTime = SystemClock.uptimeMillis()

        scanGradient(maskBmpWidth, maskBmpHeight, resultPixels, threshold, false)
        scanGradient(maskBmpWidth, maskBmpHeight, resultPixels, threshold)

        var classNo: Int
        var i = 0
        var j: Int

        // Paint current mask with mapping of gradient color and mask height
        val alphaGradient = -1f / bgMaskHeight
        val gradientSpeedArray = FloatArray(fgMaskCeilingArray!!.size)
        var largestSpeed = Float.MIN_VALUE
        var largestSpeedIndex = 0
        fgMaskCeilingArray!!.forEachIndexed { index, ceiling ->
            if (ceiling > 0) {
                gradientSpeedArray[index] = bgMaskHeight / ceiling * 0.9f
            } else
                gradientSpeedArray[index] = -bgMaskHeight / maskBmpHeight + 2.5f
            if (largestSpeed < gradientSpeedArray[index]) {
                largestSpeed = gradientSpeedArray[index]
                largestSpeedIndex = index
            }
        }
        gradientSpeedArray.forEachIndexed { index, speed ->
            when {
                index < largestSpeedIndex -> {
                    val a = ((largestSpeed - gradientSpeedArray.first()) / largestSpeedIndex)
                    gradientSpeedArray[index] = a * index + (largestSpeed - a * largestSpeedIndex)
                }
                index > largestSpeedIndex -> {
                    val a = ((gradientSpeedArray.last() - largestSpeed) / (maskBmpWidth - 1 - largestSpeedIndex))
                    gradientSpeedArray[index] = a * index + (gradientSpeedArray.last() - a * (maskBmpWidth - 1))
                }
                else -> gradientSpeedArray[index] = speed
            }
        }

        while (i < maskBmpWidth) {
            j = 0
            while (j < maskBmpHeight) {
                classNo = resultPixels[j * maskBmpWidth + i].toInt() and 0xFF
                if (classNo > threshold) {
                    val alphaFactor = calculateGradients(
                            j,
                            bgMaskCeiling,
                            alphaGradient,
                            gradientSpeedArray[i]
                    )
                    val pixel = background.getPixel(i, j)
                    maskPixels[j * maskBmpHeight + i] =
                            Color.argb(
                                    (Color.alpha(pixel) * alphaFactor).toInt(),
                                    Color.red(pixel),
                                    Color.green(pixel),
                                    Color.blue(pixel)
                            )
                } else {
                    maskPixels[j * maskBmpHeight + i] = Color.TRANSPARENT
                }
                j++
            }
            i++
        }

        val endTime = SystemClock.uptimeMillis()
        val inferenceTime = endTime - startTime

        if (VERBOSE) DebugLog.d(TAG, "setGradientColors time=$inferenceTime")
    }

    private fun calculateGradients(
            index: Int,
            ceiling: Int,
            gradient: Float,
            gradient_speed: Float
    ): Float {
        var alphaFactor = (1f + (index - ceiling).toFloat() * gradient * gradient_speed)
        alphaFactor = if (gradient < 0) {
            if (alphaFactor < 0f) 0f else alphaFactor
        } else {
            if (alphaFactor > 0f) 0f else alphaFactor
        }
        return alphaFactor
    }
}