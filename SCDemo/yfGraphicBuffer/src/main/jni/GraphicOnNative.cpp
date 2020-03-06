//
// Created by lichenguang on 2018/8/23.
//
#include "GraphicOnNative.h"
#include "GraphicJavaImpl.h"
#include "j4a_GraphicBuffer.h"
#include "j4a_GraphicBufferWrapper.h"
#include "j4a_Parcel.h"
#include "libyuv.h"

#define GL_GLEXT_PROTOTYPES 1
#define EGL_EGLEXT_PROTOTYPES 1
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <sys/types.h>
#include <sys/syscall.h>
#include <sys/system_properties.h>

#define ANDROID_PLATFORM_VERSION_OREO 26

typedef struct GraphicBufferOnNative {
    int width;
    int height;
    graphics_pixformat color;
    graphics_handle *gb;
    EGLDisplay dpy;
    EGLImageKHR img;
    GLuint texId;
} GraphicBufferOnNative;

typedef struct NativeFd {
    int sdk;
    JavaVM *vm;
    IGrapicBufferWrapper hwd;
    void* handle;
} NativeFd;
static NativeFd _gfd;

static inline int getPlatformVersion() {
    char sdk_ver_str[PROP_VALUE_MAX + 1] = "0";
    __system_property_get("ro.build.version.sdk", sdk_ver_str);
    int sdk_ver = atoi(sdk_ver_str);
    return sdk_ver;
}

static void destroyEGLImageKHR(GraphicBufferOnNative *gbOnNative) {
    if (gbOnNative->img) {
        eglDestroyImageKHR(gbOnNative->dpy, gbOnNative->img);
        if (gbOnNative->texId) {
            glDeleteTextures(1, &gbOnNative->texId);
            gbOnNative->texId = 0;
        }
        gbOnNative->img = NULL;
    }
}

static int createEGLImageKHR(GraphicBufferOnNative *gbOnNative, int fb) {
    GLuint sharedTextureID = 0;

    if (gbOnNative->img) {
        destroyEGLImageKHR(gbOnNative);
    }

    // Create the EGLImageKHR from the native buffer
    EGLint eglImgAttrs[] = { EGL_IMAGE_PRESERVED_KHR, EGL_TRUE, EGL_NONE, EGL_NONE };

    gbOnNative->dpy = eglGetCurrentDisplay();
    if (gbOnNative->dpy == nullptr) {
        PRINTE("eglGetCurrentDisplay return null");
        return ERRNUM;
    }

    void *anw = _gfd.hwd.winbuffer(gbOnNative->gb);
    if (!anw) {
        PRINTE("anw is null");
        return ERRNUM;
    }

    /**
     * Target(EGL_NATIVE_BUFFER_ANDROID)决定了创建EGLImage的方式，例如在Android系统中专门定义了一个称为
     * EGL_NATIVE_BUFFER_ANDROID的Target，支持通过ANativeWindowBuffer创建EGLImage对象，而Buffer(clientBuffer)
     * 则对应创建EGLImage对象时使用数据
     */
    auto clientBuffer = (EGLClientBuffer)anw;
    EGLImageKHR eglImage = eglCreateImageKHR(gbOnNative->dpy, EGL_NO_CONTEXT,
                                           EGL_NATIVE_BUFFER_ANDROID, clientBuffer, eglImgAttrs);
    if (eglImage == EGL_NO_IMAGE_KHR) {
        PRINTE("create EGLImageKHR faild");
        return ERRNUM;
    }

    // bind an off-screen frame buffer from input.
    glBindFramebuffer(GL_FRAMEBUFFER, static_cast<GLuint>(fb));

    // Create GL texture, bind to GL_TEXTURE_2D, etc.
    glGenTextures(1, &sharedTextureID);
    glBindTexture(GL_TEXTURE_2D, sharedTextureID);

    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

    //glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0);
    // Attach the EGLImage to whatever texture is bound to GL_TEXTURE_2D
    glEGLImageTargetTexture2DOES(GL_TEXTURE_2D, eglImage);
    gbOnNative->img = eglImage;
    gbOnNative->texId = sharedTextureID;
    return gbOnNative->texId;
}

