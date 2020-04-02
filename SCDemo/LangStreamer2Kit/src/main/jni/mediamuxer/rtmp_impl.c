#include "rtmp_impl.h"
#include <stdlib.h>
#include <string.h>
#include <android/log.h>
#include "h264_nal.h"
#include "rtmp_helpers.h"
#include "slist.h"
#include "pili-librtmp/rtmp.h"
#include "pili-librtmp/log.h"

#define LOG_TAG "rtmp_impl"

#define ALOGD(...) ((void)__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__))
#define ALOGI(...) ((void)__android_log_print(ANDROID_LOG_INFO , LOG_TAG, __VA_ARGS__))
#define ALOGW(...) ((void)__android_log_print(ANDROID_LOG_WARN , LOG_TAG, __VA_ARGS__))
#define ALOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__))

#define RTMP_RECEIVE_TIMEOUT        4 // set receive from server timeout sec.
#define RTMP_SEND_TIMEOUT           6 // set send to server timeout sec.

typedef struct video_stream_info {
    int32_t     width;
    int32_t     height;
    int32_t     frame_rate;
    int32_t     bitrate_kbps;
} video_stream_info;

typedef struct audio_stream_info {
    int32_t     sample_rate;
    int32_t     channel;
    int32_t     bits_per_sample;
    int32_t     bitrate_kbps;
} audio_stream_info;

typedef struct rtmp_impl_context {
    void* weak_thiz;
    rtmp_impl_callback_context* callback;

    PILI_RTMP* rtmp;
    RTMPError* rtmp_error;
    uint8_t* rtmp_body_data;  // used to store real rtmp sender data.

    uint8_t* aac_codec_specific_data;
    int32_t aac_codec_specific_data_length;

    uint8_t* sps;
    int32_t sps_length;
    uint8_t * pps;
    int32_t pps_length;

    uint8_t* bitstream;

    audio_stream_info* audio_info;
    video_stream_info* video_info;
} rtmp_impl_context;

static void* aligned_malloc(size_t required_bytes, size_t alignment) {
    void *p1;
    void **p2;
    size_t offset = alignment -1 + sizeof(void*);
    p1 = malloc(required_bytes + offset);               // the line you are missing
    p2 = (void**)(((size_t)(p1) + offset)&~(alignment - 1));  //line 5
    p2[-1] = p1; //line 6
    return p2;
}

static void aligned_free(void* p) {
    void* p1 = ((void**)p)[-1];         // get the pointer to the buffer we allocated
    free(p1);
}

static void rtmp_log_debug(int level, const char *format, va_list args) {
    if (level > RTMP_LOGWARNING)
        return;

    char out[4096];
    vsnprintf(out, sizeof(out), format, args);
    ALOGD("%s", out);
}

static void rtmp_error_callback(RTMPError* error, void* userData) {
    if (error->code < 0) {
        ALOGW("[%s]: error message: %s", __FUNCTION__, error->message);
        rtmp_impl_context* rtmp_context = (rtmp_impl_context *)userData;
        if (rtmp_context->callback->error_callback) {
            rtmp_context->callback->error_callback(userData, error->message);
        }
    }
}

static void rtmp_connection_time_callback(PILI_CONNECTION_TIME* connTime, void* userData) {
    uint32_t connect_time = connTime->connect_time;
    uint32_t handshake_time = connTime->handshake_time;
    ALOGI("rtmp_connection_time_callback: connect_time = %d handshake_time = %d",
          connect_time, handshake_time);
}

