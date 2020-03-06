package net.lang.gpuimage.filter.advanced.beauty;

import android.opengl.GLES20;

public class MagicDissolveBlendFilter extends MagicTwoInputFilter {
    public static final String DISSOLVE_BLEND_FRAGMENT_SHADER = "" +
            " varying highp vec2 textureCoordinate;\n" +
            " varying highp vec2 textureCoordinate2;\n" +
            "\n" +
            " uniform sampler2D inputImageTexture;\n" +
            " uniform sampler2D inputImageTexture2;\n" +
            " uniform lowp float mixturePercent;\n" +
            " \n" +
            " void main()\n" +
            " {\n" +
            "    lowp vec4 textureColor = texture2D(inputImageTexture, textureCoordinate);\n" +
            "    lowp vec4 textureColor2 = texture2D(inputImageTexture2, textureCoordinate);\n" +
            "    \n" +
            "    gl_FragColor = mix(textureColor, textureColor2, mixturePercent);\n" +
            " }";

    private int mixLocation;
    private float mix;

    public MagicDissolveBlendFilter(float mix) {
        super(DISSOLVE_BLEND_FRAGMENT_SHADER);
        this.mix = mix;
    }

    @Override
    public void onInit() {
        super.onInit();
        mixLocation = GLES20.glGetUniformLocation(getProgram(), "mixturePercent");
    }

    @Override
    public void onInitialized() {
        super.onInitialized();
        setMix(mix);
    }

    /**
     * @param mix ranges from 0.0 (only image 1) to 1.0 (only image 2), with 0.5 (half of either) as the normal level
     */
    public void setMix(final float mix) {
        this.mix = mix;
        setFloat(mixLocation, this.mix);
    }

}
