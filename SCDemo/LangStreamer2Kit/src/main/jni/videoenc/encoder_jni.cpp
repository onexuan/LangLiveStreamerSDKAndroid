#include <jni.h>
#include <android/log.h>
#include <assert.h>
#include <string.h>
#include <cstdlib>
#include "openh264_encoder.h"

#define LOG_TAG "openh264-JNI"

#define JAVA_HOST_CLASS_NAME "net/lang/streamer2/engine/encoder/LangOpenh264Encoder"

#define ENCODER_LOGD(...) ((void)__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__))
#define ENCODER_LOGI(...) ((void)__android_log_print(ANDROID_LOG_INFO , LOG_TAG, __VA_ARGS__))
#define ENCODER_LOGW(...) ((void)__android_log_print(ANDROID_LOG_WARN , LOG_TAG, __VA_ARGS__))
#define ENCODER_LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__))

#define OPENH264_ARRAY_ELEMS(a)  (sizeof(a) / sizeof(a[0]))

static JavaVM* jvm;
static JNIEnv *jenv;


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

// encoder callback function
void onOpenh264FrameAvailable(void* userData, EncodedFrame* encodedFrame) {
    //ENCODER_LOGD("[%s]: Enter", __FUNCTION__);
    jobject weak_thiz = (jobject)getEncoderWeakThiz(
            reinterpret_cast<EncoderContext *>(userData));
    if (weak_thiz == nullptr) {
        ENCODER_LOGW("[%s]: callback on a dead object", __FUNCTION__);
        return;
    }

    bool attached_ = false;
    JNIEnv* env_ = nullptr;
    jint ret_val = jvm->GetEnv(reinterpret_cast<void**>(&env_), JNI_VERSION_1_6);
    if (ret_val == JNI_EDETACHED) {
        // Attach the thread to the Java VM.
        ret_val = jvm->AttachCurrentThread(&env_, nullptr);
        attached_ = ret_val >= 0;
        assert(attached_);
    }

    uint8_t* h264_es = encodedFrame->data;
    int es_len = encodedFrame->length;
    if (es_len <= 0) {
        ENCODER_LOGE("[%s]: Fail to encode nalu", __FUNCTION__);
        return;
    }

    long long ptsUs = encodedFrame->ptsMs * 1000;
    int flags = encodedFrame->flags;

    if (flags) {
        ENCODER_LOGD("[%s]: length = %d time = %lld flags = %d", __FUNCTION__, es_len, ptsUs, flags);
    }

    jbyteArray outputFrame = env_->NewByteArray(es_len);
    env_->SetByteArrayRegion(outputFrame, 0, es_len, (jbyte *) h264_es);

    //ENCODER_LOGD("[%s]: weak reference get real object", __FUNCTION__);
    jclass weakRefclz = env_->GetObjectClass(weak_thiz);
    jmethodID weakRefMid = env_->GetMethodID(weakRefclz, "get", "()Ljava/lang/Object;");
    jobject realObj = env_->CallObjectMethod(weak_thiz, weakRefMid);
    if (realObj == nullptr) {
        ENCODER_LOGW("[%s]: callback not invoked due to real object released", __FUNCTION__);
        return;
    }

    //ENCODER_LOGI("[%s]: callback to java", __FUNCTION__);
    jclass clz = env_->GetObjectClass(realObj);
    jmethodID mid = env_->GetMethodID(clz, "onSoftEncodedData", "([BJI)V");
    env_->CallVoidMethod(realObj, mid, outputFrame, ptsUs, flags);

    if (attached_ && (jvm->DetachCurrentThread() < 0)) {
        assert(false);
    }

    //ENCODER_LOGD("[%s]: Leave", __FUNCTION__);
}

// Constructor counterpart.
static jlong h264encoder_nativeSetup(JNIEnv *env, jclass clazz,
        jint width, jint height, jint fps, jint bitrateBps, jint keyFrameIntervalSec) {
    ENCODER_LOGD("native_setup");

    EncoderConfig encoderConfig;
    encoderConfig.width = width;
    encoderConfig.height = height;
    encoderConfig.fps = fps;
    encoderConfig.bitrateBps = bitrateBps;
    encoderConfig.keyFrameIntervalSec = keyFrameIntervalSec;
    EncoderContext* encoderContext = createEncoder(&encoderConfig, true);
    if (!encoderContext) {
        jniThrowException(env, "java/lang/RuntimeException",
                          "Encoder create failed!");
        return 0;
    }

    // set callback now.
    EncoderCallback encoderCallback;
    encoderCallback.onEncodedBitstream = onOpenh264FrameAvailable;
    setEncoderCallback(encoderContext, &encoderCallback);

    return reinterpret_cast<jlong>(encoderContext);
}

static void h264encoder_nativeSetWeakReference(JNIEnv* env, jclass clazz,
        jlong nativeObject, jobject weakThiz) {
    ENCODER_LOGD("native_SetWeakReference");

    EncoderContext* encoderContext = reinterpret_cast<EncoderContext *>(nativeObject);
    if (!encoderContext) {
        jniThrowException(env, "java/lang/RuntimeException",
                          "Encoder was not set up correctly");
    }

    setEncoderWeakThiz(encoderContext, env->NewGlobalRef(weakThiz));
}

