package net.lang.streamer;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import android.util.Log;

import net.lang.streamer.utils.DebugLog;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by lichao on 17-3-14.
 */

public class LangCapabilityDetecting {
    static final String TAG = LangCapabilityDetecting.class.getName();
    private static Map<String, String> mCapabilitys = null;
    static final String sNo = "no";
    static final String sOk = "ok";
    static final String sFirstABI = "abi";
    // M is base unit of memory
    static final String sMem = "memory";
    static final String sEGL = "egl3";
    static final String sGyro = "gyroscope";
    static final String sEncodeH263 = "encode.h263";
    static final String sEncodeMpeg4 = "encode.mpeg4";
    static final String sEncodeH264 = "encode.h264";
    static final String sEncodeH265 = "encode.h265";
    static final String sDecodeH263 = "decode.h263";
    static final String sDecodeMpeg4 = "decode.mpeg4";
    static final String sDecodeH264 = "decode.h264";
    static final String sDecodeH265 = "decode.h265";
    // Example:
    // map.put(sDecodeH265 + sCodecCapabilitySuffix,
    // width + "|" + height +"|"+ profile / level +"|" + framerate +"|"+ bitrate);
    static final String sCodecCapabilitySuffix = ".cap";
    static {
        mCapabilitys = new HashMap<String, String>();
        mCapabilitys.put(sEGL, sNo);
        mCapabilitys.put(sGyro,sNo);
        mCapabilitys.put(sEncodeH263, sNo);
        mCapabilitys.put(sEncodeMpeg4, sNo);
        mCapabilitys.put(sEncodeH264, sNo);
        mCapabilitys.put(sEncodeH265, sNo);
        mCapabilitys.put(sDecodeH263, sNo);
        mCapabilitys.put(sDecodeMpeg4, sNo);
        mCapabilitys.put(sDecodeH264, sNo);
        mCapabilitys.put(sDecodeH265, sNo);
        mCapabilitys.put(sFirstABI, sNo);
        mCapabilitys.put(sMem, sNo);
    }
//    static long mFlags = 0;
//    // 0 - 1 bit
//    static int FLAG_EGL3_SUPPORTS = 1;
//    static int FLAG_GYROSCOPE_SUPPORTS = 2;
//    // 2 - 31
//    static int FLAG_HARD_ENCODEC_H264_SUPPORTS = 4;
//    static int FLAG_HARD_ENCODEC_H265_SUPPORTS = 8;
//    static int FLAG_HARD_DECODEC_H264_SUPPORTS = 16;
//    static int FLAG_HARD_DECODEC_H265_SUPPORTS = 32;
    private Context mCtx;

    public Map<String, String > capabilityMap() {
        return mCapabilitys;
    }

    public LangCapabilityDetecting(Context ctx) {
        mCtx = ctx;
    }

    public void dump() {
        DebugLog.w(TAG, cpuInfo());
        DebugLog.w(TAG, eglInfo());
        DebugLog.w(TAG, memInfo());
        DebugLog.w(TAG, sensorInfo());
        DebugLog.w(TAG, codecInfo());
    }

    public void dumpToSystem() {
        Log.w(TAG, cpuInfo());
        Log.w(TAG, eglInfo());
        Log.w(TAG, memInfo());
        Log.w(TAG, sensorInfo());
        Log.w(TAG, codecInfo());
    }

    public String eglInfo() {
        ActivityManager am = (ActivityManager) mCtx.getSystemService(Context.ACTIVITY_SERVICE);
        ConfigurationInfo info = am.getDeviceConfigurationInfo();
        if ((info.reqGlEsVersion >= 0x30000)) {
            mCapabilitys.put(sEGL, sOk);
        }
        return "EGL " + info.getGlEsVersion() + "(" + Integer.toHexString(info.reqGlEsVersion) + ")";
    }

    public String sensorInfo() {
        SensorManager sm = (SensorManager) mCtx.getSystemService(Context.SENSOR_SERVICE);
        List<Sensor> allSensors = sm.getSensorList(Sensor.TYPE_ALL);
        StringBuffer str = new StringBuffer();
        str.append("Total " + allSensors.size() + " sensors:\n");
        Sensor s;
        for (int i = 0; i < allSensors.size(); i++) {
            s = allSensors.get(i);

            if (s.getType() == Sensor.TYPE_GYROSCOPE) {
                str.append("Name        :" + s.getName() + "\n");
                str.append("Version     :" + s.getVersion() + "\n");
                str.append("GenericType :" + s.getType() + "\n");
                str.append("Vendor      :" + s.getVendor() + "\n");
                str.append("Power       :" + s.getPower() + "\n");
                str.append("Resolution  :" + s.getResolution() + "\n");
                str.append("MaximumRange:" + s.getMaximumRange() + "\n");
                str.append("TypeName    :GYROSCOPE \n\n");
                mCapabilitys.put(sGyro, sOk);
            }
//            switch (s.getType()) {
//                case Sensor.TYPE_ACCELEROMETER:
//                    str.append(" ACCELEROMETER");
//                    break;
//                case Sensor.TYPE_GYROSCOPE:
//                    str.append(" GYROSCOPE");
//                    break;
//                case Sensor.TYPE_LIGHT:
//                    str.append(" LIGHT");
//                    break;
//                case Sensor.TYPE_MAGNETIC_FIELD:
//                    str.append(" MAGNETIC_FIELD");
//                    break;
//                case Sensor.TYPE_ORIENTATION:
//                    str.append(" ORIENTATION");
//                    break;
//                case Sensor.TYPE_PRESSURE:
//                    str.append(" PRESSURE");
//                    break;
//                case Sensor.TYPE_PROXIMITY:
//                    str.append(" PROXIMITY");
//                    break;
//                case Sensor.TYPE_TEMPERATURE:
//                    str.append(" TEMPERATURE");
//                    break;
//                default:
//                    str.append(" Unknown");
//                    break;
//            }
//            str.append("\n\n");
        }
        return str.toString();
    }

