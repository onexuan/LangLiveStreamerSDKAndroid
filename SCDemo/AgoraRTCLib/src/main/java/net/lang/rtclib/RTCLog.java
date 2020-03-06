package net.lang.rtclib;

import android.os.Debug;
import android.util.Log;

import java.util.Locale;

public class RTCLog {
    class RTCLogLevel {
        public static final int RTC_LOG_INFO = 0;
        public static final int RTC_LOG_DEBUG = 1;
        public static final int RTC_LOG_WARNING = 2;
        public static final int RTC_LOG_ERROR = 3;
        public static final int RTC_LOG_NONE = 4;
    }
    private static int mLevel = RTCLogLevel.RTC_LOG_DEBUG;

    public static void print(String tag, int level, String msg) {
        if (level >= mLevel) {
            switch (level) {
                case RTCLogLevel.RTC_LOG_INFO:
                    Log.i(tag, msg);
                    break;
                case RTCLogLevel.RTC_LOG_DEBUG:
                    Log.d(tag, msg);
                    break;
                case RTCLogLevel.RTC_LOG_WARNING:
                    Log.w(tag, msg);
                    break;
                case RTCLogLevel.RTC_LOG_ERROR:
                    Log.e(tag, msg);
                    break;
                case RTCLogLevel.RTC_LOG_NONE:
                    break;
                default:
                    break;
            }
        }
    }

    public static int e(String tag, String msg) {
        RTCLog.print(tag, RTCLogLevel.RTC_LOG_ERROR, msg);
        return 0;
    }

    public static int e(String tag, String msg, Throwable tr) {
        return e(tag, msg);
    }

    public static int efmt(String tag, String fmt, Object... args) {
        String msg = String.format(Locale.US, fmt, args);
        return e(tag, msg);
    }

    public static int i(String tag, String msg) {
        RTCLog.print(tag, RTCLogLevel.RTC_LOG_INFO, msg);
        return 0;
    }

    public static int i(String tag, String msg, Throwable tr) {
        return i(tag, msg);
    }

    public static int ifmt(String tag, String fmt, Object... args) {
        String msg = String.format(Locale.US, fmt, args);
        return i(tag, msg);
    }

    public static int w(String tag, String msg) {
        RTCLog.print(tag, RTCLogLevel.RTC_LOG_WARNING, msg);
        return 0;
    }

    public static int w(String tag, String msg, Throwable tr) {
        return w(tag, msg);
    }

    public static int wfmt(String tag, String fmt, Object... args) {
        String msg = String.format(Locale.US, fmt, args);
        return w(tag, msg);
    }

    public static int d(String tag, String msg) {
        RTCLog.print(tag, RTCLogLevel.RTC_LOG_DEBUG, msg);
        return 0;
    }

    public static int d(String tag, String msg, Throwable tr) {
        return d(tag, msg);
    }

    public static int dfmt(String tag, String fmt, Object... args) {
        String msg = String.format(Locale.US, fmt, args);
        return d(tag, msg);
    }

    public static int f(String tag, String msg) {
        e(tag, msg);
        return 0;
    }

    public static int f(String tag, String msg, Throwable tr) {
        return f(tag, msg);
    }

    public static int ffmt(String tag, String fmt, Object... args) {
        String msg = String.format(Locale.US, fmt, args);
        return f(tag, msg);
    }

    public static void printStackTrace(Throwable e) {
        e(Debug.class.getName(), e.toString());
    }

    public static void printCause(Throwable e) {
        Throwable cause = e.getCause();
        if (cause != null)
            e = cause;
        printStackTrace(e);
    }
}
