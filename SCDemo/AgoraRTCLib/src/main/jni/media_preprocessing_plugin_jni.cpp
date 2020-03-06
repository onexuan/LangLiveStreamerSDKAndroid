#include <jni.h>
#include <android/log.h>
#include <cstring>
#include <string.h>
#include <map>
#include "IAgoraMediaEngine.h"
#include "IAgoraRtcEngine.h"

using namespace std;

jobject gCallBack = nullptr;
jclass gCallbackClass = nullptr;
jmethodID recordAudioMethodId = nullptr;
jmethodID playbackAudioMethodId = nullptr;
jmethodID playBeforeMixAudioMethodId = nullptr;
jmethodID mixAudioMethodId = nullptr;
jmethodID captureVideoMethodId = nullptr;
jmethodID renderVideoMethodId = nullptr;
void *_javaDirectPlayBufferCapture = nullptr;
void *_javaDirectPlayBufferRecordAudio = nullptr;
void *_javaDirectPlayBufferPlayAudio = nullptr;
void *_javaDirectPlayBufferBeforeMixAudio = nullptr;
void *_javaDirectPlayBufferMixAudio = nullptr;
map<int, void *> decodeBufferMap;

static JavaVM *gJVM = nullptr;

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

class AgoraVideoFrameObserver : public agora::media::IVideoFrameObserver {

public:
    AgoraVideoFrameObserver() {

    }

    ~AgoraVideoFrameObserver() {

    }

    void
    getVideoFrame(VideoFrame &videoFrame, _jmethodID *jmethodID, void *_byteBufferObject, unsigned int uid) {

        if (_byteBufferObject) {
            int width = videoFrame.width;
            int height = videoFrame.height;
            size_t widthAndHeight = (size_t) width * height;
            size_t length = widthAndHeight * 3 / 2;


            uint8_t const* yBuffer = (uint8_t*)videoFrame.yBuffer;
            uint8_t const* uBuffer = (uint8_t*)videoFrame.uBuffer;
            uint8_t const* vBuffer = (uint8_t*)videoFrame.vBuffer;
            int32_t yStride = videoFrame.yStride;
            int32_t uStride = videoFrame.uStride;
            int32_t vStride = videoFrame.vStride;

            uint8_t *temp = (uint8_t*)_byteBufferObject;
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

            AttachThreadScoped ats(gJVM);
            JNIEnv *env = ats.env();
            if (env == nullptr) {
                return;
            }
            if (gCallBack == nullptr || jmethodID == nullptr) {
                return;
            }

            if (uid == 0) {
                env->CallVoidMethod(gCallBack, jmethodID, videoFrame.type, width, height, length,
                                    videoFrame.yStride, videoFrame.uStride,
                                    videoFrame.vStride, videoFrame.rotation,
                                    videoFrame.renderTimeMs);
            } else {
                env->CallVoidMethod(gCallBack, jmethodID, uid, videoFrame.type, width, height,
                                    length,
                                    videoFrame.yStride, videoFrame.uStride,
                                    videoFrame.vStride, videoFrame.rotation,
                                    videoFrame.renderTimeMs);
            }
        }

    }

    void writebackVideoFrame(VideoFrame &videoFrame, void *byteBuffer) {
        if (byteBuffer == nullptr) {
            return;
        }

        int width = videoFrame.width;
        int height = videoFrame.height;
        size_t widthAndHeight = (size_t) videoFrame.yStride * height;

        memcpy(videoFrame.yBuffer, byteBuffer, widthAndHeight);
        memcpy(videoFrame.uBuffer, (uint8_t *) byteBuffer + widthAndHeight, widthAndHeight / 4);
        memcpy(videoFrame.vBuffer, (uint8_t *) byteBuffer + widthAndHeight * 5 / 4,
               widthAndHeight / 4);
    }

public:
    virtual bool onCaptureVideoFrame(VideoFrame &videoFrame) override {
        getVideoFrame(videoFrame, captureVideoMethodId, _javaDirectPlayBufferCapture, 0);
        return true;
    }

