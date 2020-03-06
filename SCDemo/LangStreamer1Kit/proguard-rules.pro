# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /home/lichao/tools/android-sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

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

-keep public interface net.lang.streamer.widget.LangPushAuth{*;}
-keep public interface net.lang.streamer.widget.LangAuthCallBack{*;}
-keep public class net.lang.streamer.widget.LangPushAuthentication{*;}
-keep public class net.lang.streamer.widget.LangMagicCameraView{*;}

-keep enum net.lang.streamer.ILangCameraStreamer$**{**[] $VALUES;
                                                          public *;}
-keepnames public interface net.lang.streamer.ILangCameraStreamer.**{*;}
-keep public interface net.lang.streamer.ILangCameraStreamer{*;}
-keep public interface net.lang.streamer.ILangCameraStreamer$*{*;}

-keep enum net.lang.streamer.ILangRtcAudience$**{**[] $VALUES;
                                                        public *;}
-keep public interface net.lang.streamer.ILangRtcAudience{*;}
-keep public interface net.lang.streamer.ILangRtcAudience$*{*;}

-keep public class net.lang.streamer.LangCameraStreamer{*;}
-keep public class net.lang.streamer.LangLiveInfo{*;}
-keep public class net.lang.streamer.LangRtcInfo{*;}
-keep public class net.lang.streamer.utils.DebugLog{*;}
-keep public class net.lang.streamer.LangRtcAudienceProxy{*;}

-keep public class net.lang.streamer.engine.LangVideoEncoderImpl{*;}
-keep enum net.lang.streamer.engine.LangVideoEncoderImpl**{**[] $VALUES;
                                                          public *;}

-keep class io.agora.rtc.** {*;}
-keep interface com.sensetime.** {*;}
-keep class com.sensetime.** {*;}
-keep class net.lang.streamer.rtc.io.agora.ex.AudioVideoPreProcessing{*;}
-keep class net.lang.streamer.config.** {*;}
-keep class net.lang.streamer.video.gb.** {*;}
-keep class net.lang.streamer.video.gles.** {*;}