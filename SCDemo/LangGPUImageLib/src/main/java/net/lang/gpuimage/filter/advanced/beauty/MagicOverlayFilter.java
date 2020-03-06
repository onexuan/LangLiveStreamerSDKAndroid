package net.lang.gpuimage.filter.advanced.beauty;

import net.lang.gpuimage.filter.base.gpuimage.GPUImageFilter;

public class MagicOverlayFilter extends GPUImageFilter {
    private static final String CHANNEL_OVERLAY_FRAGMENT_SHADER = "" +
            "precision lowp float;\n" +
            "varying highp vec2 textureCoordinate;\n" +
            "uniform sampler2D inputImageTexture;\n" +
            "\n" +
            "void main() {\n" +
            "    vec4 image = texture2D(inputImageTexture, textureCoordinate);\n" +
            "    vec4 base = vec4(image.g, image.g, image.g, 1.0);\n" +
            "    vec4 overlay = vec4(image.b, image.b, image.b, 1.0);\n" +
            "    float ba = 2.0 * overlay.b * base.b + overlay.b * (1.0 - base.a) + base.b * (1.0 - overlay.a);\n" +
            "    gl_FragColor = vec4(ba, ba, ba, image.a);\n" +
            "}";

    public MagicOverlayFilter() {
        super(GPUImageFilter.NO_FILTER_VERTEX_SHADER, CHANNEL_OVERLAY_FRAGMENT_SHADER);
    }
}
