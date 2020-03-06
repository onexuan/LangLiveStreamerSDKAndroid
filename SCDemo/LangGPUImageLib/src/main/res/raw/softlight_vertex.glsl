attribute vec4 position;
attribute vec4 inputTextureCoordinate;
attribute vec4 maskTextureCoordinate;

varying vec2 textureCoordinate;
varying vec2 maskCoordinate;

const int GAUSSIAN_SAMPLES = 5;
uniform float texelWidthOffset;
uniform float texelHeightOffset;
varying vec2 blurCoordinates[GAUSSIAN_SAMPLES];

void main() {
    gl_Position = position;
    textureCoordinate = inputTextureCoordinate.xy;
    maskCoordinate = maskTextureCoordinate.xy;

    // Calculate the positions for the blur
    int multiplier = 0;
    vec2 blurStep;
    vec2 singleStepOffset = vec2(texelHeightOffset, texelWidthOffset);

    for (int i = 0; i < GAUSSIAN_SAMPLES; i++) {
        multiplier = (i - ((GAUSSIAN_SAMPLES - 1) / 2));
        // Blur in x (horizontal)
        blurStep = float(multiplier) * singleStepOffset;
        blurCoordinates[i] = maskTextureCoordinate.xy + blurStep;
    }
}
