package net.lang.gpuimage.filter.advanced.beauty;

import android.opengl.GLES20;

import net.lang.gpuimage.filter.base.gpuimage.GPUImageFilter;

public class MagicGaussianOptimizedFilter extends GPUImageFilter {
    private static final String VERTEX_SHADER = "" +
            "precision highp float;" +
            "attribute vec4 position;\n" +
            "attribute vec4 inputTextureCoordinate;\n" +
            "\n" +
            "uniform float texelWidthOffset;\n" +
            "uniform float texelHeightOffset;\n" +
            "\n" +
            "varying vec2 blurCoordinates[13];\n" +
            "\n" +
            "void main()\n" +
            "{\n" +
            "    gl_Position = position;\n" +
            " \n" +
            "    vec2 singleStepOffset = vec2(texelWidthOffset, texelHeightOffset);\n" +
            "    blurCoordinates[0] = inputTextureCoordinate.xy;\n" +
            "    blurCoordinates[1] = inputTextureCoordinate.xy + singleStepOffset * 1.485004;\n" +
            "    blurCoordinates[2] = inputTextureCoordinate.xy - singleStepOffset * 1.485004;\n" +
            "    blurCoordinates[3] = inputTextureCoordinate.xy + singleStepOffset * 3.465057;\n" +
            "    blurCoordinates[4] = inputTextureCoordinate.xy - singleStepOffset * 3.465057;\n" +
            "    blurCoordinates[5] = inputTextureCoordinate.xy + singleStepOffset * 5.445220;\n" +
            "    blurCoordinates[6] = inputTextureCoordinate.xy - singleStepOffset * 5.445220;\n" +
            "    blurCoordinates[7] = inputTextureCoordinate.xy + singleStepOffset * 7.425558;\n" +
            "    blurCoordinates[8] = inputTextureCoordinate.xy - singleStepOffset * 7.425558;\n" +
            "    blurCoordinates[9] = inputTextureCoordinate.xy + singleStepOffset * 9.406127;\n" +
            "    blurCoordinates[10] = inputTextureCoordinate.xy - singleStepOffset * 9.406127;\n" +
            "    blurCoordinates[11] = inputTextureCoordinate.xy + singleStepOffset * 11.386989;\n" +
            "    blurCoordinates[12] = inputTextureCoordinate.xy - singleStepOffset * 11.386989;\n" +
            "}\n";

    private static final String FRAGMENT_SHADER = "" +
            "precision highp float;" +
            "uniform sampler2D inputImageTexture;\n" +
            "\n" +
            "uniform highp float texelWidthOffset;\n" +
            "uniform highp float texelHeightOffset;\n" +
            "\n" +
            "varying highp vec2 blurCoordinates[13];\n" +
            "\n" +
            "void main()\n" +
            "{\n" +
            "    lowp vec4 sum = vec4(0.0);\n" +
            " \n" +
            "    sum += texture2D(inputImageTexture, blurCoordinates[0]) * 0.080780;\n" +
            "    sum += texture2D(inputImageTexture, blurCoordinates[1]) * 0.153750;\n" +
            "    sum += texture2D(inputImageTexture, blurCoordinates[2]) * 0.153750;\n" +
            "    sum += texture2D(inputImageTexture, blurCoordinates[3]) * 0.126131;\n" +
            "    sum += texture2D(inputImageTexture, blurCoordinates[4]) * 0.126131;\n" +
            "    sum += texture2D(inputImageTexture, blurCoordinates[5]) * 0.088315;\n" +
            "    sum += texture2D(inputImageTexture, blurCoordinates[6]) * 0.088315;\n" +
            "    sum += texture2D(inputImageTexture, blurCoordinates[7]) * 0.052777;\n" +
            "    sum += texture2D(inputImageTexture, blurCoordinates[8]) * 0.052777;\n" +
            "    sum += texture2D(inputImageTexture, blurCoordinates[9]) * 0.026919;\n" +
            "    sum += texture2D(inputImageTexture, blurCoordinates[10]) * 0.026919;\n" +
            "    sum += texture2D(inputImageTexture, blurCoordinates[11]) * 0.011718;\n" +
            "    sum += texture2D(inputImageTexture, blurCoordinates[12]) * 0.011718;\n" +
            " \n" +
            "	 gl_FragColor = sum;\n" +
            "}";

    private float blurSize;

    public MagicGaussianOptimizedFilter() {
        super(VERTEX_SHADER, FRAGMENT_SHADER);
        this.blurSize = 1.0f;
    }

    @Override
    public void onInitialized() {
        super.onInitialized();
        setBlurSize(this.blurSize);
    }

    @Override
    public void onInputSizeChanged(final int width, final int height) {
        super.onInputSizeChanged(width, height);
        initTexelOffsets();
    }

    /**
     * A multiplier for the blur size, ranging from 0.0 on up, with a default of 1.0
     *
     * @param blurSize from 0.0 on up, default 1.0
     */
    private void setBlurSize(float blurSize) {
        this.blurSize = blurSize;
        runOnDraw(new Runnable() {
            @Override
            public void run() {
                initTexelOffsets();
            }
        });
    }

    private void initTexelOffsets() {

        float horizontalRatio = getHorizontalTexelOffsetRatio();
        float verticalRatio = getVerticalTexelOffsetRatio();
        int texelWidthOffsetLocation = GLES20.glGetUniformLocation(getProgram(), "texelWidthOffset");
        int texelHeightOffsetLocation = GLES20.glGetUniformLocation(getProgram(), "texelHeightOffset");
        setFloat(texelWidthOffsetLocation, horizontalRatio / getInputWidth());
        setFloat(texelHeightOffsetLocation, verticalRatio / getInputHeight());
    }

    private float getVerticalTexelOffsetRatio() {
        return blurSize;
    }

    private float getHorizontalTexelOffsetRatio() {
        return blurSize;
    }
}
