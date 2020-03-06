//
// Created by lichenguang on 2018/8/27.
//
#include "GraphicHwBufferImpl.h"
#include <android/log.h>
#include <android/hardware_buffer.h>
#define EGL_EGLEXT_PROTOTYPES 1
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <unistd.h>

#define LOG_TAG	"GraphicBuffer_Oreo"

#ifndef ALOGI
#define ALOGI(fmt, ...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "[%s:%d] " fmt "\n",__FUNCTION__,__LINE__, ##__VA_ARGS__)
#endif

#ifndef ALOGE
#define ALOGE(fmt, ...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "[%s:%d] " fmt "\n",__FUNCTION__,__LINE__, ##__VA_ARGS__)
#endif

#ifndef ALOGW
#define ALOGW(fmt, ...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, "[%s:%d] " fmt "\n",__FUNCTION__,__LINE__, ##__VA_ARGS__)
#endif

static graphics_handle* Android_AHardwareBufferAlloc(int width, int height, graphics_pixformat pixfmt, int usage) {
    AHardwareBuffer_Desc desc = {};
    desc.width = width;
    desc.height = height;
    desc.layers = 1;
    desc.format = pixfmt; // graphics_pixformat correspnd to <android/hardware_buffer.h> enums.
    desc.usage = usage; // usage are equals to AHARDWAREBUFFER_USAGE_*
    desc.rfu0 = 0;
    desc.rfu1 = 0;

    ALOGI("call AHardwareBuffer_allocate");

    AHardwareBuffer* hardwareBuf = NULL;
    int err = AHardwareBuffer_allocate(&desc, &hardwareBuf);
    if (err != 0) {
        ALOGE("AHardwareBuffer_allocate failed, err = %d", err);
        return NULL;
    }

    //Acquire a reference on the given AHardwareBuffer object
    //AHardwareBuffer_acquire(hardwareBuf);

    //Check stride.
    AHardwareBuffer_describe(hardwareBuf, &desc);
    ALOGI("AHardwareBuffer stride = %d", desc.stride);

    return reinterpret_cast<graphics_handle*>(hardwareBuf);
}

static void Android_AHardwareBufferDestroy(graphics_handle *handle) {
    AHardwareBuffer* hardwareBuf = reinterpret_cast<AHardwareBuffer*>(handle);
    if (hardwareBuf) {
        ALOGI("call AHardwareBuffer_release");
        // Remove a reference that was previously acquired with * AHardwareBuffer_acquire().
        AHardwareBuffer_release(hardwareBuf);
        hardwareBuf = NULL;
    }
}

static uint32_t Android_AHardwareBufferWidth(graphics_handle *handle) {
    AHardwareBuffer* hardwareBuf = reinterpret_cast<AHardwareBuffer*>(handle);
    if (hardwareBuf) {
        AHardwareBuffer_Desc desc = {};
        AHardwareBuffer_describe(hardwareBuf, &desc);
        return desc.width;
    }
    return 0;
}

static uint32_t Android_AHardwareBufferHeight(graphics_handle *handle) {
    AHardwareBuffer* hardwareBuf = reinterpret_cast<AHardwareBuffer*>(handle);
    if (hardwareBuf) {
        AHardwareBuffer_Desc desc = {};
        AHardwareBuffer_describe(hardwareBuf, &desc);
        return desc.height;
    }
    return 0;
}

static uint32_t Android_AHardwareBufferUsage(graphics_handle *handle) {
    AHardwareBuffer* hardwareBuf = reinterpret_cast<AHardwareBuffer*>(handle);
    if (hardwareBuf) {
        AHardwareBuffer_Desc desc = {};
        AHardwareBuffer_describe(hardwareBuf, &desc);
        uint64_t usage = desc.usage;
        // all the usage flags are refered from NDK <hardware_buffer.h>(platform26 or later).
        // the enums are equal to IOMX_GRALLOC_USAGE_*** in our <graphics_type.h>
        if (usage & AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN) {
            ALOGW("usage have AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN flags");
        }
        if (usage & AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN) {
            ALOGW("usage have AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN flags");
        }
        return (uint32_t)usage;
    }
    return 0;
}

static uint32_t Android_AHardwareBufferStride(graphics_handle *handle) {
    AHardwareBuffer* hardwareBuf = reinterpret_cast<AHardwareBuffer*>(handle);
    if (hardwareBuf) {
        AHardwareBuffer_Desc desc = {};
        AHardwareBuffer_describe(hardwareBuf, &desc);
        return desc.stride;
    }
    return 0;
}

static int Android_AHardwareBufferPixelFormat(graphics_handle *handle) {
    AHardwareBuffer* hardwareBuf = reinterpret_cast<AHardwareBuffer*>(handle);
    if (hardwareBuf) {
        AHardwareBuffer_Desc desc = {};
        AHardwareBuffer_describe(hardwareBuf, &desc);
        return desc.format;
    }
    return 0;
}

static void* Android_AHardwareBufferWinptr(graphics_handle *handle) {
    AHardwareBuffer* hardwareBuf = reinterpret_cast<AHardwareBuffer*>(handle);
    EGLClientBuffer clientBuffer;
    if (hardwareBuf) {
        clientBuffer = eglGetNativeClientBufferANDROID(hardwareBuf);
    }
    return clientBuffer;
}

static int Android_AHardwareBufferLock(graphics_handle *handle, uint32_t usage, void** vaddr) {
    AHardwareBuffer* hardwareBuf = reinterpret_cast<AHardwareBuffer*>(handle);
    if (hardwareBuf) {
        ARect* rect = NULL;
        int err = AHardwareBuffer_lock(hardwareBuf, usage, -1, rect, vaddr);
        if (err != 0) {
            ALOGE("AHardwareBuffer_lock failed, err = %d", err);
        }
        return err;
    }
    return -1;
}

static int Android_AHardwareBufferUnlock(graphics_handle *handle) {
    AHardwareBuffer* hardwareBuf = reinterpret_cast<AHardwareBuffer*>(handle);
    if (hardwareBuf) {
        int err = AHardwareBuffer_unlock(hardwareBuf, NULL);
        if (err != 0) {
            ALOGE("AHardwareBuffer_unlock failed, err = %d", err);
        }
        return err;
    }
    return -1;
}

int InitializeGraphicBufferFromNative(IGrapicBufferWrapper *wrapper)
{
    if (!wrapper) {
        ALOGE("IGrapicBufferWrapper handle invalid!");
    }

    wrapper->alloc = Android_AHardwareBufferAlloc;
    wrapper->destroy = Android_AHardwareBufferDestroy;
    wrapper->width = Android_AHardwareBufferWidth;
    wrapper->height = Android_AHardwareBufferHeight;
    wrapper->stride = Android_AHardwareBufferStride;
    wrapper->usage = Android_AHardwareBufferUsage;
    wrapper->pixelFormat = Android_AHardwareBufferPixelFormat;
    wrapper->winbuffer = Android_AHardwareBufferWinptr;
    wrapper->lock = Android_AHardwareBufferLock;
    wrapper->unlock = Android_AHardwareBufferUnlock;

    return 0;
}