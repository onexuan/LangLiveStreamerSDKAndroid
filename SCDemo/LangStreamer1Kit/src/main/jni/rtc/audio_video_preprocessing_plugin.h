#include <jni.h>
#ifndef audio_video_preprocessing_plugin_h_
#define audio_video_preprocessing_plugin_h_
#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT void JNICALL doRegisterPreProcessing(JNIEnv *env, jobject obj);

JNIEXPORT void JNICALL doDeregisterPreProcessing(JNIEnv *env, jobject obj);

#ifdef __cplusplus
}
#endif
#endif // audio_video_preprocessing_plugin_h_