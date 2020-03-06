LOCAL_PATH := $(call my-dir)

############# prebuilt ###############
#####$(TARGET_ARCH_ABI):= armeabi-v7a/arm64-v8a/x86 ...#####
include $(CLEAR_VARS)
LOCAL_MODULE := libyuv
LOCAL_SRC_FILES := libs/$(TARGET_ARCH_ABI)/libyuv.a
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := libx264
LOCAL_SRC_FILES := libs/$(TARGET_ARCH_ABI)/libx264.a
include $(PREBUILT_STATIC_LIBRARY)

############# build libenc ###########
include $(CLEAR_VARS)

LOCAL_MODULE := libenc
LOCAL_SRC_FILES := libenc.cc
LOCAL_CFLAGS    :=
LOCAL_LDLIBS    := -llog
LOCAL_C_INCLUDES += $(LOCAL_PATH)/include/libyuv $(LOCAL_PATH)/include/libx264
LOCAL_STATIC_LIBRARIES := libx264 libyuv
LOCAL_DISABLE_FORMAT_STRING_CHECKS := true
LOCAL_DISABLE_FATAL_LINKER_WARNINGS := true
include $(BUILD_SHARED_LIBRARY)
