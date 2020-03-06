package net.lang.gpuimage.filter.advanced.beauty;

import android.opengl.GLES20;

import net.lang.gpuimage.utils.OpenGlUtils;
import net.lang.gpuimage.utils.Rotation;
import net.lang.gpuimage.utils.TextureRotationUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class MagicThreeInputFilter extends MagicTwoInputFilter {
    public static final String VERTEX_SHADER =  "" +
            "attribute vec4 position;\n" +
            "attribute vec4 inputTextureCoordinate;\n" +
            "attribute vec4 inputTextureCoordinate2;\n" +
            "attribute vec4 inputTextureCoordinate3;\n" +
            " \n" +
            "varying vec2 textureCoordinate;\n" +
            "varying vec2 textureCoordinate2;\n" +
            "varying vec2 textureCoordinate3;\n" +
            " \n" +
            "void main()\n" +
            "{\n" +
            "    gl_Position = position;\n" +
            "    textureCoordinate = inputTextureCoordinate.xy;\n" +
            "    textureCoordinate2 = inputTextureCoordinate2.xy;\n" +
            "    textureCoordinate3 = inputTextureCoordinate3.xy;\n" +
            "}";

    private int filterThirdTextureCoordinateAttribute;
    private int filterInputTextureUniform3;
    private int filterSourceTexture3 = OpenGlUtils.NO_TEXTURE;
    private ByteBuffer texture3CoordinatesBuffer;

    public MagicThreeInputFilter(String fragmentShader) {
        this(VERTEX_SHADER, fragmentShader);
    }

    public MagicThreeInputFilter(String vertexShader, String fragmentShader) {
        super(vertexShader, fragmentShader);
        setRotation2(Rotation.NORMAL, false, false);
    }

    @Override
    public void onInit() {
        super.onInit();

        filterThirdTextureCoordinateAttribute = GLES20.glGetAttribLocation(getProgram(), "inputTextureCoordinate3");
        filterInputTextureUniform3 = GLES20.glGetUniformLocation(getProgram(), "inputImageTexture3"); // This does assume a name of "inputImageTexture2" for second input texture in the fragment shader
        //GLES20.glEnableVertexAttribArray(filterThirdTextureCoordinateAttribute);
    }

    @Override
    protected void onDrawArraysPre() {
        super.onDrawArraysPre();

        GLES20.glEnableVertexAttribArray(filterThirdTextureCoordinateAttribute);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE3);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, filterSourceTexture3);
        GLES20.glUniform1i(filterInputTextureUniform3, 3);

        texture3CoordinatesBuffer.position(0);
        GLES20.glVertexAttribPointer(filterThirdTextureCoordinateAttribute, 2, GLES20.GL_FLOAT, false, 0, texture3CoordinatesBuffer);
    }


    public int onDrawFrame(final int textureId, final int textureId2, final int textureId3, final FloatBuffer cubeBuffer,
                           final FloatBuffer textureBuffer) {
        filterSourceTexture3 = textureId3;
        return onDrawFrame(textureId, textureId2, cubeBuffer, textureBuffer);
    }

    public int onDrawFrame(final int textureId, final int textureId2, final int textureId3) {
        filterSourceTexture3 = textureId3;
        return onDrawFrame(textureId, textureId2);
    }

    public void setRotation2(final Rotation rotation, final boolean flipHorizontal, final boolean flipVertical) {
        float[] buffer = TextureRotationUtil.getRotation(rotation, flipHorizontal, flipVertical);

        ByteBuffer bBuffer = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder());
        FloatBuffer fBuffer = bBuffer.asFloatBuffer();
        fBuffer.put(buffer);
        fBuffer.flip();

        texture3CoordinatesBuffer = bBuffer;
    }
}