static int InitializeGraphicBufferFromLib(IGrapicBufferWrapper* wrapper, void* handle) {
    int ret = -__LINE__;
    int (*Ptr_InitializeGraphicBufferFromNative)(IGrapicBufferWrapper *) = NULL;
    if (wrapper && handle) {
        memset(wrapper, 0, sizeof(IGrapicBufferWrapper));
        void *target = dlsym(handle, "InitializeGraphicBufferFromNative");
        if (!target) {
            PRINTE("symbol InitializeGraphicBufferFromNative is null");
            return ret;
        }
        Ptr_InitializeGraphicBufferFromNative = (__typeof__(Ptr_InitializeGraphicBufferFromNative))target;
        ret = Ptr_InitializeGraphicBufferFromNative(wrapper);
    }
    return ret;
}

static jint load(JNIEnv *env, jobject obj, jint sdk, jstring path) {
    int ret = ERRNUM;
    long ptr = 0;
    void *handle = NULL;
    if (_gfd.handle == NULL) {
        IGrapicBufferWrapper api;
        int platform = getPlatformVersion();
        if (platform > 18 && platform < 26) {
            PRINTI("Using Java GraphicBuffer module.");
            ret = InitializeGraphicBufferFromJava(&api);
        } else {
            const char* dir = env->GetStringUTFChars(path, (jboolean *)NULL);
            PRINTI("Using Native GraphicBuffer module, path %s", dir);
            int tsdk = platform;
            while (tsdk > 10) {
                char buffer[512] = { 0 };
                if (dir == NULL)
                    sprintf(buffer, "libgb_%d.so", tsdk);
                else
                    sprintf(buffer, "%s/libgb_%d.so", dir, tsdk);
                PRINTI("Dlopen %s...", buffer);
                handle = dlopen(buffer, RTLD_NOW);
                if (handle) {
                    PRINTI("Loaded GraphicBuffer module succeed with sdk %d", tsdk);
                    break;
                }
                tsdk--;
            }

            if (dir) env->ReleaseStringUTFChars(path, dir);

            if (!handle) {
                PRINTE("Loading gb module faild (%s).", dlerror());
                ret = ERRNUM;
                goto failed;
            }
            ret = InitializeGraphicBufferFromLib(&api, handle);
        }

        if (ret) {
            PRINTE("Init GraphicBuffer method faild (%d).", ret);
            ret = ERRNUM;
            goto failed;
        } else if (api.alloc == NULL || api.lock == NULL || api.unlock == NULL || api.destroy == NULL) {
            PRINTE("GraphicBuffer method is imperfect.");
            ret = ERRNUM;
            goto failed;
        } else {
            PRINTI("Hook graphic method succeed, alloc %p, destroy %p", api.alloc, api.destroy);
        }

        _gfd.handle = handle;
        _gfd.hwd = api;
    }

    ret = 0;
    ptr = com_yunfan_graphicbuffer_GraphicBufferWrapper__mNativeObject__get(env, obj);
    if (!ptr) {
        GraphicBufferOnNative *gbOnNative = (GraphicBufferOnNative *)calloc(1, sizeof(GraphicBufferOnNative));
        com_yunfan_graphicbuffer_GraphicBufferWrapper__mNativeObject__set__catchAll(
                env, obj, (long)gbOnNative);
    } else {
        PRINTI("already exist native object %ld", ptr);
    }

failed:
    if (ret && handle) {
        dlclose(handle);
    }
    return ret;
}

static void unload(JNIEnv *env, jobject obj) {
    if (_gfd.handle) {
        dlclose(_gfd.handle);
        _gfd.handle = NULL;
    }
}

static jint createFrameBufferAndBind(JNIEnv *env, jobject obj, jint width, jint height, jint color, int fb) {
    auto *gbOnNative =
            (GraphicBufferOnNative *)com_yunfan_graphicbuffer_GraphicBufferWrapper__mNativeObject__get(env, obj);
    if (!gbOnNative) {
        return ERRNUM;
    }

    switch (color) {
        case IOMX_HAL_PIXEL_FORMAT_RGBA_8888:
        case IOMX_HAL_PIXEL_FORMAT_RGBX_8888:
        case IOMX_HAL_PIXEL_FORMAT_RGB_888:
        case IOMX_HAL_PIXEL_FORMAT_RGB_565:
            break;
        default:
            return ERRNUM;
    }

    graphics_handle *gb = _gfd.hwd.alloc(width, height, color,
                                         IOMX_GRALLOC_USAGE_HW_TEXTURE |
                                         IOMX_GRALLOC_USAGE_SW_READ_OFTEN |
                                         IOMX_GRALLOC_USAGE_SW_WRITE_OFTEN);
    if (!gb) {
        return ERRNUM;
    }

    gbOnNative->color = color;
    gbOnNative->gb = gb;
    gbOnNative->width = width;
    gbOnNative->height = height;

    int texture = createEGLImageKHR(gbOnNative, fb);
    if (texture <= 0) {
        PRINTE("createEGLImageKHR create failed!");
        _gfd.hwd.destroy(gb);
        gbOnNative->gb = nullptr;
        return ERRNUM;
    }

    PRINTI("Create FrameBuffer succeed!");
    return texture;
}

