precision highp float;

varying highp vec2 textureCoordinate;
varying highp vec2 maskCoordinate;

uniform sampler2D inputImageTexture;
uniform sampler2D maskTexture;
uniform float saturation;

const lowp int GAUSSIAN_SAMPLES = 5;
varying highp vec2 blurCoordinates[GAUSSIAN_SAMPLES];

float blendSoftLight(float base, float blend) {
    return ((sqrt(base)*blend+base*(1.0-blend))*saturation+base*(1.0-saturation));
}

highp vec3 blendSoftLight(vec3 base, vec3 blend) {
    return vec3(blendSoftLight(base.r, blend.r), blendSoftLight(base.g, blend.g), blendSoftLight(base.b, blend.b));
}

highp vec3 blendSoftLight(vec3 base, vec3 blend, float opacity) {
    return (blendSoftLight(base, blend) * opacity + base * (1.0 - opacity));
}

void main(void) {
    lowp vec3 blur = vec3(0.0);

    blur += texture2D(maskTexture, blurCoordinates[0]).rgb * 0.2;
    blur += texture2D(maskTexture, blurCoordinates[1]).rgb * 0.2;
    blur += texture2D(maskTexture, blurCoordinates[2]).rgb * 0.2;
    blur += texture2D(maskTexture, blurCoordinates[3]).rgb * 0.2;
    blur += texture2D(maskTexture, blurCoordinates[4]).rgb * 0.2;

    lowp vec4 mask = vec4(blur.r, blur.g, blur.b, texture2D(maskTexture, maskCoordinate).a);

    lowp vec4 image = texture2D(inputImageTexture, textureCoordinate);
    highp vec3 color = blendSoftLight(image.rgb, mask.rgb);
    gl_FragColor = vec4(color, 1.0);
}
