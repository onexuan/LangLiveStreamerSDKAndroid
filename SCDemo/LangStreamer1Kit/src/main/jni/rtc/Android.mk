LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

# All of the source files that we will compile.
LOCAL_SRC_FILES := \
	media_preprocessing_plugin_jni.cpp

LOCAL_C_INCLUDES := \
	$(LOCAL_PATH)/include

LOCAL_CPPFLAGS := -std=c++11
	
LOCAL_LDLIBS := -ldl -llog

LOCAL_MODULE := apm-plugin-raw-data-api-java

include $(BUILD_SHARED_LIBRARY)