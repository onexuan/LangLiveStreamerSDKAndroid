package net.lang.gpuimage.filter.advanced.beauty;

public class MagicHighPassFilter extends MagicTwoInputFilter {
    private static final String HIGH_PASS_FILTER_FRAGMENT_SHADER = "" +
            "precision lowp float;\n" +
            "varying highp vec2 textureCoordinate;\n" +
            "varying highp vec2 textureCoordinate2;\n" +
            "\n" +
            "uniform sampler2D inputImageTexture;\n" +
            "uniform sampler2D inputImageTexture2;\n" +
            "\n" +
            "void main()\n" +
            "{\n" +
            "    vec4 image = texture2D(inputImageTexture, textureCoordinate);\n" +
            "    vec4 blurredImage = texture2D(inputImageTexture2, textureCoordinate);\n" +
            "    gl_FragColor = vec4((image.rgb - blurredImage.rgb + vec3(0.5, 0.5, 0.5)), image.a);\n" +
            "}";

    public MagicHighPassFilter() {
        super(HIGH_PASS_FILTER_FRAGMENT_SHADER);
    }
}
