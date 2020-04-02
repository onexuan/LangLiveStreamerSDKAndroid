#ifndef H264_ENCODER_H_
#define H264_ENCODER_H_

#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#ifdef __cplusplus
extern "C"{
#endif

typedef struct ImageFrame {
    int width;
    int height;
    uint8_t *data;
    uint8_t *y;
    uint8_t *u;
    uint8_t *v;
    long long ptsMs;
} ImageFrame;

typedef struct EncodedFrame {
    uint8_t* data;
    int length;
    long long ptsMs;
    int flags; // flags=2 header flags=1 IDR flags=0 P/B
}EncodedFrame;

typedef struct EncoderConfig {
    int width;
    int height;
    int fps;
    int bitrateBps;
    int keyFrameIntervalSec;
} EncoderConfig;

typedef struct EncoderCallback {
    void (*onEncodedBitstream)(void* userData, EncodedFrame* encodedFrame);
} EncoderCallback;

typedef struct EncoderContext EncoderContext;

EncoderContext* createEncoder(EncoderConfig* config, bool bDetailedLogging);

void releaseEncoder(EncoderContext* context);

void* setEncoderWeakThiz(EncoderContext* context, void* weak_thiz);

void* getEncoderWeakThiz(EncoderContext* context);

void setEncoderCallback(EncoderContext* context, EncoderCallback* callback);

void setEncoderBitrate(EncoderContext* context, int bitrateBps);

int getEncoderBitrate(EncoderContext* context);

int getEncoderWidth(EncoderContext* context);

int getEncoderHeight(EncoderContext* context);

void encodeParameterSets(EncoderContext* context);

int encodeFrame(EncoderContext* context, ImageFrame* frame);

#ifdef __cplusplus
}
#endif

#endif //H264_ENCODER_H_