static jint destroyFrameBuffer(JNIEnv *env, jobject obj) {
    GraphicBufferOnNative *gbOnNative =
            (GraphicBufferOnNative *)com_yunfan_graphicbuffer_GraphicBufferWrapper__mNativeObject__get(
                    env, obj);
    if (gbOnNative) {
        destroyEGLImageKHR(gbOnNative);
        if (gbOnNative->gb) {
            _gfd.hwd.destroy(gbOnNative->gb);
        }
        free(gbOnNative);
        com_yunfan_graphicbuffer_GraphicBufferWrapper__mNativeObject__set__catchAll(env, obj, 0);
    }

    PRINTI("Destroy FrameBuffer succeed!");
    return 0;
}

static jlong lockAddr(JNIEnv *env, jobject obj) {
    int ret = ERRNUM;
    void *addr = NULL;
    GraphicBufferOnNative *gbOnNative =
            (GraphicBufferOnNative *)com_yunfan_graphicbuffer_GraphicBufferWrapper__mNativeObject__get(
                    env, obj);
    if (gbOnNative) {
        if (gbOnNative->gb) {
            ret = _gfd.hwd.lock(gbOnNative->gb,
                                IOMX_GRALLOC_USAGE_SW_READ_OFTEN|IOMX_GRALLOC_USAGE_SW_WRITE_OFTEN,
                                &addr);
        }
    }
    return (jlong)addr;
}

static jint unlock(JNIEnv *env, jobject obj) {
    GraphicBufferOnNative *gbOnNative =
            (GraphicBufferOnNative *)com_yunfan_graphicbuffer_GraphicBufferWrapper__mNativeObject__get(
                    env, obj);
    if (gbOnNative) {
        if (gbOnNative->gb) {
            _gfd.hwd.unlock(gbOnNative->gb);
        }
    }
    return 0;
}

static jint stride(JNIEnv *env, jobject obj) {
    GraphicBufferOnNative *gbOnNative =
            (GraphicBufferOnNative *)com_yunfan_graphicbuffer_GraphicBufferWrapper__mNativeObject__get(
                    env, obj);
    int stride = 0;
    if (gbOnNative) {
        if (gbOnNative->gb) {
            stride = _gfd.hwd.stride(gbOnNative->gb);
        }
    }
    return stride;
}

static jint loadAndroidInternelClass(JNIEnv *env) {
    if (loadClass__android_view_GraphicBuffer(env)) {
        PRINTW("Loading class android_view_GraphicBuffer error.");
        return ERRNUM;
    }

    if (loadClass__android_os_Parcel(env)) {
        PRINTW("Loading class android_os_Parcel error.");
        return ERRNUM;
    }

    return JNI_VERSION_1_6;
}

static void copyRgbaData(JNIEnv* env, jobject thiz, jint width, jint height,
                        jlong addr, jint addrStride, jbyteArray mData) {
    jbyte* rgbaBuffer = NULL;
    if (addr == 0) {
        PRINTE("copyRgbaData: source buffer is invalid");
        return;
    }
    rgbaBuffer = (jbyte*) addr;
    jbyte* rgbaData = env->GetByteArrayElements(mData, NULL);

    jbyte * dstAddr = rgbaData;
    for (int i = 0; i < height; ++i) {
        memcpy(dstAddr, rgbaBuffer, width * 4 * sizeof(jbyte));
        dstAddr += width * 4 * sizeof(jbyte);
        rgbaBuffer += addrStride * 4;
    }
    //memcpy(rgbaData, rgbaBuffer, 4*width*height*sizeof(jbyte));

    env->ReleaseByteArrayElements(mData, rgbaData, JNI_ABORT);
}

