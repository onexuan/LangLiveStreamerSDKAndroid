package net.lang.streamer2.engine.data;

public final class LangMediaBuffer {
    private byte[] data;
    private int dataLength;
    private long presentationTimeUs;

    public LangMediaBuffer(int capacity) {
        this.data = new byte[capacity];
        this.dataLength = 0;
        this.presentationTimeUs = 0;
    }

    private LangMediaBuffer(byte[] data, int dataLength, long presentationTimeUs) {
        if (data.length < dataLength) {
            throw new IllegalArgumentException("LangMediaBuffer: input dataLength:" + dataLength +
                    " larger than data capacity: " + data.length);
        }
        this.data = data;
        this.dataLength = dataLength;
        this.presentationTimeUs = presentationTimeUs;
    }

    public byte[] data() {
        return this.data;
    }

    public int dataCapacity() {
        return this.data.length;
    }

    public void setDataLength(int dataLength) throws IllegalArgumentException {
        if (dataCapacity() < dataLength) {
            throw new IllegalArgumentException("setDataLength: input dataLength:" + dataLength +
                    " larger than data capacity: " + data.length);
        }
        this.dataLength = dataLength;
    }

    public int dataLength() {
        return this.dataLength;
    }

    public void setPresentationTimeUs(long presentationTimeUs) {
        this.presentationTimeUs = presentationTimeUs;
    }

    public long presentationTimeUs() {
        return this.presentationTimeUs;
    }

    public static LangMediaBuffer wrap(byte[] data, int dataLength, long presentationTimeUs)
            throws IllegalArgumentException {
        return new LangMediaBuffer(data, dataLength, presentationTimeUs);
    }
}
