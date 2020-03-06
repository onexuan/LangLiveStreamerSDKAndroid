//
// Created by lichenguang on 2018/8/23.
//
#include <jni.h>

#include "GraphicOnNative.h"
#include "GraphicJavaImpl.h"
#include "j4a_GraphicBuffer.h"
#include <cstring>
#include <sys/mman.h>

#define ANDROID_NATIVE_MAKE_CONSTANT(a,b,c,d) \
    (((unsigned)(a)<<24)|((unsigned)(b)<<16)|((unsigned)(c)<<8)|(unsigned)(d))

#define ANDROID_NATIVE_WINDOW_MAGIC \
    ANDROID_NATIVE_MAKE_CONSTANT('_','w','n','d')

#define ANDROID_NATIVE_BUFFER_MAGIC \
    ANDROID_NATIVE_MAKE_CONSTANT('_','b','f','r')

typedef struct native_handle {
    int version; /* sizeof(native_handle_t) */
    int numFds; /* number of file-descriptors at &data[0] */
    int numInts; /* number of ints at &data[numFds] */
    int data[0]; /* numFds + numInts ints */
} native_handle_t;
typedef const native_handle_t* buffer_handle_t;

typedef struct android_native_base_t {
    /* a magic value defined by the actual EGL native type */
    int magic;

    /* the sizeof() of the actual EGL native type */
    int version;

    void* reserved[4];

    /* reference-counting interface */
    void (*incRef)(struct android_native_base_t* base);
    void (*decRef)(struct android_native_base_t* base);
} android_native_base_t;

typedef struct ANativeWindowBuffer {
    ANativeWindowBuffer() {
        common.magic = ANDROID_NATIVE_BUFFER_MAGIC;
        common.version = sizeof(ANativeWindowBuffer);
        memset(common.reserved, 0, sizeof(common.reserved));
    }

    // Implement the methods that sp<ANativeWindowBuffer> expects so that it
    // can be used to automatically refcount ANativeWindowBuffer's.
    void incStrong(const void* /*id*/) const {
        common.incRef(const_cast<android_native_base_t*>(&common));
    }
    void decStrong(const void* /*id*/) const {
        common.decRef(const_cast<android_native_base_t*>(&common));
    }

    struct android_native_base_t common{};

    int width{};
    int height{};
    int stride{};
    int format{};
    int usage{};

    void* reserved[2]{};

    buffer_handle_t handle{};

    void* reserved_proc[8]{};
} ANativeWindowBuffer_t;



typedef struct GraphicBufferFromJava {
    jobject gb;
    ANativeWindowBuffer_t* awb;
    int fdIdx;
    void* base;
    jobject mCanvas;
} GraphicBufferFromJava;

int getPixformatSize(int width, int height, graphics_pixformat pixfmt) {
    int size = 0;
    switch (pixfmt) {
        case IOMX_HAL_PIXEL_FORMAT_RGBA_8888:
        case IOMX_HAL_PIXEL_FORMAT_RGBX_8888:
        case IOMX_HAL_PIXEL_FORMAT_BGRA_8888: {
            size = width * height * 4;
            break;
        }
        case IOMX_HAL_PIXEL_FORMAT_RGB_888: {
            size = width * height * 3;
            break;
        }
        case IOMX_HAL_PIXEL_FORMAT_RGB_565: {
            size = width * height * 2;
            break;
        }
        default:
            break;
    }
    return size;
}

static void dumpAWB(ANativeWindowBuffer_t *t) {
    PRINTI("====================DUMP awb ===========================");
    PRINTI("awb: %p", t);
    if (t) {
        PRINTI("common.magic 0x%x VS 0x%x", t->common.magic,
               (int)ANDROID_NATIVE_BUFFER_MAGIC);
        PRINTI("common.version %d VS %d", t->common.version,
               (int )sizeof(ANativeWindowBuffer));
        PRINTI("W:%d H:%d Format:%d Stride:%d Usage: 0x%x", t->width, t->height,
               t->format, t->stride, t->usage);
        PRINTI("handle.version %d VS %d", t->handle->version,
               (int )sizeof(native_handle_t));
        PRINTI("handle.numFds %d", t->handle->numFds);
        PRINTI("handle.numInts %d", t->handle->numInts);

        int idx = 0;
        for (; idx < t->handle->numFds; idx++) {
            PRINTI("data[%d] = %d (fd)", idx, t->handle->data[idx]);
        }

        for (int ints = 0; ints < t->handle->numInts; ints++) {
            PRINTI("data[%d] = %d", idx + ints, t->handle->data[idx + ints]);
        }
    }
    PRINTI("===============================================---------=");
}

