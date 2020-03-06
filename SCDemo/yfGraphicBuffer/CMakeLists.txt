# 声明要求的cmake最低版本
cmake_minimum_required(VERSION 3.4.1)

#允许gdb调试
set(CMAKE_BUILD_TYPE "RELEASE")
set(CMAKE_CXX_FLAGS_DEBUG "$ENV{CXXFLAGS} -O0 -Wall -g -ggdb")
set(CMAKE_CXX_FLAGS_RELEASE "$ENV{CXXFLAGS} -O3 -Wall")

# 添加c++11标准支持
set(CMAKE_CXX_FLAGS "-std=c++11")

# 设置路径（下面生成共享库的路径）
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${PROJECT_SOURCE_DIR}/libs/${ANDROID_ABI})

if(${ANDROID_ABI} STREQUAL "armeabi-v7a")
    set(ANDROID_SYS_INCLUDE ${ANDROID_SYSROOT}/usr/include/arm-linux-androideabi)
elseif(${ANDROID_ABI} STREQUAL "arm64-v8a")
    set(ANDROID_SYS_INCLUDE ${ANDROID_SYSROOT}/usr/include/aarch64-linux-android)
else()
    set(ANDROID_SYS_INCLUDE ${ANDROID_SYSROOT}/usr/include/arm-linux-androideabi)
endif()

include_directories(${ANDROID_SYS_INCLUDE}
                    src/main/jni/include)

link_directories(src/main/jni/prebuilt/${ANDROID_ABI})


# 创建共享库（把工程内的cpp文件都创建成共享库文件，方便通过头文件来调用）
add_library(yf-graphic
            SHARED
            src/main/jni/GraphicJavaImpl.cpp
            src/main/jni/GraphicOnNative.cpp
            src/main/jni/j4a_base.c
            src/main/jni/j4a_GraphicBuffer.c
            src/main/jni/j4a_GraphicBufferWrapper.c
            src/main/jni/j4a_Parcel.c
            )

target_link_libraries(yf-graphic
                      log
                      GLESv2
                      EGL
                      yuv)