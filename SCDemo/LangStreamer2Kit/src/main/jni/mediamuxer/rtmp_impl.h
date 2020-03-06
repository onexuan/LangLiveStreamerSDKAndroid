#ifndef RTMP_IMPL_H_
#define RTMP_IMPL_H_

#include <stdint.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C"{
#endif

#define MSG_RTMP_CONNECTING         0
#define MSG_RTMP_CONNECTED          1
#define MSG_RTMP_STOPPED            2
#define MSG_RTMP_SOCKET_EXCEPTION   3

typedef struct PILI_RTMP PILI_RTMP;
typedef struct RTMPError RTMPError;
typedef struct rtmp_impl_context rtmp_impl_context;

typedef struct rtmp_impl_callback_context {
    void (*connecting_callback)(void* user_data);
    void (*connected_callback)(void* user_data, const char* server_info);
    void (*disconnected_callback)(void* user_data);
    void (*error_callback)(void* user_data, const char* error_info);
} rtmp_impl_callback_context;

rtmp_impl_context* rtmp_impl_create();

void* rtmp_impl_set_weak_thiz(rtmp_impl_context* rtmp_context, void* weak_thiz);

void* rtmp_impl_get_weak_thiz(rtmp_impl_context* rtmp_context);

void rtmp_impl_set_callback(rtmp_impl_context* rtmp_context, rtmp_impl_callback_context* callback);

int rtmp_impl_add_audio_track(rtmp_impl_context* rtmp_context,
                              const int32_t sample_rate,
                              const int32_t channel,
                              const int32_t bits_per_sample,
                              const int32_t bitrate_kbps);

int rtmp_impl_add_video_track(rtmp_impl_context* rtmp_context,
                              const int32_t width,
                              const int32_t height,
                              const int32_t frame_rate,
                              const int32_t bitrate_kbps);

int rtmp_impl_start(rtmp_impl_context* rtmp_impl_context, const char *url);

int rtmp_impl_write_audio_header(rtmp_impl_context* rtmp_context,
                                 const uint8_t* data,
                                 const int32_t data_length,
                                 const int64_t pts);

int rtmp_impl_write_video_header(rtmp_impl_context* rtmp_context,
                                 const uint8_t* header,
                                 const int32_t header_length,
                                 const int64_t pts);

int rtmp_impl_write_audio(rtmp_impl_context* rtmp_context,
                          const uint8_t* data,
                          const int32_t data_length,
                          const int64_t pts);

int rtmp_impl_write_video(rtmp_impl_context* rtmp_context,
                          const uint8_t* data,
                          const int32_t data_length,
                          const int64_t pts,
                          const int64_t dts);

int rtmp_impl_stop(rtmp_impl_context* rtmp_context);

void rtmp_impl_destroy(rtmp_impl_context* rtmp_impl_context);

#ifdef __cplusplus
}
#endif
#endif // RTMP_IMPL_H_