static void build_flv_meta_data(rtmp_impl_context* rtmp_context, uint8_t** metaData, int* metaDataSize)
{
    char buf[4096];
    char *enc = buf;
    char *end = enc + sizeof(buf);

    audio_stream_info* audio_info = rtmp_context->audio_info;
    video_stream_info* video_info = rtmp_context->video_info;

    enc_str(&enc, end, "@setDataFrame"); //++rayman
    enc_str(&enc, end, "onMetaData");

    //*enc++ = AMF_ECMA_ARRAY;
    //enc = AMF_EncodeInt32(enc, end, 14);
    *enc++ = AMF_OBJECT;

    enc_num_val(&enc, end, "duration", 0.0);
    enc_num_val(&enc, end, "fileSize", 0.0);

    // video meta data.
    if (rtmp_context->video_info) {
        enc_num_val(&enc, end, "width", (double)video_info->width);
        enc_num_val(&enc, end, "height", (double)video_info->height);
        enc_str_val(&enc, end, "videocodecid", "avc1");
        enc_num_val(&enc, end, "videodatarate", (double)video_info->bitrate_kbps);
        enc_num_val(&enc, end, "framerate", (double)video_info->frame_rate);
    }

    // audio meta data
    if (rtmp_context->audio_info) {
        enc_str_val(&enc, end, "audiocodecid", "mp4a");
        enc_num_val(&enc, end, "audiodatarate", (double)audio_info->bitrate_kbps);
        enc_num_val(&enc, end, "audiosamplerate", (double)audio_info->sample_rate);
        enc_num_val(&enc, end, "audiosamplesize", (double)audio_info->bits_per_sample);
        //enc_num_val(&enc, end, "audiochannels", (double)audio_info->channel);
        enc_bool_val(&enc, end, "stereo", audio_info->channel == 2);
    }

    enc_str_val(&enc, end, "encoder", "langlive-android-rtmp-streamer-1.0.0");
    *enc++ = 0;
    *enc++ = 0;
    *enc++ = AMF_OBJECT_END;

    //*metaDataSize = enc - buf;
    int copy_size = enc - buf;
    *metaData = (uint8_t*)malloc(sizeof(uint8_t) * copy_size);
    memcpy(*metaData, buf, copy_size);
    *metaDataSize = copy_size;
}

static int send_meta_data(rtmp_impl_context* rtmp_context)
{
    uint8_t* meta_data = NULL;
    int meta_data_size;

    build_flv_meta_data(rtmp_context, &meta_data, &meta_data_size);

    memcpy(rtmp_context->rtmp_body_data + RTMP_MAX_HEADER_SIZE, meta_data, meta_data_size);
    free(meta_data);

    PILI_RTMPPacket packet;
    packet.m_nChannel = 0x03;     // control channel (invoke)
    packet.m_headerType = RTMP_PACKET_SIZE_LARGE;
    packet.m_packetType = RTMP_PACKET_TYPE_INFO;
    packet.m_nTimeStamp = 0;
    packet.m_nInfoField2 = rtmp_context->rtmp->m_stream_id;
    packet.m_hasAbsTimestamp = TRUE;

    packet.m_nBodySize = (uint32_t)meta_data_size;
    packet.m_body = rtmp_context->rtmp_body_data + RTMP_MAX_HEADER_SIZE;

    if (!PILI_RTMP_SendPacket(rtmp_context->rtmp, &packet, FALSE, rtmp_context->rtmp_error)) {
        ALOGE("[%s]: failed to send meta data!\n", __FUNCTION__);
        return -1;
    }

    return 0;
}

static int send_packet(rtmp_impl_context* rtmp_context, int packet_type, uint8_t* data, int32_t data_length, int64_t pts)
{
    int ret = 0;
    PILI_RTMP* rtmp = rtmp_context->rtmp;
    RTMPError* rtmp_error = rtmp_context->rtmp_error;

    PILI_RTMPPacket rtmp_packet;
    PILI_RTMPPacket_Reset(&rtmp_packet);

    rtmp_packet.m_nChannel = 0x04;
    rtmp_packet.m_headerType = RTMP_PACKET_SIZE_LARGE;
    if (packet_type == RTMP_PACKET_TYPE_AUDIO && data_length != 4) {
        rtmp_packet.m_headerType = RTMP_PACKET_SIZE_MEDIUM;
    }
    rtmp_packet.m_packetType = (uint8_t)packet_type;
    rtmp_packet.m_nTimeStamp = (uint32_t)pts;
    if (rtmp) {
        rtmp_packet.m_nInfoField2 = rtmp->m_stream_id;
    }
    rtmp_packet.m_hasAbsTimestamp = FALSE;

    memcpy(rtmp_context->rtmp_body_data + RTMP_MAX_HEADER_SIZE, data, data_length);
    rtmp_packet.m_body = (char*)rtmp_context->rtmp_body_data + RTMP_MAX_HEADER_SIZE;
    rtmp_packet.m_nBodySize = (uint32_t)data_length;

    ret = PILI_RTMP_SendPacket(rtmp, &rtmp_packet, FALSE, rtmp_error);
    return ret;
}

