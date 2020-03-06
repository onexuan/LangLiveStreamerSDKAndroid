//
// Created by lichenguang on 2018/8/23.
//

#ifndef GRAPHIC_ON_NATIVE_H
#define GRAPHIC_ON_NATIVE_H
#include <jni.h>

#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <dlfcn.h>

#include "IGraphicBuffer.h"

//--------------------------------------------------------------------------------------------------
#ifndef ERRNUM
#define ERRNUM -__LINE__
#endif

#ifndef PRINTI
#define PRINTI(fmt, ...) __android_log_print(ANDROID_LOG_INFO,"GraphicOnNative","[%s:%d] " fmt "\n",__FUNCTION__,__LINE__, ##__VA_ARGS__)
#endif

#ifndef PRINTE
#define PRINTE(fmt, ...) __android_log_print(ANDROID_LOG_ERROR, "GraphicOnNative","[%s:%d] " fmt "\n",__FUNCTION__,__LINE__, ##__VA_ARGS__)
#endif

#ifndef PRINTW
#define PRINTW(fmt, ...) __android_log_print(ANDROID_LOG_WARN, "GraphicOnNative","[%s:%d] " fmt "\n",__FUNCTION__,__LINE__, ##__VA_ARGS__)
#endif

//--------------------------------------------------------------------------------------------------

JNIEnv* getEnv();

#endif //GRAPHIC_ON_NATIVE_H
