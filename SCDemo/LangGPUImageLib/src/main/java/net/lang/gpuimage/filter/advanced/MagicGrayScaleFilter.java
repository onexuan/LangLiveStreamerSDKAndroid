package net.lang.gpuimage.filter.advanced;

import net.lang.gpuimage.filter.base.gpuimage.GPUImageFilter;

public class MagicGrayScaleFilter extends GPUImageFilter {

    public static final String GRAYSCALE_FRAGMENT_SHADER = "" +
            "varying highp vec2 textureCoordinate;\n" +
            "uniform sampler2D inputImageTexture;\n" +
            "\n" +
            "void main(void) {\n" +
            "    highp vec4 color = texture2D(inputImageTexture, textureCoordinate);\n" +
            "    highp float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));\n" +
            "    gl_FragColor = vec4(vec3(gray), 1.0);\n" +
            "}";

    public MagicGrayScaleFilter() {
        super(NO_FILTER_VERTEX_SHADER, GRAYSCALE_FRAGMENT_SHADER);
    }
}