static graphics_handle* java_alloc(int width, int height, graphics_pixformat pixfmt, int usage) {
    JNIEnv *env = getEnv();
    jobject gb = android_view_GraphicBuffer__create__asGlobalRef__catchAll(
            env, width, height, pixfmt, usage);
    if (!gb) {
        PRINTE("Create object GraphicBuffer failed.");
        return nullptr;
    }

    jlong gbWrapper = android_view_GraphicBuffer__mNativeObject__get__catchAll(env, gb);

    // GraphicBuffer指針的指針，GraphicBufferWrapper中又只有一個GraphBuffer成員，根據c/c++編譯的特性，
    // 這個成員在instance佔有的內存中應該也是排在開頭，(*ptr)等於是指向GraphicBuffer
    auto **ptr = (ANativeWindowBuffer_t **)gbWrapper;

    char *tp = (char *) (*ptr);
    int found = 0;
    for (int i = 0; i < 32; i++) {
        if (strncmp(tp + i, "rfb_", 4) == 0) {
            tp = tp + i;
            found = 1;
            break;
        }
    }
    if (!found) {
        PRINTE("Not found magic 'rfb_'");
        J4A_DeleteGlobalRef(env, gb);
        return nullptr;
    }

    auto *nb = (ANativeWindowBuffer_t *)(tp);
    if (nb->width != width || nb->height != height || nb->format != pixfmt || nb->usage != usage) {
        PRINTE("Unknown ANativeWindowBuffer_t struct.!!!");
        J4A_DeleteGlobalRef(env, gb);
        return nullptr;
    } else {
        dumpAWB(nb);
    }

    auto *gbJava = (GraphicBufferFromJava *)calloc(1, sizeof(GraphicBufferFromJava));
    gbJava->gb = gb;
    gbJava->awb = nb;
    gbJava->fdIdx = 0;
    PRINTI("create gb %p", gbJava);
    return gbJava;
}

static int java_lock(graphics_handle* handle, uint32_t usage, void** vaddr) {
    JNIEnv *env = getEnv();
    if (!handle || env == nullptr)
        return ERRNUM;

    auto *gbJava = (GraphicBufferFromJava *)handle;
    if (gbJava->base) {
        *vaddr = gbJava->base;
        return 0;
    }

    if (gbJava->fdIdx >= gbJava->awb->handle->numFds) {
        PRINTE("Invalid mmap fdIDX.");
        return ERRNUM;
    }

    int fd = gbJava->awb->handle->data[gbJava->fdIdx];
    int offset = 0;

    if (fd <= 0) return ERRNUM;

    int size = getPixformatSize(gbJava->awb->stride, gbJava->awb->height, gbJava->awb->format);
    if (!size) return ERRNUM;

    gbJava->mCanvas = android_view_GraphicBuffer__lockCanvas(env, gbJava->gb);
    if (gbJava->mCanvas == nullptr) {
        PRINTE("Lock canvas faild");
        return ERRNUM;
    }

    void *addr =  mmap(nullptr, (size_t)size, PROT_WRITE | PROT_READ, MAP_SHARED, fd, offset);
    if (!addr || addr == MAP_FAILED) {
        PRINTE("mmap vaddr faild. fdIdx %d", gbJava->fdIdx);
        while (++gbJava->fdIdx < gbJava->awb->handle->numFds) {
            fd = gbJava->awb->handle->data[gbJava->fdIdx];
            addr = mmap(nullptr, (size_t)size, PROT_WRITE | PROT_READ, MAP_SHARED, fd, offset);
            if (!addr || addr == MAP_FAILED) {
                PRINTE("mmap vaddr faild. fd %d", gbJava->fdIdx);
            } else {
                break;
            }
        }
    }

    if (!addr || addr == MAP_FAILED) {
        android_view_GraphicBuffer__unlockCanvasAndPost(env, gbJava->gb, gbJava->mCanvas);
        gbJava->mCanvas = nullptr;
        return ERRNUM;
    }else {
        gbJava->mCanvas = J4A_NewGlobalRef__catchAll(env, gbJava->mCanvas);
    }

    gbJava->base = addr;
    *vaddr = gbJava->base;
    return 0;
}

static int java_unlock(graphics_handle* handle) {
    JNIEnv *env = getEnv();
    if (!handle || env == nullptr)
        return ERRNUM;

    auto *gbJava = (GraphicBufferFromJava *)handle;
    if (gbJava->base) {
        int size = getPixformatSize(gbJava->awb->stride, gbJava->awb->height, gbJava->awb->format);
        munmap(gbJava->base, (size_t)size);
        gbJava->base = nullptr;
        android_view_GraphicBuffer__unlockCanvasAndPost(env, gbJava->gb, gbJava->mCanvas);
        J4A_DeleteGlobalRef(env, gbJava->mCanvas);
        gbJava->mCanvas = nullptr;
    }
    return 0;
}

static unsigned int java_stride(graphics_handle* handle) {
    auto *gbJava = (GraphicBufferFromJava *)handle;
    if (gbJava) {
        return (uint32_t)gbJava->awb->stride;
    }
    return 0;
}

static void* java_winbuffer(graphics_handle* handle) {
    auto *gbJava = (GraphicBufferFromJava *)handle;
    if (gbJava) {
        return gbJava->awb;
    }
    return nullptr;
}

static void java_destroy(graphics_handle* handle) {
    JNIEnv *env = getEnv();
    if (!handle || env == nullptr)
        return;

    auto *gbJava = (GraphicBufferFromJava *)handle;
    PRINTI("destroy gb %p", gbJava);
    java_unlock(gbJava);
    J4A_DeleteGlobalRef(env, gbJava->gb);
    free(gbJava);
}

int InitializeGraphicBufferFromJava(IGrapicBufferWrapper *wrapper)
{
    memset(wrapper, 0, sizeof(IGrapicBufferWrapper));
    wrapper->alloc = java_alloc;
    wrapper->lock = java_lock;
    wrapper->unlock = java_unlock;
    wrapper->stride = java_stride;
    wrapper->winbuffer = java_winbuffer;
    wrapper->destroy = java_destroy;

    return 0;
}