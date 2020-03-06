/*
 * Copyright (C) 2013 Zhang Rui <bbcallen@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.lang.streamer2.utils;

import android.os.Debug;

import net.lang.streamer2.ILangCameraStreamer;
import net.lang.streamer2.LangMediaCameraStreamer;

import java.util.Locale;

public class DebugLog {

    public static int e(String tag, String msg) {
        LangMediaCameraStreamer.print(tag, ILangCameraStreamer.LangStreamerLogLevel.LANG_LOG_ERROR, msg);
        return 0;
    }

    public static int e(String tag, String msg, Throwable tr) {
       return e(tag, msg);
    }

    public static int efmt(String tag, String fmt, Object... args) {
            String msg = String.format(Locale.US, fmt, args);
            return e(tag, msg);
    }

    public static int w(String tag, String msg) {
        LangMediaCameraStreamer.print(tag, ILangCameraStreamer.LangStreamerLogLevel.LANG_LOG_WARNING, msg);
        return 0;
    }

    public static int w(String tag, String msg, Throwable tr) {
        return w(tag, msg);
    }

    public static int wfmt(String tag, String fmt, Object... args) {
        String msg = String.format(Locale.US, fmt, args);
        return w(tag, msg);
    }

    public static int i(String tag, String msg) {
        LangMediaCameraStreamer.print(tag, ILangCameraStreamer.LangStreamerLogLevel.LANG_LOG_INFO, msg);
        return 0;
    }

    public static int i(String tag, String msg, Throwable tr) {
        return i(tag, msg);
    }

    public static int ifmt(String tag, String fmt, Object... args) {
        String msg = String.format(Locale.US, fmt, args);
        return i(tag, msg);
    }

    public static int d(String tag, String msg) {
           LangMediaCameraStreamer.print(tag, ILangCameraStreamer.LangStreamerLogLevel.LANG_LOG_DEBUG, msg);
            return 0;
    }

    public static int d(String tag, String msg, Throwable tr) {
       return d(tag, msg);
    }

    public static int dfmt(String tag, String fmt, Object... args) {
            String msg = String.format(Locale.US, fmt, args);
            return d(tag, msg);
    }

    public static int v(String tag, String msg) {
        LangMediaCameraStreamer.print(tag, ILangCameraStreamer.LangStreamerLogLevel.LANG_LOG_VERBOSE, msg);
        return 0;
    }

    public static int v(String tag, String msg, Throwable tr) {
        return v(tag, msg);
    }

    public static int vfmt(String tag, String fmt, Object... args) {
        String msg = String.format(Locale.US, fmt, args);
        return v(tag, msg);
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
