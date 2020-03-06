package net.lang.gpuimage.filter.advanced.beauty;

import android.opengl.GLES20;

import net.lang.gpuimage.filter.base.gpuimage.GPUImageFilter;
import net.lang.gpuimage.utils.OpenGlUtils;
import net.lang.gpuimage.utils.Rotation;
import net.lang.gpuimage.utils.TextureRotationUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class MagicTwoInputFilter extends GPUImageFilter {
    private static final String VERTEX_SHADER = "\n" +
            "attribute vec4 position;\n" +
            "attribute vec4 inputTextureCoordinate;\n" +
            "attribute vec4 inputTextureCoordinate2;\n" +
            " \n" +
            "varying vec2 textureCoordinate;\n" +
            "varying vec2 textureCoordinate2;\n" +
            " \n" +
            "void main()\n" +
            "{\n" +
            "    gl_Position = position;\n" +
            "    textureCoordinate = inputTextureCoordinate.xy;\n" +
            "    textureCoordinate2 = inputTextureCoordinate2.xy;\n" +
            "}";

    private int filterSecondTextureCoordinateAttribute;
    private int filterInputTextureUniform2;
    private int filterSourceTexture2 = OpenGlUtils.NO_TEXTURE;
    private ByteBuffer texture2CoordinatesBuffer;

    public MagicTwoInputFilter(String fragmentShader) {
        this(VERTEX_SHADER, fragmentShader);
    }

    public MagicTwoInputFilter(String vertexShader, String fragmentShader) {
        super(vertexShader, fragmentShader);
        setRotation(Rotation.NORMAL, false, false);
    }


    @Override
    public void onInit() {
        super.onInit();

        filterSecondTextureCoordinateAttribute = GLES20.glGetAttribLocation(getProgram(), "inputTextureCoordinate2");
        filterInputTextureUniform2 = GLES20.glGetUniformLocation(getProgram(), "inputImageTexture2"); // This does assume a name of "inputImageTexture2" for second input texture in the fragment shader
        //GLES20.glEnableVertexAttribArray(filterSecondTextureCoordinateAttribute);
    }

    @Override
    protected void onDrawArraysPre() {
        GLES20.glEnableVertexAttribArray(filterSecondTextureCoordinateAttribute);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, filterSourceTexture2);
        GLES20.glUniform1i(filterInputTextureUniform2, 2);

        texture2CoordinatesBuffer.position(0);
        GLES20.glVertexAttribPointer(filterSecondTextureCoordinateAttribute, 2, GLES20.GL_FLOAT, false, 0, texture2CoordinatesBuffer);
    }

    public int onDrawFrame(final int textureId, final int textureId2, final FloatBuffer cubeBuffer,
                           final FloatBuffer textureBuffer) {

        filterSourceTexture2 = textureId2;
        return onDrawFrame(textureId, cubeBuffer, textureBuffer);
    }

    public int onDrawFrame(final int textureId, final int textureId2) {
        filterSourceTexture2 = textureId2;
        return onDrawFrame(textureId);
    }

    public void setRotation(final Rotation rotation, final boolean flipHorizontal, final boolean flipVertical) {
        float[] buffer = TextureRotationUtil.getRotation(rotation, flipHorizontal, flipVertical);

        ByteBuffer bBuffer = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder());
        FloatBuffer fBuffer = bBuffer.asFloatBuffer();
        fBuffer.put(buffer);
        fBuffer.flip();

        texture2CoordinatesBuffer = bBuffer;
    }
}
