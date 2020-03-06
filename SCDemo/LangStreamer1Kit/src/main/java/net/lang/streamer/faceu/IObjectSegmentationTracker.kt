package net.lang.streamer.faceu

import net.lang.streamer.config.LangObjectSegmentationConfig
import net.lang.gpuimage.filter.base.gpuimage.GPUImageFilter
import net.lang.streamer.widget.AnimationCallback

import java.io.InputStream
import java.nio.FloatBuffer

interface IObjectSegmentationTracker : IFaceTracker {

    fun switchParams(params: LangObjectSegmentationConfig)

    fun setAnimationData(inputStream: InputStream, giftStream: InputStream?)

    fun setAnimationData(inputPath: String, giftPath: String?)

    fun getPixelData(filter: GPUImageFilter?, cameraTextureId: Int, cubeBuffer: FloatBuffer, textureBuffer: FloatBuffer)

    fun setListener(listener: IAnimationListener)
}