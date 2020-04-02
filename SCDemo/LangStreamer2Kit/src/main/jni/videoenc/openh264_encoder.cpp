#include "openh264_encoder.h"
#include <assert.h>
#include <android/log.h>
#include "api/svc/codec_api.h"
#include "api/svc/codec_app_def.h"
#include "api/svc/codec_def.h"

#define LOG_TAG "openh264_encoder"

#define H264_ENCODER_LOGD(...) ((void)__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__))
#define H264_ENCODER_LOGI(...) ((void)__android_log_print(ANDROID_LOG_INFO , LOG_TAG, __VA_ARGS__))
#define H264_ENCODER_LOGW(...) ((void)__android_log_print(ANDROID_LOG_WARN , LOG_TAG, __VA_ARGS__))
#define H264_ENCODER_LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__))


typedef struct EncoderContext{
    void* weakThiz;
    ISVCEncoder* encoder;
    EncoderCallback* encoderCallback;
    EncodedFrame* encodedFrame;
}EncoderContext;

// Initialization parameters.
// There are two ways to initialize. There is SEncParamBase (cleared with
// memset(&p, 0, sizeof(SEncParamBase)) used in Initialize, and SEncParamExt
// which is a superset of SEncParamBase (cleared with GetDefaultParams) used
// in InitializeExt.
static SEncParamExt createEncoderParams(ISVCEncoder* encoder, EncoderConfig* config) {
    SEncParamExt encoder_params;
    encoder->GetDefaultParams(&encoder_params);
    encoder_params.iUsageType = CAMERA_VIDEO_REAL_TIME;
    encoder_params.iPicWidth = config->width;
    encoder_params.iPicHeight = config->height;
    // |encoder_params| uses bit/s,
    encoder_params.iTargetBitrate = config->bitrateBps;
    encoder_params.iMaxBitrate = (int)((float)config->bitrateBps * 1.2f);
    // Rate Control mode
    encoder_params.iRCMode = RC_BITRATE_MODE;
    encoder_params.fMaxFrameRate = config->fps;

    // The following parameters are extension parameters (they're in SEncParamExt,
    // not in SEncParamBase).
    encoder_params.bEnableFrameSkip = false;

    // |uiIntraPeriod|    - multiple of GOP size
    // |keyFrameInterval| - number of frames
    encoder_params.uiIntraPeriod = (unsigned int)(config->keyFrameIntervalSec * config->fps);
    encoder_params.uiMaxNalSize = 0;
    // Threading model: use auto.
    //  0: auto (dynamic imp. internal encoder)
    //  1: single thread (default value)
    // >1: number of threads
    encoder_params.iMultipleThreadIdc = 1;

    encoder_params.iSpatialLayerNum = 1;

    // The base spatial layer 0 is the only one we use.
    encoder_params.sSpatialLayers[0].iVideoWidth = encoder_params.iPicWidth;
    encoder_params.sSpatialLayers[0].iVideoHeight = encoder_params.iPicHeight;
    encoder_params.sSpatialLayers[0].fFrameRate = encoder_params.fMaxFrameRate;
    encoder_params.sSpatialLayers[0].iSpatialBitrate = encoder_params.iTargetBitrate;
    encoder_params.sSpatialLayers[0].iMaxSpatialBitrate = encoder_params.iMaxBitrate;
    // Slice num according to number of threads.
    encoder_params.sSpatialLayers[0].sSliceArgument.uiSliceMode = SM_SINGLE_SLICE;
    //@Eric -- add for CONSTANT_ID(sps,pps)
    encoder_params.eSpsPpsIdStrategy = CONSTANT_ID;

    return encoder_params;
}

static size_t calcBufferSize(int video_format, int width, int height) {
    assert(width >= 0);
    assert(height >= 0);
    size_t buffer_size = 0;
    switch (video_format) {
        case EVideoFormatType::videoFormatI420:
        case EVideoFormatType::videoFormatYV12:
        case EVideoFormatType::videoFormatNV12: {
            int half_width = (width + 1) >> 1;
            int half_height = (height + 1) >> 1;
            buffer_size = width * height + half_width * half_height * 2;
            break;
        }
        case EVideoFormatType::videoFormatYUY2:
            buffer_size = width * height * 2;
        default:
            assert(false);
            break;
    }
    return buffer_size;
}

static void deinitializeEncoder(ISVCEncoder* encoder) {
    if (encoder) {
        encoder->Uninitialize();
        WelsDestroySVCEncoder(encoder);
    }
}