static int send_audio_header(rtmp_impl_context* rtmp_context,
                             const uint8_t* data,
                             const int32_t data_length,
                             const int64_t pts)
{
    int result = 0;
    int32_t  output_length = data_length + 2;
    uint8_t* output = rtmp_context->bitstream;//(uint8_t *)malloc(sizeof(uint8_t) * output_length);
    uint32_t offset = 0;

    //flv AudioTagHeader
    output[offset++] = 0xAF; // sound format aac
    output[offset++] = 0x00; // aac raw data

    //flv VideoTagBody --raw aac data
    memcpy(output + offset, data, data_length);

    result = send_packet(rtmp_context, RTMP_PACKET_TYPE_AUDIO, output, output_length, pts);
    //free(output);
    //output = NULL;

    return result;
}

static int send_audio_frame(rtmp_impl_context* rtmp_context,
                            const uint8_t* data,
                            const int32_t data_length,
                            const int64_t pts)
{
    int result = 0;
    int32_t  output_length = data_length + 2;
    uint8_t* output = rtmp_context->bitstream;//(uint8_t *)malloc(sizeof(uint8_t) * output_length);
    uint32_t offset = 0;

    //flv AudioTagHeader
    output[offset++] = 0xAF; // sound format aac
    output[offset++] = 0x01; //aac raw data

    //flv VideoTagBody --raw aac data
    memcpy(output + offset, data, data_length);

    result = send_packet(rtmp_context, RTMP_PACKET_TYPE_AUDIO, output, output_length, pts);
    //free(output);
    //output = NULL;

    return result;
}

static int send_video_header(rtmp_impl_context* rtmp_context,
                             const uint8_t* sps,
                             const int32_t sps_length,
                             const uint8_t* pps,
                             const int32_t pps_length,
                             const int64_t pts)
{
    int result = 0;

    uint8_t* output = rtmp_context->bitstream;//(uint8_t *)malloc(sizeof(uint8_t) * 1024);
    uint32_t offset = 0;


    //flv VideoTagHeader
    output[offset++] = 0x17; //key frame, AVC
    output[offset++] = 0x00; //avc sequence header
    output[offset++] = 0x00; //composit time ??????????
    output[offset++] = 0x00; // composit time
    output[offset++] = 0x00; //composit time

    //flv VideoTagBody --AVCDecoderCOnfigurationRecord
    output[offset++] = 0x01; //configurationversion
    output[offset++] = sps[1]; //avcprofileindication
    output[offset++] = sps[2]; //profilecompatibilty
    output[offset++] = sps[3]; //avclevelindication
    output[offset++] = 0xff; //reserved + lengthsizeminusone

    //sps
    output[offset++] = 0xe1; //numofsequenceset
    output[offset++] = (uint8_t)(sps_length >> 8); //sequence parameter set length high 8 bits
    output[offset++] = (uint8_t)(sps_length); //sequence parameter set  length low 8 bits
    memcpy(output + offset, sps, sps_length); //H264 sequence parameter set
    offset += sps_length;

    //pps
    output[offset++] = 0x01; //numofpictureset
    output[offset++] = (uint8_t)(pps_length >> 8); //picture parameter set length high 8 bits
    output[offset++] = (uint8_t)(pps_length); //picture parameter set length low 8 bits
    memcpy(output + offset, pps, pps_length); //H264 picture parameter set
    offset += pps_length;

    result = send_packet(rtmp_context, RTMP_PACKET_TYPE_VIDEO, output, offset, pts);
    //free(output);

    return result;
}

