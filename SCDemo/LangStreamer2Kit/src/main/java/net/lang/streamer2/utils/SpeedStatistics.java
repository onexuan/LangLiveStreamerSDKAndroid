package net.lang.streamer2.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by lichao on 17-5-31.
 */

public class SpeedStatistics {
    public final int kWinLenMs = 2000; // 2 sec
    private int mMaxList = 0;
    private int mWinLen = kWinLenMs;
    private List<Long> mlist;

    public SpeedStatistics(int winLenMs) {
        this();
        mWinLen = winLenMs;
    }

    public SpeedStatistics() {
        mlist = new ArrayList<Long>();
        // 1 cnt per of millisecond
        mMaxList = mWinLen;
    }

    public void add() {
        synchronized (this) {
            mlist.add(System.currentTimeMillis());
            int size = mlist.size();
            if (size > mMaxList) {
                check_l();
            }
        }
    }

    private double check_l() {
        Long time = System.currentTimeMillis();
        Long oldest = time - kWinLenMs;
        while (mlist.size() > 0) {
            if (mlist.get(0).longValue() < oldest) {
                mlist.remove(0);
            }else {
                break;
            }
        }
        double rate = mlist.size() / (kWinLenMs / 1000);
        return rate;
    }

    public double rate() {
        double v;
        synchronized (this) {
            v = check_l();
        }
        return v;
    }
}
