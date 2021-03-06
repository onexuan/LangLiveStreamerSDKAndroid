//
// Created by lichenguang on 2018/8/23.
//

#include "j4a_Parcel.h"

typedef struct android_os_Parcel {
    jclass id;

    jmethodID method_obtain;
    jmethodID method_readInt;
    jmethodID method_setDataPosition;
    jmethodID method_dataSize;
    jmethodID method_dataAvail;
    jmethodID method_dataPosition;
    jmethodID method_dataCapacity;
    jmethodID method_setDataSize;
} android_os_Parcel;
static android_os_Parcel class_android_os_Parcel;

jobject android_os_Parcel__obtain(JNIEnv *env)
{
    return (*env)->CallStaticObjectMethod(env, class_android_os_Parcel.id, class_android_os_Parcel.method_obtain);
}

jobject android_os_Parcel__obtain__catchAll(JNIEnv *env)
{
    jobject ret_object = android_os_Parcel__obtain(env);
    if (J4A_ExceptionCheck__catchAll(env) || !ret_object) {
        return NULL;
    }

    return ret_object;
}

jobject android_os_Parcel__obtain__asGlobalRef__catchAll(JNIEnv *env)
{
    jobject ret_object   = NULL;
    jobject local_object = android_os_Parcel__obtain__catchAll(env);
    if (J4A_ExceptionCheck__catchAll(env) || !local_object) {
        ret_object = NULL;
        goto fail;
    }

    ret_object = J4A_NewGlobalRef__catchAll(env, local_object);
    if (!ret_object) {
        ret_object = NULL;
        goto fail;
    }

    fail:
    J4A_DeleteLocalRef__p(env, &local_object);
    return ret_object;
}

jint android_os_Parcel__readInt(JNIEnv *env, jobject thiz)
{
    return (*env)->CallIntMethod(env, thiz, class_android_os_Parcel.method_readInt);
}

jint android_os_Parcel__readInt__catchAll(JNIEnv *env, jobject thiz)
{
    jint ret_value = android_os_Parcel__readInt(env, thiz);
    if (J4A_ExceptionCheck__catchAll(env)) {
        return 0;
    }

    return ret_value;
}

void android_os_Parcel__setDataPosition(JNIEnv *env, jobject thiz, jint pos)
{
    (*env)->CallVoidMethod(env, thiz, class_android_os_Parcel.method_setDataPosition, pos);
}

void android_os_Parcel__setDataPosition__catchAll(JNIEnv *env, jobject thiz, jint pos)
{
    android_os_Parcel__setDataPosition(env, thiz, pos);
    J4A_ExceptionCheck__catchAll(env);
}

jint android_os_Parcel__dataSize(JNIEnv *env, jobject thiz)
{
    return (*env)->CallIntMethod(env, thiz, class_android_os_Parcel.method_dataSize);
}

jint android_os_Parcel__dataSize__catchAll(JNIEnv *env, jobject thiz)
{
    jint ret_value = android_os_Parcel__dataSize(env, thiz);
    if (J4A_ExceptionCheck__catchAll(env)) {
        return 0;
    }

    return ret_value;
}

jint android_os_Parcel__dataAvail(JNIEnv *env, jobject thiz)
{
    return (*env)->CallIntMethod(env, thiz, class_android_os_Parcel.method_dataAvail);
}

jint android_os_Parcel__dataAvail__catchAll(JNIEnv *env, jobject thiz)
{
    jint ret_value = android_os_Parcel__dataAvail(env, thiz);
    if (J4A_ExceptionCheck__catchAll(env)) {
        return 0;
    }

    return ret_value;
}

jint android_os_Parcel__dataPosition(JNIEnv *env, jobject thiz)
{
    return (*env)->CallIntMethod(env, thiz, class_android_os_Parcel.method_dataPosition);
}

jint android_os_Parcel__dataPosition__catchAll(JNIEnv *env, jobject thiz)
{
    jint ret_value = android_os_Parcel__dataPosition(env, thiz);
    if (J4A_ExceptionCheck__catchAll(env)) {
        return 0;
    }

    return ret_value;
}

