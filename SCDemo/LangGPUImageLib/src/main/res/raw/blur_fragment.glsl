precision lowp float;

varying lowp vec2 textureCoordinate;

uniform sampler2D inputImageTexture;

const lowp int GAUSSIAN_SAMPLES = 9;
varying lowp vec2 blurCoordinates[GAUSSIAN_SAMPLES];

void main(void) {
    lowp vec4 image = vec4(0.0);

    image += texture2D(inputImageTexture, blurCoordinates[0]) * 0.04;
    image += texture2D(inputImageTexture, blurCoordinates[1]) * 0.08;
    image += texture2D(inputImageTexture, blurCoordinates[2]) * 0.12;
    image += texture2D(inputImageTexture, blurCoordinates[3]) * 0.16;
    image += texture2D(inputImageTexture, blurCoordinates[4]) * 0.2;
    image += texture2D(inputImageTexture, blurCoordinates[5]) * 0.16;
    image += texture2D(inputImageTexture, blurCoordinates[6]) * 0.12;
    image += texture2D(inputImageTexture, blurCoordinates[7]) * 0.08;
    image += texture2D(inputImageTexture, blurCoordinates[8]) * 0.04;

    gl_FragColor = image;
}