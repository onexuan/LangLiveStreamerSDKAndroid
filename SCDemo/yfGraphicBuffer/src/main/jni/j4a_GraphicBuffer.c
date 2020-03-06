//
// Created by lichenguang on 2018/8/23.
//
#include "j4a_GraphicBuffer.h"
#include <stdlib.h>

typedef struct android_view_GraphicBuffer {
    jclass id;

    jfieldID field_mNativeObject;
    jmethodID method_create;
    jmethodID method_writeToParcel;
    jmethodID method_lockCanvas;
    jmethodID method_unlockCanvasAndPost;
} android_view_GraphicBuffer;
static android_view_GraphicBuffer class_android_view_GraphicBuffer;

jlong android_view_GraphicBuffer__mNativeObject__get(JNIEnv *env, jobject thiz)
{
    return (*env)->GetLongField(env, thiz, class_android_view_GraphicBuffer.field_mNativeObject);
}

jlong android_view_GraphicBuffer__mNativeObject__get__catchAll(JNIEnv *env, jobject thiz)
{
    jlong ret_value = android_view_GraphicBuffer__mNativeObject__get(env, thiz);
    if (J4A_ExceptionCheck__catchAll(env)) {
        return 0;
    }

    return ret_value;
}

void android_view_GraphicBuffer__mNativeObject__set(JNIEnv *env, jobject thiz, jlong value)
{
    (*env)->SetLongField(env, thiz, class_android_view_GraphicBuffer.field_mNativeObject, value);
}

void android_view_GraphicBuffer__mNativeObject__set__catchAll(JNIEnv *env, jobject thiz, jlong value)
{
    android_view_GraphicBuffer__mNativeObject__set(env, thiz, value);
    J4A_ExceptionCheck__catchAll(env);
}

jobject android_view_GraphicBuffer__create(JNIEnv *env, jint width, jint height, jint format, jint usage)
{
    return (*env)->CallStaticObjectMethod(env, class_android_view_GraphicBuffer.id, class_android_view_GraphicBuffer.method_create, width, height, format, usage);
}

jobject android_view_GraphicBuffer__create__catchAll(JNIEnv *env, jint width, jint height, jint format, jint usage)
{
    jobject ret_object = android_view_GraphicBuffer__create(env, width, height, format, usage);
    if (J4A_ExceptionCheck__catchAll(env) || !ret_object) {
        return NULL;
    }

    return ret_object;
}

