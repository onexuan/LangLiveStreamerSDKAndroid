# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
-keep enum net.lang.streamer2.ILangCameraStreamer$**{**[] $VALUES;
                                                          public *;}
-keepnames public interface net.lang.streamer2.ILangCameraStreamer.**{*;}
-keep public interface net.lang.streamer2.ILangCameraStreamer{*;}
-keep public interface net.lang.streamer2.ILangCameraStreamer$*{*;}
-keep public class net.lang.streamer2.LangMediaCameraStreamer{*;}
-keep public class net.lang.streamer2.LangRtcInfo{*;}
-keep public class net.lang.streamer2.LangRtcUser{*;}
-keep public class net.lang.streamer2.LangRtmpInfo{*;}
-keep public class net.lang.streamer2.LangStreamerVersion{*;}
-keep class net.lang.streamer2.config.** {*;}
#keep native jni reference class
-keep public class net.lang.streamer2.engine.encoder.LangOpenh264Encoder{*;}
-keep public class net.lang.streamer2.engine.publish.LangRtmpMuxer{*;}