    public String memInfo(){
        String str1 = "/proc/meminfo";
        String str2;
        Double memory=0.0 ;
        try {
            FileReader r=new FileReader(str1);
            BufferedReader bufferedRead=new BufferedReader(r, 8192);
            str2=bufferedRead.readLine();
            String str4[] =str2.split(" ");
            memory= Double.parseDouble(str4[str4.length - 2]) / 1024;
        } catch (Exception e) {
            // TODO: handle exception
        }
        mCapabilitys.put(sMem, String.valueOf(memory));
        return String.valueOf(memory) + "M";
    }

    public String cpuInfo() {
        String str = "";
        Process process = null;
        String cpu_abi = null;
        try {
            process = Runtime.getRuntime().exec("getprop ro.product.cpu.abi");
            InputStreamReader ir = new InputStreamReader(process.getInputStream());
            BufferedReader input = new BufferedReader(ir);
            cpu_abi =  input.readLine();
        } catch (IOException e) {
            cpu_abi = "non";
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            for (String abi: Build.SUPPORTED_ABIS) {
                str += abi + " ";
            }
        }else {
            str = cpu_abi;
        }
        mCapabilitys.put(sFirstABI, cpu_abi);
        return  str + "/ " + Build.VERSION.RELEASE + "(" + String.valueOf(Build.VERSION.SDK_INT) + ")";
    }

    private List<MediaCodecInfo> selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        List<MediaCodecInfo> infos = new ArrayList<MediaCodecInfo>();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    infos.add(codecInfo);
                }
            }
        }
        return infos;
    }

    private String appendMap(MediaCodecInfo info, String mime) {
        String rev = "non";
        if (info.getName().contains("google") ||
                info.getName().contains("GOOGLE") ||
                info.getName().contains("Google") )
            return rev;

        boolean encode = info.isEncoder();

        if (mime == "video/avc") {
            rev = encode ? mCapabilitys.put(sEncodeH264, sOk) : mCapabilitys.put(sDecodeH264, sOk);
            rev = sEncodeH264;
        }else if (mime == "video/hevc") {
            rev = encode ? mCapabilitys.put(sEncodeH265, sOk) : mCapabilitys.put(sDecodeH265, sOk);
            rev = sEncodeH265;
        }else if (mime == "video/mp4v-es") {
            rev = encode ? mCapabilitys.put(sEncodeMpeg4, sOk) : mCapabilitys.put(sDecodeMpeg4, sOk);
            rev = sEncodeMpeg4;
        }else if (mime == "video/3gpp") {
            rev = encode ? mCapabilitys.put(sEncodeH263, sOk) : mCapabilitys.put(sDecodeH263, sOk);
            rev = sEncodeH263;
        }else {

        }
        return rev;
    }

    public String codecInfo() {
        String mimes[] = {
                "video/avc",
                "video/hevc",
                "video/mp4v-es",
                "video/3gpp"
        };
        StringBuffer str = new StringBuffer();
        for (int i = 0; i < mimes.length; i++) {
            String mime = mimes[i];
            str.append("MIME         : " + mime + "\n");
            List<MediaCodecInfo> infos = selectCodec(mime);
            for (MediaCodecInfo info: infos) {
                MediaCodecInfo.CodecCapabilities capabilities = null;
                try {
                    capabilities = info.getCapabilitiesForType(mime);
                    String key = appendMap(info, mime);
                    str.append("OpenMAX Name   : " + info.getName() + "\n");
                    if (capabilities != null) {
                        String max_w = "";
                        String max_h = "";
                        String max_bitrate = "";
                        String max_framerate = "";
                        String profile = "";
                        MediaCodecInfo.CodecProfileLevel profiles[] =  capabilities.profileLevels;
                        for (MediaCodecInfo.CodecProfileLevel pro: profiles) {
                            profile += String.valueOf(pro.profile) + "/" + String.valueOf(pro.level) + ",";
                        }
                        profile += "NULL";

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            MediaCodecInfo.VideoCapabilities videoCapabilities= capabilities.getVideoCapabilities();
                            max_bitrate = videoCapabilities.getBitrateRange().getLower().toString() + "-" + videoCapabilities.getBitrateRange().getUpper().toString();
                            max_framerate = videoCapabilities.getSupportedFrameRates().getLower().toString() + "-" + videoCapabilities.getSupportedFrameRates().getUpper().toString();
                            max_h = videoCapabilities.getSupportedHeights().getLower().toString() + "-" + videoCapabilities.getSupportedHeights().getUpper().toString();
                            max_w = videoCapabilities.getSupportedWidths().getLower().toString() + "-" + videoCapabilities.getSupportedWidths().getUpper().toString();
                        }
                        str.append("Range Width    : " + max_w + "\n");
                        str.append("Range Height   : " + max_h + "\n");
                        str.append("Range Bitrate  : " + max_bitrate + "\n");
                        str.append("Range Framerate: " + max_framerate + "\n");
                        str.append("Profile      : " + profile + "\n");
                        mCapabilitys.put(key + sCodecCapabilitySuffix, max_w + "|" + max_h + "|" + profile + "|" + max_framerate + "|" + max_bitrate);
                    }
                }catch (RuntimeException e) {

                }
                str.append("\n");
            }
        }
        return str.toString();
    }
}