static ISVCEncoder* initializeEncoder(EncoderConfig* config, bool bDetailedLogging) {
    ISVCEncoder* encoder = NULL;
    // Create encoder.
    if (WelsCreateSVCEncoder(&encoder) != 0) {
        // Failed to create encoder.
        H264_ENCODER_LOGE("Failed to create OpenH264 encoder");
        return NULL;
    }

    if (bDetailedLogging) {
        int trace_level = WELS_LOG_DETAIL;
        encoder->SetOption(ENCODER_OPTION_TRACE_LEVEL, &trace_level);
    }
    // else WELS_LOG_DEFAULT is used by default.

    SEncParamExt encoder_params = createEncoderParams(encoder, config);
    // Initialize.
    if (encoder->InitializeExt(&encoder_params) != 0) {
        H264_ENCODER_LOGE("Failed to initialize OpenH264 encoder");
        deinitializeEncoder(encoder);
        return NULL;
    }

    return encoder;
}

void releaseEncoder(EncoderContext* context) {
    if (context) {
        deinitializeEncoder(context->encoder);
        context->encoder = NULL;

        if (context->encodedFrame) {
            if (context->encodedFrame->data) {
                free(context->encodedFrame->data);
                context->encodedFrame->data = NULL;
            }
            free(context->encodedFrame);
            context->encodedFrame = NULL;
        }
        if (context->encoderCallback) {
            context->encoderCallback = NULL;
        }
        context = NULL;
    }
}

EncoderContext* createEncoder(EncoderConfig* config, bool bDetailedLogging) {
    EncoderContext* context = (EncoderContext *)calloc(1, sizeof(EncoderContext));
    ISVCEncoder* encoder = initializeEncoder(config, bDetailedLogging);
    if (!encoder) {
        releaseEncoder(context);
        return NULL;
    }
    // Keep reference to encoder.
    context->encoder = encoder;

    int video_format = EVideoFormatType::videoFormatI420;
    encoder->SetOption(ENCODER_OPTION_DATAFORMAT, &video_format);

    context->encodedFrame = (EncodedFrame *)calloc(1, sizeof(EncodedFrame));

    // Initialize encoded image. Default buffer size: size of unencoded data.
    size_t encodedSize = calcBufferSize(video_format, config->width, config->height);
    context->encodedFrame->data = (uint8_t *)malloc(sizeof(uint8_t) * encodedSize);

    // callback context allocation
    context->encoderCallback = (EncoderCallback *)calloc(1, sizeof(EncoderCallback));

    return context;
}

void* setEncoderWeakThiz(EncoderContext* context, void* weakThiz) {
    if (context == NULL) {
        return NULL;
    }

    void *prevWeakThiz = context->weakThiz;
    context->weakThiz = weakThiz;

    return prevWeakThiz;
}

void* getEncoderWeakThiz(EncoderContext* context) {
    if (context == NULL) {
        return NULL;
    }

    return context->weakThiz;
}

void setEncoderCallback(EncoderContext* context, EncoderCallback* callback) {
    if (context) {
        H264_ENCODER_LOGI("set encoder callback, output frames will be available");
        context->encoderCallback->onEncodedBitstream = callback->onEncodedBitstream;
    }
}

void setEncoderBitrate(EncoderContext* context, int bitrateBps) {
    if (context) {
        ISVCEncoder* encoder = context->encoder;
        if (encoder) {
            SBitrateInfo target_bitrate;
            memset(&target_bitrate, 0, sizeof(SBitrateInfo));
            target_bitrate.iLayer = SPATIAL_LAYER_ALL;
            target_bitrate.iBitrate = bitrateBps;
            encoder->SetOption(ENCODER_OPTION_BITRATE, &target_bitrate);

            SBitrateInfo target_max_bitrate;
            memset(&target_max_bitrate, 0, sizeof(SBitrateInfo));
            target_max_bitrate.iLayer = SPATIAL_LAYER_ALL;
            target_max_bitrate.iBitrate = (int)((float)bitrateBps * 1.1f);
            encoder->SetOption(ENCODER_OPTION_MAX_BITRATE, &target_bitrate);
        }
    }
}

int getEncoderBitrate(EncoderContext* context) {
    if (context) {
        ISVCEncoder* encoder = context->encoder;
        if (encoder) {
            SBitrateInfo target_bitrate;
            memset(&target_bitrate, 0, sizeof(SBitrateInfo));
            if (encoder->GetOption(ENCODER_OPTION_MAX_BITRATE, &target_bitrate) == CM_RETURN::cmResultSuccess) {
                return target_bitrate.iBitrate;
            }
        }
    }
    return 0;
}

