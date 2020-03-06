
#extension GL_OES_EGL_image_external : require
 precision highp float;
 //precision highp vec3;
 varying highp vec2 textureCoordinate;

 uniform samplerExternalOES inputImageTexture;
 uniform highp vec4 mixColor;


void main()
{
  vec4 color = texture2D(inputImageTexture, textureCoordinate);
  vec3 rgb = color.rgb;
  color.rgb = color.rgb * pow(2.0, mixColor.x);
  gl_FragColor = color;
}