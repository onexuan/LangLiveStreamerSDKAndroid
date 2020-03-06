package com.snail.scdemo.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;


/**
 * Created by MRKING on 2017/4/26.
 */

public class Utils {

    private Utils() {
    }

    /**
     * 获得App版本号
     *
     * @param context
     * @return
     */

    public static String getVersion(Context context) {
        try {
            PackageManager manager = context.getPackageManager();
            PackageInfo info = manager.getPackageInfo(context.getPackageName(),
                    0);
            return info.versionName;
        } catch (Exception e) {
            e.printStackTrace();
            return "获得版本错误";
        }
    }


    /**
     * 检测是否是视频地址
     *
     * @param text
     * @return
     */

    public static boolean isVideoUrl(String text) {
        return !StringUtils.isNull(text) && (text.startsWith("rtmp://") || ((text.startsWith("http://") || text.startsWith("https://")) && (text.contains("m3u8") || text.contains("mp4") || text.contains("flv"))));
    }

    public static float readCpuUsage() {
        try {
            java.io.RandomAccessFile reader = new java.io.RandomAccessFile("/proc/stat", "r");
            String load = reader.readLine();

            String[] toks = load.split(" +");  // Split on one or more spaces

            long idle1 = Long.parseLong(toks[4]);
            long cpu1 = Long.parseLong(toks[2]) + Long.parseLong(toks[3]) + Long.parseLong(toks[5])
                    + Long.parseLong(toks[6]) + Long.parseLong(toks[7]) + Long.parseLong(toks[8]);

            try {
                Thread.sleep(360);
            } catch (Exception e) {}

            reader.seek(0);
            load = reader.readLine();
            reader.close();

            toks = load.split(" +");

            long idle2 = Long.parseLong(toks[4]);
            long cpu2 = Long.parseLong(toks[2]) + Long.parseLong(toks[3]) + Long.parseLong(toks[5])
                    + Long.parseLong(toks[6]) + Long.parseLong(toks[7]) + Long.parseLong(toks[8]);

            return (float)(cpu2 - cpu1) / ((cpu2 + idle2) - (cpu1 + idle1));

        } catch (IOException ex) {
            ex.printStackTrace();
        }

        return 0;
    }


    public static double readMemory() {
        final Runtime runtime = Runtime.getRuntime();
        //1024 * 1024 == 1048576
        final long usedMemInMB=(runtime.totalMemory() - runtime.freeMemory()) / 1048576L;
        return usedMemInMB;
    }

}
