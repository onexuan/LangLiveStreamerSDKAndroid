package net.lang.streamer.config;

import io.agora.rtc.Constants;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by lang on 2017/9/4.
 */

public class LangRtcConfig {
    public static final String rtcAppId = "6fa0a2b2d5a44f7b93d038eb17e975d5";
    public static boolean localMixed = false;   // audio/video mix using mobile device.

    // please check details string_array_resolutions/string_array_frame_rate/string_array_bit_rate at strings_config.xml
    public static int[] kVideoProfiles = new int[]{
            Constants.VIDEO_PROFILE_120P,   //160x120   15fps   65kbps
            Constants.VIDEO_PROFILE_180P,   //320x180   15fps   140kbps
            Constants.VIDEO_PROFILE_240P,   //320x240	15fps	200kbps
            Constants.VIDEO_PROFILE_360P,   //640x360	15fps	400kbps
            Constants.VIDEO_PROFILE_480P,   //640x480	15fps	500kbps
            Constants.VIDEO_PROFILE_720P};  //1280x720	15fps	1130kbps


    public int videoProfile;

    public int audioSampleRate;
    public int audioChannel;
    public int videoBitrate;
    public int videoFPS;
    public String pushStreamUrl;

    public String channelName;

    public String encryptionKey;
    public String encryptionMode;

    public boolean audience = false;   // audience flag indicate joined user act as broadcaster/audience

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
