package net.lang.streamer2.engine.publish;

import android.media.MediaCodec;

import net.lang.streamer2.utils.DebugLog;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import static android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME;

public final class LangStreamingBuffer {

    private static final String TAG = LangStreamingBuffer.class.getSimpleName();

    private int mCurrentInterval = 0;
    private static final int sCallbackInterval = 5;
    private static final int sUpdateInterval = 1;
    private static final int sSendBufferMaxCount = 90;

    private IStreamingBufferListener mListener;

    private final Object mFence = new Object();
    private List<LangMediaFrame> mFrameList;
    private List<LangMediaFrame> mSortFrameList;
    private List<Integer> mThresholdList;
    private int mLastDropFrames;

    private boolean mStartTimer = false;
    private Timer mTimer = new Timer(true);

    public LangStreamingBuffer(IStreamingBufferListener listener) {
        mListener = listener;
        mFrameList = new ArrayList<LangMediaFrame>();
        mSortFrameList = new ArrayList<LangMediaFrame>();
        mThresholdList = new ArrayList<Integer>();
        mLastDropFrames = 0;
    }

    public void destroy() {
        mTimerTask.cancel();
        mTimer.cancel();
    }

    public void appendObjectNoDrop(LangStreamingBuffer.LangMediaFrame frame) {
        if (frame == null) {
            return;
        }

        synchronized (mFence) {
            if (mSortFrameList.size() < sSendBufferMaxCount / 4) {
                mSortFrameList.add(frame);
            } else {
                // sort
                mSortFrameList.add(frame);
                Collections.sort(mSortFrameList);

                // add to local buffering list.
                LangMediaFrame firstFrame = popFirstObjectInternal(mSortFrameList);
                if (firstFrame != null) {
                    mFrameList.add(firstFrame);
                }
            }
        }
    }

    public void appendObject(LangStreamingBuffer.LangMediaFrame frame) {
        if (frame == null) {
            return;
        }
        if (!mStartTimer) {
            mStartTimer = true;
            mTimer.scheduleAtFixedRate(mTimerTask, 1, sUpdateInterval * 1000);
        }

        synchronized (mFence) {
            if (mSortFrameList.size() < sSendBufferMaxCount) {
                mSortFrameList.add(frame);
            } else {
                // sort
                mSortFrameList.add(frame);
                Collections.sort(mSortFrameList);
                // discard frames
                removeExpireFrame();
                // add to network buffering list.
                LangMediaFrame firstFrame = popFirstObjectInternal(mSortFrameList);
                if (firstFrame != null) {
                    mFrameList.add(firstFrame);
                }
            }
        }
    }

    public LangStreamingBuffer.LangMediaFrame popFirstObject() {
        synchronized (mFence) {
            LangMediaFrame firstFrame = popFirstObjectInternal(mFrameList);
            return firstFrame;
        }
    }

    public void removeAllObject() {
        synchronized (mFence) {
            mFrameList.clear();
        }
    }

    public int getLastDropFrames() {
        synchronized (mFence) {
            return mLastDropFrames;
        }
    }

    public void setLastDropFrames(int lastDropFrames) {
        synchronized (mFence) {
            mLastDropFrames = lastDropFrames;
        }
    }

    public int getReorderedFrames(boolean audio) {
        synchronized (mFence) {
            int audioFrames = 0;
            int videoFrames = 0;
            java.util.Iterator<LangMediaFrame> iter = mFrameList.iterator();
            while(iter.hasNext()){
                LangMediaFrame frame = iter.next();
                if (frame.isAudio) {
                    audioFrames++;
                } else {
                    videoFrames++;
                }
            }
            return audio ? audioFrames : videoFrames;
        }
    }

    private LangStreamingBuffer.LangMediaFrame popFirstObjectInternal(List<LangMediaFrame> list) {
        LangMediaFrame object = null;
        if (list.size() > 0) {
            object = list.get(0);
            list.remove(0);
        }
        return object;
    }

    private void removeExpireFrame() {
        if (mFrameList.size() < sSendBufferMaxCount) {
            return;
        }

        // delete P frames between first P and first I (PPPPPPPI)
        List<LangMediaFrame> pFrames = expirePFrames();
        mLastDropFrames += pFrames.size();
        if (pFrames.size() > 0) {
            mFrameList.removeAll(pFrames);
            return;
        }

        // delete one I frame
        List<LangMediaFrame> iFrames = expireIFrames();
        mLastDropFrames += iFrames.size();
        if (iFrames.size() > 0) {
            mFrameList.removeAll(iFrames);
            return;
        }

        mFrameList.clear();
    }