int getEncoderWidth(EncoderContext* context) {
    if (context) {
        ISVCEncoder* encoder = context->encoder;
        if (encoder) {
            SEncParamExt encParamExt;
            if (encoder->GetOption(ENCODER_OPTION_SVC_ENCODE_PARAM_EXT, &encParamExt) == CM_RETURN::cmResultSuccess) {
                return encParamExt.iPicWidth;
            } else {
                H264_ENCODER_LOGW("getEncoderWidth from encoder failed");
            }
        }
    }
    return 0;
}

int getEncoderHeight(EncoderContext* context) {
    if (context) {
        ISVCEncoder* encoder = context->encoder;
        if (encoder) {
            SEncParamExt encParamExt;
            if (encoder->GetOption(ENCODER_OPTION_SVC_ENCODE_PARAM_EXT, &encParamExt) == CM_RETURN::cmResultSuccess) {
                return encParamExt.iPicHeight;
            } else {
                H264_ENCODER_LOGW("getEncoderHeight from encoder failed");
            }
        }
    }
    return 0;
}


static void copyEncodedFrames(EncodedFrame* encodedFrame, SFrameBSInfo* bsInfo) {

    //H264_ENCODER_LOGD("[%s]: Enter", __FUNCTION__);
    encodedFrame->length = 0;

    for (int layer = 0; layer < bsInfo->iLayerNum; ++layer) {
        const SLayerBSInfo& layerInfo = bsInfo->sLayerInfo[layer];
        size_t layer_len = 0;
        for (int nal = 0; nal < layerInfo.iNalCount; ++nal) {
            layer_len += layerInfo.pNalLengthInByte[nal];
        }
        // Copy the entire layer's data (including start codes).
        memcpy(encodedFrame->data + encodedFrame->length, layerInfo.pBsBuf, layer_len);
        encodedFrame->length += layer_len;
    }

    // Copy timestamp
    encodedFrame->ptsMs = bsInfo->uiTimeStamp;

    // Set Frame type
    int flags = (bsInfo->eFrameType == EVideoFrameType::videoFrameTypeIDR ? 1 : 0);
    encodedFrame->flags = flags;

    //H264_ENCODER_LOGD("[%s]: Leave", __FUNCTION__);
}

void encodeParameterSets(EncoderContext* context)
{
    if (!context) {
        return;
    }

    ISVCEncoder* encoder = context->encoder;
    if (!encoder) {
        return;
    }

    H264_ENCODER_LOGD("[%s]: EncodeParameterSets", __FUNCTION__);

    SFrameBSInfo sFbi;
    encoder->EncodeParameterSets(&sFbi);

    H264_ENCODER_LOGD("[%s]: copyEncodedFrames", __FUNCTION__);
    EncodedFrame* encodedFrame = context->encodedFrame;
    copyEncodedFrames(encodedFrame, &sFbi);
    // force setting flags
    encodedFrame->ptsMs = 0;
    encodedFrame->flags = 2;

    if (encodedFrame->length > 0) {
        H264_ENCODER_LOGD("[%s]: callback sps and pps, length = %d",
                __FUNCTION__, encodedFrame->length);
        EncoderCallback* encoderCallback = context->encoderCallback;
        if (encoderCallback) {
            encoderCallback->onEncodedBitstream(context, encodedFrame);
        }
    }
}

int encodeFrame(EncoderContext* context, ImageFrame* frame) {
    if (!context) {
        return -1;
    }
    ISVCEncoder* encoder = context->encoder;
    if (!encoder) {
        return -1;
    }

    // EncodeFrame input.
    SSourcePicture picture;
    memset(&picture, 0, sizeof(SSourcePicture));
    picture.iPicWidth = frame->width;
    picture.iPicHeight = frame->height;
    picture.iColorFormat = EVideoFormatType::videoFormatI420;
    picture.uiTimeStamp = frame->ptsMs;
    picture.iStride[0] = frame->width;
    picture.iStride[1] = frame->width/2;
    picture.iStride[2] = frame->width/2;
    picture.pData[0] = frame->y;
    picture.pData[1] = frame->u;
    picture.pData[2] = frame->v;

    // EncodeFrame output.
    SFrameBSInfo info;
    memset(&info, 0, sizeof(SFrameBSInfo));

    // Encode!
    int enc_ret = encoder->EncodeFrame(&picture, &info);
    if (enc_ret != 0) {
        H264_ENCODER_LOGE("OpenH264 frame encoding failed, EncodeFrame returned = %d", enc_ret);
        return enc_ret;
    }

    // get encoded bitstream
    EncodedFrame* encodedFrame = context->encodedFrame;
    copyEncodedFrames(encodedFrame, &info);

    if (encodedFrame->length > 0) {
        EncoderCallback* encoderCallback = context->encoderCallback;
        if (encoderCallback) {
            encoderCallback->onEncodedBitstream(context, encodedFrame);
        }
    }

    return 0;
}
