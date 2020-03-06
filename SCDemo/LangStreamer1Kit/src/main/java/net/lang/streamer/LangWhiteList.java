package net.lang.streamer;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import net.lang.streamer.engine.LangVideoEncoderImpl;

import java.util.Map;

/**
 * Created by lichao on 17/2/23.
 */

public class LangWhiteList {
    public static final String TAG = "LangWhiteList";
    public static final int kPlayerOnlySystemPlayer = 1;
    public static final int kPlayerUnsupportsEGL = 2;
    public static final int kPlayerUnsupportsCPU = 4;
    public static final int kPlayerUnsupports = kPlayerUnsupportsEGL | kPlayerUnsupportsCPU;

    public static final int kCodecUsingPipeLine = 1;
    public static final int kCodecUsingMediaCodec = 2;
    public static final int kCodecUsingX264 = 4;
    public static final int kCodecEnableGraphicBuffer = 8;
    private static Integer mDeviceCellId = -1;

    private static LangWhiteListCell[] mList = {
            // VIVO Y937
            new LangWhiteListCell("vivo Y937", kCodecUsingMediaCodec | kCodecUsingX264 | kCodecEnableGraphicBuffer, 0),
            new LangWhiteListCell("OPPO A33m", kCodecUsingMediaCodec | kCodecUsingX264 | kCodecEnableGraphicBuffer, 0),
            new LangWhiteListCell("HTC_D728x", kCodecUsingPipeLine | kCodecUsingMediaCodec | kCodecUsingX264, 0),
            new LangWhiteListCell("MP1503", kCodecUsingPipeLine | kCodecUsingMediaCodec | kCodecUsingX264, 0),
    };


    public static LangWhiteListCell cell(Context ctx) {
        LangWhiteListCell cl = null;
        synchronized (mDeviceCellId) {
            if (mDeviceCellId < 0 ) {
                cl = findWhiteList(ctx);
            }else if (mDeviceCellId < mList.length){
                cl = mList[mDeviceCellId];
            }
        }
        return cl;
    }

    public static LangVideoEncoderImpl.EncoderType chooseType(int flag, LangVideoEncoderImpl.EncoderType type) {
        LangVideoEncoderImpl.EncoderType t = type;
        switch (type) {
            case kHardwarePipeline: {
                if ((flag & kCodecUsingPipeLine ) == 0) {
                    t = chooseType(flag, LangVideoEncoderImpl.EncoderType.kHardware);
                }
                break;
            }
            case kHardware: {
                if ((flag & kCodecUsingMediaCodec ) == 0) {
                    t = chooseType(flag, LangVideoEncoderImpl.EncoderType.kSoftware);
                }
                break;
            }
        }
        return t;
    }

    private static LangWhiteListCell findWhiteList(Context ctx) {
        // Create capability map.
        LangCapabilityDetecting detecting = new LangCapabilityDetecting(ctx);
        detecting.dumpToSystem();
        Map<String, String> cap = detecting.capabilityMap();
        String model = android.os.Build.MODEL;
        String manufacturer = Build.MANUFACTURER;
        LangWhiteListCell cell = null;
        int idx = 0;
        for (; idx < mList.length; idx++) {
            Log.v(TAG, "Find phone [" + mList[idx].name() +"]in whitelist ...");
            if (mList[idx].name().equals(model)) {
                cell = mList[idx];
                Log.d(TAG, "Find phone [" + model +"("+manufacturer+")] with flag " +
                        "(Codec："+ Integer.toHexString(cell.flagCodec())+") " +
                        "(Render:"+ Integer.toHexString(cell.flagRender())+") " +
                        "in whitelist.");
                break;
            }
        }
        mDeviceCellId = idx;
        return cell;
    }
}