    virtual bool onRenderVideoFrame(unsigned int uid, VideoFrame &videoFrame) override {
        map<int, void *>::iterator it_find;
        it_find = decodeBufferMap.find(uid);

        if (it_find != decodeBufferMap.end()) {
            if (it_find->second != nullptr) {
                getVideoFrame(videoFrame, renderVideoMethodId, it_find->second, uid);
            }
        }

        return true;
    }

};


class AgoraAudioFrameObserver : public agora::media::IAudioFrameObserver {

public:
    AgoraAudioFrameObserver() {
        gCallBack = nullptr;
    }

    ~AgoraAudioFrameObserver() {
    }

    void getAudioFrame(AudioFrame &audioFrame, _jmethodID *jmethodID, void *_byteBufferObject, unsigned int uid) {
        if (_byteBufferObject) {
            AttachThreadScoped ats(gJVM);
            JNIEnv *env = ats.env();
            if (env == nullptr) {
                return;
            }
            if (gCallBack == nullptr || jmethodID == nullptr) {
                return;
            }
            int len = audioFrame.samples * audioFrame.bytesPerSample * audioFrame.channels;
            memcpy(_byteBufferObject, audioFrame.buffer, (size_t) len); // * sizeof(int16_t)

            if (uid == 0) {
                env->CallVoidMethod(gCallBack, jmethodID, audioFrame.type, audioFrame.samples,
                                    audioFrame.bytesPerSample,
                                    audioFrame.channels, audioFrame.samplesPerSec,
                                    audioFrame.renderTimeMs, len);
            } else {
                env->CallVoidMethod(gCallBack, jmethodID, uid, audioFrame.type, audioFrame.samples,
                                    audioFrame.bytesPerSample,
                                    audioFrame.channels, audioFrame.samplesPerSec,
                                    audioFrame.renderTimeMs, len);
            }
        }

    }

    void writebackAudioFrame(AudioFrame &audioFrame, void *byteBuffer) {
        if (byteBuffer == nullptr) {
            return;
        }

        int len = audioFrame.samples * audioFrame.bytesPerSample * audioFrame.channels;
        memcpy(audioFrame.buffer, byteBuffer, (size_t) len);
    }

public:
    virtual bool onRecordAudioFrame(AudioFrame &audioFrame) override {
        getAudioFrame(audioFrame, recordAudioMethodId, _javaDirectPlayBufferRecordAudio, 0);
        writebackAudioFrame(audioFrame, _javaDirectPlayBufferRecordAudio);
        return true;
    }

    virtual bool onPlaybackAudioFrame(AudioFrame &audioFrame) override {
        getAudioFrame(audioFrame, playbackAudioMethodId, _javaDirectPlayBufferPlayAudio, 0);
        writebackAudioFrame(audioFrame, _javaDirectPlayBufferPlayAudio);
        return true;
    }

    virtual bool
    onPlaybackAudioFrameBeforeMixing(unsigned int uid, AudioFrame &audioFrame) override {
        getAudioFrame(audioFrame, playBeforeMixAudioMethodId, _javaDirectPlayBufferBeforeMixAudio, uid);
        writebackAudioFrame(audioFrame, _javaDirectPlayBufferBeforeMixAudio);
        return true;
    }

    virtual bool onMixedAudioFrame(AudioFrame &audioFrame) override {
        getAudioFrame(audioFrame, mixAudioMethodId, _javaDirectPlayBufferMixAudio, 0);
        writebackAudioFrame(audioFrame, _javaDirectPlayBufferMixAudio);
        return true;
    }
};


static AgoraAudioFrameObserver s_audioFrameObserver;
static AgoraVideoFrameObserver s_videoFrameObserver;
static agora::rtc::IRtcEngine *rtcEngine = nullptr;

