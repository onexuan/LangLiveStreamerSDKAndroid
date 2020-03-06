package net.lang.streamer2.engine.session;

import net.lang.streamer2.engine.data.LangMediaBuffer;
import net.lang.streamer2.utils.DebugLog;

import java.util.LinkedList;
import java.util.Queue;

public final class RecyclableBufferQueue {
    private static final String TAG = RecyclableBufferQueue.class.getSimpleName();
    private static final int sDefaultBufferCnt = 5;

    private final Object mBufferFence = new Object();

    private int mMaxBufferCnt;

    private Queue<LangMediaBuffer> mFilledQueue;
    private Queue<LangMediaBuffer> mEmptyQueue;

    public RecyclableBufferQueue(int dataCapacity) {
        this(dataCapacity, sDefaultBufferCnt);
    }

    public RecyclableBufferQueue(int dataCapacity, int totalBlocks) {
        mFilledQueue = new LinkedList<>();
        mEmptyQueue = new LinkedList<>();
        allocateMediaBuffer(dataCapacity, totalBlocks);
    }

    public LangMediaBuffer dequeueEmptyBuffer() {
        LangMediaBuffer emptyBuffer = null;
        //synchronized (mBufferFence) {
            emptyBuffer = mEmptyQueue.peek();
        //}
        return emptyBuffer;
    }

    public void queueEmptyBuffer() {
        //synchronized (mBufferFence) {
            LangMediaBuffer emptyBuffer = mEmptyQueue.poll();
            if (emptyBuffer != null) {
                mFilledQueue.add(emptyBuffer);
            }
        //}
    }

    public LangMediaBuffer dequeueUsedBuffer() {
        LangMediaBuffer filledBuffer = null;
        //synchronized (mBufferFence) {
            filledBuffer = mFilledQueue.peek();
        //}
        return filledBuffer;
    }

    public void queueUsedBuffer() {
        //synchronized (mBufferFence) {
            LangMediaBuffer filledBuffer = mFilledQueue.poll();
            if (filledBuffer != null) {
                mEmptyQueue.add(filledBuffer);
            }
        //}
    }

    public void reset() {
        //synchronized (mBufferFence) {
            DebugLog.d(TAG, "before resee(), empty queue size = " + mEmptyQueue.size());
            while (!mFilledQueue.isEmpty()) {
                LangMediaBuffer filledBuffer = mFilledQueue.poll();
                mEmptyQueue.add(filledBuffer);
            }
            DebugLog.d(TAG, "after reset(), empty queue size = " + mEmptyQueue.size());
        //}
    }

    public int capacity() {
        return mMaxBufferCnt;
    }

    private void allocateMediaBuffer(final int dataCapacity, final int totalBlocks) {
        for (int i = 0; i < totalBlocks; i++) {
            LangMediaBuffer mediaBuffer = new LangMediaBuffer(dataCapacity);
            mEmptyQueue.add(mediaBuffer);
        }
        mMaxBufferCnt = totalBlocks;
    }
}