static int send_video_frame(rtmp_impl_context* rtmp_context,
                            const uint8_t* data,
                            const int32_t data_length,
                            const int64_t pts,
                            const int64_t dts,
                            const bool key_frame)
{
    int result = 0;
    int32_t  output_length = data_length + 9;
    uint8_t* output = rtmp_context->bitstream;//(uint8_t *)malloc(sizeof(uint8_t) * output_length);
    uint32_t offset = 0;

    int time_offset = (int)(pts - dts);

    //flv VideoTagHeader
    if (key_frame) {
        output[offset++] = 0x17; //key frame, AVC
    } else {
        output[offset++] = 0x27; //not key frame, AVC
    }
    output[offset++] = 0x01; //avc NALU unit
    output[offset++] = (uint8_t)(time_offset >> 24); //composit time ??????????
    output[offset++] = (uint8_t)(time_offset >> 16); // composit time
    output[offset++] = (uint8_t)(time_offset >> 8);  //composit time

    output[offset++] = (uint8_t)(data_length >> 24); //nal length
    output[offset++] = (uint8_t)(data_length >> 16); //nal length
    output[offset++] = (uint8_t)(data_length >> 8); //nal length
    output[offset++] = (uint8_t)(data_length); //nal length
    memcpy(output + offset, data, data_length);

    result = send_packet(rtmp_context, RTMP_PACKET_TYPE_VIDEO, output, output_length, pts);
    //free(output);

    return result;
}

void rtmp_impl_destroy(rtmp_impl_context* rtmp_context)
{
    if (rtmp_context) {
        if (rtmp_context->audio_info) {
            free(rtmp_context->audio_info);
            rtmp_context->audio_info = NULL;
        }
        if (rtmp_context->video_info) {
            free(rtmp_context->video_info);
            rtmp_context->video_info = NULL;
        }

        if (rtmp_context->callback) {
            free(rtmp_context->callback);
            rtmp_context->callback = NULL;
        }

        if (rtmp_context->rtmp) {
            PILI_RTMP_Free(rtmp_context->rtmp);
            rtmp_context->rtmp = NULL;
        }
        if (rtmp_context->rtmp_error) {
            free(rtmp_context->rtmp_error);
            rtmp_context->rtmp_error = NULL;
        }

        if (rtmp_context->rtmp_body_data) {
            aligned_free(rtmp_context->rtmp_body_data);
            rtmp_context->rtmp_body_data = NULL;
        }
        if (rtmp_context->aac_codec_specific_data) {
            aligned_free(rtmp_context->aac_codec_specific_data);
            rtmp_context->aac_codec_specific_data = NULL;
        }
        if (rtmp_context->sps) {
            aligned_free(rtmp_context->sps);
            rtmp_context->sps = NULL;
        }
        if (rtmp_context->pps) {
            aligned_free(rtmp_context->pps);
            rtmp_context->pps = NULL;
        }
        if (rtmp_context->bitstream) {
            aligned_free(rtmp_context->bitstream);
            rtmp_context->bitstream = NULL;
        }

        free(rtmp_context);
        rtmp_context = NULL;
    }
}

rtmp_impl_context* rtmp_impl_create()
{
    rtmp_impl_context* rtmp_context = (rtmp_impl_context *)calloc(1, sizeof(rtmp_impl_context));
    rtmp_impl_callback_context* rtmp_callback_context =
            (rtmp_impl_callback_context *)calloc(1, sizeof(rtmp_impl_callback_context));

    uint8_t* rtmp_body_data = (uint8_t *)aligned_malloc(sizeof(uint8_t) * 409600, 16);
    uint8_t* aac_codec_specific_data = (uint8_t *)aligned_malloc(sizeof(uint8_t) * 1024, 16);
    uint8_t* sps = (uint8_t *)aligned_malloc(sizeof(uint8_t) * 4096, 16);
    uint8_t* pps = (uint8_t *)aligned_malloc(sizeof(uint8_t) * 4096, 16);
    uint8_t* bitstream = (uint8_t *)aligned_malloc(sizeof(uint8_t*) * 409600, 16);


    rtmp_context->callback = rtmp_callback_context;
    rtmp_context->rtmp_body_data = rtmp_body_data;
    rtmp_context->aac_codec_specific_data = aac_codec_specific_data;
    rtmp_context->aac_codec_specific_data_length = 0;
    rtmp_context->sps = sps;
    rtmp_context->sps_length = 0;
    rtmp_context->pps = pps;
    rtmp_context->pps_length = 0;
    rtmp_context->bitstream = bitstream;
    rtmp_context->audio_info = NULL; //audio_info;
    rtmp_context->video_info = NULL; //video_info;
    return rtmp_context;
}

