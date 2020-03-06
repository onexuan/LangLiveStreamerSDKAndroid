//http://www.sunsetlakesoftware.com/2013/10/21/optimizing-gaussian-blurs-mobile-gpu
attribute vec4 position;
attribute vec4 inputTextureCoordinate;

uniform float texelWidthOffset;
uniform float texelHeightOffset;

varying vec2 blurCoordinates[13];

void main()
{
    gl_Position = position;

    vec2 singleStepOffset = vec2(texelWidthOffset, texelHeightOffset);
    blurCoordinates[0] = inputTextureCoordinate.xy;
    blurCoordinates[1] = inputTextureCoordinate.xy + singleStepOffset * 1.485004;
    blurCoordinates[2] = inputTextureCoordinate.xy - singleStepOffset * 1.485004;
    blurCoordinates[3] = inputTextureCoordinate.xy + singleStepOffset * 3.465057;
    blurCoordinates[4] = inputTextureCoordinate.xy - singleStepOffset * 3.465057;
    blurCoordinates[5] = inputTextureCoordinate.xy + singleStepOffset * 5.445220;
    blurCoordinates[6] = inputTextureCoordinate.xy - singleStepOffset * 5.445220;
    blurCoordinates[7] = inputTextureCoordinate.xy + singleStepOffset * 7.425558;
    blurCoordinates[8] = inputTextureCoordinate.xy - singleStepOffset * 7.425558;
    blurCoordinates[9] = inputTextureCoordinate.xy + singleStepOffset * 9.406127;
    blurCoordinates[10] = inputTextureCoordinate.xy - singleStepOffset * 9.406127;
    blurCoordinates[11] = inputTextureCoordinate.xy + singleStepOffset * 11.386989;
    blurCoordinates[12] = inputTextureCoordinate.xy - singleStepOffset * 11.386989;
}