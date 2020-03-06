#include <jni.h>
#include <android/log.h>
#include <assert.h>
#include <string.h>
#include <cstdlib>
#include "rtmp_impl.h"

#define LOG_TAG "RtmpMuxer-JNI"

#define JAVA_HOST_CLASS_NAME "net/lang/streamer2/engine/publish/LangRtmpMuxer"

#define RTMP_MUXER_LOGD(...) ((void)__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__))
#define RTMP_MUXER_LOGI(...) ((void)__android_log_print(ANDROID_LOG_INFO , LOG_TAG, __VA_ARGS__))
#define RTMP_MUXER_LOGW(...) ((void)__android_log_print(ANDROID_LOG_WARN , LOG_TAG, __VA_ARGS__))
#define RTMP_MUXER_LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__))

#define RTMP_MUXER_ARRAY_ELEMS(a)  (sizeof(a) / sizeof(a[0]))

#define RTMP_AUDIO_TRACK_INDEX  0
#define RTMP_VIDEO_TRACK_INDEX  1

static int BUFFER_FLAG_CODEC_CONFIG = 2;

struct fields_t {
    jclass classId;
    jmethodID arrayID;
    jmethodID postEventFromNative;
};
static fields_t gFields;

static JavaVM* jvm;
static JNIEnv *jenv;

static void postEvent(JNIEnv *env, jobject weak_this, int what, int arg1, int arg2, jobject obj) {
    env->CallStaticVoidMethod(gFields.classId, gFields.postEventFromNative, weak_this, what, arg1, arg2, obj);
}

static void onRtmpConnecting(void* userData) {
    jobject weak_thiz = (jobject) rtmp_impl_get_weak_thiz(
            reinterpret_cast<rtmp_impl_context *>(userData));

    bool attached_ = false;
    JNIEnv* env_ = nullptr;
    jint ret_val = jvm->GetEnv(reinterpret_cast<void**>(&env_), JNI_VERSION_1_6);
    if (ret_val == JNI_EDETACHED) {
        // Attach the thread to the Java VM.
        ret_val = jvm->AttachCurrentThread(&env_, nullptr);
        attached_ = ret_val >= 0;
        assert(attached_);
    }

    postEvent(env_, weak_thiz, MSG_RTMP_CONNECTING, 0, 0, nullptr);

    if (attached_ && (jvm->DetachCurrentThread() < 0)) {
        assert(false);
    }
}

static void onRtmpConnected(void* userData, const char* serverInfo) {
    jobject weak_thiz = (jobject) rtmp_impl_get_weak_thiz(
            reinterpret_cast<rtmp_impl_context *>(userData));

    bool attached_ = false;
    JNIEnv* env_ = nullptr;
    jint ret_val = jvm->GetEnv(reinterpret_cast<void**>(&env_), JNI_VERSION_1_6);
    if (ret_val == JNI_EDETACHED) {
        // Attach the thread to the Java VM.
        ret_val = jvm->AttachCurrentThread(&env_, nullptr);
        attached_ = ret_val >= 0;
        assert(attached_);
    }

    jstring messageObj = env_->NewStringUTF(serverInfo);
    postEvent(env_, weak_thiz, MSG_RTMP_CONNECTED, 0, 0, messageObj);

    if (attached_ && (jvm->DetachCurrentThread() < 0)) {
        assert(false);
    }
}

static void onRtmpDisconnected(void* userData) {
    jobject weak_thiz = (jobject) rtmp_impl_get_weak_thiz(
            reinterpret_cast<rtmp_impl_context *>(userData));

    bool attached_ = false;
    JNIEnv* env_ = nullptr;
    jint ret_val = jvm->GetEnv(reinterpret_cast<void**>(&env_), JNI_VERSION_1_6);
    if (ret_val == JNI_EDETACHED) {
        // Attach the thread to the Java VM.
        ret_val = jvm->AttachCurrentThread(&env_, nullptr);
        attached_ = ret_val >= 0;
        assert(attached_);
    }

    postEvent(env_, weak_thiz, MSG_RTMP_STOPPED, 0, 0, nullptr);

    if (attached_ && (jvm->DetachCurrentThread() < 0)) {
        assert(false);
    }
}