void* rtmp_impl_set_weak_thiz(rtmp_impl_context* rtmp_context, void* weak_thiz)
{
    if (rtmp_context == NULL) {
        return NULL;
    }

    void *prev_weak_thiz = rtmp_context->weak_thiz;

    rtmp_context->weak_thiz = weak_thiz;

    return prev_weak_thiz;
}

void* rtmp_impl_get_weak_thiz(rtmp_impl_context* rtmp_context)
{
    if (rtmp_context == NULL) {
        return NULL;
    }

    return rtmp_context->weak_thiz;
}

void rtmp_impl_set_callback(rtmp_impl_context* rtmp_context, rtmp_impl_callback_context* callback)
{
    if (rtmp_context == NULL) {
        return;
    }

    rtmp_context->callback->connecting_callback = callback->connecting_callback;
    rtmp_context->callback->connected_callback = callback->connected_callback;
    rtmp_context->callback->disconnected_callback = callback->disconnected_callback;
    rtmp_context->callback->error_callback = callback->error_callback;
}

int rtmp_impl_add_audio_track(rtmp_impl_context* rtmp_context,
                              const int32_t sample_rate,
                              const int32_t channel,
                              const int32_t bits_per_sample,
                              const int32_t bitrate_kbps)
{
    if (rtmp_context == NULL) {
        return -1;
    }

    //audio_stream_info* audio_info = rtmp_context->audio_info;
    audio_stream_info* audio_info = calloc(1, sizeof(audio_stream_info));
    audio_info->sample_rate = sample_rate;
    audio_info->channel = channel;
    audio_info->bits_per_sample = bits_per_sample;
    audio_info->bitrate_kbps = bitrate_kbps;
    rtmp_context->audio_info = audio_info;

    ALOGD("[%s]: audio info sample_rate=%d channel=%d bits_per_sample=%d bitrate_kbps=%d",
          __FUNCTION__, sample_rate, channel, bits_per_sample, bitrate_kbps);

    return 0;
}

int rtmp_impl_add_video_track(rtmp_impl_context* rtmp_context,
                              const int32_t width,
                              const int32_t height,
                              const int32_t frame_rate,
                              const int32_t bitrate_kbps)
{
    if (rtmp_context == NULL) {
        return -1;
    }

    //video_stream_info* video_info = rtmp_context->video_info;
    video_stream_info* video_info = calloc(1, sizeof(video_stream_info));
    video_info->width = width;
    video_info->height = height;
    video_info->frame_rate = frame_rate;
    video_info->bitrate_kbps = bitrate_kbps;
    rtmp_context->video_info = video_info;

    ALOGD("[%s]: video info width=%d height=%d frame_rate=%d bitrate_kbps=%d",
          __FUNCTION__, width, height, frame_rate, bitrate_kbps);

    return 0;
}

