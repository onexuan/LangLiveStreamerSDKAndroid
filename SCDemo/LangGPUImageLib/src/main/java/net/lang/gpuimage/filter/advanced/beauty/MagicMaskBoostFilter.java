package net.lang.gpuimage.filter.advanced.beauty;

import net.lang.gpuimage.filter.base.gpuimage.GPUImageFilter;

public class MagicMaskBoostFilter extends GPUImageFilter {
    private static final String MASK_BOOST_FRAGMENT_SHADER = "" +
            "precision lowp float;\n" +
            "varying highp vec2 textureCoordinate;\n" +
            "\n" +
            "uniform sampler2D inputImageTexture;\n" +
            "\n" +
            "void main()\n" +
            "{\n" +
            "    vec4 color = texture2D(inputImageTexture, textureCoordinate);\n" +
            "    float hardLightColor = color.b;\n" +
            "    for (int i = 0; i < 3; i++)\n" +
            "    {\n" +
            "        if (hardLightColor < 0.5) {\n" +
            "            hardLightColor = hardLightColor * hardLightColor * 2.0;\n" +
            "        } else {\n" +
            "            hardLightColor = 1.0 - (1.0 - hardLightColor) * (1.0 - hardLightColor) * 2.0;\n" +
            "        }\n" +
            "    }\n" +
            "\n" +
            "    float k = 255.0 / (164.0 - 75.0);\n" +
            "    hardLightColor = (hardLightColor - 75.0 / 255.0) * k;\n" +
            "\n" +
            "    gl_FragColor = vec4(vec3(hardLightColor), color.a);\n" +
            "}";

    public MagicMaskBoostFilter() {
        super(GPUImageFilter.NO_FILTER_VERTEX_SHADER, MASK_BOOST_FRAGMENT_SHADER);
    }

}