static void onRtmpError(void* userData, const char* message) {
    jobject weak_thiz = (jobject) rtmp_impl_get_weak_thiz(
            reinterpret_cast<rtmp_impl_context *>(userData));

    bool attached_ = false;
    JNIEnv* env_ = nullptr;
    jint ret_val = jvm->GetEnv(reinterpret_cast<void**>(&env_), JNI_VERSION_1_6);
    if (ret_val == JNI_EDETACHED) {
        // Attach the thread to the Java VM.
        ret_val = jvm->AttachCurrentThread(&env_, nullptr);
        attached_ = ret_val >= 0;
        assert(attached_);
    }

    jstring messageObj = env_->NewStringUTF(message);
    postEvent(env_, weak_thiz, MSG_RTMP_SOCKET_EXCEPTION, 0, 0, messageObj);

    if (attached_ && (jvm->DetachCurrentThread() < 0)) {
        assert(false);
    }
}

// utility functions 
static void jniThrowException(JNIEnv* env, const char *name, const char *msg) {
    jclass cls = env->FindClass(name);
    // if cls is NULL, an exception has already been thrown
    if (cls != NULL) {
        env->ThrowNew(cls, msg);
    }
    // free the local ref
    env->DeleteLocalRef(cls);
}

static void rtmp_MediaMuxer_writeSampleData(
        JNIEnv *env, jclass clazz, jlong nativeObject, jint trackIndex,
        jobject byteBuf, jint offset, jint size, jlong timeUs, jint flags) {

    rtmp_impl_context* rtmp_context = reinterpret_cast<rtmp_impl_context *>(nativeObject);
    if (!rtmp_context) {
        jniThrowException(env, "java/lang/RuntimeException",
                          "Muxer create failed!");
        return;
    }

    // Try to convert the incoming byteBuffer into ABuffer
    void *dst = env->GetDirectBufferAddress(byteBuf);

    jlong dstSize;
    jbyteArray byteArray = NULL;

    if (dst == NULL) {
        byteArray = (jbyteArray)env->CallObjectMethod(byteBuf, gFields.arrayID);
        if (byteArray == NULL) {
            jniThrowException(env, "java/lang/IllegalArgumentException",
                              "byteArray is null");
            return;
        }

        jboolean isCopy;
        dst = env->GetByteArrayElements(byteArray, &isCopy);

        dstSize = env->GetArrayLength(byteArray);
    } else {
        dstSize = env->GetDirectBufferCapacity(byteBuf);
    }

    if (dstSize < (offset + size)) {
        RTMP_MUXER_LOGE("writeSampleData saw wrong dstSize %lld, size  %d, offset %d",
                        (long long)dstSize, size, offset);
        if (byteArray != NULL) {
            env->ReleaseByteArrayElements(byteArray, (jbyte *)dst, 0);
        }
        jniThrowException(env, "java/lang/IllegalArgumentException",
                          "sample has a wrong size");
        return;
    }

    // write sample data
    const unsigned char* buffer = (unsigned char *)dst + offset;
    long long timestamp = timeUs / 1000;
    if (trackIndex == RTMP_AUDIO_TRACK_INDEX) {
        if ((flags & BUFFER_FLAG_CODEC_CONFIG) != 0) {
            rtmp_impl_write_audio_header(rtmp_context, buffer, size, timestamp);
        } else {
            rtmp_impl_write_audio(rtmp_context, buffer, size, timestamp);
        }
    } else if (trackIndex == RTMP_VIDEO_TRACK_INDEX) {
        if ((flags & BUFFER_FLAG_CODEC_CONFIG) != 0) {
            rtmp_impl_write_video_header(rtmp_context, buffer, size, timestamp);
        } else {
            rtmp_impl_write_video(rtmp_context, buffer, size, timestamp, timestamp);
        }
    } else {
        RTMP_MUXER_LOGE("writeSampleData trackIndex(%d) neither audio track nor video track", trackIndex);
    }

    if (byteArray != NULL) {
        env->ReleaseByteArrayElements(byteArray, (jbyte *)dst, 0);
    }
}

