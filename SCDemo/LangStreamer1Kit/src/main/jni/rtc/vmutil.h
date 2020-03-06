#ifndef __VM_UTIL_H__
#define __VM_UTIL_H__

#include <jni.h>
#include <stddef.h>

#include <assert.h>

#include <pthread.h>
#include <android/log.h>

#include "autolock.h"

#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "AG_EX_AV", __VA_ARGS__)
#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, "AG_EX_AV", __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "AG_EX_AV", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "AG_EX_AV", __VA_ARGS__)

#define TRUE true
#define FALSE false

#define CHECK_POINTER(pValue, rValue, ...) 	if (NULL == pValue) {  \
					                           LOGE(__VA_ARGS__); \
					                           return rValue; }

class AttachThreadScoped
{
public:
    explicit AttachThreadScoped(JavaVM* jvm)
        : attached_(false), jvm_(jvm), env_(nullptr) {
        jint ret_val = jvm->GetEnv(reinterpret_cast<void**>(&env_),
            JNI_VERSION_1_6);
        if (ret_val == JNI_EDETACHED) {
            // Attach the thread to the Java VM.
            ret_val = jvm_->AttachCurrentThread(&env_, nullptr);
            attached_ = ret_val >= 0;
            assert(attached_);
        }
	}

    ~AttachThreadScoped() {
        if (attached_ && (jvm_->DetachCurrentThread() < 0)) {
            assert(false);
        }
    }

    JNIEnv* env() { return env_; }

private:
    bool attached_;
    JavaVM* jvm_;
    JNIEnv* env_;
};

class VMUtil
{
public:
    static VMUtil& instance() { static VMUtil instance; return instance;}

    bool initialize(JNIEnv *jni_env);

    bool addJNIHostObject(JNIEnv *env,
			                  jobject obj);

    bool removeJNIHostObject(JNIEnv *env);

    bool on_mixed_audio_data(int16_t const*, int32_t length);

    bool on_remote_video_data(unsigned int uid,
                              uint8_t const*, int32_t,
                              uint8_t const*, int32_t,
                              uint8_t const*, int32_t,
                              int32_t,
                              int32_t,
                              int32_t);

private:
    VMUtil();
    ~VMUtil();

private:
    JavaVM    *mpVM;
    jobject   mJNIHost;

    jmethodID mOnMixedAudioData;
    jmethodID mOnVideoData;

    jbyteArray mVideoArray;

    CMutexLock mLock;
};


#endif  // __VM_UTIL_H__