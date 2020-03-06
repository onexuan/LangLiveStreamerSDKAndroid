package net.lang.streamer;

import android.opengl.GLES20;
import android.opengl.Matrix;

import net.lang.gpuimage.filter.base.gpuimage.GPUImageFilter;
import net.lang.gpuimage.utils.Rotation;
import net.lang.gpuimage.utils.TextureRotationUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Created by lichao on 17-6-12.
 */

public class NonPIPEMagicFilter extends GPUImageFilter {
    public static final String NO_FILTER_VERTEX_SHADER2 = "" +
            "attribute vec4 position;\n" +
            "attribute vec4 inputTextureCoordinate;\n" +
            "uniform mat4 textureTransform;\n" +
            " \n" +
            "varying vec2 textureCoordinate;\n" +
            " \n" +
            "void main()\n" +
            "{\n" +
            "    gl_Position = position;\n" +
            "    textureCoordinate = (textureTransform * inputTextureCoordinate).xy;\n" +
            "}";

    private  float[] mTextureTransformMatrix = {
            1.0f, 0.0f, 0.f,0f,
            0.0f, 1.0f, 0.f,0f,
            0.0f, 0.0f, 1.f,0f,
            0.0f, 0.0f, 0.f,1f,
    };

    private int mTextureTransformMatrixLocation = -1;
    private float mScaleX;
    private float mScaleY;
    private float mScaleZ;

    public NonPIPEMagicFilter(){
        super(NO_FILTER_VERTEX_SHADER2, NO_FILTER_FRAGMENT_SHADER);
        mGLCubeBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.CUBE.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mGLCubeBuffer.put(TextureRotationUtil.CUBE).position(0);

        mGLTextureBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mGLTextureBuffer.put(TextureRotationUtil.getRotation(Rotation.NORMAL, false, false)).position(0);
        Matrix.setIdentityM(mTextureTransformMatrix, 0);
        //setScale(0.5f, 0.5f, 0f);
    }

    protected void onInit() {
        super.onInit();
        mTextureTransformMatrixLocation = GLES20.glGetUniformLocation(mGLProgId, "textureTransform");
    }

    protected void onDrawArraysPre() {
        GLES20.glUniformMatrix4fv(mTextureTransformMatrixLocation, 1, false, mTextureTransformMatrix, 0);
    }

    protected void onDrawArraysAfter() {

    }

    public void setScale(float x, float y, float z) {
        mScaleX = x;
        mScaleY = y;
        mScaleZ = z;
        runOnDraw(new Runnable() {
            @Override
            public void run() {
                Matrix.scaleM(mTextureTransformMatrix, 0, mScaleX, mScaleY, mScaleZ);
            }
        });
    }
}