// Constructor counterpart.
static jlong rtmp_MediaMuxer_native_setup(
        JNIEnv *env, jclass clazz, jobject weak) {
    RTMP_MUXER_LOGD("native_setup");

    rtmp_impl_context* rtmp_context = rtmp_impl_create();
    if (!rtmp_context) {
        jniThrowException(env, "java/lang/RuntimeException",
                          "Muxer create failed!");
        return 0;
    }
    rtmp_impl_set_weak_thiz(rtmp_context, env->NewGlobalRef(weak));

    rtmp_impl_callback_context rtmp_callback_context;
    rtmp_callback_context.connecting_callback = onRtmpConnecting;
    rtmp_callback_context.connected_callback = onRtmpConnected;
    rtmp_callback_context.disconnected_callback = onRtmpDisconnected;
    rtmp_callback_context.error_callback = onRtmpError;
    rtmp_impl_set_callback(rtmp_context, &rtmp_callback_context);

    return reinterpret_cast<jlong>(rtmp_context);
}

static jint rtmp_MediaMuxer_addAudioTrack(
        JNIEnv *env, jclass clazz, jlong nativeObject, jint samplerate, jint channel, jint bitrateKbps) {
    RTMP_MUXER_LOGD("native_addAudioTrack");

    rtmp_impl_context* rtmp_context = reinterpret_cast<rtmp_impl_context *>(nativeObject);
    if (rtmp_context == NULL) {
        jniThrowException(env, "java/lang/IllegalStateException",
            "Muxer was not set up correctly");
        return -1;
    }
    return rtmp_impl_add_audio_track(rtmp_context, samplerate, channel, 16, bitrateKbps);
}

static jint rtmp_MediaMuxer_addVideoTrack(
        JNIEnv *env, jclass clazz, jlong nativeObject, jint width, jint height,  jint fps, jint bitrateKbps) {
    RTMP_MUXER_LOGD("native_addVideoTrack");

    rtmp_impl_context* rtmp_context = reinterpret_cast<rtmp_impl_context *>(nativeObject);
    if (rtmp_context == NULL) {
        jniThrowException(env, "java/lang/IllegalStateException",
                          "Muxer was not set up correctly");
        return -1;
    }

    return rtmp_impl_add_video_track(rtmp_context, width, height, fps, bitrateKbps);
}

static void rtmp_MediaMuxer_start(JNIEnv *env, jclass clazz,
        jlong nativeObject, jstring jurl) {
    RTMP_MUXER_LOGD("native_start");

    rtmp_impl_context* rtmp_context = reinterpret_cast<rtmp_impl_context *>(nativeObject);
    if (rtmp_context == NULL) {
        jniThrowException(env, "java/lang/IllegalStateException",
                          "Muxer was not set up correctly");
        return;
    }

    const char *rtmp_url = env->GetStringUTFChars(jurl, NULL);
    int result = rtmp_impl_start(rtmp_context, rtmp_url);
    env->ReleaseStringUTFChars(jurl, rtmp_url);

    if (result != 0) {
        RTMP_MUXER_LOGW("Failed to start rtmp muxer(%s), result = %d", rtmp_url, result);
    }
}

