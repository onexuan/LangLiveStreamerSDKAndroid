package net.lang.streamer.rtc;

import java.nio.FloatBuffer;

/**
 * Created by lang on 2017/9/8.
 */

public abstract class IRtcSession {

    protected FloatBuffer mGLTextureBuffer;

    protected int mUid;

    public int uid() {return mUid;}

    public void setUid(int uid) {mUid = uid;}

    public abstract void sendYUVData(final byte[] yuv, final int width, final int height);

    public abstract void generateYUVTextures();

    public abstract void destoryYUVTextures();

    public abstract void updateYUVTextures();

    public abstract boolean texturesMatch();

    public static void convertToOpenglesVertexCoordinates(
            float sub_x, float sub_y, float sub_width, float sub_height,
            float frame_width, float frame_height, float[] vertCoords)
    {
        // bottom left x & y
        vertCoords[0] = changeToGLPointX(sub_x, frame_width);
        vertCoords[1] = changeToGLPointY(sub_y + sub_height, frame_height);

        // bottom right x & y
        vertCoords[2] = changeToGLPointX(sub_x + sub_width, frame_width);
        vertCoords[3] = vertCoords[1];

        // top left x & y
        vertCoords[4] = vertCoords[0];
        vertCoords[5] = changeToGLPointY(sub_y, frame_height);

        // top right x & y
        vertCoords[6] = vertCoords[2];
        vertCoords[7] = vertCoords[5];
    }

    public static void flipVertexCoordinates(float[] dst, float[] src) {

        // new bottom left
        dst[0] = src[0];
        dst[1] = -src[5];

        // new bottom right
        dst[2] = src[2];
        dst[3] = -src[7];

        // new top left
        dst[4] = src[4];
        dst[5] = -src[1];

        // new top right
        dst[6] = src[6];
        dst[7] = -src[3];
    }

    protected static float changeToGLPointX(float x, float frameWidth) {
        float tempX = (x - frameWidth/2) / (frameWidth/2);
        return tempX;
    }

    protected static float changeToGLPointY(float y, float frameHeight) {
        float tempY = (frameHeight/2 - y) / (frameHeight/2);
        return tempY;
    }
}
