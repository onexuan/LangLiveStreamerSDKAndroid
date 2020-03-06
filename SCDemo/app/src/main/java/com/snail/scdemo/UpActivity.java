package com.snail.scdemo;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.snail.scdemo.utils.LogUtils;
import com.snail.scdemo.utils.ToastUtils;
import com.snail.scdemo.utils.Utils;
import com.snail.scdemo.view.GestureView;

import net.lang.streamer.ILangCameraStreamer;
import net.lang.streamer.LangCameraStreamer;
import net.lang.streamer.LangLiveInfo;
import net.lang.streamer.LangRtcInfo;
import net.lang.streamer.config.*;
import net.lang.streamer.engine.LangVideoEncoderImpl;
import net.lang.streamer.faceu.LangFaceHandler;
import net.lang.streamer.utils.DebugLog;
import net.lang.streamer.widget.AnimationCallback;
import net.lang.streamer.widget.CopyAssets;
import net.lang.streamer.widget.LangMagicCameraView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.TimerTask;
import java.lang.ref.WeakReference;

import butterknife.BindArray;
import butterknife.BindView;
import butterknife.OnClick;

import com.lang.lang.R;

/**
 * Created by MRKING on 2017/5/19.
 */

public class UpActivity extends BaseActivity implements ILangCameraStreamer.ILangCameraStreamerOnErrorListener,
        ILangCameraStreamer.ILangCameraStreamerOnEventListener,
        GestureView.GestureListener {

    @BindView(R.id.snail_magic_camera_view)
    LangMagicCameraView mSnailMagicCameraView;

    @BindView(R.id.link_tip)
    TextView mLinkTip;
    @BindView(R.id.push_info)
    TextView mPushInfo;
    @BindView(R.id.error_info)
    TextView mErrorInfo;
    @BindView(R.id.faceu_hand_info)
    TextView mFaceuHandInfo;

    @BindView(R.id.snail_debugLinear)
    LinearLayout mDebugLinear;
    @BindView(R.id.snail_resolutionDebug)
    TextView mResolutionTextView;
    @BindView(R.id.snail_previewFpsDebug)
    TextView mPrevFpsTextView;
    @BindView(R.id.snail_cpuDebug)
    TextView mCpuUsageTextView;
    @BindView(R.id.snail_memoryDebug)
    TextView mMemoryTextView;
    @BindView(R.id.snail_rtmpBandwidthDebug)
    TextView mRtmpSpeedTextView;
    @BindView(R.id.snail_rtmpFpsDebug)
    TextView mRtmpFpsTextView;
    @BindView(R.id.snail_rtmpAudioCacheDebug)
    TextView mRtmpAudioCacheNumView;
    @BindView(R.id.snail_rtmpVideoCacheDebug)
    TextView mRtmpVideoCacheNumView;
    @BindView(R.id.snail_rtcLocalConnectStatusDebug)
    TextView mRtcConnectStatusTextView;
    @BindView(R.id.snail_rtcMessageDebug)
    TextView mRtcMessageTextView;
    @BindView(R.id.snail_rtcJoinedUsersDebug)
    TextView mRtcUsersTextView;
    @BindView(R.id.snail_rtcTxBandwidthDebug)
    TextView mRtcTxTextView;
    @BindView(R.id.snail_rtcRxBandwidthDebug)
    TextView mRtcRxTextView;

    @BindView(R.id.show_debug_view)
    Button mDebugView;
    @BindView(R.id.gift_animation)
    Button mGiftView;
    @BindView(R.id.beauty_hair)
    Button mBeautyHairView;
    @BindView(R.id.switch_orientation)
    Button mSwitchOrientation;
    @BindView(R.id.switch_camera)
    Button mSwitchCamera;
    @BindView(R.id.beauty)
    Button mBeauty;
    @BindView(R.id.filter)
    Button mFilter;
    @BindView(R.id.water_mark)
    Button mWaterMark;
    @BindView(R.id.mirror)
    Button mMirror;
    @BindView(R.id.rtc)
    Button mRtc;
    @BindView(R.id.scale)
    Button mScale;
    @BindView(R.id.torch)
    Button mTorch;
    @BindView(R.id.voice)
    Button mVoice;
    @BindView(R.id.record)
    Button mRecord;
    @BindView(R.id.faceu)
    Button mFaceu;
    @BindView(R.id.gesture_view)
    GestureView mGestureView;
    @BindView(R.id.bright_progress)
    ProgressBar mBrightProgress;

    @BindView(R.id.remote_video_view_container1)
    FrameLayout mRemoteContainer1;
    @BindView(R.id.remote_video_view_container2)
    FrameLayout mRemoteContainer2;
    @BindView(R.id.remote_video_view_container3)
    FrameLayout mRemoteContainer3;

    @BindArray(R.array.beauty_levels)
    String[] mBeautyLevels;
    @BindArray(R.array.faceppstickers)
    String[] mFaceppStickers;
    @BindArray(R.array.sensemestickers)
    String[] mSenseMeStickers;
    @BindArray(R.array.filters)
    String[] mFilters;
    @BindArray(R.array.zooms)
    String[] mZooms;

    // should only be modified under UI thread
    private final HashMap<Integer, SurfaceView> mUidsList = new HashMap<>(); // uid = 0 || uid == EngineConfig.mUid

    private String mRtmpUrl;
    private ILangCameraStreamer magicEngine = null;
    private ILangCameraStreamer.LangVideoQuality mVideoQuality = ILangCameraStreamer.LangVideoQuality.LANG_VIDEO_QUALITY_LOW_1;
    private int mVideoWhich = 0;

    private boolean mHasPush = false;
    private ILangCameraStreamer.LangCameraBeauty mBeautyLevel = ILangCameraStreamer.LangCameraBeauty.LANG_BEAUTY_LEVEL_3;
    private int mBeautyWhich = 3;
    private int mFilterWhich = 0;
    private int mFaceuWhich = 0;
    private List<String> mFaceuStickersList;
    private LangFaceuConfig mFaceuConfig;
    private static final int START_PUSH = 0;
    private static final int GET_PUSH_INFO = 1;
    private static final int HINDDEN_PROGRESS = 2;
    private static final int GET_SYSTEM_INFO = 3;
    private AnimationCallback animationCallback = new AnimationCallback() {
        @Override
        public void onDecodeSuccess() {
            Log.e(TAG, "AnimationCallback - onDecodeSuccess");
        }

        @Override
        public void onDecodeError() {
            Log.e(TAG, "AnimationCallback - onDecodeError");
        }

        @Override
        public void onAnimationPlayFinish() {
            Log.e(TAG, "AnimationCallback - onAnimationPlayFinish");
        }
    } ;

    private float mCurBright = 0.0f;

    private int mScaleWhich = 0;

    private static class UpstreamHandler extends Handler {
        private WeakReference<UpActivity> mWeakActivity;
        public UpstreamHandler(UpActivity activity) {
            mWeakActivity = new WeakReference<UpActivity>(activity) ;
        }
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            UpActivity activity = mWeakActivity.get();
            if (activity == null) {
                return;
            }
            switch (msg.what) {
                case START_PUSH:
                    if (activity.magicEngine != null) {
                        activity.magicEngine.setBeauty(activity.mBeautyLevel);
                        activity.magicEngine.startStreaming(activity.mRtmpUrl);
                    }
                    break;
                case GET_PUSH_INFO:
                    //if (this != null)
                        //this.sendEmptyMessageDelayed(GET_PUSH_INFO, 1000);
                    activity.showPushInfo();
                    break;

                case HINDDEN_PROGRESS:
                    if (activity.mBrightProgress != null && activity.mBrightProgress.getVisibility() == View.VISIBLE)
                        activity.mBrightProgress.setVisibility(View.GONE);
                    break;
                case GET_SYSTEM_INFO:
                    activity.showSystemInfo(msg.obj);
                    break;
            }
        }
    }
    private static UpstreamHandler mHandler;

    private TimerTask mInfoTimerTask = new TimerTask() {
        @Override
        public void run() {
            //Message msg = new Message();
            int cpuUsagePercentage = (int) (Utils.readCpuUsage() * 100.0f);
            int memory = (int) Utils.readMemory();
            //msg.what = GET_SYSTEM_INFO;
            //msg.obj = new Object[]{cpuUsagePercentage, memory};

            if (mHandler != null) {
                mHandler.sendMessage(mHandler.obtainMessage(GET_SYSTEM_INFO, new Object[]{cpuUsagePercentage, memory})); //mHandler.sendMessage(msg);
                mHandler.sendEmptyMessage(GET_PUSH_INFO);
            }
        }
    };
    private java.util.Timer mTimer;

    private String mEncoderType;

    private void showPushInfo() {
        if (magicEngine != null && mPushInfo != null) {
            LangLiveInfo info = magicEngine.getLiveInfo();
            if (info != null) {
                StringBuilder s = new StringBuilder("");
                s.append("speed:" + (info.uploadSpeed * 8 / 1000) + "kbps\n");
                s.append("fps:  " + info.videoPushFrameCountPerSecond + "\n");
                s.append("preview fps:  " + info.previewFrameCountPerSecond);
                mPushInfo.setText(s);

                mRtmpSpeedTextView.setText("rtmp speed: " + (info.uploadSpeed * 8 / 1000) + "kbps");
                mRtmpFpsTextView.setText("rtmp fps: " + info.videoPushFrameCountPerSecond);
                mRtmpAudioCacheNumView.setText("audio cache: " + info.localBufferAudioCount + "frames");
                mRtmpVideoCacheNumView.setText("video cache: " + info.localBufferVideoCount + "frames");
                mPrevFpsTextView.setText("preview fps: " + info.previewFrameCountPerSecond);
            }

        }
    }

    private void showSystemInfo(Object obj) {
        Object[] data = (Object[])obj;
        int cpuUsagePercentage = (int)data[0];
        int memory = (int)data[1];
        mCpuUsageTextView.setText("cpu usage: " + cpuUsagePercentage + "%");
        mMemoryTextView.setText("memory: " + memory + "MB");
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_up;
    }

    private void checkRequiredPermissions() {
        List<String> permissionsList = new ArrayList<>();

        int checkPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if(checkPermission != PackageManager.PERMISSION_GRANTED) {
            permissionsList.add(Manifest.permission.CAMERA);
        }
        checkPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        if (checkPermission != PackageManager.PERMISSION_GRANTED) {
            permissionsList.add(Manifest.permission.RECORD_AUDIO);
        }
        checkPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (checkPermission != PackageManager.PERMISSION_GRANTED) {
            permissionsList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        if (permissionsList.size() > 0) {
            ActivityCompat.requestPermissions(this, permissionsList.toArray(new String[permissionsList.size()]), 100);
        }
    }

    @Override
    protected void init() {
        checkRequiredPermissions();

        mVideoWhich = getIntent().getIntExtra("video_set", 0);
        mRtmpUrl = getIntent().getStringExtra("rtmp_url");
        mEncoderType = getIntent().getStringExtra("encoder_type");
        mGestureView.setGestureListener(this);
        mGestureView.setMinDistance(65);
        LogUtils.d(mRtmpUrl + "");
        changeSettings();

        magicEngine = LangCameraStreamer.create();
        magicEngine.setOnErrorListener(this);
        magicEngine.setOnEventListener(this);
       /* if (mEncoderType.contains("MediaCodecPipeline")) {
            mSnailMagicCameraView.setEncoderType(LangVideoEncoderImpl.EncoderType.kHardwarePipeline);
        }else if (mEncoderType.contains("MediaCodec")) {
            mSnailMagicCameraView.setEncoderType(LangVideoEncoderImpl.EncoderType.kHardware);
        }else {
            mSnailMagicCameraView.setEncoderType(LangVideoEncoderImpl.EncoderType.kSoftware);
        }*/
        /*magicEngine.init(ILangCameraStreamer.LangAudioQuality.LANG_AUDIO_QUALITY_DEFAULT,
                mVideoQuality,
                mSnailMagicCameraView);*/
        if(getIntent().getBooleanExtra("open_hardware_speedup",true)){
            mSnailMagicCameraView.setEncoderType(LangVideoEncoderImpl.EncoderType.kHardware);
        }else {
            mSnailMagicCameraView.setEncoderType(LangVideoEncoderImpl.EncoderType.kSoftware);
        }
        mSnailMagicCameraView.enableGraphicBuffer(getIntent().getBooleanExtra("gb_enable",false));

        LangStreamerConfig config = new LangStreamerConfig();
        config.audioBitrate = 256 * 1000;
        config.audioSampleRate = 48000;
        config.audioChannel = 2;

        config.videoBitrate = getIntent().getIntExtra("bt",1000) * 1000;
        config.videoFPS = getIntent().getIntExtra("fps",24);
        config.videoMaxKeyframeInterval = 4 * config.videoFPS;
        if (mEncoderType.contains("720p")) {
            config.videoResolution = ILangCameraStreamer.LangVideoResolution.LANG_VIDEO_RESOLUTION_720P;
        }else if (mEncoderType.contains("540p")) {
            config.videoResolution = ILangCameraStreamer.LangVideoResolution.LANG_VIDEO_RESOLUTION_540P;
        }else if (mEncoderType.contains("480p")) {
            config.videoResolution = ILangCameraStreamer.LangVideoResolution.LANG_VIDEO_RESOLUTION_480P;
        }else if (mEncoderType.contains("360p")) {
            config.videoResolution = ILangCameraStreamer.LangVideoResolution.LANG_VIDEO_RESOLUTION_360P;
        }

        int result = magicEngine.init(config, mSnailMagicCameraView);
        mSnailMagicCameraView.setAnimationCallback(animationCallback);

        if(result != 0)
            ToastUtils.show(this, "初始化失败，请填写正确的参数");

        if (mBeautyWhich == 0)
            mBeauty.setText("美颜/关");
        else
            mBeauty.setText("美颜/" + mBeautyWhich);

        mFaceuConfig = new LangFaceuConfig();
        preloadStickerPackage();

        mHandler = new UpstreamHandler(this);
        mTimer = new java.util.Timer();
        mTimer.scheduleAtFixedRate(mInfoTimerTask, 1, 1000);
    }

    private void preloadStickerPackage() {
        mFaceuStickersList = new java.util.ArrayList<String>();
        /*
        for (int index = 1; index < mFaceppStickers.length; index++) {
            String stickerDir = "facepp_sticker";
            String stickerName = mFaceppStickers[index] + ".zip";
            String stickerReadablePath = saveAssestsData(this, stickerDir, stickerName);
            mFaceuStickersList.add(stickerReadablePath);
        }
        */
        for (int index = 1; index < mSenseMeStickers.length; index++) {
            String stickerDir = "senseme_sticker";
            String stickerName = mSenseMeStickers[index] + ".zip";
            String stickerReadablePath = saveAssestsData(this, stickerDir, stickerName);
            mFaceuStickersList.add(stickerReadablePath);
        }
    }

    static String saveAssestsData(Context context, String filePath, String name) {
        InputStream inputStream;
        try {
            inputStream = context.getResources().getAssets().open(filePath + "/" + name);

//            File file = context.getExternalFilesDir("beautify");
//			File file = context.getCacheDir();
//			File file = new File(Environment.getExternalStorageDirectory() + "/beautify");
            File file = new File("/sdcard/tempdir");
            if (!file.exists()) {
                file.mkdirs();
            }

            String path = file + "/" + name;
            File stickerFile = new File(path);
            if (stickerFile.exists())
                return stickerFile.getAbsolutePath();

            FileOutputStream fileOutputStream = new FileOutputStream(path);
            byte[] buffer = new byte[512];
            int count = 0;
            while ((count = inputStream.read(buffer)) > 0) {
                fileOutputStream.write(buffer, 0, count);
            }

            fileOutputStream.flush();
            fileOutputStream.close();
            inputStream.close();
            return path;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }


    private void changeSettings() {
        switch (mVideoWhich) {
            case 0:
                mVideoQuality = ILangCameraStreamer.LangVideoQuality.LANG_VIDEO_QUALITY_LOW_1;
                break;
            /*case 1:
                mVideoQuality = ILangCameraStreamer.LangVideoQuality.LANG_VIDEO_QUALITY_LOW_2;
                break;
            case 2:
                mVideoQuality = ILangCameraStreamer.LangVideoQuality.LANG_VIDEO_QUALITY_LOW_3;
                break;
            case 3:
                mVideoQuality = ILangCameraStreamer.LangVideoQuality.LANG_VIDEO_QUALITY_MEDIUM_1;
                break;
            case 4:
                mVideoQuality = ILangCameraStreamer.LangVideoQuality.LANG_VIDEO_QUALITY_MEDIUM_2;
                break;*/
            case 1:
                mVideoQuality = ILangCameraStreamer.LangVideoQuality.LANG_VIDEO_QUALITY_MEDIUM_3;
                break;
           /* case 6:
                mVideoQuality = ILangCameraStreamer.LangVideoQuality.LANG_VIDEO_QUALITY_HIGH_1;
                break;
            case 7:
                mVideoQuality = ILangCameraStreamer.LangVideoQuality.LANG_VIDEO_QUALITY_HIGH_2;
                break;*/
            case 2:
                mVideoQuality = ILangCameraStreamer.LangVideoQuality.LANG_VIDEO_QUALITY_HIGH_3;
                break;
            default:
                mVideoQuality = ILangCameraStreamer.LangVideoQuality.LANG_VIDEO_QUALITY_LOW_1;
                break;
        }
    }

    @OnClick({R.id.back, R.id.switch_camera, R.id.switch_orientation, R.id.show_debug_view, R.id.gift_animation, R.id.beauty_hair,
                R.id.beauty, R.id.filter, R.id.water_mark, R.id.mirror, R.id.rtc,
                R.id.scale, R.id.torch, R.id.voice, R.id.record, R.id.faceu})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.back:
                finish();
                break;
            case R.id.switch_camera:
                magicEngine.switchCamera();
                break;
            case R.id.switch_orientation:
                screenshot(); //switchOrientation();
                break;
            case R.id.show_debug_view:
                showDebugView();
                break;
            case R.id.gift_animation:
                toggleGiftAnimation();
                break;
            case R.id.beauty_hair:
                toggleBeautyHair();
                break;
            case R.id.beauty:
                changeBeauty();
                break;
            case R.id.filter:
                changeFilter();
                break;
            case R.id.water_mark:
                switchWaterMark();
                break;
            case R.id.mirror:
                switchMirror();
                break;
            case R.id.rtc:
                toggleRtc();
                break;
            case R.id.scale:
                changeScale();
                break;
            case R.id.torch:
                switchTorch();
                break;
            case R.id.voice:
                switchVoice();
                break;
            case R.id.record:
                switchRecord();
                break;
            case R.id.faceu:
                changeFaceu();
                break;
        }
    }

    private boolean isLandscape = false;
    private void switchOrientation() {
        if (isLandscape == false) {
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else {
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        isLandscape = !isLandscape;
    }

    private void screenshot() {
        String folder = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "SnailStream/record/pictures/";
        File folderFile = new File(folder);
        if (!folderFile.exists())
            folderFile.mkdirs();
        magicEngine.screenshot(folder);
    }

    private boolean showDebugView = false;
    private void showDebugView() {
        showDebugView = !showDebugView;
        if (showDebugView) {
            mDebugLinear.setVisibility(View.VISIBLE);
        } else {
            mDebugLinear.setVisibility(View.INVISIBLE);
        }
    }

    private void toggleGiftAnimation() {
        LangObjectSegmentationConfig params = new LangObjectSegmentationConfig(LangObjectSegmentationConfig.Companion.getMATTING(), 1f);
        try {
//            magicEngine.setGiftAnimation(params, getAssets().open("webp/PNG_8064MS.webp"), getAssets().open("webp/3309_1.webp"));

            //background - use webp file
            String folderName = "webp";
            String filename = "實底_7056MS.webp";  // 光斑_19068MS

            //background - use png zip file
//            String folderName = "pngbgm";
//            String filename = "bgmpng_40.zip";
            String backgroundFilePath = copyToSdcard(folderName, filename);

            //gift
            folderName = "webp";
            filename = "3309_1.webp";
            String giftFilePath = copyToSdcard(folderName, filename);
            magicEngine.setMattingAnimation(params, backgroundFilePath, null, animationCallback);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String copyToSdcard(String folderName, String inputString){
        CopyAssets widget = new CopyAssets();
        String result = widget.saveArAssestsDataLang(this, folderName, inputString);
        File file = new File(result);
        if(file.exists())
            return result;
        else
            return null;
    }

    private boolean showBeautyHair = false;
    private void toggleBeautyHair() {
        showBeautyHair = !showBeautyHair;
        if (showBeautyHair) {
            LangObjectSegmentationConfig params = new LangObjectSegmentationConfig(
                    LangObjectSegmentationConfig.Companion.getHAIR(),
                    0.5f,
                    0xFFB92B27,
                    0xFFB92B27);
            magicEngine.setHairColors(params);
        } else {
            magicEngine.setHairColors(null);
        }
    }

    private void switchRecord() {
        if (mRecord.getText().toString().contentEquals("录制/关")) {
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                String folder = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "SnailStream/record/mp4/";
                File folderFile = new File(folder);
                if (!folderFile.exists())
                    folderFile.mkdirs();
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS");
                magicEngine.startRecording(folder + format.format(new Date()) + ".mp4");
                mRecord.setText("录制/开");
            } else {
                ToastUtils.show(this, "sdcard 不可用");
            }
        } else if (mRecord.getText().toString().contentEquals("录制/开")) {
            magicEngine.stopRecording();

            mRecord.setText("录制/关");
        }
    }

    private void switchVoice() {
        if (mVoice.getText().toString().contentEquals("静音/开")) {
            magicEngine.setMute(false);
            mVoice.setText("静音/关");
        } else if (mVoice.getText().toString().contentEquals("静音/关")) {
            magicEngine.setMute(true);
            mVoice.setText("静音/开");
        }
    }

    private void switchTorch() {
        if (mTorch.getText().toString().contentEquals("手电/关")) {
            magicEngine.setCameraToggleTorch(true);
            mTorch.setText("手电/开");
        } else if (mTorch.getText().toString().contentEquals("手电/开")) {
            magicEngine.setCameraToggleTorch(false);
            mTorch.setText("手电/关");
        }
    }

    private void changeScale() {
        new AlertDialog.Builder(this).setTitle("选择放大倍数")
                .setSingleChoiceItems(mZooms, mScaleWhich,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                if (mScaleWhich != which) {

                                    mScaleWhich = which;
                                    magicEngine.setZoom(mScaleWhich + 1);
                                    mScale.setText("放大/" + (mScaleWhich + 1) + ".0");
                                }
                                dialog.dismiss();
                            }
                        }).setNegativeButton("取消", null).show();
    }

    private void switchMirror() {
        if (mMirror.getText().toString().contentEquals("镜像/关")) {
            magicEngine.setMirror(true);

            mMirror.setText("镜像/开");
        } else if (mMirror.getText().toString().contentEquals("镜像/开")) {
            magicEngine.setMirror(false);
            mMirror.setText("镜像/关");
        }

        LangLiveInfo info = magicEngine.getLiveInfo();
        /*
        *   public int cameraPresent;  //当前camera 位置， 0前置（默认），1（后置）
            public int connectStatus;  //连接状态 0 未连接 ，1连接中， 2，连接成功
            public int uploadSpeed;//当前上传速度，单位 Byte
            public int localBufferAudioCount; //本地缓存audio帧数
            public int localBufferVideoCount; //本地缓存video帧数
            public int videoEncodeFrameCountPerSecond; //　视频编码速度
            public int videoPushFrameCountPerSecond; //推流速度
            public int videoDropFrameCountPerSencond; // 丢帧速度

            public int encodeVideoFrameCount;//总编码video帧数
            public int pushVideoFrameCount;//总推流video帧数
            public int videoDiscardFrameCount;//video丢帧数
            public int previewFrameCountPerSecond;        //camera实时预览帧率
        */
        DebugLog.efmt("lichao", "connectStatus %d uploadSpeed %d localBufferAudioCount %d localBufferVideoCount %d" +
                        " videoEncodeFrameCountPerSecond %d videoPushFrameCountPerSecond %d videoDropFrameCountPerSencond %d" +
                        " encodeVideoFrameCount %d pushVideoFrameCount %d videoDiscardFrameCount %d previewFrameCountPerSecond %d",
                info.connectStatus,
                info.uploadSpeed,
                info.localBufferAudioCount,
                info.localBufferVideoCount,
                info.videoEncodeFrameCountPerSecond,
                info.videoPushFrameCountPerSecond,
                info.videoDropFrameCountPerSencond,
                info.encodeVideoFrameCount,
                info.pushVideoFrameCount,
                info.videoDiscardFrameCount,
                info.previewFrameCountPerSecond);
    }

    private void toggleRtc() {
        if (mRtc.getText().toString().contentEquals("加入连麦")) {

            LangRtcConfig rtcConfig = new LangRtcConfig();
            rtcConfig.videoProfile = LangRtcConfig.kVideoProfiles[2];
            rtcConfig.channelName = "langlive-test";
            rtcConfig.encryptionKey = null;
            rtcConfig.encryptionMode = "AES-128-XTS";

            if (!magicEngine.rtcAvailable(rtcConfig)) {
                mRtcConnectStatusTextView.setText("连麦: 当前设备不支持");
                return;
            }

            LangRtcConfig.RtcDisplayParams rtcDisplay = new LangRtcConfig.RtcDisplayParams();
            rtcDisplay.topOffsetPercent = 0.15f;
            rtcDisplay.subWidthPercentOnTwoWins = 0.5f;
            rtcDisplay.subHeightPercentOnTwoWins = 0.45f;
            magicEngine.setRtcDisplayLayoutParams(rtcDisplay);

            /*
            SurfaceView surfaceV = magicEngine.createRtcRenderView();
            surfaceV.setZOrderOnTop(false);
            surfaceV.setZOrderMediaOverlay(false);
            mUidsList.put(0, surfaceV); // get first surface view
            */

            magicEngine.joinRtcChannel(rtcConfig, null);

            mRtc.setText("离开连麦");
        } else if (mRtc.getText().toString().contentEquals("离开连麦")) {

            magicEngine.leaveRtcChannel();

            mRtc.setText("加入连麦");
        }
    }

    private void switchWaterMark() {
        if (mWaterMark.getText().toString().contentEquals("水印/开")) {
            LangWatermarkConfig config = new LangWatermarkConfig();
            config.url = "";//"filter/snail_h.png";
            config.x = 0;
            config.y = 0;
            config.w = 250;
            config.h = 250;
            config.enable = false;
            magicEngine.setWatermark(config);
            mWaterMark.setText("水印/关");
        } else if (mWaterMark.getText().toString().contentEquals("水印/关")) {

            LangWatermarkConfig config = new LangWatermarkConfig();
            config.url = "";//"filter/snail_h.png";
            Bitmap image = null;
            AssetManager am = getResources().getAssets();
            try{
                InputStream is = am.open("filter/lang_logo.png");
                BitmapFactory.Options opt = new BitmapFactory.Options();
                opt.inPreferredConfig = Bitmap.Config.ARGB_8888;
                image = BitmapFactory.decodeStream(is, null, opt);
                is.close();
            }catch (IOException e){
                e.printStackTrace();
            }
            config.picture = image;
            config.x = 100;
            config.y = 80;
            config.w = 120;
            config.h = 120;
            config.enable = true;
            magicEngine.setWatermark(config);
            mWaterMark.setText("水印/开");
        }
    }

    private void changeFilter() {
        new AlertDialog.Builder(this).setTitle("选择滤镜")
                .setSingleChoiceItems(mFilters, mFilterWhich,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                if (mFilterWhich != which) {
                                    ILangCameraStreamer.LangCameraFilter type = ILangCameraStreamer.LangCameraFilter.values()[which];
                                    mFilterWhich = which;
                                    magicEngine.setFilter(type);
                                    if (mFilterWhich == 0)
                                        mFilter.setText("滤镜/关");
                                    else
                                        mFilter.setText("滤镜/" + mFilterWhich);
                                }
                                dialog.dismiss();
                            }
                        }).setNegativeButton("取消", null).show();
    }

    private void changeFaceu() {
        final Context context = this;
        new AlertDialog.Builder(this).setTitle("选择贴纸")
                .setSingleChoiceItems(mSenseMeStickers/*mFaceppStickers*/, mFaceuWhich,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                if (mFaceuWhich != which) {
                                    mFaceuWhich = which;
                                    if (which == 0) {
                                        mFaceuConfig.defaultBeautyParams();
                                        mFaceuConfig.beautyStickerPath = null;
                                    }
                                    else {
                                        mFaceuConfig.defaultBeautyParams();
                                        mFaceuConfig.beautyStickerPath = mFaceuStickersList.get(mFaceuWhich-1);
                                        if (mFaceuConfig.beautyStickerPath.contains("beauty")) {
                                            mFaceuConfig.needSpecialSticker = true;
                                        } else {
                                            mFaceuConfig.needSpecialSticker = false;
                                        }
                                    }
                                    magicEngine.setFaceu(mFaceuConfig);

                                    // optional invoke api.
                                    // if you prefer not to use default preset beauty level, you can use mFaceuConfig params
                                    // to modify specific detail params.
                                    magicEngine.setBeauty(mBeautyLevel);
                                }
                                dialog.dismiss();
                            }
                        }).setNegativeButton("取消", null).show();
    }

    private void changeBeauty() {
        new AlertDialog.Builder(this).setTitle("选择美颜级别")
                .setSingleChoiceItems(mBeautyLevels, mBeautyWhich,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                if (mBeautyWhich != which) {
                                    mBeautyWhich = which;
                                    switch (which) {
                                        case 0:
                                            mBeautyLevel = ILangCameraStreamer.LangCameraBeauty.LANG_BEAUTY_NONE;
                                            break;
                                        case 1:
                                            mBeautyLevel = ILangCameraStreamer.LangCameraBeauty.LANG_BEAUTY_LEVEL_1;
                                            break;
                                        case 2:
                                            mBeautyLevel = ILangCameraStreamer.LangCameraBeauty.LANG_BEAUTY_LEVEL_2;
                                            break;
                                        case 3:
                                            mBeautyLevel = ILangCameraStreamer.LangCameraBeauty.LANG_BEAUTY_LEVEL_3;
                                            break;
                                        case 4:
                                            mBeautyLevel = ILangCameraStreamer.LangCameraBeauty.LANG_BEAUTY_LEVEL_4;
                                            break;
                                        case 5:
                                            mBeautyLevel = ILangCameraStreamer.LangCameraBeauty.LANG_BEAUTY_LEVEL_5;
                                            break;
                                        default:
                                            return;
                                    }
                                    magicEngine.setBeauty(mBeautyLevel);
                                    if (mBeautyWhich == 0)
                                        mBeauty.setText("美颜/关");
                                    else
                                        mBeauty.setText("美颜/" + mBeautyWhich);
                                }

                                dialog.dismiss();
                            }
                        }).setNegativeButton("取消", null).show();
    }

    @Override
    public void onError(ILangCameraStreamer iLangCameraStreamer, ILangCameraStreamer.LangStreamerError langStreamerError, int i) {
        DebugLog.d(TAG, "ERROR :" + langStreamerError.name());
        //Toast.makeText(this, langStreamerError.name(), Toast.LENGTH_LONG).show();
        dealError(langStreamerError);
    }

    private void dealError(ILangCameraStreamer.LangStreamerError langStreamerError) {
        switch (langStreamerError) {
            case LANG_ERROR_PUSH_CONNECT_FAIL:
                setLinkTipText("连接失败");
                break;
            case LANG_ERROR_OPEN_CAMERA_FAIL:
                setErrorInfoText("打开相机失败");
                break;
            case LANG_ERROR_OPEN_MIC_FAIL:
                setErrorInfoText("打开麦克风失败");
                break;
            case LANG_ERROR_VIDEO_ENCODE_FAIL:
                setErrorInfoText("视频编码失败");
                break;
            case LANG_ERROR_AUDIO_ENCODE_FAIL:
                setErrorInfoText("音频编码失败");
                break;
            case LANG_ERROR_RECORD_FAIL:
                setErrorInfoText("录制失败");
                break;
            case LANG_ERROR_UNSUPPORTED_FORMAT:
                setErrorInfoText("不支持的推流格式");
                break;
            case LANG_ERROR_NO_PERMISSIONS:
                setErrorInfoText("权限不足");
                break;
            case LANG_ERROR_AUTH_FAIL:
                setErrorInfoText("SDK未授权");
                break;
            case LANG_ERROR_SCREENSHOT_FAIL:
                setErrorInfoText("截屏失败");
                break;
        }
    }


    @Override
    public void onEvent(ILangCameraStreamer iLangCameraStreamer, ILangCameraStreamer.LangStreamerEvent langStreamerEvent, int i) {

        DebugLog.d(TAG, "EVENT :" + langStreamerEvent.name());
        dealEvent(langStreamerEvent);
    }

    @Override
    public void onEvent(ILangCameraStreamer iLangCameraStreamer, ILangCameraStreamer.LangStreamerEvent langStreamerEvent, Object obj) {

        DebugLog.d(TAG, "EVENT :" + langStreamerEvent.name());
        dealEvent(langStreamerEvent, obj);
    }

    private void dealEvent(ILangCameraStreamer.LangStreamerEvent langStreamerEvent) {
        switch (langStreamerEvent) {
            case LANG_EVENT_PUSH_CONNECT_SUCC:
                setLinkTipText("连接成功");
                /*
                if (mHandler != null) {
                    mHandler.removeMessages(GET_PUSH_INFO);
                    mHandler.sendEmptyMessage(GET_PUSH_INFO);
                }
                */
                break;
            case LANG_EVENT_PUSH_DISCONNECT:
                setLinkTipText("连接断开");
                break;
            case LANG_EVENT_PUSH_RECONNECTING://LANG_WARNNING_RECONNECTING:
                setLinkTipText("重连中...");
                break;
            case LANG_EVENT_PUSH_CONNECTING:
                setLinkTipText("连接中...");
                break;
            case LANG_EVENT_RECORD_BEGIN:
                setErrorInfoText("开始录制");
                break;
            case LANG_EVENT_RECORD_END:
                setErrorInfoText("结束录制");
                break;
            case LANG_WARNNING_NETWORK_WEAK:
                setErrorInfoText("网络差");
                break;
            case LANG_WARNNING_HW_ACCELERATION_FAIL:
                setErrorInfoText("硬编码失败");
                break;
        }
    }

    private void dealEvent(ILangCameraStreamer.LangStreamerEvent langStreamerEvent, Object obj) {
        switch (langStreamerEvent) {
            case LANG_EVENT_RTC_CONNECTING: {
                mRtcConnectStatusTextView.setText("连麦: 连接中...");
                break;
            }
            case LANG_EVENT_RTC_CONNECT_SUCC: {
                mRtcConnectStatusTextView.setText("连麦: 成功加入");
                break;
            }
            case LANG_EVENT_RTC_DISCONNECT: {
                mRtcConnectStatusTextView.setText("连麦: 成功退出");
                mRtcMessageTextView.setText("");
                mRtcUsersTextView.setText("");
                mRtcTxTextView.setText("");
                mRtcRxTextView.setText("");
                mUidsList.clear();
                refreshRemoteViews();
                break;
            }
            case LANG_EVENT_RTC_USER_JOINED: {
                Object[] joinedData = (Object[]) obj;
                int uid = (int) joinedData[0];
                doRenderRemoteUi(uid);
                mRtcMessageTextView.setText(String.format("连麦: 用户(0x%x)加入房间", uid));
                break;
            }
            case LANG_EVENT_RTC_USER_VIDEO_RENDERED: {
                Object[] renderedData = (Object[]) obj;
                int uid = (int)renderedData[0];
                int width = (int)renderedData[1];
                int height = (int)renderedData[2];
                mRtcMessageTextView.setText(String.format("连麦: 用户(0x%x)分辨率变化为(%dx%d)", uid, width, height));
                break;
            }
            case LANG_EVENT_RTC_USER_OFFLINE: {
                Object[] offlineData = (Object[]) obj;
                int uid = (int) offlineData[0];
                doRemoveRemoteUi(uid);
                mRtcMessageTextView.setText(String.format("连麦: 用户(0x%x)离开房间", uid));
                break;
            }
            case LANG_EVENT_RTC_USER_AUDIO_MUTED:
                break;
            case LANG_EVENT_RTC_USER_VIDEO_MUTED:
                break;
            case LANG_EVENT_RTC_AUDIO_ROUTE_CHANGE:
                break;
            case LANG_EVENT_RTC_STATS_UPDATE: {
                LangRtcInfo rtcInfo = (LangRtcInfo)obj;
                mRtcUsersTextView.setText(String.format("连麦: 在线(%d)人", rtcInfo.onlineUsers));
                int txKBitrate = rtcInfo.txAudioKBitrate + rtcInfo.txVideoKBitrate;
                int rxKBitrate = rtcInfo.rxAudioKBitrate + rtcInfo.rxVideoKBitrate;
                mRtcTxTextView.setText(String.format("连麦: 上行速率(%.0f)KB/s", (float)txKBitrate/8));
                mRtcRxTextView.setText(String.format("连麦: 下行速率(%.0f)KB/s", (float)rxKBitrate/8));
                break;
            }
            case LANG_EVENT_RTC_NETWORK_LOST: {
                mRtcConnectStatusTextView.setText("连麦: 重新连接中...");
                break;
            }
            case LANG_EVENT_RTC_NETWORK_TIMEOUT: {
                mRtcConnectStatusTextView.setText("连麦: 已超时");
                break;
            }
            case LANG_EVENT_SCREENSHOT_BEGIN:
                break;
            case LANG_EVENT_RECORD_END:
                break;
            case LANG_EVENT_FACE_DETECTED:
                setErrorInfoText("检测到人脸");
                break;
            case LANG_EVENT_FACE_LOST:
                setErrorInfoText("人脸丢失");
                break;
            case LANG_EVENT_HAND_DETECTED:
                LangFaceHandler.GestureType gesture = (LangFaceHandler.GestureType)obj;
                setFaceuHandInfoText(gesture.name());
                break;
            case LANG_EVENT_HAND_LOST:
                setFaceuHandInfoText("手势丢失");
                break;
        }
    }

    private FrameLayout currentAvailableLayout() {

        if (mRemoteContainer1.getChildCount() < 1)
            return mRemoteContainer1;

        if (mRemoteContainer2.getChildCount() < 1)
            return mRemoteContainer2;

        if (mRemoteContainer3.getChildCount() < 1)
            return mRemoteContainer3;

        return null;
    }

    private void refreshRemoteViews() {

        mRemoteContainer1.removeAllViews();
        mRemoteContainer2.removeAllViews();
        mRemoteContainer3.removeAllViews();

        for (HashMap.Entry<Integer, SurfaceView> entry : mUidsList.entrySet()) {

            FrameLayout layout = currentAvailableLayout();
            if (layout != null) {
                layout.addView(entry.getValue());
            } else {
                throw new RuntimeException("rtc session count > 4, view not support.");
            }
        }
    }

    private void doRenderRemoteUi(final int uid) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isFinishing()) {
                    return;
                }

                if (mUidsList.containsKey(uid)) {
                    return;
                }

                SurfaceView surfaceV = magicEngine.createRtcRenderView();
                mUidsList.put(uid, surfaceV);

                surfaceV.setTag(uid);
                surfaceV.setZOrderOnTop(true);
                surfaceV.setZOrderMediaOverlay(true);

                refreshRemoteViews();

                magicEngine.setupRtcRemoteUser(uid, surfaceV);
            }
        });
    }

    private void doRemoveRemoteUi(final int uid) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isFinishing()) {
                    return;
                }

                Object target = mUidsList.remove(uid);
                if (target == null) {
                    return;
                }

                refreshRemoteViews();
            }
        });
    }

    private void setLinkTipText(String text) {
        if (mLinkTip != null)
            mLinkTip.setText(text);
    }

    private void setErrorInfoText(String text) {
        if (mErrorInfo != null)
            mErrorInfo.setText(text);
    }

    private void setFaceuHandInfoText(String text) {
        if (mFaceuHandInfo != null)
            mFaceuHandInfo.setText(text);
    }


    @Override
    protected void onResume() {
        super.onResume();
        if (magicEngine != null && mHandler != null) {
            magicEngine.startPreview();
            mHandler.sendEmptyMessageDelayed(START_PUSH, 2000);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (magicEngine != null) {
            magicEngine.stopPreview();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (magicEngine != null) {

            magicEngine.stopPreview();
            magicEngine.release();
            magicEngine.setOnErrorListener(null);
            magicEngine.setOnEventListener(null);
            magicEngine = null;
        }
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
            mHandler = null;
        }

        if (mFaceuStickersList != null) {
            mFaceuStickersList.clear();
            mFaceuStickersList = null;
        }

        mFaceuConfig = null;

        mInfoTimerTask.cancel();
        mInfoTimerTask = null;
        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }
        //System.gc();
        //BaseApplication.getRefWatcher().watch(this);
    }

    @Override
    public void onClick(float x, float y) {
        if (magicEngine != null)
            magicEngine.setCameraFocusing(x, y);
    }

    @Override
    public void onLongPress() {

    }

    @Override
    public void onVerticalUp() {
        if (mBrightProgress != null && mBrightProgress.getVisibility() == View.GONE)
            mBrightProgress.setVisibility(View.VISIBLE);
        mCurBright += 0.1f;

        if (mCurBright > 1.0f)
            mCurBright = 1.0f;
        if (magicEngine != null)
            magicEngine.setCameraBrightLevel(mCurBright);
        if (mBrightProgress != null)
            mBrightProgress.setProgress((int) (mCurBright * 10));

    }

    @Override
    public void onVerticalDown() {
        if (mBrightProgress != null && mBrightProgress.getVisibility() == View.GONE)
            mBrightProgress.setVisibility(View.VISIBLE);

        mCurBright -= 0.1f;

        if (mCurBright < 0.0f)
            mCurBright = 0.0f;
        if (magicEngine != null)
            magicEngine.setCameraBrightLevel(mCurBright);
        if (mBrightProgress != null)
            mBrightProgress.setProgress((int) (mCurBright * 10));
    }

    @Override
    public void onHorizontalLeft() {

    }

    @Override
    public void onHorizontalRight() {

    }

    @Override
    public void onScale(float scale) {
        Log.e(TAG, "onScale-->" + scale);
    }


    @Override
    public void onPointerUp() {
        if (mHandler != null) {
            mHandler.sendEmptyMessage(HINDDEN_PROGRESS);
        }
    }
}