static void rtmp_MediaMuxer_stop(JNIEnv *env, jclass clazz,
        jlong nativeObject) {
    RTMP_MUXER_LOGD("native_stop");

    rtmp_impl_context* rtmp_context = reinterpret_cast<rtmp_impl_context *>(nativeObject);
    if (rtmp_context == NULL) {
        jniThrowException(env, "java/lang/IllegalStateException",
                          "Muxer was not set up correctly");
        return;
    }

    int result = rtmp_impl_stop(rtmp_context);
    if (result != 0) {
        RTMP_MUXER_LOGW("Failed to stop rtmp muxer, result = %d", result);
    }
}

static void rtmp_MediaMuxer_native_release(
        JNIEnv* env, jclass clazz, jlong nativeObject) {
    RTMP_MUXER_LOGD("native_release");

    rtmp_impl_context* rtmp_context = reinterpret_cast<rtmp_impl_context *>(nativeObject);
    if (rtmp_context != NULL) {
        //only delete weak_thiz at release
        jobject weak_thiz = (jobject)rtmp_impl_set_weak_thiz(rtmp_context, nullptr);
        env->DeleteGlobalRef(weak_thiz);

        rtmp_impl_destroy(rtmp_context);
        rtmp_context = NULL;
    }
}

static JNINativeMethod gMuxerMethods[] = {
    { "nativeSetup", "(Ljava/lang/Object;)J",
        (void *)rtmp_MediaMuxer_native_setup },

    { "nativeAddAudioTrack", "(JIII)I",
        (void *)rtmp_MediaMuxer_addAudioTrack },

    { "nativeAddVideoTrack", "(JIIII)I",
        (void *)rtmp_MediaMuxer_addVideoTrack },
    
    { "nativeStart", "(JLjava/lang/String;)V",
        (void *)rtmp_MediaMuxer_start },
    
    { "nativeWriteSampleData", "(JILjava/nio/ByteBuffer;IIJI)V",
        (void *)rtmp_MediaMuxer_writeSampleData },

    { "nativeStop", "(J)V",
        (void *)rtmp_MediaMuxer_stop },

    { "nativeRelease", "(J)V",
        (void *)rtmp_MediaMuxer_native_release },
};

// This function only registers the native methods, and is called from
// JNI_OnLoad
static int register_rtmp_MediaMuxer(JNIEnv *env) {
    jclass clz = env->FindClass(JAVA_HOST_CLASS_NAME);
    if (clz == NULL) {
        RTMP_MUXER_LOGE("Class %s not found", JAVA_HOST_CLASS_NAME);
        return JNI_ERR;
    }

    jclass byteBufClass = env->FindClass("java/nio/ByteBuffer");
    if (byteBufClass == NULL) {
        RTMP_MUXER_LOGE("Class %s not found", byteBufClass);
        return JNI_ERR;
    }

    if (env->RegisterNatives(clz, gMuxerMethods, RTMP_MUXER_ARRAY_ELEMS(gMuxerMethods))) {
        RTMP_MUXER_LOGE("methods not registered");
        return JNI_ERR;
    }

    jobject clzObject = env->NewGlobalRef(clz);
    gFields.classId = (jclass)clzObject;

    char* name = "array";
    char* sign = "()[B";
    gFields.arrayID = env->GetMethodID(byteBufClass, name, sign);
    if (gFields.arrayID == NULL) {
        RTMP_MUXER_LOGE("method %s not found", name);
        return JNI_ERR;
    }

    name = "postEventFromNative";
    sign = "(Ljava/lang/Object;IIILjava/lang/Object;)V";
    gFields.postEventFromNative = env->GetStaticMethodID(clz, name, sign);
    if (gFields.postEventFromNative == NULL) {
        RTMP_MUXER_LOGE("method %s not found", name);
        return JNI_ERR;
    }

    return JNI_OK;
}

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    jvm = vm;

    if (jvm->GetEnv((void **)&jenv, JNI_VERSION_1_6) != JNI_OK) {
        RTMP_MUXER_LOGE("Env not got");
        return JNI_ERR;
    }

    register_rtmp_MediaMuxer(jenv);

    return JNI_VERSION_1_6;
}
