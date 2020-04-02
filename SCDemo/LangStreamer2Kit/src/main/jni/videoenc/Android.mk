LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := libopenh264
LOCAL_SRC_FILES := libs/$(TARGET_ARCH_ABI)/libopenh264.a
include $(PREBUILT_STATIC_LIBRARY)



include $(CLEAR_VARS)

LOCAL_CPP_EXTENSION := .cpp

LOCAL_C_INCLUDES := \
	$(LOCAL_PATH)/include

# All of the source files that we will compile.
LOCAL_SRC_FILES := \
	openh264_encoder.cpp \
	encoder_jni.cpp

LOCAL_MODULE := lang_openh264enc

LOCAL_LDLIBS := -llog

LOCAL_STATIC_LIBRARIES := libopenh264

include $(BUILD_SHARED_LIBRARY)