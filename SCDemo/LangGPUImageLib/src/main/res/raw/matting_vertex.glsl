attribute vec4 position;
attribute vec4 inputTextureCoordinate;
attribute vec4 backgroundTextureCoordinate;
attribute vec4 webpTextureCoordinate;

varying vec2 textureCoordinate;
varying vec2 backgroundCoordinate;
varying vec2 webpCoordinate;

void main() {
    gl_Position = position;
    textureCoordinate = inputTextureCoordinate.xy;
    backgroundCoordinate = backgroundTextureCoordinate.xy;
    webpCoordinate = webpTextureCoordinate.xy;
}
