package net.lang.streamer2.config;

import android.os.Build;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by lang on 2017/9/4.
 */

public class LangRtcConfig {
    public static final String rtcAppId = "query-id-from-agora";// query this identifier from agora.io

    public static final int RTC_VIDEO_PROFILE_120P = 0; //160x120   15fps   65kbps
    public static final int RTC_VIDEO_PROFILE_180P = 1; //320x180   15fps   140kbps
    public static final int RTC_VIDEO_PROFILE_240P = 2; //320x240	15fps	200kbps
    public static final int RTC_VIDEO_PROFILE_360P = 3; //640x360	15fps	400kbps
    public static final int RTC_VIDEO_PROFILE_480P = 4; //640x480	15fps	500kbps
    public static final int RTC_VIDEO_PROFILE_720P = 5; //1280x720	15fps	1130kbps

    public int rtcVideoProfile = RTC_VIDEO_PROFILE_360P;
    public String channelName;
    public String encryptionKey;
    public String encryptionMode;
    public boolean localMixed = true;   // audio/video mix using mobile device.

    private List<String> mBlackLists = null;

    public LangRtcConfig() {
        initBlackLists();
    }

    private void initBlackLists() {
        mBlackLists = new ArrayList<String>();
        mBlackLists.add("HTC_D728x");
        mBlackLists.add("HTC E9pw");
    }

    public boolean deviceInBlackLists() {
        String modelDevice = Build.MODEL;
        for (int idx = 0; idx < mBlackLists.size(); idx++) {
            if (mBlackLists.get(idx).equals(modelDevice)) {
                return true;
            }
        }
        return false;
    }

    public static final class RtcDisplayParams {

        public float topOffsetPercent = 0.1f;
        public float subWidthPercentOnTwoWins = 0.5f;
        public float subHeightPercentOnTwoWins = 0.5f;
        public float subWidthPercentAboveTwoWins = 0.333f;
        public float subHeightPercentAboveTwoWins = 0.333f;

        public void set(
                float newTopOffset, float newSubWidthOnTwo, float newSubHeightOnTwo, float newSubWidthAboveTwo, float newSubHeightAboveTwo) {
            topOffsetPercent = newTopOffset;
            subWidthPercentOnTwoWins = newSubWidthOnTwo;
            subHeightPercentOnTwoWins = newSubHeightOnTwo;
            subWidthPercentAboveTwoWins = newSubWidthAboveTwo;
            subHeightPercentAboveTwoWins = newSubHeightAboveTwo;
        }

        public RtcDisplayParams dup() {
            RtcDisplayParams copy = new RtcDisplayParams();
            copy.set(topOffsetPercent, subWidthPercentOnTwoWins, subHeightPercentOnTwoWins, subWidthPercentAboveTwoWins, subHeightPercentAboveTwoWins);
            return copy;
        }
    }
}
