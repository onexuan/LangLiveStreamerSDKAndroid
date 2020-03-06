#include <android/log.h>
#include <stdlib.h>
#include <string.h>
#include <limits.h>
#include <assert.h>
//#include <algorithm>
#include <IAgoraMediaEngine.h>
#include <IAgoraRtcEngine.h>
#include "audio_video_preprocessing_plugin.h"
#include "vmutil.h"

#define ALOGD(...) ((void)__android_log_print(ANDROID_LOG_DEBUG, "libav_preprocessing", __VA_ARGS__))
#define ALOGI(...) ((void)__android_log_print(ANDROID_LOG_INFO , "libav_preprocessing", __VA_ARGS__))
#define ALOGW(...) ((void)__android_log_print(ANDROID_LOG_WARN , "libav_preprocessing", __VA_ARGS__))
#define ALOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, "libav_preprocessing", __VA_ARGS__))

static uid_t remoteUid = -1;

static const int maxPicSize = 1920 * 1080; // suppose it's 1920 * 1080

static agora::media::IVideoFrameObserver::VideoFrame remoteFrame;

static uint8_t* remoteY;
static uint8_t* remoteU;
static uint8_t* remoteV;

static uint8_t* rotateY;
static uint8_t* rotateU;
static uint8_t* rotateV;

static uint8_t* localRotatedY;
static uint8_t* localRotatedU;
static uint8_t* localRotatedV;

static bool hasRemoteVideo = false;

class AgoraAudioFrameObserver : public agora::media::IAudioFrameObserver
{
public:
    virtual bool onRecordAudioFrame(AudioFrame& audioFrame) override
    {
        return true;
    }

    virtual bool onPlaybackAudioFrame(AudioFrame& audioFrame) override
    {
        return true;
    }

    virtual bool onMixedAudioFrame(AudioFrame& audioFrame) override
    {
        VMUtil::instance().on_mixed_audio_data((short*) audioFrame.buffer, audioFrame.samples * 2);
        return true;

    }

    virtual bool onPlaybackAudioFrameBeforeMixing(unsigned int uid, AudioFrame& audioFrame) override
    {
        return true;
    }
};

class AgoraVideoFrameObserver : public agora::media::IVideoFrameObserver
{
public:
    virtual bool onCaptureVideoFrame(VideoFrame& videoFrame) override
    {
        return true;
    }

    virtual bool onRenderVideoFrame(unsigned int uid, VideoFrame& videoFrame) override
    {
        VMUtil::instance().on_remote_video_data(uid,
                                                (uint8_t *)videoFrame.yBuffer,
                                                videoFrame.yStride,
                                                (uint8_t *)videoFrame.uBuffer,
                                                videoFrame.uStride,
                                                (uint8_t *)videoFrame.vBuffer,
                                                videoFrame.vStride,
                                                videoFrame.width,
                                                videoFrame.height,
                                                videoFrame.rotation);
        return true;
    }
};

static AgoraAudioFrameObserver s_audioFrameObserver;
static AgoraVideoFrameObserver s_videoFrameObserver;
static agora::rtc::IRtcEngine* rtcEngine = NULL;

#ifdef __cplusplus
extern "C" {
#endif

int __attribute__((visibility("default"))) loadAgoraRtcEnginePlugin(agora::rtc::IRtcEngine* engine)
{
    rtcEngine = engine;
    return 0;
}

void __attribute__((visibility("default"))) unloadAgoraRtcEnginePlugin(agora::rtc::IRtcEngine* engine)
{
    rtcEngine = NULL;
}
#ifdef __cplusplus
}
#endif

void JNICALL doRegisterPreProcessing(JNIEnv *env, jobject obj)
{
    if (!rtcEngine)
        return;

    agora::util::AutoPtr<agora::media::IMediaEngine> mediaEngine;
    mediaEngine.queryInterface(rtcEngine, agora::rtc::AGORA_IID_MEDIA_ENGINE);
    if (mediaEngine) {
        mediaEngine->registerVideoFrameObserver(&s_videoFrameObserver);
        mediaEngine->registerAudioFrameObserver(&s_audioFrameObserver);
    }

    VMUtil::instance().addJNIHostObject(env, obj);
}

void JNICALL doDeregisterPreProcessing(JNIEnv *env, jobject obj)
{
    agora::util::AutoPtr<agora::media::IMediaEngine> mediaEngine;
    mediaEngine.queryInterface(rtcEngine, agora::rtc::AGORA_IID_MEDIA_ENGINE);
    if (mediaEngine) {
        mediaEngine->registerVideoFrameObserver(NULL);
        mediaEngine->registerAudioFrameObserver(NULL);
    }

    VMUtil::instance().removeJNIHostObject(env);
}

static JNINativeMethod libav_preprocessing_methods[] = {
    { "doRegisterPreProcessing", "()V", (void *)doRegisterPreProcessing },
    { "doDeregisterPreProcessing", "()V", (void *)doDeregisterPreProcessing },
};

#define LIBAV_PREPROCESSING_ARRAY_ELEMS(a)  (sizeof(a) / sizeof(a[0]))
#define LIBAV_PREPROCESSING_CLASS_NAME      "net/lang/streamer/rtc/io/agora/ex/AudioVideoPreProcessing"

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved)
{
    JNIEnv *env = NULL;

    jint result = -1;

    if (vm->GetEnv((void**) &env, JNI_VERSION_1_4) != JNI_OK) {
        ALOGE("ERROR: GetEnv failed\n");
        return result;
    }

    assert(env != NULL);

    VMUtil::instance().initialize(env);

    jclass clz = env->FindClass(LIBAV_PREPROCESSING_CLASS_NAME);
    if (clz == NULL) {
        ALOGE("Class \"AudioVideoPreProcessing\" not found");
        return JNI_ERR;
    }

    if (env->RegisterNatives(clz, libav_preprocessing_methods, LIBAV_PREPROCESSING_ARRAY_ELEMS(libav_preprocessing_methods))) {
        ALOGE("methods for AudioVideoPreProcessing not registered");
        return JNI_ERR;
    }

    /* success -- return valid version number */
    result = JNI_VERSION_1_6;

    return result;
}