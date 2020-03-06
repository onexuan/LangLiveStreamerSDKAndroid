 precision highp float;
 varying mediump vec2 textureCoordinate;
 
 uniform sampler2D inputImageTexture;
 uniform sampler2D inputImageTexture2;
 
 uniform float inputImageTextureHeight;
 uniform float inputImageTextureWidth;

 uniform float strength;

 void main()
{
    vec4 camera = texture2D(inputImageTexture, textureCoordinate);
    color.a = 1.0;
    vec4 water_mark = texture2D(inputImageTexture2, textureCoordinate);
    water_mark.a = 0.5;

    gl_FragColor = color + rd;
}