static void rgbaToI420(JNIEnv* env, jobject thiz, jlong addr, jbyteArray dstframe,
                           jint src_width, jint src_height,jint src_stride,
                           jboolean need_flip, jint rotate_degree, int srctype) {
    jbyte* rgba_frame = NULL;
    if (addr == 0) {
        PRINTE("RGBAToI420Fast: source buffer is invalid");
        return;
    }
    rgba_frame = (jbyte*) addr;

    jbyte* yuv_frame = env->GetByteArrayElements(dstframe, NULL);

    uint8_t* y = (uint8_t*)yuv_frame;
    uint8_t* u = y + src_width * src_height;
    uint8_t* v = u + src_width * src_height / 4;

    jint ret = ConvertToI420((uint8_t *) rgba_frame, src_stride,
                             y, src_width,
                             u, src_width / 2,
                             v, src_width / 2,
                             0, 0,
                             src_stride, src_height,
                             src_width, src_height,
                             (libyuv::RotationMode)rotate_degree, srctype);

    if (ret < 0) {
        PRINTE("ConvertToI420 failed");
    }

    env->ReleaseByteArrayElements(dstframe, yuv_frame, JNI_ABORT);
}

static void i420ToNv12(JNIEnv* env, jobject thiz, jbyteArray i420frame, jbyteArray nv12frame,
                       jint src_width, jint src_height) {

    jbyte* i420_frame = env->GetByteArrayElements(i420frame, NULL);
    jbyte* nv12_frame = env->GetByteArrayElements(nv12frame, NULL);

    uint8_t* y = (uint8_t*)i420_frame;
    uint8_t* u = y + src_width * src_height;
    uint8_t* v = u + src_width * src_height / 4;

    uint8_t* nv12 = (uint8_t*)nv12_frame;

    int ret = ConvertFromI420(y, src_width,
                              u, src_width / 2,
                              v, src_width / 2,
                              nv12, src_width,
                              src_width, src_height,
                              libyuv::FOURCC_NV12);

    env->ReleaseByteArrayElements(i420frame, i420_frame, JNI_ABORT);
    env->ReleaseByteArrayElements(nv12frame, nv12_frame, JNI_ABORT);
}

static JNINativeMethod gMethods[] = {
        { "_load", "(ILjava/lang/String;)I", (void *)load },
        { "_unload", "()V", (void *)unload },
        { "_createFrameBufferAndBind", "(IIII)I", (void *)createFrameBufferAndBind },
        { "_destroyFrameBuffer", "()I", (void *)destroyFrameBuffer },
        { "_lock", "()J", (void *)lockAddr },
        { "_unlock", "()I", (void *)unlock },
        { "_stride", "()I", (void *)stride },
        { "_CopyRgbaData", "(IIJI[B)V", (void *)copyRgbaData},
        { "_RgbaToI420", "(J[BIIIZII)V", (void *)rgbaToI420},
        { "_I420ToNv12", "([B[BII)V", (void *)i420ToNv12}
};

jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    JNIEnv *env = NULL;
    _gfd.vm = vm;

    PRINTI("JNI_Onload! on %d bit", (int )(sizeof(long) * 8));
    vm->GetEnv((void **) &env, JNI_VERSION_1_6);

    if (!env) {
        PRINTE("Get env faild.");
        return ERRNUM;
    }

    jclass clazz = env->FindClass(GRAPHIC_BUFFER_WRAPPER_PACKAGE_NAME);
    if (!clazz) {
        PRINTE("Not found class %s", GRAPHIC_BUFFER_WRAPPER_PACKAGE_NAME);
        return ERRNUM;
    }

    if (env->RegisterNatives(clazz, gMethods, sizeof(gMethods)/sizeof(gMethods[0]))) {
        PRINTE("Register native methods failed.");
        return ERRNUM;
    }

    if (loadClass__com_yunfan_graphicbuffer_GraphicBufferWrapper(env)) {
        PRINTE("Loading class GraphicBufferWrapper failed.");
        return ERRNUM;
    }

    if (getPlatformVersion() < ANDROID_PLATFORM_VERSION_OREO) {
        return loadAndroidInternelClass(env);
    }

    return JNI_VERSION_1_6;
}

void JNI_OnUnload(JavaVM* vm, void* reserved) {
    if (_gfd.handle) {
        dlclose(_gfd.handle);
        _gfd.handle = NULL;
    }
}

JNIEnv* getEnv() {
    JNIEnv *env = NULL;
    JavaVM *vm = _gfd.vm;
    vm->GetEnv((void**) &env, JNI_VERSION_1_6);
    return env;
}