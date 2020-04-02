//
// Created by Rayan on 2018/12/29.
//

#include <jni.h>
#include <stdio.h>
#include <string.h>
#include <errno.h>

#include "log.h"
#include "webp/decode.h"
#include "webp/encode.h"
#include "examples/anim_util.h"
#include "examples/unicode.h"
#include "imageio/image_enc.h"

#define JAVA_HOST_CLASS_NAME "net/lang/animation/LibwebpProxy"

static AnimatedImage image;
static jboolean isInitialized = JNI_FALSE;

static jint WebPAnimInit_Native(JNIEnv *jenv, jclass jcls) {
    jint jresult = 0 ;
    if (isInitialized == JNI_FALSE) {
        memset(&image, 0, sizeof(image));
        isInitialized = JNI_TRUE;
    } else {
        jresult = -1;
    }

    return jresult;
}

static jint WebPAnimDecodeRGBA_Native(JNIEnv *jenv, jclass jcls, jstring   jarg1, jintArray jarg2,
        jintArray jarg3, jintArray jarg4, jint jarg5, jint jarg6) {
    if (isInitialized) {
        memset(&image, 0, sizeof(image));
    } else {
        return -1;
    }

    int width;
    int height;
    int nbFrames;

    const char *input_file = (*jenv)->GetStringUTFChars(jenv, jarg1, 0);

    if (!ReadAnimatedImage(input_file, &image, 0, NULL, jarg5, jarg6)) {
        LOGE("Error decoding file.\n Aborting.\n");
        return -2;
    } else {
        width = image.canvas_width;
        height = image.canvas_height;
        nbFrames = image.num_frames;
        {
            jint jvalue = (jint)width;
            (*jenv)->SetIntArrayRegion(jenv, jarg2, 0, 1, &jvalue);
        }
        {
            jint jvalue = (jint)height;
            (*jenv)->SetIntArrayRegion(jenv, jarg3, 0, 1, &jvalue);
        }
        {
            jint jvalue = (jint)nbFrames;
            (*jenv)->SetIntArrayRegion(jenv, jarg4, 0, 1, &jvalue);
        }
    }

    return 0;
}


static void WebPGetDecodedFrame_Native(JNIEnv *jenv, jclass jcls, jint jarg1, jbyteArray jresult) {
    size_t size = image.canvas_width * sizeof(uint32_t) * image.canvas_height;
    (*jenv)->SetByteArrayRegion(jenv, jresult, 0, size, image.frames[jarg1].rgba);
}


static jint WebPGetFrameDuration_Native(JNIEnv *jenv, jclass jcls, jint jarg1) {
    return image.frames[jarg1].duration;
}


static jint WebPSaveImage_Native(JNIEnv *jenv, jclass jcls, jstring jarg1) {
    const W_CHAR* prefix = TO_W_CHAR("dump_");
    const W_CHAR* suffix = TO_W_CHAR("bmp");
    WebPOutputFileFormat format = BMP;
    const W_CHAR* dump_folder = (*jenv)->GetStringUTFChars(jenv, jarg1, 0);

    for (int i = 0; i < image.num_frames; ++i) {
        W_CHAR out_file[1024];
        WebPDecBuffer buffer;
        WebPInitDecBuffer(&buffer);
        buffer.colorspace = MODE_RGBA;
        buffer.is_external_memory = 1;
        buffer.width = image.canvas_width;
        buffer.height = image.canvas_height;
        buffer.u.RGBA.rgba = image.frames[i].rgba;
        buffer.u.RGBA.stride = buffer.width * sizeof(uint32_t);
        buffer.u.RGBA.size = buffer.u.RGBA.stride * buffer.height;
        WSNPRINTF(out_file, sizeof(out_file), "%s/%s%.4d.%s",
                  dump_folder, prefix, i, suffix);
        if (!WebPSaveImage(&buffer, format, (const char*)out_file)) {
            LOGE("Error while saving image '%s'\n", out_file);
            WFPRINTF(stderr, "Error while saving image '%s'\n", out_file);
        }
        WebPFreeDecBuffer(&buffer);
    }

    return 0;
}


static jint WebPAnimRelease_Native(JNIEnv *jenv, jclass jcls) {
    isInitialized = JNI_FALSE;
    ClearAnimatedImage(&image);

    return 0;
}


static JNINativeMethod gWebpMethods[] = {
        { "WebPAnimInit", "()I",
                (void *)WebPAnimInit_Native },

        { "WebPAnimDecodeRGBA", "(Ljava/lang/String;[I[I[III)I",
                (void *)WebPAnimDecodeRGBA_Native },

        { "WebPGetDecodedFrame", "(I[B)V",
                (void *)WebPGetDecodedFrame_Native },

        { "WebPGetFrameDuration", "(I)I",
                (void *)WebPGetFrameDuration_Native },

        { "WebPSaveImage", "(Ljava/lang/String;)I",
                (void *)WebPSaveImage_Native },

        { "WebPAnimRelease", "()I",
                (void *)WebPAnimRelease_Native },
};

#define WEBP_METHODS_ARRAY_ELEMS(a)  (sizeof(a) / sizeof(a[0]))

static int RegisterWebpMethods(JNIEnv *env) {
    jclass clz = (*env)->FindClass(env, JAVA_HOST_CLASS_NAME);
    if (clz == NULL) {
        LOGE("Class %s not found", JAVA_HOST_CLASS_NAME);
        return JNI_ERR;
    }

    if ((*env)->RegisterNatives(env, clz, gWebpMethods, WEBP_METHODS_ARRAY_ELEMS(gWebpMethods))) {
        LOGE("methods not registered");
        return JNI_ERR;
    }

    return JNI_OK;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    JNIEnv* env;
    int result = -1;
    if ((*vm)->GetEnv(vm, (void**)&env, JNI_VERSION_1_4) != JNI_OK) {
        return result;
    }

    result = RegisterWebpMethods(env);
    if (result != JNI_OK) {
        return result;
    }

    return JNI_VERSION_1_6;
}
