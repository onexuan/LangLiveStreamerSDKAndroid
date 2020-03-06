LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_CPP_EXTENSION := .cpp

# All of the source files that we will compile.
LOCAL_SRC_FILES := \
	pili-librtmp/amf.c \
	pili-librtmp/error.c \
	pili-librtmp/hashswf.c \
	pili-librtmp/log.c \
	pili-librtmp/parseurl.c \
	pili-librtmp/rtmp.c \
	slist.c \
	rtmp_impl.c \
	muxer_jni.cpp

LOCAL_C_INCLUDES := \
	$(LOCAL_PATH) \
	$(LOCAL_PATH)/./pili-librtmp \

LOCAL_CPPFLAGS := -std=c++11 -D__ANDROID__

LOCAL_LDLIBS := -ldl -llog -landroid

LOCAL_STATIC_LIBRARIES :=

LOCAL_MODULE := lang_mediamuxer

include $(BUILD_SHARED_LIBRARY)