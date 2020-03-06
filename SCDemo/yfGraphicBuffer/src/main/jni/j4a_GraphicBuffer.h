//
// Created by lichenguang on 2018/8/23.
//

#ifndef J4A_GRAPHIC_BUFFER_H
#define J4A_GRAPHIC_BUFFER_H

#ifdef __cplusplus
extern "C" {
#endif
#include "j4a_base.h"

jlong android_view_GraphicBuffer__mNativeObject__get(JNIEnv *env, jobject thiz);
jlong android_view_GraphicBuffer__mNativeObject__get__catchAll(JNIEnv *env, jobject thiz);
void android_view_GraphicBuffer__mNativeObject__set(JNIEnv *env, jobject thiz, jlong value);
void android_view_GraphicBuffer__mNativeObject__set__catchAll(JNIEnv *env, jobject thiz, jlong value);
jobject android_view_GraphicBuffer__create(JNIEnv *env, jint width, jint height, jint format, jint usage);
jobject android_view_GraphicBuffer__create__catchAll(JNIEnv *env, jint width, jint height, jint format, jint usage);
jobject android_view_GraphicBuffer__create__asGlobalRef__catchAll(JNIEnv *env, jint width, jint height, jint format, jint usage);
void android_view_GraphicBuffer__writeToParcel(JNIEnv *env, jobject thiz, jobject obj, jint flag);
void android_view_GraphicBuffer__writeToParcel__catchAll(JNIEnv *env, jobject thiz, jobject obj, jint flag);
jobject android_view_GraphicBuffer__lockCanvas(JNIEnv *env, jobject thiz);
jobject android_view_GraphicBuffer__lockCanvas__catchAll(JNIEnv *env, jobject thiz);
jobject android_view_GraphicBuffer__lockCanvas__asGlobalRef__catchAll(JNIEnv *env, jobject thiz);
void android_view_GraphicBuffer__unlockCanvasAndPost(JNIEnv *env, jobject thiz, jobject canvas);
void android_view_GraphicBuffer__unlockCanvasAndPost__catchAll(JNIEnv *env, jobject thiz, jobject canvas);
int loadClass__android_view_GraphicBuffer(JNIEnv *env);
#ifdef __cplusplus
}
#endif

#endif //J4A_GRAPHIC_BUFFER_H