static void h264encoder_nativeSetBitrate(JNIEnv* env, jclass clazz,
        jlong nativeObject, jint bitrateBps) {
    ENCODER_LOGD("native_set_bitrate: %dkBps", bitrateBps/1024);

    EncoderContext* encoderContext = reinterpret_cast<EncoderContext *>(nativeObject);
    if (!encoderContext) {
        jniThrowException(env, "java/lang/RuntimeException",
                          "Encoder was not set up correctly");
    }

    setEncoderBitrate(encoderContext, bitrateBps);
}

static jint h264encoder_nativeGetBitrate(JNIEnv* env, jclass clazz,
        jlong nativeObject) {
    //ENCODER_LOGD("native_get_bitrate");

    EncoderContext* encoderContext = reinterpret_cast<EncoderContext *>(nativeObject);
    if (!encoderContext) {
        jniThrowException(env, "java/lang/RuntimeException",
                          "Encoder was not set up correctly");
    }

    return getEncoderBitrate(encoderContext);
}

static void h264encoder_nativeEncodeParameterSets(JNIEnv* env, jclass clazz,
        jlong nativeObject) {
    ENCODER_LOGD("native_get_parameterSets");
    EncoderContext* encoderContext = reinterpret_cast<EncoderContext *>(nativeObject);
    if (!encoderContext) {
        jniThrowException(env, "java/lang/RuntimeException",
                          "Encoder was not set up correctly");
        return;
    }

    encodeParameterSets(encoderContext);
}

static jint h264encoder_nativeEncodeFrame(JNIEnv* env, jclass clazz,
        jlong nativeObject, jbyteArray frame, jint size, jint width, jint height, jlong ptsUs) {
    EncoderContext* encoderContext = reinterpret_cast<EncoderContext *>(nativeObject);
    if (!encoderContext) {
        jniThrowException(env, "java/lang/RuntimeException",
                          "Encoder was not set up correctly");
        return -1;
    }

    int encoderWidth = getEncoderWidth(encoderContext);
    int encoderHeight = getEncoderHeight(encoderContext);
    if (encoderWidth != width || encoderHeight != height) {
        ENCODER_LOGE("picture size mismatch input(%dx%d) enc(%dx%d)",
                width, height, encoderWidth, encoderHeight);
        return -1;
    }

    jboolean isCopy;
    void *frameData = env->GetByteArrayElements(frame, &isCopy);
    jlong frameDataSize = env->GetArrayLength(frame);
    if ((jint)frameDataSize < size) {
        ENCODER_LOGE("nativeEncodeFrame saw wrong frameDataSize %lld, size %d",
                        (long long)frameDataSize, size);
        env->ReleaseByteArrayElements(frame, (jbyte *)frameData, 0);
    }

    ImageFrame imageFrame;
    imageFrame.width = width;
    imageFrame.height = height;
    imageFrame.data = (uint8_t *)frameData;
    imageFrame.y = imageFrame.data;
    imageFrame.u = imageFrame.y + width * height;
    imageFrame.v = imageFrame.u + width * height / 4;
    imageFrame.ptsMs = ((int64_t)ptsUs)/1000;

    int result = encodeFrame(encoderContext, &imageFrame);
    return result;
}

// Release resource
static void h264encoder_nativeRelease(JNIEnv* env, jclass clazz, jlong nativeObject) {
    ENCODER_LOGD("native_release");

    EncoderContext* encoderContext = reinterpret_cast<EncoderContext *>(nativeObject);
    if (encoderContext) {
        jobject prev_weak_thiz = (jobject)setEncoderWeakThiz(encoderContext, nullptr);
        if (prev_weak_thiz) {
            env->DeleteGlobalRef(prev_weak_thiz);
            prev_weak_thiz = nullptr;
        }
        releaseEncoder(encoderContext);
        encoderContext = NULL;
    }
}

static JNINativeMethod gOpenh264EncoderMethods[] = {
        { "nativeSetup", "(IIIII)J",
                (void *)h264encoder_nativeSetup },

        { "nativeSetWeakReference", "(JLjava/lang/Object;)V",
                (void *)h264encoder_nativeSetWeakReference },

        { "nativeSetBitrate", "(JI)V",
                (void *)h264encoder_nativeSetBitrate },

        { "nativeGetBitrate", "(J)I",
                (void *)h264encoder_nativeGetBitrate},

        { "nativeEncodeParameterSets", "(J)V",
                (void *)h264encoder_nativeEncodeParameterSets },

        { "nativeEncodeFrame", "(J[BIIIJ)I",
                (void *)h264encoder_nativeEncodeFrame },

        { "nativeRelease", "(J)V",
                (void *)h264encoder_nativeRelease },
};


static int register_openh264Encoder(JNIEnv *env) {
    jclass clz = env->FindClass(JAVA_HOST_CLASS_NAME);
    if (clz == NULL) {
        ENCODER_LOGE("Class %s not found", JAVA_HOST_CLASS_NAME);
        return JNI_ERR;
    }


    if (env->RegisterNatives(clz, gOpenh264EncoderMethods, OPENH264_ARRAY_ELEMS(gOpenh264EncoderMethods))) {
        ENCODER_LOGE("methods not registered");
        return JNI_ERR;
    }

    return JNI_OK;
}

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    jvm = vm;

    if (jvm->GetEnv((void **)&jenv, JNI_VERSION_1_6) != JNI_OK) {
        ENCODER_LOGE("Env not got");
        return JNI_ERR;
    }

    register_openh264Encoder(jenv);

    return JNI_VERSION_1_6;
}