    private List<LangMediaFrame> expirePFrames() {
        List<LangMediaFrame> pFrames = new ArrayList<LangMediaFrame>();
        for (int index = 0; index < mFrameList.size(); index++) {
            LangMediaFrame frame = mFrameList.get(index);
            if (!frame.isAudio()) {
                boolean isKeyFrame = ((frame.flags & BUFFER_FLAG_KEY_FRAME) != 0);
                if (isKeyFrame && pFrames.size() > 0) {
                    break;
                } else if (!isKeyFrame) {
                    pFrames.add(frame);
                }
            }
        }
        return pFrames;
    }

    private List<LangMediaFrame> expireIFrames() {
        List<LangMediaFrame> iFrames = new ArrayList<LangMediaFrame>();
        long decodeTimeUs = 0;
        for (int index = 0; index < mFrameList.size(); index++) {
            LangMediaFrame frame = mFrameList.get(index);
            if (!frame.isAudio()) {
                boolean isKeyFrame = ((frame.flags & BUFFER_FLAG_KEY_FRAME) != 0);
                if (isKeyFrame) {
                    if (decodeTimeUs != 0 && decodeTimeUs != frame.decodeTimeUs)
                        break;
                    iFrames.add(frame);
                    decodeTimeUs = frame.decodeTimeUs;
                }
            }
        }
        return iFrames;
    }

    private void currentBufferStatus() {
        int currentCount = 0;
        int increaseCount = 0;
        int decreaseCount = 0;

        for (int index = 0; index < mThresholdList.size(); index++) {
            Integer number = mThresholdList.get(index);
            if (number.intValue() > currentCount) {
                increaseCount++;
            } else {
                decreaseCount++;
            }
            currentCount = number.intValue();
        }

        if (increaseCount >= sCallbackInterval) {
            if (mListener != null) {
                mListener.onBufferStatusIncrease();
            }
            return;
        }

        if (decreaseCount >= sCallbackInterval) {
            if (mListener != null) {
                mListener.onBufferStatusDecline();
            }
            return;
        }

        if (mListener != null) {
            mListener.onBufferStatusUnknown();
        }
        return;
    }

    private void tick() {
        mCurrentInterval += sUpdateInterval;

        synchronized (mFence) {
            Integer frameCount = Integer.valueOf(mFrameList.size());
            mThresholdList.add(frameCount);
        }

        if (mCurrentInterval >= sCallbackInterval) {

            currentBufferStatus();

            mCurrentInterval = 0;
            mThresholdList.clear();
        }
    }

    private TimerTask mTimerTask = new TimerTask() {
        @Override
        public void run() {
            tick();
        }
    };


    public static final class LangMediaFrame implements Comparable<LangMediaFrame> {

        private ByteBuffer bufferWrapper;
        private MediaCodec.BufferInfo bufferWrapperInfo;
        private byte[] data;
        private int size;
        private int flags;
        private long presentationTimeUs;
        private long decodeTimeUs;
        private boolean isAudio;

        public LangMediaFrame(ByteBuffer buffer, MediaCodec.BufferInfo bufferInfo, boolean audio) {
            this.data = new byte[bufferInfo.size];
            buffer.position(0);
            buffer.get(this.data, 0, bufferInfo.size);

            this.size = bufferInfo.size;
            this.flags = bufferInfo.flags;
            this.presentationTimeUs = bufferInfo.presentationTimeUs;
            this.decodeTimeUs = this.presentationTimeUs;
            this.isAudio = audio;

            bufferWrapper = ByteBuffer.wrap(data);
            bufferWrapperInfo = new MediaCodec.BufferInfo();
            bufferWrapperInfo.set(0, this.size, this.presentationTimeUs, this.flags);
        }

        public LangMediaFrame(byte[] data, int size, long pts, long dts, int flags, boolean audio) {
            data = new byte[size];
            System.arraycopy(data, 0, this.data, 0, size);

            this.size = size;
            this.flags = flags;
            this.presentationTimeUs = pts;
            this.decodeTimeUs = dts;
            this.isAudio = audio;

            bufferWrapper = ByteBuffer.wrap(data);
            bufferWrapperInfo = new MediaCodec.BufferInfo();
            bufferWrapperInfo.set(0, this.size, this.presentationTimeUs, this.flags);
        }

        public ByteBuffer getBuffer() {
            return bufferWrapper;
        }

        public MediaCodec.BufferInfo getBufferInfo() {
            return bufferWrapperInfo;
        }

        public byte[] getData() {
            return data;
        }

        public int getSize() {
            return size;
        }

        public long getPts() {
            return presentationTimeUs;
        }

        public long getDts() {
            return decodeTimeUs;
        }

        public int getFlags() {
            return flags;
        }

        public boolean isAudio() {
            return isAudio;
        }

        @Override
        public int compareTo(LangMediaFrame data) {
            if (this.getDts() > data.getDts()) {
                return 1;
            }
            return -1;
        }
    }

    public interface IStreamingBufferListener {

        void onBufferStatusUnknown();

        void onBufferStatusIncrease();

        void onBufferStatusDecline();
    }
}
