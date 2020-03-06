//
// Created by lichenguang on 2018/8/23.
//
#ifndef J4A_PARCEL_H
#define J4A_PARCEL_H
#ifdef __cplusplus
extern "C" {
#endif
#include "j4a_base.h"

jobject android_os_Parcel__obtain(JNIEnv *env);
jobject android_os_Parcel__obtain__catchAll(JNIEnv *env);
jobject android_os_Parcel__obtain__asGlobalRef__catchAll(JNIEnv *env);
jint android_os_Parcel__readInt(JNIEnv *env, jobject thiz);
jint android_os_Parcel__readInt__catchAll(JNIEnv *env, jobject thiz);
void android_os_Parcel__setDataPosition(JNIEnv *env, jobject thiz, jint pos);
void android_os_Parcel__setDataPosition__catchAll(JNIEnv *env, jobject thiz, jint pos);
jint android_os_Parcel__dataSize(JNIEnv *env, jobject thiz);
jint android_os_Parcel__dataSize__catchAll(JNIEnv *env, jobject thiz);
jint android_os_Parcel__dataAvail(JNIEnv *env, jobject thiz);
jint android_os_Parcel__dataAvail__catchAll(JNIEnv *env, jobject thiz);
jint android_os_Parcel__dataPosition(JNIEnv *env, jobject thiz);
jint android_os_Parcel__dataPosition__catchAll(JNIEnv *env, jobject thiz);
jint android_os_Parcel__dataCapacity(JNIEnv *env, jobject thiz);
jint android_os_Parcel__dataCapacity__catchAll(JNIEnv *env, jobject thiz);
void android_os_Parcel__setDataSize(JNIEnv *env, jobject thiz, jint size);
void android_os_Parcel__setDataSize__catchAll(JNIEnv *env, jobject thiz, jint size);
int loadClass__android_os_Parcel(JNIEnv *env);
#ifdef __cplusplus
}
#endif
#endif //J4A_PARCEL_H
