#ifndef H264_NAL_H_
#define H264_NAL_H_

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#ifdef __cplusplus
extern "C"{
#endif

#define NEXT_32_BITS(x) (x[0]<<3)|(x[1]<<2)|(x[2]<<1)|(x[3])
#define NEXT_24_BITS(x) (x[0]<<2)|(x[1]<<1)|(x[2])

enum AVC_NAL_UNIT_TYPE {
    AVC_NON_IDR        = 1,  /// Coded slice of a non-IDR picture
    AVC_DPA            = 2,  /// Coded slice data partition A
    AVC_DPB            = 3,  /// Coded slice data partition B
    AVC_DPC            = 4,  /// Coded slice data partition C
    AVC_IDR            = 5,  /// Coded slice of an IDR picture
    AVC_SEI            = 6,  /// Supplemental enhancement information (SEI)
    AVC_SPS            = 7,  /// Sequence parameter set
    AVC_PPS            = 8,  /// Picture parameter set
    AVC_AUD            = 9,  /// Access unit delimiter
    AVC_EOSEQUENCE     = 10, /// End of sequence
    AVC_EOSTREAM       = 11, /// End of stream
    AVC_FILTER_DATA    = 12, /// Filler data
    AVC_SPS_EXT        = 13, /// Sequence parameter set extension
    AVC_PREFIX_NALU    = 14, /// Prefix NAL unit
    AVC_SUBSET_SPS     = 15  /// Subset sequence parameter set
};

// @input:          input data buffer.
// @inputLength:    input data buffer length.
// @output:         a valid nal unit data buffer address(with start code).
// @outputLength:   a valid nal unit data buffer length(with start code).
// @startCodeLength: start code length 3 or 4 (00|00|00|01 or 00|00|01)
// @nal_tyle:       a valid nal unit type.
static int get_nal_unit(uint8_t *input,
                        int32_t in_length,
                        uint8_t **output,
                        int32_t *out_length,
                        int32_t *start_code_length,
                        int32_t *nal_type) {
    /*find 0x000001 */
    uint8_t *head0 = input, *end = input + in_length;
    uint8_t *head1 = NULL;

    if (!input || in_length <= 0) {
        if (output) {
            *output = NULL;
        }
        if (out_length) {
            *out_length = 0;
        }
        if (start_code_length) {
            *start_code_length = 0;
        }
        return -1;
    }

    while ((NEXT_32_BITS(head0) != 0x00000001)) {
        head0++;
        if (head0 == end)
            break;
    }

    if (end == head0) {
        if (output) {
            *output = NULL;
        }
        if (out_length) {
            *out_length = 0;
        }
        if (start_code_length) {
            *start_code_length = 0;
        }
        return -1;//no sc
    }

    // sc find.
    head1 = head0;
    if (NEXT_32_BITS(head0) == 0x00000001) {
        head1 += 4;
        if (start_code_length) {
            *start_code_length = 4;
        }
    }

    *nal_type = (head1[0] & 0x1f);//((head[0] >> 3) & 0x1f);
    //printf("head[0] = 0x%x\n", head[0]);

    // scan to buffer end or find next nal with start code.
    while ((end > head1) && (NEXT_32_BITS(head1) != 0x00000001)) {
        head1++;
    }

    if (output) {
        *output = head0;
    }
    if (out_length) {
        *out_length = head1 - head0;
    }

    return 0;
}

#ifdef __cplusplus
}
#endif

#endif // H264_NAL_H_