#ifdef __cplusplus
extern "C" {
#endif

int __attribute__((visibility("default")))
loadAgoraRtcEnginePlugin(agora::rtc::IRtcEngine *engine) {
    __android_log_print(ANDROID_LOG_DEBUG, "apm-rtc-media-preprocessing", "loadAgoraRtcEnginePlugin");
    rtcEngine = engine;
    return 0;
}

void __attribute__((visibility("default")))
unloadAgoraRtcEnginePlugin(agora::rtc::IRtcEngine *engine) {
    __android_log_print(ANDROID_LOG_DEBUG, "apm-rtc-media-preprocessing", "unloadAgoraRtcEnginePlugin");

    rtcEngine = nullptr;
}

#ifdef __cplusplus
}
#endif

void setCallback(JNIEnv *env, jobject obj, jobject callback) {
    if (!rtcEngine) return;

    env->GetJavaVM(&gJVM);

    agora::util::AutoPtr<agora::media::IMediaEngine> mediaEngine;
    mediaEngine.queryInterface(rtcEngine, agora::INTERFACE_ID_TYPE::AGORA_IID_MEDIA_ENGINE);
    if (mediaEngine) {
        mediaEngine->registerVideoFrameObserver(&s_videoFrameObserver);
        mediaEngine->registerAudioFrameObserver(&s_audioFrameObserver);
    }

    if (gCallBack == nullptr) {
        gCallBack = env->NewGlobalRef(callback);
        gCallbackClass = env->GetObjectClass(gCallBack);

        recordAudioMethodId = env->GetMethodID(gCallbackClass, "onRecordAudioFrame", "(IIIIIJI)V");
        playbackAudioMethodId = env->GetMethodID(gCallbackClass, "onPlaybackAudioFrame",
                                                 "(IIIIIJI)V");
        playBeforeMixAudioMethodId = env->GetMethodID(gCallbackClass,
                                                      "onPlaybackAudioFrameBeforeMixing",
                                                      "(IIIIIIJI)V");
        mixAudioMethodId = env->GetMethodID(gCallbackClass, "onMixedAudioFrame", "(IIIIIJI)V");

        captureVideoMethodId = env->GetMethodID(gCallbackClass, "onCaptureVideoFrame",
                                                "(IIIIIIIIJ)V");
        renderVideoMethodId = env->GetMethodID(gCallbackClass, "onRenderVideoFrame",
                                               "(IIIIIIIIIJ)V");

        __android_log_print(ANDROID_LOG_DEBUG, "apm-rtc-media-preprocessing", "setCallback done successfully");
    }
}

void setVideoCaptureByteBuffer(JNIEnv *env, jobject obj, jobject bytebuffer) {
    _javaDirectPlayBufferCapture = env->GetDirectBufferAddress(bytebuffer);
}

void setAudioRecordByteBuffer(JNIEnv *env, jobject obj, jobject bytebuffer) {
    _javaDirectPlayBufferRecordAudio = env->GetDirectBufferAddress(bytebuffer);
}

void setAudioPlayByteBuffer(JNIEnv *env, jobject obj, jobject bytebuffer) {
    _javaDirectPlayBufferPlayAudio = env->GetDirectBufferAddress(bytebuffer);
}

void setBeforeAudioMixByteBuffer(JNIEnv *env, jobject obj, jobject bytebuffer) {
    _javaDirectPlayBufferBeforeMixAudio = env->GetDirectBufferAddress(bytebuffer);
}

void setAudioMixByteBuffer(JNIEnv *env, jobject obj, jobject bytebuffer) {
    _javaDirectPlayBufferMixAudio = env->GetDirectBufferAddress(bytebuffer);
}

