package net.lang.streamer.faceu.impl

import android.content.Context
import android.graphics.Color
import android.opengl.GLES20
import android.os.SystemClock

import com.langlive.LangAIKit.TFLiteInterprerHair

import net.lang.streamer.camera.LangCameraEngine
import net.lang.streamer.config.LangObjectSegmentationConfig
import net.lang.gpuimage.filter.advanced.MagicGrayScaleFilter
import net.lang.gpuimage.filter.advanced.MagicSoftlightBlendFilter
import net.lang.gpuimage.filter.base.gpuimage.GPUImageFilter
import net.lang.gpuimage.utils.*;
import net.lang.streamer.utils.DebugLog
import net.lang.streamer.video.gles.GlUtil

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class BeautyHairTracker(ctx: Context,
                        params: LangObjectSegmentationConfig) : LangObjectSegmentationTracker(ctx, params) {

    private val VERBOSE = false
    private val TAG = this.javaClass.simpleName

    private var gradientAlpha: Int = 0
    private var redGradientStart: Int = 0
    private var redGradientEnd: Int = 0
    private var greenGradientStart: Int = 0
    private var greenGradientEnd: Int = 0
    private var blueGradientStart: Int = 0
    private var blueGradientEnd: Int = 0

    private var maskTextureBuffer: FloatBuffer? = null

    init {
        // render filters allocation
        grayScaleFilter = MagicGrayScaleFilter()

        tfLite = TFLiteInterprerHair(ctx)

        softlightBlendFilter = MagicSoftlightBlendFilter(ctx, true, params.getSaturation(), 4f)

        maskTextureBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
        maskTextureBuffer!!.put(TextureRotationUtil.getRotation(Rotation.NORMAL, false, true)).position(0)
    }

    @Synchronized
    override fun switchParams(params: LangObjectSegmentationConfig) {
        if (this.params.getStartColor() != params.getStartColor()) {
            this.params = params
            if (params.getEndColor() != params.getStartColor()) {
                gradientAlpha = Color.alpha(params.getStartColor())
                redGradientStart = Color.red(params.getStartColor())
                redGradientEnd = Color.red(params.getEndColor())
                greenGradientStart = Color.green(params.getStartColor())
                greenGradientEnd = Color.green(params.getEndColor())
                blueGradientStart = Color.blue(params.getStartColor())
                blueGradientEnd = Color.blue(params.getEndColor())
            }
        }
    }

    override fun loadFaceTracker(imageWidth: Int, imageHeight: Int): Boolean {
        if (params.getEndColor() != params.getStartColor()) {
            gradientAlpha = Color.alpha(params.getStartColor())
            redGradientStart = Color.red(params.getStartColor())
            redGradientEnd = Color.red(params.getEndColor())
            greenGradientStart = Color.green(params.getStartColor())
            greenGradientEnd = Color.green(params.getEndColor())
            blueGradientStart = Color.blue(params.getStartColor())
            blueGradientEnd = Color.blue(params.getEndColor())
        }

        return super.loadFaceTracker(imageWidth, imageHeight)
    }

    override fun processFromTexture(filter: GPUImageFilter?, cameraFboId: Int, cameraTextureId: Int, rgbaBuffer: ByteBuffer?): Int {
        maskTextureBuffer?.position(0)
        srcTextureBuffer.position(0)

        synchronized(this) {
            when (getCurrentOrientation()) {
                0 -> {
                    updateTextureBuffer(maskTextureBuffer!!, Rotation.NORMAL)
                    updateTextureBuffer(srcTextureBuffer, Rotation.NORMAL)
                }
                1 -> if (LangCameraEngine.isFrontCamera()) {
                    updateTextureBuffer(maskTextureBuffer!!, Rotation.ROTATION_90)
                    updateTextureBuffer(srcTextureBuffer, Rotation.ROTATION_270)
                } else {
                    updateTextureBuffer(maskTextureBuffer!!, Rotation.ROTATION_270)
                    updateTextureBuffer(srcTextureBuffer, Rotation.ROTATION_90)
                }
                2 -> {
                    updateTextureBuffer(maskTextureBuffer!!, Rotation.ROTATION_180)
                    updateTextureBuffer(srcTextureBuffer, Rotation.ROTATION_180)
                }
                3 -> if (LangCameraEngine.isFrontCamera()) {
                    updateTextureBuffer(maskTextureBuffer!!, Rotation.ROTATION_270)
                    updateTextureBuffer(srcTextureBuffer, Rotation.ROTATION_90)
                } else {
                    updateTextureBuffer(maskTextureBuffer!!, Rotation.ROTATION_90)
                    updateTextureBuffer(srcTextureBuffer, Rotation.ROTATION_270)
                }
                else -> maskTextureBuffer?.put(TextureRotationUtil.getRotation(Rotation.NORMAL, false, true))?.position(0)
            }

            getPixelData(filter, cameraTextureId, mGLCubeBuffer, srcTextureBuffer)
            if (graphicBuffer!!.tryLockGB()) {
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

            applyOverlayToTexture(filter!!, cameraTextureId, mGLCubeBuffer, srcTextureBuffer)

            return processedTexture!!.textureID()
        }
    }

    /**
     * Convert a color image to a gray-scale image using average gray shader.
     * Gray(i,j) = 0.299*R(i,j) + 0.578*G(i,j) + 0.114*B(i,j)
     */
    override fun getPixelData(filter: GPUImageFilter?, cameraTextureId: Int, cubeBuffer: FloatBuffer, textureBuffer: FloatBuffer) {
        val previousViewport = IntArray(4)
        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, previousViewport, 0)
        GLES20.glViewport(0, 0, tfLite.inputWidth, tfLite.inputHeight)

        if (graphicBuffer != null) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, graphicBufferID!![0])
            GlUtil.checkGlError("glBindFramebuffer")

            grayScaleFilter!!.onDrawFrame(cameraTextureId, cubeBuffer, textureBuffer)
        }

        GLES20.glViewport(previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3])
    }

    override fun handleSegmentation(resultPixels: ByteArray, maskWidth: Int, maskHeight: Int) {
        super.handleSegmentation(resultPixels, maskWidth, maskHeight)

        val threshold = tfLite.threshold

        if (params.getStartColor() == params.getEndColor()) {
            setColors(
                    maskPixels!!,
                    tfLite.maskWidth,
                    tfLite.maskHeight,
                    resultPixels,
                    maskWidth,
                    maskHeight,
                    threshold,
                    params.getStartColor()
            )
        } else {
            setGradientColors(
                    maskPixels!!,
                    tfLite.maskWidth,
                    tfLite.maskHeight,
                    resultPixels,
                    threshold
            )
        }

        maskBmp!!.setPixels(
                maskPixels,
                0,
                tfLite.maskWidth,
                0,
                0,
                tfLite.maskWidth,
                tfLite.maskHeight
        )
        maskTexId = OpenGlUtils.loadTexture(maskBmp, maskTexId, false)
    }

    override fun applyOverlayToTexture(filter: GPUImageFilter, srcTexId: Int, cubeBuffer: FloatBuffer, textureBuffer: FloatBuffer) {
        GLES20.glViewport(0, 0, previewWidth, previewHeight)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, processedTexture!!.id())

        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        softlightBlendFilter!!.setMaskTextureId(maskTexId, maskTextureBuffer!!)
        softlightBlendFilter!!.onDrawFrame(srcTexId, mGLCubeBuffer, mGLTextureBuffer)

        GLES20.glDisable(GLES20.GL_BLEND)

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_NONE)
    }

    private fun setColors(
            maskPixels: IntArray,
            maskBmpWidth: Int,
            maskBmpHeight: Int,
            resultPixels: ByteArray,
            maskWidth: Int,
            maskHeight: Int,
            threshold: Int,
            color: Int
    ) {
        val startTime = SystemClock.uptimeMillis()
        val isMatting = LangObjectSegmentationConfig.MATTING == params.getModelID()
        for (i in 0 until maskWidth) {
            for (j in 0 until maskHeight) {
                val classNo = resultPixels[j * maskWidth + i].toInt() and 0xFF // very tricky part
                if (!isMatting) {
                    if (classNo >= threshold) {
                        maskPixels[j * maskBmpWidth + i] = color
                    } else {
                        maskPixels[j * maskBmpHeight + i] = Color.TRANSPARENT
                    }
                } else {
                    if (classNo < threshold) {
                        maskPixels[j * maskBmpWidth + i] = color
                    } else {
                        maskPixels[j * maskBmpHeight + i] = Color.TRANSPARENT
                    }
                }
            }
        }

        val endTime = SystemClock.uptimeMillis()
        val inferenceTime = endTime - startTime

        if (VERBOSE) DebugLog.d(TAG, "setColors time=$inferenceTime")
    }

    private fun setGradientColors(
            maskPixels: IntArray,
            maskBmpWidth: Int,
            maskBmpHeight: Int,
            resultPixels: ByteArray,
            threshold: Int
    ) {
        val startTime = SystemClock.uptimeMillis()

        scanGradient(maskBmpWidth, maskBmpHeight, resultPixels, threshold)

        var classNo: Int
        var i = 0
        var j: Int

        // Paint current mask with mapping of gradient color and mask height
        val redGradient = (redGradientEnd - redGradientStart).toFloat() / fgMaskHeight
        val greenGradient = (greenGradientEnd - greenGradientStart).toFloat() / fgMaskHeight
        val blueGradient = (blueGradientEnd - blueGradientStart).toFloat() / fgMaskHeight
        val gradientSpeed = -fgMaskHeight / maskBmpHeight + 2f
        while (i < maskBmpWidth) {
            j = 0
            while (j < maskBmpHeight) {
                classNo = resultPixels[j * maskBmpWidth + i].toInt() and 0xFF
                if (classNo >= threshold) {
                    val red = calculateGradients(
                            redGradientStart,
                            redGradientEnd,
                            j,
                            fgMaskCeiling,
                            redGradient,
                            gradientSpeed
                    )
                    val green = calculateGradients(
                            greenGradientStart,
                            greenGradientEnd,
                            j,
                            fgMaskCeiling,
                            greenGradient,
                            gradientSpeed
                    )
                    val blue = calculateGradients(
                            blueGradientStart,
                            blueGradientEnd,
                            j,
                            fgMaskCeiling,
                            blueGradient,
                            gradientSpeed
                    )
                    maskPixels[j * maskBmpHeight + i] = Color.argb(gradientAlpha, red, green, blue)
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
            start: Int,
            end: Int,
            index: Int,
            ceiling: Int,
            gradient: Float,
            gradient_speed: Float
    ): Int {
        var gradientColor = (start + (index - ceiling).toFloat() * gradient * gradient_speed).toInt()
        gradientColor = if (gradient < 0) {
            if (gradientColor < end) end else gradientColor
        } else {
            if (gradientColor > end) end else gradientColor
        }
        return gradientColor
    }
}