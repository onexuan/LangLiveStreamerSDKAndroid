//
// Created by lichenguang on 2018/8/23.
//
#ifndef J4A_GRAPHIC_BUFFER_WRAPPER_H
#define J4A_GRAPHIC_BUFFER_WRAPPER_H
#ifdef __cplusplus
extern "C" {
#endif
#include "j4a_base.h"

#define GRAPHIC_BUFFER_WRAPPER_PACKAGE_NAME "com/yunfan/graphicbuffer/GraphicBufferWrapper"

jlong com_yunfan_graphicbuffer_GraphicBufferWrapper__mNativeObject__get(JNIEnv *env, jobject thiz);
jlong com_yunfan_graphicbuffer_GraphicBufferWrapper__mNativeObject__get__catchAll(JNIEnv *env, jobject thiz);
void com_yunfan_graphicbuffer_GraphicBufferWrapper__mNativeObject__set(JNIEnv *env, jobject thiz, jlong value);
void com_yunfan_graphicbuffer_GraphicBufferWrapper__mNativeObject__set__catchAll(JNIEnv *env, jobject thiz, jlong value);
int loadClass__com_yunfan_graphicbuffer_GraphicBufferWrapper(JNIEnv *env);

#ifdef __cplusplus
}
#endif
#endif //J4A_GRAPHIC_BUFFER_WRAPPER_H