int rtmp_impl_start(rtmp_impl_context* rtmp_context, const char *url)
{
    if (rtmp_context == NULL) {
        return -1;
    }

    PILI_RTMP* rtmp = PILI_RTMP_Alloc();
    RTMPError* rtmp_error = (RTMPError *)calloc(1, sizeof(RTMPError));

    rtmp_context->rtmp = rtmp;
    rtmp_context->rtmp_error = rtmp_error;

    PILI_RTMP_Init(rtmp);
    RTMP_LogSetCallback(rtmp_log_debug);
    RTMP_LogSetLevel(RTMP_LOGDEBUG);

    if (rtmp_context->callback->connecting_callback) {
        rtmp_context->callback->connecting_callback(rtmp_context);
    }
    ALOGD("[%s]: Connecting to RTMP URL %s...\n", __FUNCTION__, url);

    if (PILI_RTMP_SetupURL(rtmp, url, rtmp_error) == FALSE) {
        //rtmp_impl_stop(rtmp_context);
        return -1;
    }

    rtmp->m_errorCallback = rtmp_error_callback;
    rtmp->m_connCallback = rtmp_connection_time_callback;
    rtmp->m_userData = (void *)rtmp_context;
    rtmp->m_msgCounter = 1;
    rtmp->Link.timeout = RTMP_RECEIVE_TIMEOUT;
    rtmp->Link.send_timeout = RTMP_SEND_TIMEOUT;
    rtmp->m_outChunkSize = 4096;

    // must be called before connecting, setting readable and writable(eg, publish stream)
    PILI_RTMP_EnableWrite(rtmp); //set it to publish

    // connect server
    if (PILI_RTMP_Connect(rtmp, NULL, rtmp_error) == FALSE) {
        ALOGW("[%s]: PILI_RTMP_Connect(%s) failed(code：%d reason: %s)\n",
              __FUNCTION__, url, rtmp_error->code, rtmp_error->message);
        if (rtmp_context->callback->error_callback) {
            rtmp_context->callback->error_callback(rtmp_context, rtmp_error->message);
        }
        return -1;
    }

    // connect stream
    if (PILI_RTMP_ConnectStream(rtmp, 0, rtmp_error) == FALSE) {
        ALOGW("[%s]: PILI_RTMP_ConnectStream(%s) failed(code:%d reason: %s)\n",
              __FUNCTION__, url, rtmp_error->code, rtmp_error->message);
        if (rtmp_context->callback->error_callback) {
            rtmp_context->callback->error_callback(rtmp_context, rtmp_error->message);
        }
        return -1;
    }

    ALOGD("[%s]: Connection to %s successful\n", __FUNCTION__, url);

    if (rtmp_context->callback->connected_callback) {
        rtmp_context->callback->connected_callback(rtmp_context, url);
    }

    // send rtmp meta data
    return send_meta_data(rtmp_context);
}

int rtmp_impl_stop(rtmp_impl_context* rtmp_context)
{
    if (rtmp_context == NULL) {
        return 0;
    }

    if (rtmp_context->callback->disconnected_callback) {
        rtmp_context->callback->disconnected_callback(rtmp_context);
    }

    if (rtmp_context->rtmp) {
        PILI_RTMP_Close(rtmp_context->rtmp, rtmp_context->rtmp_error);
        PILI_RTMP_Free(rtmp_context->rtmp);
        rtmp_context->rtmp = NULL;
    }

    if (rtmp_context->rtmp_error) {
        free(rtmp_context->rtmp_error);
        rtmp_context->rtmp_error = NULL;
    }

    return 0;
}

int rtmp_impl_write_audio_header(rtmp_impl_context* rtmp_context,
                                 const uint8_t* data,
                                 const int32_t data_length,
                                 const int64_t pts)
{
    memcpy(rtmp_context->aac_codec_specific_data, data, data_length);
    rtmp_context->aac_codec_specific_data_length = data_length;

    send_audio_header(rtmp_context, rtmp_context->aac_codec_specific_data,
                      rtmp_context->aac_codec_specific_data_length, pts);

    return 0;
}

int rtmp_impl_write_video_header(rtmp_impl_context* rtmp_context,
                                 const uint8_t* header,
                                 const int32_t header_length,
                                 const int64_t pts)
{
    uint8_t* payload = (unsigned char*)header;
    int payload_size = header_length;
    uint8_t* nal_start;
    int nal_size;
    int nal_type;
    int start_code_length;
    while (get_nal_unit(payload, payload_size,
                        &nal_start, &nal_size, &start_code_length, &nal_type) == 0) {
        payload += nal_size;
        payload_size -= nal_size;
        if (nal_type == AVC_SPS || nal_type == AVC_PPS) {
            ALOGI("[%s]: annexb demux %dB, pts=%lld, frame=%dB, nalu=%d",
                  __FUNCTION__, header_length, pts, nal_size, nal_type);
        }
        nal_start += start_code_length; // skip start code.
        nal_size -= start_code_length;

        // for sps
        if (nal_type == AVC_SPS) {
            memcpy(rtmp_context->sps, nal_start, nal_size);
            rtmp_context->sps_length = nal_size;
            continue;
        }
        // for pps
        if (nal_type == AVC_PPS) {
            memcpy(rtmp_context->pps, nal_start, nal_size);
            rtmp_context->pps_length = nal_size;
            continue;
        }
    }

    if (rtmp_context->sps_length < 0 || rtmp_context->pps_length < 0) {
        ALOGW("%s: sps or pps not received", __FUNCTION__);
        return -1;
    }

    send_video_header(rtmp_context, rtmp_context->sps, rtmp_context->sps_length,
                      rtmp_context->pps, rtmp_context->pps_length, pts);

    return 0;
}