jobject android_view_GraphicBuffer__create__asGlobalRef__catchAll(JNIEnv *env, jint width, jint height, jint format, jint usage)
{
    jobject ret_object   = NULL;
    jobject local_object = android_view_GraphicBuffer__create__catchAll(env, width, height, format, usage);
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

void android_view_GraphicBuffer__writeToParcel(JNIEnv *env, jobject thiz, jobject obj, jint flag)
{
    (*env)->CallVoidMethod(env, thiz, class_android_view_GraphicBuffer.method_writeToParcel, obj, flag);
}

void android_view_GraphicBuffer__writeToParcel__catchAll(JNIEnv *env, jobject thiz, jobject obj, jint flag)
{
    android_view_GraphicBuffer__writeToParcel(env, thiz, obj, flag);
    J4A_ExceptionCheck__catchAll(env);
}

jobject android_view_GraphicBuffer__lockCanvas(JNIEnv *env, jobject thiz)
{
    return (*env)->CallObjectMethod(env, thiz, class_android_view_GraphicBuffer.method_lockCanvas);
}

jobject android_view_GraphicBuffer__lockCanvas__catchAll(JNIEnv *env, jobject thiz)
{
    jobject ret_object = android_view_GraphicBuffer__lockCanvas(env, thiz);
    if (J4A_ExceptionCheck__catchAll(env) || !ret_object) {
        return NULL;
    }

    return ret_object;
}

jobject android_view_GraphicBuffer__lockCanvas__asGlobalRef__catchAll(JNIEnv *env, jobject thiz)
{
    jobject ret_object   = NULL;
    jobject local_object = android_view_GraphicBuffer__lockCanvas__catchAll(env, thiz);
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

void android_view_GraphicBuffer__unlockCanvasAndPost(JNIEnv *env, jobject thiz, jobject canvas)
{
    (*env)->CallVoidMethod(env, thiz, class_android_view_GraphicBuffer.method_unlockCanvasAndPost, canvas);
}

void android_view_GraphicBuffer__unlockCanvasAndPost__catchAll(JNIEnv *env, jobject thiz, jobject canvas)
{
    android_view_GraphicBuffer__unlockCanvasAndPost(env, thiz, canvas);
    J4A_ExceptionCheck__catchAll(env);
}

int loadClass__android_view_GraphicBuffer(JNIEnv *env)
{
    int         ret                   = -1;
    const char *J4A_UNUSED(name)      = NULL;
    const char *J4A_UNUSED(sign)      = NULL;
    jclass      J4A_UNUSED(class_id)  = NULL;
    int         J4A_UNUSED(api_level) = 0;

    if (class_android_view_GraphicBuffer.id != NULL)
        return 0;

    sign = "android/view/GraphicBuffer";
    class_android_view_GraphicBuffer.id = J4A_FindClass__asGlobalRef__catchAll(env, sign);
    if (class_android_view_GraphicBuffer.id == NULL) {
        J4A_ALOGE("%s: find class GraphicBuffer error.", __func__);
        goto fail;
    }

    class_id = class_android_view_GraphicBuffer.id;
    name     = "mNativeObject";
    sign     = "J";
    class_android_view_GraphicBuffer.field_mNativeObject = J4A_GetFieldID__catchAll(env, class_id, name, sign);
    if (class_android_view_GraphicBuffer.field_mNativeObject == NULL) {
        sign = "I";
        class_android_view_GraphicBuffer.field_mNativeObject = J4A_GetFieldID__catchAll(env, class_id, name, sign);
        if (class_android_view_GraphicBuffer.field_mNativeObject == NULL) {
            J4A_ALOGE("%s: find mNativeObject in android/view/GraphicBuffer error.", __func__);
            goto fail;
        }
    }

    class_id = class_android_view_GraphicBuffer.id;
    name     = "create";
    sign     = "(IIII)Landroid/view/GraphicBuffer;";
    class_android_view_GraphicBuffer.method_create = J4A_GetStaticMethodID__catchAll(env, class_id, name, sign);
    if (class_android_view_GraphicBuffer.method_create == NULL) {
        J4A_ALOGE("%s: find create method in android/view/GraphicBuffer failed.", __func__);
        goto fail;
    }

    class_id = class_android_view_GraphicBuffer.id;
    name     = "writeToParcel";
    sign     = "(Landroid/os/Parcel;I)V";
    class_android_view_GraphicBuffer.method_writeToParcel = J4A_GetMethodID__catchAll(env, class_id, name, sign);
    if (class_android_view_GraphicBuffer.method_writeToParcel == NULL) {
        J4A_ALOGE("%s: find writeToParcel method in android/view/GraphicBuffer failed.", __func__);
        goto fail;
    }

    class_id = class_android_view_GraphicBuffer.id;
    name     = "lockCanvas";
    sign     = "()Landroid/graphics/Canvas;";
    class_android_view_GraphicBuffer.method_lockCanvas = J4A_GetMethodID__catchAll(env, class_id, name, sign);
    if (class_android_view_GraphicBuffer.method_lockCanvas == NULL) {
        J4A_ALOGE("find lockCanvas method in android/view/GraphicBuffer failed.");
        goto fail;
    }

    class_id = class_android_view_GraphicBuffer.id;
    name     = "unlockCanvasAndPost";
    sign     = "(Landroid/graphics/Canvas;)V";
    class_android_view_GraphicBuffer.method_unlockCanvasAndPost = J4A_GetMethodID__catchAll(env, class_id, name, sign);
    if (class_android_view_GraphicBuffer.method_unlockCanvasAndPost == NULL) {
        J4A_ALOGE("find unlockCanvasAndPost method in android/view/GraphicBuffer failed.");
        goto fail;
    }

    J4A_ALOGD("J4ALoader: OK: '%s' loaded\n", "android.view.GraphicBuffer");
    ret = 0;
    fail:
    return ret;
}
