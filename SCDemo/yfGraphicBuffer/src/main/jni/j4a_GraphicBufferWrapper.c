//
// Created by lichenguang on 2018/8/23.
//

#include "j4a_GraphicBufferWrapper.h"

typedef struct com_yunfan_graphicbuffer_GraphicBufferWrapper {
    jclass id;

    jfieldID field_mNativeObject;
} com_yunfan_graphicbuffer_GraphicBufferWrapper;
static com_yunfan_graphicbuffer_GraphicBufferWrapper class_com_yunfan_graphicbuffer_GraphicBufferWrapper;

jlong com_yunfan_graphicbuffer_GraphicBufferWrapper__mNativeObject__get(JNIEnv *env, jobject thiz)
{
    return (*env)->GetLongField(env, thiz, class_com_yunfan_graphicbuffer_GraphicBufferWrapper.field_mNativeObject);
}

jlong com_yunfan_graphicbuffer_GraphicBufferWrapper__mNativeObject__get__catchAll(JNIEnv *env, jobject thiz)
{
    jlong ret_value = com_yunfan_graphicbuffer_GraphicBufferWrapper__mNativeObject__get(env, thiz);
    if (J4A_ExceptionCheck__catchAll(env)) {
        return 0;
    }

    return ret_value;
}

void com_yunfan_graphicbuffer_GraphicBufferWrapper__mNativeObject__set(JNIEnv *env, jobject thiz, jlong value)
{
    (*env)->SetLongField(env, thiz, class_com_yunfan_graphicbuffer_GraphicBufferWrapper.field_mNativeObject, value);
}

void com_yunfan_graphicbuffer_GraphicBufferWrapper__mNativeObject__set__catchAll(JNIEnv *env, jobject thiz, jlong value)
{
    com_yunfan_graphicbuffer_GraphicBufferWrapper__mNativeObject__set(env, thiz, value);
    J4A_ExceptionCheck__catchAll(env);
}

int loadClass__com_yunfan_graphicbuffer_GraphicBufferWrapper(JNIEnv *env)
{
    int         ret                   = -1;
    const char *J4A_UNUSED(name)      = NULL;
    const char *J4A_UNUSED(sign)      = NULL;
    jclass      J4A_UNUSED(class_id)  = NULL;
    int         J4A_UNUSED(api_level) = 0;

    if (class_com_yunfan_graphicbuffer_GraphicBufferWrapper.id != NULL)
        return 0;

    sign = GRAPHIC_BUFFER_WRAPPER_PACKAGE_NAME;
    class_com_yunfan_graphicbuffer_GraphicBufferWrapper.id = J4A_FindClass__asGlobalRef__catchAll(env, sign);
    if (class_com_yunfan_graphicbuffer_GraphicBufferWrapper.id == NULL)
        goto fail;

    class_id = class_com_yunfan_graphicbuffer_GraphicBufferWrapper.id;
    name     = "mNativeObject";
    sign     = "J";
    class_com_yunfan_graphicbuffer_GraphicBufferWrapper.field_mNativeObject = J4A_GetFieldID__catchAll(env, class_id, name, sign);
    if (class_com_yunfan_graphicbuffer_GraphicBufferWrapper.field_mNativeObject == NULL)
        goto fail;

    J4A_ALOGD("J4ALoader: OK: '%s' loaded\n", "com.yunfan.graphicbuffer.GraphicBufferWrapper");
    ret = 0;
    fail:
    return ret;
}