LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := libgb_26

LOCAL_SRC_FILES := \
	GraphicHwBufferImpl.cpp \
	
LOCAL_C_INCLUDES := \
	$(LOCAL_PATH) \

LOCAL_LDLIBS := -llog -lEGL -landroid
LOCAL_CFLAGS := -DEGL_EGLEXT_PROTOTYPES

include $(BUILD_SHARED_LIBRARY)