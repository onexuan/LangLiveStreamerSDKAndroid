package net.lang.rtclib;

import java.nio.ByteBuffer;

public class MediaDataBuffer {
    private int uid;
    private ByteBuffer byteBuffer;

    public MediaDataBuffer(int uid, ByteBuffer byteBuffer) {
        this.uid = uid;
        this.byteBuffer = byteBuffer;
    }

    public int getUid() {
        return uid;
    }

    public void setUid(int uid) {
        this.uid = uid;
    }

    public ByteBuffer getByteBuffer() {
        return byteBuffer;
    }

    public void setByteBuffer(ByteBuffer byteBuffer) {
        this.byteBuffer = byteBuffer;
    }
}
