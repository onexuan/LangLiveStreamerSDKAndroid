attribute vec4 position;
attribute vec4 inputTextureCoordinate;

uniform mat4 textureTransform;
varying vec2 textureCoordinate;
uniform mat4 customTransform;
uniform highp vec4 mixColor;

void main()
{	
    textureCoordinate = ( (textureTransform * customTransform) * inputTextureCoordinate).xy;
	//textureCoordinate.x = textureCoordinate.x * mixColor.y;
	//textureCoordinate.x = textureCoordinate.x + (1-mixColor.y)/2;
	//textureCoordinate.y = textureCoordinate.y * mixColor.y;
	//textureCoordinate.y = textureCoordinate.y + (1-mixColor.y)/2;
    gl_Position = position;
}