jint android_os_Parcel__dataCapacity(JNIEnv *env, jobject thiz)
{
    return (*env)->CallIntMethod(env, thiz, class_android_os_Parcel.method_dataCapacity);
}

jint android_os_Parcel__dataCapacity__catchAll(JNIEnv *env, jobject thiz)
{
    jint ret_value = android_os_Parcel__dataCapacity(env, thiz);
    if (J4A_ExceptionCheck__catchAll(env)) {
        return 0;
    }

    return ret_value;
}

void android_os_Parcel__setDataSize(JNIEnv *env, jobject thiz, jint size)
{
    (*env)->CallVoidMethod(env, thiz, class_android_os_Parcel.method_setDataSize, size);
}

void android_os_Parcel__setDataSize__catchAll(JNIEnv *env, jobject thiz, jint size)
{
    android_os_Parcel__setDataSize(env, thiz, size);
    J4A_ExceptionCheck__catchAll(env);
}

int loadClass__android_os_Parcel(JNIEnv *env)
{
    int         ret                   = -1;
    const char *J4A_UNUSED(name)      = NULL;
    const char *J4A_UNUSED(sign)      = NULL;
    jclass      J4A_UNUSED(class_id)  = NULL;
    int         J4A_UNUSED(api_level) = 0;

    if (class_android_os_Parcel.id != NULL)
        return 0;

    sign = "android/os/Parcel";
    class_android_os_Parcel.id = J4A_FindClass__asGlobalRef__catchAll(env, sign);
    if (class_android_os_Parcel.id == NULL)
        goto fail;

    class_id = class_android_os_Parcel.id;
    name     = "obtain";
    sign     = "()Landroid/os/Parcel;";
    class_android_os_Parcel.method_obtain = J4A_GetStaticMethodID__catchAll(env, class_id, name, sign);
    if (class_android_os_Parcel.method_obtain == NULL)
        goto fail;

    class_id = class_android_os_Parcel.id;
    name     = "readInt";
    sign     = "()I";
    class_android_os_Parcel.method_readInt = J4A_GetMethodID__catchAll(env, class_id, name, sign);
    if (class_android_os_Parcel.method_readInt == NULL)
        goto fail;

    class_id = class_android_os_Parcel.id;
    name     = "setDataPosition";
    sign     = "(I)V";
    class_android_os_Parcel.method_setDataPosition = J4A_GetMethodID__catchAll(env, class_id, name, sign);
    if (class_android_os_Parcel.method_setDataPosition == NULL)
        goto fail;

    class_id = class_android_os_Parcel.id;
    name     = "dataSize";
    sign     = "()I";
    class_android_os_Parcel.method_dataSize = J4A_GetMethodID__catchAll(env, class_id, name, sign);
    if (class_android_os_Parcel.method_dataSize == NULL)
        goto fail;

    class_id = class_android_os_Parcel.id;
    name     = "dataAvail";
    sign     = "()I";
    class_android_os_Parcel.method_dataAvail = J4A_GetMethodID__catchAll(env, class_id, name, sign);
    if (class_android_os_Parcel.method_dataAvail == NULL)
        goto fail;

    class_id = class_android_os_Parcel.id;
    name     = "dataPosition";
    sign     = "()I";
    class_android_os_Parcel.method_dataPosition = J4A_GetMethodID__catchAll(env, class_id, name, sign);
    if (class_android_os_Parcel.method_dataPosition == NULL)
        goto fail;

    class_id = class_android_os_Parcel.id;
    name     = "dataCapacity";
    sign     = "()I";
    class_android_os_Parcel.method_dataCapacity = J4A_GetMethodID__catchAll(env, class_id, name, sign);
    if (class_android_os_Parcel.method_dataCapacity == NULL)
        goto fail;

    class_id = class_android_os_Parcel.id;
    name     = "setDataSize";
    sign     = "(I)V";
    class_android_os_Parcel.method_setDataSize = J4A_GetMethodID__catchAll(env, class_id, name, sign);
    if (class_android_os_Parcel.method_setDataSize == NULL)
        goto fail;

    J4A_ALOGD("J4ALoader: OK: '%s' loaded\n", "android.os.Parcel");
    ret = 0;
    fail:
    return ret;
}
