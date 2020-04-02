//
// Created by Rayan on 2018/12/29.
//

#ifndef LIBWEBP_LOG_H
#define LIBWEBP_LOG_H

#include <android/log.h>

#define LOG_TAG "libwebpanim"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, ##__VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, ##__VA_ARGS__)

#endif //LIBWEBP_LOG_H
