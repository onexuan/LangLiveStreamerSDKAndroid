package net.lang.streamer.faceu.impl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.util.Log

import com.langlive.LangAIKit.ITFLiteInterpreter
import net.lang.streamer.LangTexture
import net.lang.streamer.faceu.IObjectSegmentationTracker
import net.lang.streamer.config.LangObjectSegmentationConfig
import net.lang.streamer.faceu.IAnimationListener

import net.lang.gpuimage.filter.advanced.MagicGrayScaleFilter
import net.lang.gpuimage.filter.advanced.MagicSoftlightBlendFilter
import net.lang.gpuimage.filter.base.gpuimage.GPUImageFilter
import net.lang.gpuimage.utils.*;
import net.lang.streamer.utils.*

import com.yunfan.graphicbuffer.GraphicBufferWrapper

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

abstract class LangObjectSegmentationTracker(private var ctx: Context,
                                             params: LangObjectSegmentationConfig) : IObjectSegmentationTracker {

    private val VERBOSE = false
    private val TAG = this.javaClass.simpleName

    protected var params: LangObjectSegmentationConfig = params

    protected var previewWidth: Int = 0
    protected var previewHeight: Int = 0

    private var handler: Handler? = null
    private var handlerThread: HandlerThread? = null

    protected lateinit var tfLite: ITFLiteInterpreter

    protected var pixelClasses: ByteArray? = null

    protected var grayScaleFilter: MagicGrayScaleFilter? = null
    protected var softlightBlendFilter: MagicSoftlightBlendFilter? = null

    protected var graphicBuffer: GraphicBufferWrapper? = null
    protected var graphicBufferID: IntArray? = null
    private var graphicBufferTexID = OpenGlUtils.NO_TEXTURE

    protected var maskTexId = OpenGlUtils.NO_TEXTURE

    protected var maskBmp: Bitmap? = null
    protected var maskPixels: IntArray? = null

    protected var fgMaskCeiling: Int = 0
    protected var bgMaskCeiling: Int = 0
    protected var fgMaskHeight = 0f
    protected var bgMaskHeight = 0f

    protected var mGLCubeBuffer: FloatBuffer
    protected var mGLTextureBuffer: FloatBuffer

    protected val srcTextureBuffer: FloatBuffer

    protected  var prevFps = 0L
    protected var fps = 0f

    private var status = Status.kUnInit
    internal enum class Status constructor(var mName: String) {
        kUnInit("UnInit"),
        kReady("Ready"),
        kRelease("Release")
    }

    protected var processedTexture: LangTexture? = null

    protected var fgMaskCeilingArray: IntArray? = null

    protected var animationListener: IAnimationListener? = null

    init {
        initBackgroundThread()

        srcTextureBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
        srcTextureBuffer.put(TextureRotationUtil.getRotation(Rotation.NORMAL, false, true))
                .position(0)

        mGLCubeBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.CUBE.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
        mGLCubeBuffer.put(TextureRotationUtil.CUBE).position(0)

        mGLTextureBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
        mGLTextureBuffer.put(TextureRotationUtil.getRotation(Rotation.NORMAL, false, true))
                .position(0)
    }

    override fun loadFaceTracker(imageWidth: Int, imageHeight: Int): Boolean {
        if (status() != Status.kUnInit && status() != Status.kRelease) {
            DebugLog.e(TAG, "SenseMe: loadFaceTracker failed due to invalid status:" + status().name)
            return false
        }

        previewWidth = imageWidth
        previewHeight = imageHeight

        processedTexture = LangTexture()
        processedTexture!!.initTexture_l(previewWidth, previewHeight)

        preAllocateBuffers()

        grayScaleFilter?.init()
        grayScaleFilter?.onInputSizeChanged(imageWidth, imageHeight)

        softlightBlendFilter?.init()
        softlightBlendFilter?.onInputSizeChanged(imageWidth, imageHeight)

        updateStatus(Status.kReady)

        return true
    }

    override fun processFromTexture(filter: GPUImageFilter?, cameraFboId: Int, cameraTextureId: Int, rgbaBuffer: ByteBuffer?): Int {
        return cameraTextureId
    }

    override fun unloadFaceTracker() {
        synchronized(this) {
            releaseGraphicBuffer()
            releaseMaskTextures()

            grayScaleFilter?.destroy()
            softlightBlendFilter?.destroy()

            releaseBackgroundThread()

            tfLite.close()

            updateStatus(Status.kRelease)
        }
    }

    override fun onDetectFrame(data: ByteArray?) {
    }

    override fun switchParams(params: LangObjectSegmentationConfig) {
    }

    override fun setAnimationData(inputStream: InputStream, giftStream: InputStream?) {
    }

    override fun setAnimationData(inputPath: String, giftPath: String?) {
    }

    override fun getPixelData(filter: GPUImageFilter?, cameraTextureId: Int, cubeBuffer: FloatBuffer, textureBuffer: FloatBuffer) {
    }

    override fun setListener(listener: IAnimationListener) {
        animationListener = listener
    }

    open fun handleSegmentation(resultPixels: ByteArray, maskWidth: Int, maskHeight: Int) {
        if (maskBmp == null)
            return

        if (maskPixels == null)
            maskPixels = IntArray(tfLite.maskWidth * tfLite.maskHeight)
    }

    open fun applyOverlayToTexture(filter: GPUImageFilter, srcTexId: Int, cubeBuffer: FloatBuffer, textureBuffer: FloatBuffer) {}

    protected fun onDetectFrame_l(bufAddr: Long, bufStride: Int) {
        pixelClasses = tfLite.run(bufAddr, bufStride)
    }

    private fun initBackgroundThread() {
        handlerThread = HandlerThread("tflite-front")
        handlerThread!!.start()
        handler = Handler(handlerThread!!.looper)
    }

    @Synchronized
    protected fun runInBackground(r: Runnable) {
        if (handler != null) {
            handler!!.post(r)
        }
    }

    private fun releaseBackgroundThread() {
        handlerThread?.quit()
        try {
            handlerThread?.join()
            handlerThread = null
            handler = null
        } catch (e: InterruptedException) {
            DebugLog.e(TAG, e.toString())
        }
    }

    private fun preAllocateBuffers() {
        initMaskTextures(tfLite.maskWidth, tfLite.maskHeight)
        initGraphicBuffer(tfLite.inputWidth, tfLite.inputHeight)
    }

    private fun initMaskTextures(width: Int, height: Int) {
        if (maskBmp == null) {
            maskBmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }

        maskTexId = OpenGlUtils.loadTexture(maskBmp, maskTexId, false)
    }

    private fun releaseMaskTextures() {
        if (maskBmp != null && !maskBmp!!.isRecycled) {
            maskBmp!!.recycle()
            maskBmp = null
        }

        if (maskTexId != OpenGlUtils.NO_TEXTURE) {
            val textures = IntArray(1)
            textures[0] = maskTexId
            GLES20.glDeleteTextures(1, textures, 0)
            maskTexId = OpenGlUtils.NO_TEXTURE
        }
    }

    private fun initGraphicBuffer(width: Int, height: Int) {
        graphicBuffer = GraphicBufferWrapper.createInstance(ctx, width, height, PixelFormat.RGBA_8888)
        if (graphicBuffer != null) {
            graphicBufferID = IntArray(1)
            GLES20.glGenFramebuffers(1, graphicBufferID, 0)
            graphicBufferTexID = graphicBuffer!!.createTexture(graphicBufferID!![0])
            GLES20.glFramebufferTexture2D(
                    GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                    GLES20.GL_TEXTURE_2D, graphicBufferTexID, 0
            )
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        }
    }

    private fun releaseGraphicBuffer() {
        if (graphicBuffer != null) {
            graphicBuffer!!.destroy()
            graphicBuffer = null
        }
    }

    private fun status(): Status {
        return status
    }

    private fun updateStatus(status: Status) {
        DebugLog.dfmt(TAG, "ObjectSegmentation tracker change status " + status.mName + " -> " + status.mName)
        this.status = status
    }

    protected fun updateTextureBuffer(textureBuffer: FloatBuffer,
                                      rotation: Rotation,
                                      flipHorizontal: Boolean = false,
                                      flipVertical: Boolean = true) {
        textureBuffer.put(TextureRotationUtil.getRotation(rotation, flipHorizontal, flipVertical)).position(0)
    }

    protected fun getCurrentOrientation(): Int {
        val dir = Accelerometer.getDirection()
        var orientation = dir - 1
        if (orientation < 0) {
            orientation = dir xor 3
        }
        return orientation
    }

    protected fun scanGradient(maskBmpWidth: Int,
                             maskBmpHeight: Int,
                             resultPixels: ByteArray,
                             threshold: Int,
                             foreground: Boolean = true) {
        var classNo: Int
        var i = 0
        var j: Int

        // Iterate through mask to check mask height
        if (foreground) {
            fgMaskCeiling = maskBmpHeight
            if (null != fgMaskCeilingArray) {
                for (index in fgMaskCeilingArray!!.indices) {
                    fgMaskCeilingArray!![index] = maskBmpHeight
                }
            }
        } else
            bgMaskCeiling = maskBmpHeight
        var maskFloor = 0
        while (i < maskBmpWidth) {
            j = 0
            while (j < maskBmpHeight) {
                classNo = resultPixels[j * maskBmpWidth + i].toInt() and 0xFF // very tricky part
                if (foreground) {
                    if (classNo >= threshold) {
                        if (j < fgMaskCeiling) {
                            fgMaskCeiling = j
                        }
                        if (null != fgMaskCeilingArray) {
                            if (j < fgMaskCeilingArray!![i]) {
                                fgMaskCeilingArray!![i] = j
                            }
                        }
                        if (j > maskFloor) {
                            maskFloor = j
                            j = if (j < maskBmpHeight - 1) j + 1 else j
                        }
                    }
                } else {
                    if (classNo < threshold) {
                        if (j < bgMaskCeiling)
                            bgMaskCeiling = j
                        if (j > maskFloor) {
                            maskFloor = j
                            j = if (j < maskBmpHeight - 1) j + 1 else j
                        }
                    }
                }
                j++
            }
            i++
        }
        // Calculate moving mean of mask height
        if (foreground)
            fgMaskHeight = (if (maskFloor - fgMaskCeiling <= 0) 1 else maskFloor - fgMaskCeiling).toFloat()
        else
            bgMaskHeight = (if (maskFloor - bgMaskCeiling <= 0) 1 else maskFloor - bgMaskCeiling).toFloat()
    }
}