void setVideoDecodeByteBuffer(JNIEnv *env, jobject obj, jint uid, jobject byteBuffer) {
    if (byteBuffer == nullptr) {
        decodeBufferMap.erase(uid);
    } else {
        void *_javaDirectDecodeBuffer = env->GetDirectBufferAddress(byteBuffer);
        decodeBufferMap.insert(make_pair(uid, _javaDirectDecodeBuffer));
        __android_log_print(ANDROID_LOG_DEBUG, "apm-rtc-media-preprocessing",
                            "setVideoDecodeByteBuffer uid: %u, _javaDirectDecodeBuffer: %p",
                            uid, _javaDirectDecodeBuffer);
    }
}

void releasePoint(JNIEnv *env, jobject obj) {
    agora::util::AutoPtr<agora::media::IMediaEngine> mediaEngine;
    mediaEngine.queryInterface(rtcEngine, agora::INTERFACE_ID_TYPE::AGORA_IID_MEDIA_ENGINE);

    if (mediaEngine) {
        mediaEngine->registerVideoFrameObserver(NULL);
        mediaEngine->registerAudioFrameObserver(NULL);
    }

    if (gCallBack != nullptr) {
        env->DeleteGlobalRef(gCallBack);
        gCallBack = nullptr;
    }
    gCallbackClass = nullptr;

    recordAudioMethodId = nullptr;
    playbackAudioMethodId = nullptr;
    playBeforeMixAudioMethodId = nullptr;
    mixAudioMethodId = nullptr;
    captureVideoMethodId = nullptr;
    renderVideoMethodId = nullptr;

    _javaDirectPlayBufferCapture = nullptr;
    _javaDirectPlayBufferRecordAudio = nullptr;
    _javaDirectPlayBufferPlayAudio = nullptr;
    _javaDirectPlayBufferBeforeMixAudio = nullptr;
    _javaDirectPlayBufferMixAudio = nullptr;

    decodeBufferMap.clear();
}

#define RTCLIB_PACKAGE_NAME "net/lang/rtclib/MediaPreProcessingNative"

static JNINativeMethod gMethods[] = {
        { "setCallback", "(Lnet/lang/rtclib/IRTCFrameListener;)V", (void *)setCallback },
        { "setVideoCaptureByteBuffer", "(Ljava/nio/ByteBuffer;)V", (void *)setVideoCaptureByteBuffer },
        { "setAudioRecordByteBuffer", "(Ljava/nio/ByteBuffer;)V", (void *)setAudioRecordByteBuffer },
        { "setAudioPlayByteBuffer", "(Ljava/nio/ByteBuffer;)V", (void *)setAudioPlayByteBuffer },
        { "setBeforeAudioMixByteBuffer", "(Ljava/nio/ByteBuffer;)V", (void *)setBeforeAudioMixByteBuffer },
        { "setAudioMixByteBuffer", "(Ljava/nio/ByteBuffer;)V", (void *)setAudioMixByteBuffer },
        { "setVideoDecodeByteBuffer", "(ILjava/nio/ByteBuffer;)V", (void *)setVideoDecodeByteBuffer },
        { "releasePoint", "()V", (void *)releasePoint}
};

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    JNIEnv *env = NULL;
    __android_log_print(ANDROID_LOG_DEBUG, "apm-rtc-media-preprocessing", "JNI_Onload! on %d bit", (int )(sizeof(long) * 8));

    if (vm->GetEnv((void **)&env, JNI_VERSION_1_6) != JNI_OK) {
        __android_log_print(ANDROID_LOG_ERROR, "apm-rtc-media-preprocessing", "Get env failed");
        return __LINE__;
    }

    jclass clazz = env->FindClass(RTCLIB_PACKAGE_NAME);
    if (!clazz) {
        __android_log_print(ANDROID_LOG_ERROR, "apm-rtc-media-preprocessing", "Not found class %s", RTCLIB_PACKAGE_NAME);
        return __LINE__;
    }

    if (env->RegisterNatives(clazz, gMethods, sizeof(gMethods)/sizeof(gMethods[0]))) {
        __android_log_print(ANDROID_LOG_ERROR, "apm-rtc-media-preprocessing", "Register native methods failed.");
        return __LINE__;
    }

    return JNI_VERSION_1_6;
}
