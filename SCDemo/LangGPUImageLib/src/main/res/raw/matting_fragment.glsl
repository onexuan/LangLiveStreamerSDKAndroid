precision lowp float;

varying lowp vec2 textureCoordinate;
varying lowp vec2 backgroundCoordinate;
varying lowp vec2 webpCoordinate;

uniform sampler2D inputImageTexture;
uniform sampler2D backgroundTexture;
uniform sampler2D webpTexture;

void main(void) {
    lowp vec4 image = texture2D(inputImageTexture, textureCoordinate);
    lowp vec4 background = texture2D(backgroundTexture, backgroundCoordinate);
    lowp vec4 webp = texture2D(webpTexture, webpCoordinate);

    lowp vec4 color = webp * background.a * webp.a;
    image = image - (image * background.a * webp.a);
    gl_FragColor = image + color;
}