int rtmp_impl_write_audio(rtmp_impl_context* rtmp_context,
                          const uint8_t* data,
                          const int32_t data_length,
                          const int64_t pts)
{
    int result = send_audio_frame(rtmp_context, data, data_length, pts);
    return result;
}

int rtmp_impl_write_video(rtmp_impl_context* rtmp_context,
                          const uint8_t* data,
                          const int32_t data_length,
                          const int64_t pts,
                          const int64_t dts)
{
    int result = 0;
    uint8_t* video_data = NULL;
    int video_data_length = 0;
    uint8_t* nal_start;
    int nal_size;
    int nal_type;
    int start_code_length;
    bool sps_changed = false;
    bool pps_changed = false;

    //memcpy(rtmp_context->bitstream, data, (size_t)data_length);
    //memset(rtmp_context->bitstream + data_length, 0, 4);
    //uint8_t* payload = rtmp_context->bitstream;
    uint8_t* payload = (unsigned char*)data;
    int payload_size = data_length;

    while (payload_size > 0 && get_nal_unit(payload, payload_size,
                                            &nal_start, &nal_size, &start_code_length, &nal_type) == 0) {
        payload += nal_size;
        payload_size -= nal_size;
        nal_start += start_code_length; // skip start code.
        nal_size -= start_code_length;

        if (payload_size > 0) {
            ALOGD("[%s]: residual_payload_size=%d", __FUNCTION__, payload_size);
        }

        // for sps
        if (nal_type == AVC_SPS) {
            ALOGI("[%s]: sps updated sc=%dB len=%dB frame=%dB",
                  __FUNCTION__, start_code_length, nal_size, nal_size + start_code_length);
            memcpy(rtmp_context->sps, nal_start, (size_t)nal_size);
            rtmp_context->sps_length = nal_size;
            sps_changed = true;
            continue;
        }
        // for pps
        if (nal_type == AVC_PPS) {
            ALOGI("[%s]: pps updated sc=%dB len=%dB frame=%dB",
                  __FUNCTION__, start_code_length, nal_size, nal_size + start_code_length);
            memcpy(rtmp_context->pps, nal_start, (size_t)nal_size);
            rtmp_context->pps_length = nal_size;
            pps_changed = true;
            continue;
        }
        // filter extra sei/aud payload.
        if (nal_type == AVC_SEI || nal_type == AVC_AUD) {
            ALOGD("[%s]: skip useless nal_unit_type(%d) frame=%dB",
                  __FUNCTION__, nal_type, nal_size + start_code_length);
            continue;
        }

        video_data = nal_start;
        video_data_length = nal_size;

        //ALOGD("[%s]: nal unit type = %d length = %d pts = %lld",
        //        __FUNCTION__, nal_type, video_data_length, pts);
    }

    if (sps_changed || pps_changed) {
        result = send_video_header(rtmp_context,rtmp_context->sps, rtmp_context->sps_length,
                                   rtmp_context->pps, rtmp_context->pps_length, pts);
    }

    if (video_data && video_data_length /*&& result == 0*/) {
        bool key_frame = (nal_type == 0x05);
        //ALOGD("[%s]: send video data nal_type = %d length = %d pts = %lld", __FUNCTION__, nal_type,video_data_length, pts);
        return send_video_frame(rtmp_context, video_data, video_data_length, pts, dts, key_frame);
    }
    return result;
}