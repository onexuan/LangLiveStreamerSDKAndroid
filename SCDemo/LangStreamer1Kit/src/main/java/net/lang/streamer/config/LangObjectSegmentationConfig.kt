package net.lang.streamer.config

import android.graphics.Color
import android.util.Log

class LangObjectSegmentationConfig {

    companion object {
        // Define different models
        val GLASSES = 0
        val HAIR = 1
        val MATTING = 2

        val NO_COLOR = -1
    }

    private val TAG = LangObjectSegmentationConfig::class.java.simpleName

    private val GLASSES_MODEL_TFLITE = "cfe83f9b-f277-4874-82b0-23b2d97d9358.quantized.tflite"
    private val HAIR_MODEL_TFLITE = "processed_hair.tflite"
    private val TF_OD_API_LABELS_FILE = "file:///android_asset/classes.txt"

    private val TF_GLASSES_WIDTH = 512
    private val TF_GLASSES_HEIGHT = 512
    private val TF_GLASSES_MASK_WIDTH = 256
    private val TF_GLASSES_MASK_HEIGHT = 256
    private val TF_HAIR_WIDTH = 256
    private val TF_HAIR_HEIGHT = 256
    private val TF_HAIR_MASK_WIDTH = 256
    private val TF_HAIR_MASK_HEIGHT = 256

    private val TF_NUM_CLASSES = 1
    private val TF_GLASSES_USE_GRAY = true
    private val TF_HAIR_USE_GRAY = true
    private val TF_GLASSES_USE_P_MASK = true
    private val TF_HAIR_USE_P_MASK = true
    private val THRESHOLD = 128

    private var modelID: Int
    private var modelFile: String? = null
    private var labelFile: String? = null
    private var inputWidth: Int = 0
    private var inputHeight: Int = 0
    private var maskWidth: Int = 0
    private var maskHeight: Int = 0
    private val maskColor = Color.RED
    private val numClasses = 1
    private val numChannels: Int = 0
    private var useGrayScale: Boolean = false
    private var usePrevMask: Boolean = false
    private var useBlurFilter: Boolean = true
    private var useGussianBlur: Boolean = false

    private var saturation: Float
    private var startColor: Int = 0
    private var endColor: Int = 0
    private val gradientFileName: String? = null

    constructor(modelID: Int, saturation: Float) {
        this.modelID = modelID
        this.saturation = saturation

        setup()
    }

    constructor(modelID: Int, saturation: Float, startColor: Int, endColor: Int) {
        this.startColor = startColor
        this.endColor = endColor
        this.modelID = modelID
        this.saturation = saturation

        setup()
    }

    fun getModelFile(): String? {
        return modelFile
    }

    fun getModelFileFd(): Int {
        if (getModelFile() == GLASSES_MODEL_TFLITE) {

        } else if (getModelFile() == HAIR_MODEL_TFLITE) {

        }
        return -1
    }

    fun getLabelFile(): String? {
        return labelFile
    }

    fun getModelID(): Int {
        return modelID
    }

    fun getInputWidth(): Int {
        return inputWidth
    }

    fun getInputHeight(): Int {
        return inputHeight
    }

    fun getMaskWidth(): Int {
        return maskWidth
    }

    fun getMaskHeight(): Int {
        return maskHeight
    }

    fun getNumClasses(): Int {
        return numClasses
    }

    fun isUsingGrayScale(): Boolean {
        return useGrayScale
    }

    fun isUsingPrevMask(): Boolean {
        return usePrevMask
    }

    fun isUsingBlurFilter(): Boolean {
        return useBlurFilter
    }

    fun isUsingGussianBlur(): Boolean {
        return useGussianBlur
    }

    fun getSaturation(): Float {
        return saturation
    }

    fun getStartColor(): Int {
        return startColor
    }

    fun getEndColor(): Int {
        return endColor
    }

    fun getGradientFileName(): String? {
        return gradientFileName
    }

    fun getTfGlassesWidth(): Int {
        return TF_GLASSES_WIDTH
    }

    fun getTfGlassesHeight(): Int {
        return TF_GLASSES_HEIGHT
    }

    fun getTfHairWidth(): Int {
        return TF_HAIR_WIDTH
    }

    fun getTfHairHeight(): Int {
        return TF_HAIR_HEIGHT
    }

    fun getThreshold(): Int {
        return THRESHOLD
    }

    private fun setup() {
        when (modelID) {
            GLASSES -> {
                modelFile = GLASSES_MODEL_TFLITE
                inputWidth = TF_GLASSES_WIDTH
                inputHeight = TF_GLASSES_HEIGHT
                maskWidth = TF_GLASSES_MASK_WIDTH
                maskHeight = TF_GLASSES_MASK_HEIGHT
                useGrayScale = TF_GLASSES_USE_GRAY
                usePrevMask = TF_GLASSES_USE_P_MASK
            }
            HAIR -> {
                modelFile = HAIR_MODEL_TFLITE
                inputWidth = TF_HAIR_WIDTH
                inputHeight = TF_HAIR_HEIGHT
                maskWidth = TF_HAIR_MASK_WIDTH
                maskHeight = TF_HAIR_MASK_HEIGHT
                useGrayScale = TF_HAIR_USE_GRAY
                usePrevMask = TF_HAIR_USE_P_MASK
            }
            else -> {
                modelFile = GLASSES_MODEL_TFLITE
                inputWidth = TF_GLASSES_WIDTH
                inputHeight = TF_GLASSES_HEIGHT
                maskWidth = TF_GLASSES_MASK_WIDTH
                maskHeight = TF_GLASSES_MASK_HEIGHT
                useGrayScale = false
                usePrevMask = false
                useBlurFilter = true
                useGussianBlur = false
            }
        }
        labelFile = TF_OD_API_LABELS_FILE
    }
}