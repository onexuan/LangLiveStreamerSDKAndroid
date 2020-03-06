package net.lang.streamer.engine;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;

import com.github.faucamp.simplertmp.DefaultRtmpPublisher;
import com.github.faucamp.simplertmp.RtmpHandler;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static android.media.MediaCodec.BUFFER_FLAG_CODEC_CONFIG;
import static android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME;

/**
 * Created by winlin on 5/2/15.
 * Updated by leoma on 4/1/16.
 * to POST the h.264/avc annexb frame over RTMP.
 * @see android.media.MediaMuxer https://developer.android.com/reference/android/media/MediaMuxer.html
 *
 * Usage:
 *      muxer = new SrsRtmp("rtmp://ossrs.net/live/yasea");
 *      muxer.start();
 *
 *      MediaFormat aformat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, asample_rate, achannel);
 *      // setup the aformat for audio.
 *      atrack = muxer.addTrack(aformat);
 *
 *      MediaFormat vformat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, vsize.width, vsize.height);
 *      // setup the vformat for video.
 *      vtrack = muxer.addTrack(vformat);
 *
 *      // encode the video frame from camera by h.264 codec to es and bi,
 *      // where es is the h.264 ES(element stream).
 *      ByteBuffer es, MediaCodec.BufferInfo bi;
 *      muxer.writeSampleData(vtrack, es, bi);
 *
 *      // encode the audio frame from microphone by aac codec to es and bi,
 *      // where es is the aac ES(element stream).
 *      ByteBuffer es, MediaCodec.BufferInfo bi;
 *      muxer.writeSampleData(atrack, es, bi);
 *
 *      muxer.stop();
 *      muxer.release();
 */
public class LangFlvMuxer extends IRtmpMediaMuxer {

    private static final int VIDEO_ALLOC_SIZE = 128 * 1024;
    private static final int AUDIO_ALLOC_SIZE = 4 * 1024;

    private volatile boolean connected = false;
    private DefaultRtmpPublisher publisher;
    private RtmpHandler mHandler;

    private Thread worker;
    private Thread bye;
    private final Object txFrameLock = new Object();

    private SnailFlv flv = new SnailFlv();
    private boolean needToFindKeyFrame = true;
    private SnailFlvFrame mVideoSequenceHeader;
    private SnailFlvFrame mAudioSequenceHeader;
    private LangAllocator mVideoAllocator = new LangAllocator(VIDEO_ALLOC_SIZE);
    private LangAllocator mAudioAllocator = new LangAllocator(AUDIO_ALLOC_SIZE);
    private ConcurrentLinkedQueue<SnailFlvFrame> mFlvTagCache = new ConcurrentLinkedQueue<>();

    private static final int VIDEO_TRACK = 100;
    private static final int AUDIO_TRACK = 101;
    private static final String TAG = "LangFlvMuxer";

    /**
     * constructor.
     * @param handler the rtmp event handler.
     */
    public LangFlvMuxer(RtmpHandler handler) {
        mHandler = handler;
        publisher = new DefaultRtmpPublisher(handler);
    }

    /**
     * get cached video frame number in publisher
     */
    @Override
    public AtomicInteger getVideoFrameCacheNumber() {
        return publisher == null ? null : publisher.getVideoFrameCacheNumber();
    }

    /**
     * get cached audio frame number in publisher
     */
    @Override
    public AtomicInteger getAudioFrameCacheNumber() {
        return publisher == null ? null : publisher.getAudioFrameCacheNumber();
    }

    @Override
    public int getPushVideoFrameCounts() {
        return publisher == null ? 0 : publisher.getPushVideoFrameCounts();
    }

    @Override
    public double getPushVideoFps() {
        return publisher == null ? 0 : publisher.getPushVideoFps();
    }

    /**
     * set video resolution for publisher
     * @param width width
     * @param height height
     */
    @Override
    public void setVideoResolution(int width, int height) {
        if (publisher != null) {
            publisher.setVideoResolution(width, height);
        }
    }

    /**
     * Adds a track with the specified format.
     * @param format The media format for the track.
     * @return The track index for this newly added track.
     */
    @Override
    public int addTrack(MediaFormat format) {
        if (format.getString(MediaFormat.KEY_MIME).contentEquals(LangVideoEncoder.VCODEC)) {
            flv.setVideoTrack(format);
            return VIDEO_TRACK;
        } else {
            flv.setAudioTrack(format);
            return AUDIO_TRACK;
        }
    }

    private void disconnect() {
        try {
            publisher.close();
        } catch (IllegalStateException e) {
            // Ignore illegal state.
        }
        connected = false;
        mVideoSequenceHeader = null;
        mAudioSequenceHeader = null;
        Log.i(TAG, "worker: disconnect ok.");
    }

    private boolean connect(String url) {
        if (!connected) {
            Log.i(TAG, String.format("worker: connecting to RTMP server by url=%s\n", url));
            if (publisher.connect(url)) {
                connected = publisher.publish("live");
            }
            mVideoSequenceHeader = null;
            mAudioSequenceHeader = null;
        }
        return connected;
    }

