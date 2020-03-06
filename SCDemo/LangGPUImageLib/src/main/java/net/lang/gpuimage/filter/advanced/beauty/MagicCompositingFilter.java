package net.lang.gpuimage.filter.advanced.beauty;

public class MagicCompositingFilter extends MagicThreeInputFilter {
    private static final String COMPOSITING_FRAGMENT_SHADER = "" +
            "precision lowp float;\n" +
            "varying highp vec2 textureCoordinate;\n" +
            "varying highp vec2 textureCoordinate2;\n" +
            "varying highp vec2 textureCoordinate3;\n" +
            " \n" +
            "uniform sampler2D inputImageTexture;\n" +
            "uniform sampler2D inputImageTexture2;\n" +
            "uniform sampler2D inputImageTexture3;\n" +
            " \n" +
            "void main() {\n" +
            "    vec4 image = texture2D(inputImageTexture, textureCoordinate);\n" +
            "    vec4 toneCurvedImage = texture2D(inputImageTexture2, textureCoordinate);\n" +
            "    vec4 mask = texture2D(inputImageTexture3, textureCoordinate);\n" +
            "    gl_FragColor = vec4(mix(image.rgb, toneCurvedImage.rgb, 1.0 - mask.b), 1.0);" +
            "}";

    public MagicCompositingFilter() {
        super(COMPOSITING_FRAGMENT_SHADER);
    }
}
