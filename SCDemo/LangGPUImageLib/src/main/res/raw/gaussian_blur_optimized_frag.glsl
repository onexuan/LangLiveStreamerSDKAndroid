precision highp float;
uniform sampler2D inputImageTexture;

varying highp vec2 blurCoordinates[13];

void main()
{
    lowp vec4 sum = vec4(0.0);

    sum += texture2D(inputImageTexture, blurCoordinates[0]) * 0.080780;
    sum += texture2D(inputImageTexture, blurCoordinates[1]) * 0.153750;
    sum += texture2D(inputImageTexture, blurCoordinates[2]) * 0.153750;
    sum += texture2D(inputImageTexture, blurCoordinates[3]) * 0.126131;
    sum += texture2D(inputImageTexture, blurCoordinates[4]) * 0.126131;
    sum += texture2D(inputImageTexture, blurCoordinates[5]) * 0.088315;
    sum += texture2D(inputImageTexture, blurCoordinates[6]) * 0.088315;
    sum += texture2D(inputImageTexture, blurCoordinates[7]) * 0.052777;
    sum += texture2D(inputImageTexture, blurCoordinates[8]) * 0.052777;
    sum += texture2D(inputImageTexture, blurCoordinates[9]) * 0.026919;
    sum += texture2D(inputImageTexture, blurCoordinates[10]) * 0.026919;
    sum += texture2D(inputImageTexture, blurCoordinates[11]) * 0.011718;
    sum += texture2D(inputImageTexture, blurCoordinates[12]) * 0.011718;

    gl_FragColor = sum;
}