    private void sendFlvTag(SnailFlvFrame frame) {
        if (!connected || frame == null) {
            return;
        }

        if (frame.isVideo()) {
            if (frame.isKeyFrame()) {
                Log.i(TAG, String.format("worker: send frame type=%d, dts=%d, size=%dB",
                        frame.type, frame.dts, frame.flvTag.array().length));
            }
            publisher.publishVideoData(frame.flvTag.array(), frame.flvTag.size(), frame.dts);
            mVideoAllocator.release(frame.flvTag);
        } else if (frame.isAudio()) {
            publisher.publishAudioData(frame.flvTag.array(), frame.flvTag.size(), frame.dts);
            mAudioAllocator.release(frame.flvTag);
        }
    }

    /**
     * start to the remote SRS for remux.
     */
    @Override
    public void start(final String rtmpUrl) {
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "work prepare to connect: " + rtmpUrl);
                if (!connect(rtmpUrl)) {
                    return;
                }

                while (!Thread.interrupted()) {
                    while (!mFlvTagCache.isEmpty()) {
                        SnailFlvFrame frame = mFlvTagCache.poll();
                        if (frame.isSequenceHeader()) {
                            if (frame.isVideo()) {
                                mVideoSequenceHeader = frame;
                                sendFlvTag(mVideoSequenceHeader);
                            } else if (frame.isAudio()) {
                                mAudioSequenceHeader = frame;
                                sendFlvTag(mAudioSequenceHeader);
                            }
                        } else {
                            if (frame.isVideo() && mVideoSequenceHeader != null) {
                                sendFlvTag(frame);
                            } else if (frame.isAudio() && mAudioSequenceHeader != null) {
                                sendFlvTag(frame);
                            }
                        }
                    }
                    // Waiting for next frame
                    synchronized (txFrameLock) {
                        try {
                            // isEmpty() may take some time, so we set timeout to detect next frame
                            txFrameLock.wait(500);
                        } catch (InterruptedException ie) {
                            worker.interrupt();
                        }
                    }
                }
            }
        });
        worker.setName("FlvMuxerThread");
        worker.start();
    }

    /**
     * stop the muxer, disconnect RTMP connection.
     */
    @Override
    public void stop() {
        mFlvTagCache.clear();
        if (worker != null) {
            worker.interrupt();
            try {
                worker.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
                worker.interrupt();
            }
            worker = null;
        }
        flv.reset();
        needToFindKeyFrame = true;
        Log.i(TAG, "SrsFlvMuxer closed");
        /*
        new Thread(new Runnable() {
            @Override
            public void run() {
                disconnect();
            }
        }).start();
        */

        bye = new Thread(new Runnable() {
            @Override
            public void run() {
                disconnect();
            }
        });
        bye.start();
        try {
            bye.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
            bye.interrupt();
        }
        bye = null;
    }

    @Override
    public void release() {

    }

    /**
     * send the annexb frame over RTMP.
     * @param trackIndex The track index for this sample.
     * @param byteBuf The encoded sample.
     * @param bufferInfo The buffer information related to this sample.
     */
    @Override
    public void writeSampleData(int trackIndex, ByteBuffer byteBuf, MediaCodec.BufferInfo bufferInfo) {
        if (bufferInfo.offset > 0) {
            Log.w(TAG, String.format("encoded frame %dB, offset=%d pts=%dms",
                    bufferInfo.size, bufferInfo.offset, bufferInfo.presentationTimeUs / 1000
            ));
        }

        if (VIDEO_TRACK == trackIndex) {
            flv.writeVideoSample(byteBuf, bufferInfo);
        } else {
            flv.writeAudioSample(byteBuf, bufferInfo);
        }
    }

    // E.4.3.1 VIDEODATA
    // Frame Type UB [4]
    // Type of video frame. The following values are defined:
    //     1 = key frame (for AVC, a seekable frame)
    //     2 = inter frame (for AVC, a non-seekable frame)
    //     3 = disposable inter frame (H.263 only)
    //     4 = generated key frame (reserved for server use only)
    //     5 = video info/command frame
    private class SnailCodecVideoAVCFrame
    {
        // set to the zero to reserved, for array map.
        public final static int Reserved = 0;
        public final static int Reserved1 = 6;

        public final static int KeyFrame                     = 1;
        public final static int InterFrame                   = 2;
        public final static int DisposableInterFrame         = 3;
        public final static int GeneratedKeyFrame            = 4;
        public final static int VideoInfoFrame               = 5;
    }

    // AVCPacketType IF CodecID == 7 UI8
    // The following values are defined:
    //     0 = AVC sequence header
    //     1 = AVC NALU
    //     2 = AVC end of sequence (lower level NALU sequence ender is
    //         not required or supported)
    private class SnailCodecVideoAVCType
    {
        // set to the max value to reserved, for array map.
        public final static int Reserved                     = 3;

        public final static int SequenceHeader               = 0;
        public final static int NALU                         = 1;
        public final static int SequenceHeaderEOF            = 2;
    }

    /**
     * E.4.1 FLV Tag, page 75
     */
    private class SnailCodecFlvTag
    {
        // set to the zero to reserved, for array map.
        public final static int Reserved = 0;

        // 8 = audio
        public final static int Audio = 8;
        // 9 = video
        public final static int Video = 9;
        // 18 = script data
        public final static int Script = 18;
    };

    // E.4.3.1 VIDEODATA
    // CodecID UB [4]
    // Codec Identifier. The following values are defined:
    //     2 = Sorenson H.263
    //     3 = Screen video
    //     4 = On2 VP6
    //     5 = On2 VP6 with alpha channel
    //     6 = Screen video version 2
    //     7 = AVC
    private class SnailCodecVideo
    {
        // set to the zero to reserved, for array map.
        public final static int Reserved                = 0;
        public final static int Reserved1                = 1;
        public final static int Reserved2                = 9;

        // for user to disable video, for example, use pure audio hls.
        public final static int Disabled                = 8;

        public final static int SorensonH263             = 2;
        public final static int ScreenVideo             = 3;
        public final static int On2VP6                 = 4;
        public final static int On2VP6WithAlphaChannel = 5;
        public final static int ScreenVideoVersion2     = 6;
        public final static int AVC                     = 7;
    }

    /**
     * the aac object type, for RTMP sequence header
     * for AudioSpecificConfig, @see aac-mp4a-format-ISO_IEC_14496-3+2001.pdf, page 33
     * for audioObjectType, @see aac-mp4a-format-ISO_IEC_14496-3+2001.pdf, page 23
     */
    private class SnailAacObjectType
    {
        public final static int Reserved = 0;

        // Table 1.1 – Audio Object Type definition
        // @see @see aac-mp4a-format-ISO_IEC_14496-3+2001.pdf, page 23
        public final static int AacMain = 1;
        public final static int AacLC = 2;
        public final static int AacSSR = 3;

        // AAC HE = LC+SBR
        public final static int AacHE = 5;
        // AAC HEv2 = LC+SBR+PS
        public final static int AacHEV2 = 29;
    }

    /**
     * the aac profile, for ADTS(HLS/TS)
     * @see https://github.com/simple-rtmp-server/srs/issues/310
     */
    private class SnailAacProfile
    {
        public final static int Reserved = 3;

        // @see 7.1 Profiles, aac-iso-13818-7.pdf, page 40
        public final static int Main = 0;
        public final static int LC = 1;
        public final static int SSR = 2;
    }

    /**
     * the FLV/RTMP supported audio sample rate.
     * Sampling rate. The following values are defined:
     * 0 = 5.5 kHz = 5512 Hz
     * 1 = 11 kHz = 11025 Hz
     * 2 = 22 kHz = 22050 Hz
     * 3 = 44 kHz = 44100 Hz
     */
    private class SnailCodecAudioSampleRate
    {
        // set to the max value to reserved, for array map.
        public final static int Reserved                 = 4;

        public final static int R5512                     = 0;
        public final static int R11025                    = 1;
        public final static int R22050                    = 2;
        public final static int R44100                    = 3;
    }

    /**
     * Table 7-1 – NAL unit type codes, syntax element categories, and NAL unit type classes
     * H.264-AVC-ISO_IEC_14496-10-2012.pdf, page 83.
     */
    private class SnailAvcNaluType
    {
        // Unspecified
        public final static int Reserved = 0;

        // Coded slice of a non-IDR picture slice_layer_without_partitioning_rbsp( )
        public final static int NonIDR = 1;
        // Coded slice data partition A slice_data_partition_a_layer_rbsp( )
        public final static int DataPartitionA = 2;
        // Coded slice data partition B slice_data_partition_b_layer_rbsp( )
        public final static int DataPartitionB = 3;
        // Coded slice data partition C slice_data_partition_c_layer_rbsp( )
        public final static int DataPartitionC = 4;
        // Coded slice of an IDR picture slice_layer_without_partitioning_rbsp( )
        public final static int IDR = 5;
        // Supplemental enhancement information (SEI) sei_rbsp( )
        public final static int SEI = 6;
        // Sequence parameter set seq_parameter_set_rbsp( )
        public final static int SPS = 7;
        // Picture parameter set pic_parameter_set_rbsp( )
        public final static int PPS = 8;
        // Access unit delimiter access_unit_delimiter_rbsp( )
        public final static int AccessUnitDelimiter = 9;
        // End of sequence end_of_seq_rbsp( )
        public final static int EOSequence = 10;
        // End of stream end_of_stream_rbsp( )
        public final static int EOStream = 11;
        // Filler data filler_data_rbsp( )
        public final static int FilterData = 12;
        // Sequence parameter set extension seq_parameter_set_extension_rbsp( )
        public final static int SPSExt = 13;
        // Prefix NAL unit prefix_nal_unit_rbsp( )
        public final static int PrefixNALU = 14;
        // Subset sequence parameter set subset_seq_parameter_set_rbsp( )
        public final static int SubsetSPS = 15;
        // Coded slice of an auxiliary coded picture without partitioning slice_layer_without_partitioning_rbsp( )
        public final static int LayerWithoutPartition = 19;
        // Coded slice extension slice_layer_extension_rbsp( )
        public final static int CodedSliceExt = 20;
    }

    /**
     * the search result for annexb.
     */
    private class SnailAnnexbSearch {
        public int nb_start_code = 0;
        public boolean match = false;
    }

    /**
     * the demuxed tag frame.
     */
    private class SnailFlvFrameBytes {
        public ByteBuffer data;
        public int size;
    }

    /**
     * the muxed flv frame.
     */
    private class SnailFlvFrame {
        // the tag bytes.
        public LangAllocator.Allocation flvTag;
        // the codec type for audio/aac and video/avc for instance.
        public int avc_aac_type;
        // the frame type, keyframe or not.
        public int frame_type;
        // the tag type, audio, video or data.
        public int type;
        // the dts in ms, tbn is 1000.
        public int dts;

        public boolean isKeyFrame() {
            return isVideo() && frame_type == SnailCodecVideoAVCFrame.KeyFrame;
        }

        public boolean isSequenceHeader() {
            return avc_aac_type == 0;
        }

        public boolean isVideo() {
            return type == SnailCodecFlvTag.Video;
        }

        public boolean isAudio() {
            return type == SnailCodecFlvTag.Audio;
        }
    }

    /**
     * the raw h.264 stream, in annexb.
     */
    private class SnailRawH264Stream {
        private final static String TAG = "LangFlvMuxer";

        private SnailAnnexbSearch annexb = new SnailAnnexbSearch();
        private SnailFlvFrameBytes seq_hdr = new SnailFlvFrameBytes();
        private SnailFlvFrameBytes sps_hdr = new SnailFlvFrameBytes();
        private SnailFlvFrameBytes sps_bb = new SnailFlvFrameBytes();
        private SnailFlvFrameBytes pps_hdr = new SnailFlvFrameBytes();
        private SnailFlvFrameBytes pps_bb = new SnailFlvFrameBytes();

        public boolean isSps(SnailFlvFrameBytes frame) {
            return frame.size >= 1 && (frame.data.get(0) & 0x1f) == SnailAvcNaluType.SPS;
        }

        public boolean isPps(SnailFlvFrameBytes frame) {
            return frame.size >= 1 && (frame.data.get(0) & 0x1f) == SnailAvcNaluType.PPS;
        }

        public SnailFlvFrameBytes muxNaluHeader(SnailFlvFrameBytes frame) {
            SnailFlvFrameBytes nalu_hdr = new SnailFlvFrameBytes();
            nalu_hdr.data = ByteBuffer.allocateDirect(4);
            nalu_hdr.size = 4;
            // 5.3.4.2.1 Syntax, H.264-AVC-ISO_IEC_14496-15.pdf, page 16
            // lengthSizeMinusOne, or NAL_unit_length, always use 4bytes size
            int NAL_unit_length = frame.size;

            // mux the avc NALU in "ISO Base Media File Format"
            // from H.264-AVC-ISO_IEC_14496-15.pdf, page 20
            // NALUnitLength
            nalu_hdr.data.putInt(NAL_unit_length);

            // reset the buffer.
            nalu_hdr.data.rewind();
            return nalu_hdr;
        }

        public void muxSequenceHeader(ByteBuffer sps, ByteBuffer pps, int dts, int pts,
                                      ArrayList<SnailFlvFrameBytes> frames) {
            // 5bytes sps/pps header:
            //      configurationVersion, AVCProfileIndication, profile_compatibility,
            //      AVCLevelIndication, lengthSizeMinusOne
            // 3bytes size of sps:
            //      numOfSequenceParameterSets, sequenceParameterSetLength(2B)
            // Nbytes of sps.
            //      sequenceParameterSetNALUnit
            // 3bytes size of pps:
            //      numOfPictureParameterSets, pictureParameterSetLength
            // Nbytes of pps:
            //      pictureParameterSetNALUnit

            // decode the SPS:
            // @see: 7.3.2.1.1, H.264-AVC-ISO_IEC_14496-10-2012.pdf, page 62
            if (seq_hdr.data == null) {
                seq_hdr.data = ByteBuffer.allocate(5);
                seq_hdr.size = 5;
            }
            seq_hdr.data.rewind();
            // @see: Annex A Profiles and levels, H.264-AVC-ISO_IEC_14496-10.pdf, page 205
            //      Baseline profile profile_idc is 66(0x42).
            //      Main profile profile_idc is 77(0x4d).
            //      Extended profile profile_idc is 88(0x58).
            byte profile_idc = sps.get(1);
            //u_int8_t constraint_set = frame[2];
            byte level_idc = sps.get(3);

            // generate the sps/pps header
            // 5.3.4.2.1 Syntax, H.264-AVC-ISO_IEC_14496-15.pdf, page 16
            // configurationVersion
            seq_hdr.data.put((byte) 0x01);
            // AVCProfileIndication
            seq_hdr.data.put(profile_idc);
            // profile_compatibility
            seq_hdr.data.put((byte) 0x00);
            // AVCLevelIndication
            seq_hdr.data.put(level_idc);
            // lengthSizeMinusOne, or NAL_unit_length, always use 4bytes size,
            // so we always set it to 0x03.
            seq_hdr.data.put((byte) 0x03);

            // reset the buffer.
            seq_hdr.data.rewind();
            frames.add(seq_hdr);

            // sps
            if (sps_hdr.data == null) {
                sps_hdr.data = ByteBuffer.allocate(3);
                sps_hdr.size = 3;
            }
            sps_hdr.data.rewind();
            // 5.3.4.2.1 Syntax, H.264-AVC-ISO_IEC_14496-15.pdf, page 16
            // numOfSequenceParameterSets, always 1
            sps_hdr.data.put((byte) 0x01);
            // sequenceParameterSetLength
            sps_hdr.data.putShort((short) sps.array().length);

            sps_hdr.data.rewind();
            frames.add(sps_hdr);

            // sequenceParameterSetNALUnit
            sps_bb.size = sps.array().length;
            sps_bb.data = sps.duplicate();
            frames.add(sps_bb);

            // pps
            if (pps_hdr.data == null) {
                pps_hdr.data = ByteBuffer.allocate(3);
                pps_hdr.size = 3;
            }
            pps_hdr.data.rewind();
            // 5.3.4.2.1 Syntax, H.264-AVC-ISO_IEC_14496-15.pdf, page 16
            // numOfPictureParameterSets, always 1
            pps_hdr.data.put((byte) 0x01);
            // pictureParameterSetLength
            pps_hdr.data.putShort((short) pps.array().length);

            pps_hdr.data.rewind();
            frames.add(pps_hdr);

            // pictureParameterSetNALUnit
            pps_bb.size = pps.array().length;
            pps_bb.data = pps.duplicate();
            frames.add(pps_bb);
        }

        public LangAllocator.Allocation muxFlvTag(ArrayList<SnailFlvFrameBytes> frames, int frame_type,
                                                  int avc_packet_type, int dts, int pts) {
            // for h264 in RTMP video payload, there is 5bytes header:
            //      1bytes, FrameType | CodecID
            //      1bytes, AVCPacketType
            //      3bytes, CompositionTime, the cts.
            // @see: E.4.3 Video Tags, video_file_format_spec_v10_1.pdf, page 78
            int size = 5;
            for (int i = 0; i < frames.size(); i++) {
                size += frames.get(i).size;
            }
            LangAllocator.Allocation allocation = mVideoAllocator.allocate(size);

            // @see: E.4.3 Video Tags, video_file_format_spec_v10_1.pdf, page 78
            // Frame Type, Type of video frame.
            // CodecID, Codec Identifier.
            // set the rtmp header
            allocation.put((byte) ((frame_type << 4) | SnailCodecVideo.AVC));

            // AVCPacketType
            allocation.put((byte)avc_packet_type);

            // CompositionTime
            // pts = dts + cts, or
            // cts = pts - dts.
            // where cts is the header in rtmp video packet payload header.
            int cts = pts - dts;
            allocation.put((byte)(cts >> 16));
            allocation.put((byte)(cts >> 8));
            allocation.put((byte)cts);

            // h.264 raw data.
            for (int i = 0; i < frames.size(); i++) {
                SnailFlvFrameBytes frame = frames.get(i);
                frame.data.get(allocation.array(), allocation.size(), frame.size);
                allocation.appendOffset(frame.size);
            }

            return allocation;
        }

        private SnailAnnexbSearch searchAnnexb(ByteBuffer bb, MediaCodec.BufferInfo bi) {
            annexb.match = false;
            annexb.nb_start_code = 0;

            for (int i = bb.position(); i < bi.size - 3; i++) {
                // not match.
                if (bb.get(i) != 0x00 || bb.get(i + 1) != 0x00) {
                    break;
                }

                // match N[00] 00 00 01, where N>=0
                if (bb.get(i + 2) == 0x01) {
                    annexb.match = true;
                    annexb.nb_start_code = i + 3 - bb.position();
                    break;
                }
            }

            return annexb;
        }

        public SnailFlvFrameBytes demuxAnnexb(ByteBuffer bb, MediaCodec.BufferInfo bi) {
            SnailFlvFrameBytes tbb = new SnailFlvFrameBytes();

            while (bb.position() < bi.size) {
                // each frame must prefixed by annexb format.
                // about annexb, @see H.264-AVC-ISO_IEC_14496-10.pdf, page 211.
                SnailAnnexbSearch tbbsc = searchAnnexb(bb, bi);
                if (!tbbsc.match || tbbsc.nb_start_code < 3) {
                    Log.e(TAG, "annexb not match.");
                    mHandler.notifyRtmpIllegalArgumentException(new IllegalArgumentException(
                            String.format("annexb not match for %dB, pos=%d", bi.size, bb.position())));
                }

                // the start codes.
                for (int i = 0; i < tbbsc.nb_start_code; i++) {
                    bb.get();
                }

                // find out the frame size.
                tbb.data = bb.slice();
                int pos = bb.position();
                while (bb.position() < bi.size) {
                    SnailAnnexbSearch bsc = searchAnnexb(bb, bi);
                    if (bsc.match) {
                        break;
                    }
                    bb.get();
                }

                tbb.size = bb.position() - pos;
                break;
            }

            return tbb;
        }
    }

    private class SrsRawAacStreamCodec {
        public byte protection_absent;
        // SrsAacObjectType
        public int aac_object;
        public byte sampling_frequency_index;
        public byte channel_configuration;
        public short frame_length;

        public byte sound_format;
        public byte sound_rate;
        public byte sound_size;
        public byte sound_type;
        // 0 for sh; 1 for raw data.
        public byte aac_packet_type;

        public byte[] frame;
    }

    /**
     * remux the annexb to flv tags.
     */
    private class SnailFlv {
        private MediaFormat videoTrack;
        private MediaFormat audioTrack;
        private int achannel;
        private int asample_rate;
        private SnailRawH264Stream avc = new SnailRawH264Stream();
        private ArrayList<SnailFlvFrameBytes> ipbs = new ArrayList<>();
        private LangAllocator.Allocation audio_tag;
        private LangAllocator.Allocation video_tag;
        private ByteBuffer h264_sps;
        private boolean h264_sps_changed;
        private ByteBuffer h264_pps;
        private boolean h264_pps_changed;
        private boolean h264_sps_pps_sent;
        private boolean aac_specific_config_got;

        public SnailFlv() {
            reset();
        }

        public void reset() {
            h264_sps_changed = false;
            h264_pps_changed = false;
            h264_sps_pps_sent = false;
            aac_specific_config_got = false;
        }

        public void setVideoTrack(MediaFormat format) {
            videoTrack = format;
        }

        public void setAudioTrack(MediaFormat format) {
            audioTrack = format;
            achannel = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            asample_rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        }

        public void writeAudioSample(final ByteBuffer bb, MediaCodec.BufferInfo bi) {
            int pts = (int)(bi.presentationTimeUs / 1000);
            int dts = pts;

            audio_tag = mAudioAllocator.allocate(bi.size + 2);
            byte aac_packet_type = 1; // 1 = AAC raw
            if (!aac_specific_config_got) {
                // @see aac-mp4a-format-ISO_IEC_14496-3+2001.pdf
                // AudioSpecificConfig (), page 33
                // 1.6.2.1 AudioSpecificConfig
                // audioObjectType; 5 bslbf
                byte ch = (byte)(bb.get(0) & 0xf8);
                // 3bits left.

                // samplingFrequencyIndex; 4 bslbf
                byte samplingFrequencyIndex = (byte)mpeg4SampleFrequencyToIdx(asample_rate);
//                if (asample_rate == SnailCodecAudioSampleRate.R22050) {
//                    samplingFrequencyIndex = 0x07;
//                } else if (asample_rate == SnailCodecAudioSampleRate.R11025) {
//                    samplingFrequencyIndex = 0x0a;
//                }
                ch |= (samplingFrequencyIndex >> 1) & 0x07;
                audio_tag.put(ch, 2);

                ch = (byte)((samplingFrequencyIndex << 7) & 0x80);
                // 7bits left.

                // channelConfiguration; 4 bslbf
                byte channelConfiguration = 1;
                if (achannel == 2) {
                    channelConfiguration = 2;
                }
                ch |= (channelConfiguration << 3) & 0x78;
                // 3bits left.

                // GASpecificConfig(), page 451
                // 4.4.1 Decoder configuration (GASpecificConfig)
                // frameLengthFlag; 1 bslbf
                // dependsOnCoreCoder; 1 bslbf
                // extensionFlag; 1 bslbf
                audio_tag.put(ch, 3);

                aac_specific_config_got = true;
                aac_packet_type = 0; // 0 = AAC sequence header

                writeAdtsHeader(audio_tag.array(), 4);
                audio_tag.appendOffset(7);
            } else {
                // XXX: fix-me
                try {
                    bb.get(audio_tag.array(), 2, bi.size);
                    audio_tag.appendOffset(bi.size + 2);
                } catch (java.nio.BufferUnderflowException e) {
                    Log.w(TAG, String.format("audio encoder output buffer under-flow exception, size=%d remain=%d",
                            bi.size, bb.remaining()));
                    return;
                }

                //bb.get(audio_tag.array(), 2, bi.size);
                //audio_tag.appendOffset(bi.size + 2);
            }

            byte sound_format = 10; // AAC
            byte sound_type = 0; // 0 = Mono sound
            if (achannel == 2) {
                sound_type = 1; // 1 = Stereo sound
            }
            byte sound_size = 1; // 1 = 16-bit samples
            byte sound_rate = 3; // 44100, 22050, 11025
            if (asample_rate == 22050) {
                sound_rate = 2;
            } else if (asample_rate == 11025) {
                sound_rate = 1;
            }

            // for audio frame, there is 1 or 2 bytes header:
            //      1bytes, SoundFormat|SoundRate|SoundSize|SoundType
            //      1bytes, AACPacketType for SoundFormat == 10, 0 is sequence header.
            byte audio_header = (byte) (sound_type & 0x01);
            audio_header |= (sound_size << 1) & 0x02;
            audio_header |= (sound_rate << 2) & 0x0c;
            audio_header |= (sound_format << 4) & 0xf0;

            audio_tag.put(audio_header, 0);
            audio_tag.put(aac_packet_type, 1);

            writeRtmpPacket(SnailCodecFlvTag.Audio, dts, 0, aac_packet_type, audio_tag);
        }

        private void writeAdtsHeader(byte[] frame, int offset) {
            int frequencyIdx = mpeg4SampleFrequencyToIdx(asample_rate);
            // adts sync word 0xfff (12-bit)
            frame[offset] = (byte) 0xff;
            frame[offset + 1] = (byte) 0xf0;
            // versioin 0 for MPEG-4, 1 for MPEG-2 (1-bit)
            frame[offset + 1] |= 0 << 3;
            // layer 0 (2-bit)
            frame[offset + 1] |= 0 << 1;
            // protection absent: 1 (1-bit)
            frame[offset + 1] |= 1;
            // profile: audio_object_type - 1 (2-bit)
            frame[offset + 2] = (SnailAacObjectType.AacLC - 1) << 6;
            // sampling frequency index: frequencyIdx (4-bit)
            frame[offset + 2] |= (frequencyIdx & 0xf) << 2;
            // channel configuration (3-bit)
            frame[offset + 2] |= (achannel & (byte) 0x4) >> 2;
            frame[offset + 3] = (byte) ((achannel & (byte) 0x03) << 6);
            // original: 0 (1-bit)
            frame[offset + 3] |= 0 << 5;
            // home: 0 (1-bit)
            frame[offset + 3] |= 0 << 4;
            // copyright id bit: 0 (1-bit)
            frame[offset + 3] |= 0 << 3;
            // copyright id start: 0 (1-bit)
            frame[offset + 3] |= 0 << 2;
            // frame size (13-bit)
            frame[offset + 3] |= ((frame.length - 2) & 0x1800) >> 11;
            frame[offset + 4] = (byte) (((frame.length - 2) & 0x7f8) >> 3);
            frame[offset + 5] = (byte) (((frame.length - 2) & 0x7) << 5);
            // buffer fullness (0x7ff for variable bitrate)
            frame[offset + 5] |= (byte) 0x1f;
            frame[offset + 6] = (byte) 0xfc;
            // number of data block (nb - 1)
            frame[offset + 6] |= 0x0;
        }

        private int mpeg4SampleFrequencyToIdx(int asample_rate) {
            int idx = 4;
            switch (asample_rate) {
                case 96000:
                    idx = 0;
                    break;
                case 88200:
                    idx = 1;
                    break;
                case 64000:
                    idx = 2;
                    break;
                case 48000:
                    idx = 3;
                    break;
                case 44100:
                    idx = 4;
                    break;
                case 32000:
                    idx = 5;
                    break;
                case 24000:
                    idx = 6;
                    break;
                case 22050:
                    idx = 7;
                    break;
                case 16000:
                    idx = 8;
                    break;
                case 12000:
                    idx = 9;
                    break;
                case 11025:
                    idx = 10;
                    break;
                case 8000:
                    idx = 11;
                    break;
                case 7350:
                    idx = 12;
                    break;
            }
            return idx;
        }

        public void writeVideoSample(final ByteBuffer bb, MediaCodec.BufferInfo bi) {
            int pts = (int) (bi.presentationTimeUs / 1000);
            int dts = pts;
            SnailFlvFrameBytes frame;

            int type = SnailCodecVideoAVCFrame.InterFrame;
            if ((bi.flags & BUFFER_FLAG_KEY_FRAME) != 0) {
               type = SnailCodecVideoAVCFrame.KeyFrame;
            }

//            if ((bi.flags & BUFFER_FLAG_CODEC_CONFIG) != 0) {
//                frame = avc.demuxAnnexb(bb, bi);
//                if (avc.isSps(frame)) {
//                    if (!frame.data.equals(h264_sps)) {
//                        byte[] sps = new byte[frame.size];
//                        frame.data.get(sps);
//                        h264_sps_changed = true;
//                        h264_sps = ByteBuffer.wrap(sps);
//                    }
//                }
//
//                // for pps
//                bb.position(frame.size - 3);
//                frame = avc.demuxAnnexb(bb, bi);
//                if (avc.isPps(frame)) {
//                    if (!frame.data.equals(h264_pps)) {
//                        byte[] pps = new byte[frame.size];
//                        frame.data.get(pps);
//                        h264_pps_changed = true;
//                        h264_pps = ByteBuffer.wrap(pps);
//                    }
//                }
//                return;
//            }else {
//                frame = new SnailFlvFrameBytes();
//                bb.position(4);
//                frame.data = bb.slice();
//                frame.size = bi.size - 4;
//                //                // IPB frame.
//                byte[] bba = new byte[5];
//                bb.position(0);
//                for (int idx = 0; idx < bba.length; idx++) {
//                    bba[idx] = bb.get(idx);
//                }
//
//                byte[] framea = new byte[5];
//                for (int idx = 0; idx < bba.length; idx++) {
//                    framea[idx] = frame.data.get(idx);
//                }
//                int bbsize = bi.size;
//                int framesize = frame.size;
//            }
//
//            if ((bi.flags & BUFFER_FLAG_KEY_FRAME) != 0) {
//                type = SnailCodecVideoAVCFrame.KeyFrame;
//            }
//
//            ipbs.add(avc.muxNaluHeader(frame));
//            ipbs.add(frame);
//            writeH264SpsPps(dts, pts);
//            writeH264IpbFrame(ipbs, type, dts, pts);
//            ipbs.clear();
//            return;

            // send each frame.

            while ((bi.flags & BUFFER_FLAG_CODEC_CONFIG) != 0 && bb.position() < bi.size) {
                frame = avc.demuxAnnexb(bb, bi);

                // 5bits, 7.3.1 NAL unit syntax,
                // H.264-AVC-ISO_IEC_14496-10.pdf, page 44.
                // 7: SPS, 8: PPS, 5: I Frame, 1: P Frame
                int nal_unit_type = (int)(frame.data.get(0) & 0x1f);
                if (nal_unit_type == SnailAvcNaluType.SPS || nal_unit_type == SnailAvcNaluType.PPS) {
                    Log.i(TAG, String.format("annexb demux %dB, pts=%d, frame=%dB, nalu=%d",
                            bi.size, pts, frame.size, nal_unit_type));
                }

                // for IDR frame, the frame is keyframe.
                if (nal_unit_type == SnailAvcNaluType.IDR) {
                    type = SnailCodecVideoAVCFrame.KeyFrame;
                }

                // ignore the nalu type aud(9)
                if (nal_unit_type == SnailAvcNaluType.AccessUnitDelimiter) {
                    continue;
                }

                // for sps
                if (avc.isSps(frame)) {
                    if (!frame.data.equals(h264_sps)) {
                        byte[] sps = new byte[frame.size];
                        frame.data.get(sps);
                        h264_sps_changed = true;
                        h264_sps = ByteBuffer.wrap(sps);
                    }
                    continue;
                }

                // for pps
                if (avc.isPps(frame)) {
                    if (!frame.data.equals(h264_pps)) {
                        byte[] pps = new byte[frame.size];
                        frame.data.get(pps);
                        h264_pps_changed = true;
                        h264_pps = ByteBuffer.wrap(pps);
                    }
                    continue;
                }

                // IPB frame.
                ipbs.add(avc.muxNaluHeader(frame));
                ipbs.add(frame);
            }

            if((bi.flags & BUFFER_FLAG_CODEC_CONFIG) == 0) {
                  frame = new SnailFlvFrameBytes();
                bb.position(4);
                frame.data = bb.slice();
                frame.size = bi.size - 4;
                //frame = avc.demuxAnnexb(bb, bi);
                ipbs.add(avc.muxNaluHeader(frame));
                ipbs.add(frame);
            }

            writeH264SpsPps(dts, pts);
            writeH264IpbFrame(ipbs, type, dts, pts);
            ipbs.clear();
        }

        private void writeH264SpsPps(int dts, int pts) {
            // when sps or pps changed, update the sequence header,
            // for the pps maybe not changed while sps changed.
            // so, we must check when each video ts message frame parsed.
            if (h264_sps_pps_sent && !h264_sps_changed && !h264_pps_changed) {
                return;
            }

            // when not got sps/pps, wait.
            if (h264_pps == null || h264_sps == null) {
                return;
            }

            // h264 raw to h264 packet.
            ArrayList<SnailFlvFrameBytes> frames = new ArrayList<>();
            avc.muxSequenceHeader(h264_sps, h264_pps, dts, pts, frames);

            // h264 packet to flv packet.
            int frame_type = SnailCodecVideoAVCFrame.KeyFrame;
            int avc_packet_type = SnailCodecVideoAVCType.SequenceHeader;
            video_tag = avc.muxFlvTag(frames, frame_type, avc_packet_type, dts, pts);

            // the timestamp in rtmp message header is dts.
            writeRtmpPacket(SnailCodecFlvTag.Video, dts, frame_type, avc_packet_type, video_tag);

            // reset sps and pps.
            h264_sps_changed = false;
            h264_pps_changed = false;
            h264_sps_pps_sent = true;
            Log.i(TAG, String.format("flv: h264 sps/pps sent, sps=%dB, pps=%dB",
                    h264_sps.array().length, h264_pps.array().length));
        }

        private void writeH264IpbFrame(ArrayList<SnailFlvFrameBytes> frames, int type, int dts, int pts) {
            // when sps or pps not sent, ignore the packet.
            // @see https://github.com/simple-rtmp-server/srs/issues/203
            if (!h264_sps_pps_sent) {
                return;
            }

            video_tag = avc.muxFlvTag(frames, type, SnailCodecVideoAVCType.NALU, dts, pts);

            // the timestamp in rtmp message header is dts.
            writeRtmpPacket(SnailCodecFlvTag.Video, dts, type, SnailCodecVideoAVCType.NALU, video_tag);
        }

        private void writeRtmpPacket(int type, int dts, int frame_type, int avc_aac_type, LangAllocator.Allocation tag) {
            SnailFlvFrame frame = new SnailFlvFrame();
            frame.flvTag = tag;
            frame.type = type;
            frame.dts = dts;
            frame.frame_type = frame_type;
            frame.avc_aac_type = avc_aac_type;

            if (frame.isVideo()) {
                if (needToFindKeyFrame) {
                    if (frame.isKeyFrame()) {
                        needToFindKeyFrame = false;
                        flvTagCacheAdd(frame);
                    }
                } else {
                    flvTagCacheAdd(frame);
                }
            } else if (frame.isAudio()) {
                flvTagCacheAdd(frame);
            }
        }

        private void flvTagCacheAdd(SnailFlvFrame frame) {
            mFlvTagCache.add(frame);
            if (frame.isVideo()) {
                getVideoFrameCacheNumber().incrementAndGet();
            }else {
                getAudioFrameCacheNumber().incrementAndGet();
            }
            synchronized (txFrameLock) {
                txFrameLock.notifyAll();
            }
        }
    }
}
