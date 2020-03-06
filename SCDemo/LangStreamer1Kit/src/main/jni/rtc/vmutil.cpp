#include <string.h>
#include "vmutil.h"

#define GET_METHOD_ID(var, func, spec);                        \
	var = jni_env->GetMethodID(class_jni_host, func, spec);    \
	CHECK_POINTER(var, FALSE, "can't execute GetMethodID of %s !", func);

bool VMUtil::initialize(JNIEnv *jni_env)
{
    CHECK_POINTER(jni_env, FALSE, "jni_env is NULL!");

    // get all method id
    jclass class_jni_host;
    class_jni_host = jni_env->FindClass("net/lang/streamer/rtc/io/agora/ex/AudioVideoPreProcessing");
    CHECK_POINTER(class_jni_host, FALSE, "can't execute FindClass!");

    GET_METHOD_ID(mOnMixedAudioData, "VM_onMixedAudioData", "([B)V");
    GET_METHOD_ID(mOnVideoData, "VM_onVideoData", "([BIIII)V");

    // get JVM object
    if (JNI_OK != jni_env->GetJavaVM(&mpVM)) {
        LOGE("can't execute GetJavaVM!");
        return FALSE;
    }

    LOGE("initialize done %p, %p, %p", mOnMixedAudioData, mOnVideoData, mpVM);

    return TRUE;
}

bool VMUtil::addJNIHostObject(JNIEnv *env, jobject obj)
{
    mJNIHost = env->NewGlobalRef(obj);

    return TRUE;
}

bool VMUtil::removeJNIHostObject(JNIEnv *env)
{
    if (mJNIHost != NULL) {

        env->DeleteGlobalRef(mJNIHost);
        mJNIHost = NULL;
    }

    return TRUE;
}

bool VMUtil::on_mixed_audio_data(int16_t const* data, int32_t length)
{
    CHECK_POINTER(mJNIHost, FALSE, "mJNIHost is NULL!");
    AttachThreadScoped ats(mpVM);
    JNIEnv *jni_env = ats.env();
    CHECK_POINTER(mOnMixedAudioData, FALSE, "mOnMixedAudioData is NULL!");

    jbyteArray retArray = jni_env->NewByteArray(length);
    void *temp = jni_env->GetPrimitiveArrayCritical(retArray, NULL);
    memcpy(temp, data, length);
    jni_env->ReleasePrimitiveArrayCritical(retArray, temp, 0);

    jni_env->CallVoidMethod(mJNIHost, mOnMixedAudioData, retArray);

    jni_env->DeleteLocalRef(retArray);

    return TRUE;
}

bool VMUtil::on_remote_video_data(
        unsigned int uid,
        uint8_t const* yBuffer, int32_t yStride,
        uint8_t const* uBuffer, int32_t uStride,
        uint8_t const* vBuffer, int32_t vStride,
        int32_t width,
        int32_t height,
        int32_t rotation)
{
    CAutoLock autoLock(mLock);

    CHECK_POINTER(mJNIHost, FALSE, "mJNIHost is NULL!");
    AttachThreadScoped ats(mpVM);
    JNIEnv *jni_env = ats.env();
    CHECK_POINTER(mOnVideoData, FALSE, "mOnVideoData is NULL!");

    jbyteArray retArray = jni_env->NewByteArray(3 * width * height / 2);
    jbyte* dst =  jni_env->GetByteArrayElements(retArray, 0);

    uint8_t *temp = (uint8_t*)dst;
    for (int i = 0; i < height; ++i) {
        memcpy(temp, yBuffer, width);
        temp += width;
        yBuffer += yStride;
    }

    for (int i = 0; i < height/2; ++i) {
        memcpy(temp, uBuffer, width/2);
        temp += width/2;
        uBuffer += uStride;
    }

    for (int i = 0; i < height/2; ++i) {
        memcpy(temp, vBuffer, width/2);
        temp += width/2;
        vBuffer += vStride;
    }

    if (mJNIHost != NULL)
        jni_env->CallVoidMethod(mJNIHost, mOnVideoData, retArray, width, height, rotation, uid);

    jni_env->ReleaseByteArrayElements(retArray, dst, JNI_ABORT);
    jni_env->DeleteLocalRef(retArray);

    //jbyteArray retArray = jni_env->NewByteArray(3 * width * height / 2);
    //jni_env->SetByteArrayRegion(retArray, 0, width * height, (jbyte*) yBuffer);
    //jni_env->SetByteArrayRegion(retArray, width * height, width * height / 4, (jbyte*) uBuffer);
    //jni_env->SetByteArrayRegion(retArray, 5 * width * height / 4, width * height / 4, (jbyte*) vBuffer);

    //if (mJNIHost != NULL)
    //    jni_env->CallVoidMethod(mJNIHost, mOnVideoData, retArray, width, height, uid);

    //jni_env->DeleteLocalRef(retArray);

    return TRUE;
}

VMUtil::VMUtil()
  : mpVM(NULL),
    mJNIHost(NULL),
    mOnMixedAudioData(NULL),
    mOnVideoData(NULL)
{

}

VMUtil::~VMUtil()
{
}