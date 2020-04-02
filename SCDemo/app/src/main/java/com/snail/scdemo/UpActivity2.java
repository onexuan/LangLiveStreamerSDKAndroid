package com.snail.scdemo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.lang.ref.WeakReference;
import java.util.Locale;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.opengl.GLSurfaceView;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
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

import com.lang.lang.R;
import com.snail.scdemo.utils.ToastUtils;
import com.snail.scdemo.view.GestureView;


import net.lang.streamer2.LangRtcInfo;
import net.lang.streamer2.LangRtcUser;
import net.lang.streamer2.LangRtmpInfo;
import net.lang.streamer2.config.LangAnimationConfig;
import net.lang.streamer2.config.LangBeautyhairConfig;
import net.lang.streamer2.config.LangFaceuConfig;
import net.lang.streamer2.config.LangRtcConfig;
import net.lang.streamer2.config.LangStreamerConfig;
import net.lang.streamer2.config.LangWatermarkConfig;
import net.lang.streamer2.ILangCameraStreamer;
import net.lang.streamer2.LangMediaCameraStreamer;

import butterknife.BindArray;
import butterknife.BindView;
import butterknife.OnClick;

public class UpActivity2 extends BaseActivity implements ILangCameraStreamer.ILangCameraStreamerOnErrorListener,
        ILangCameraStreamer.ILangCameraStreamerOnEventListener {
    @BindView(R.id.up2_glsurfaceview_view)
    GLSurfaceView mSnailMagicCameraView;

    @BindView(R.id.up2_link_tip)
    TextView mLinkTip;
    @BindView(R.id.up2_rtmp_network_info)
    TextView mRtmpNetworkInfo;
    @BindView(R.id.up2_error_info)
    TextView mErrorInfo;
    @BindView(R.id.up2_faceu_hand_info)
    TextView mFaceuHandInfo;

    @BindView(R.id.up2_debugLinear)
    LinearLayout mDebugLinear;
    @BindView(R.id.up2_resolutionDebug)
    TextView mResolutionTextView;
    @BindView(R.id.up2_previewFpsDebug)
    TextView mPrevFpsTextView;
    @BindView(R.id.up2_cpuDebug)
    TextView mCpuUsageTextView;
    @BindView(R.id.up2_memoryDebug)
    TextView mMemoryTextView;
    @BindView(R.id.up2_rtmpBandwidthDebug)
    TextView mRtmpSpeedTextView;
    @BindView(R.id.up2_rtmpFpsDebug)
    TextView mRtmpFpsTextView;
    @BindView(R.id.up2_rtmpAudioCacheDebug)
    TextView mRtmpAudioCacheNumView;
    @BindView(R.id.up2_rtmpVideoCacheDebug)
    TextView mRtmpVideoCacheNumView;
    @BindView(R.id.up2_rtmpAudioTotalCountDebug)
    TextView mRtmpAudioTotalPushCountsView;
    @BindView(R.id.up2_rtmpVideoTotalCountDebug)
    TextView mRtmpVideoTotalPushCountsView;
    @BindView(R.id.up2_rtmpVideoDropCountDebug)
    TextView mRtmpVideoDropCountsView;
    @BindView(R.id.up2_recordMessageDebug)
    TextView mRecordMessageTextView;
    @BindView(R.id.up2_animationMessageDebug)
    TextView mAnimationMessageTextView;
    @BindView(R.id.up2_rtcLocalConnectStatusDebug)
    TextView mRtcConnectStatusTextView;
    @BindView(R.id.up2_rtcMessageDebug)
    TextView mRtcMessageTextView;
    @BindView(R.id.up2_rtcJoinedUsersDebug)
    TextView mRtcUsersTextView;
    @BindView(R.id.up2_rtcTxBandwidthDebug)
    TextView mRtcTxTextView;
    @BindView(R.id.up2_rtcRxBandwidthDebug)
    TextView mRtcRxTextView;

    @BindView(R.id.up2_show_debug_view)
    Button mDebugView;
    @BindView(R.id.up2_gift_animation)
    Button mGiftView;
    @BindView(R.id.up2_beauty_hair)
    Button mBeautyHairView;
    @BindView(R.id.up2_switch_orientation)
    Button mSwitchOrientation;
    @BindView(R.id.up2_switch_camera)
    Button mSwitchCamera;
    @BindView(R.id.up2_beauty)
    Button mBeauty;
    @BindView(R.id.up2_filter)
    Button mFilter;
    @BindView(R.id.up2_water_mark)
    Button mWaterMark;
    @BindView(R.id.up2_mirror)
    Button mMirror;
    @BindView(R.id.up2_rtc)
    Button mRtc;
    @BindView(R.id.up2_scale)
    Button mScale;
    @BindView(R.id.up2_torch)
    Button mTorch;
    @BindView(R.id.up2_voice)
    Button mVoice;
    @BindView(R.id.up2_record)
    Button mRecord;
    @BindView(R.id.up2_faceu)
    Button mFaceu;
    @BindView(R.id.up2_gesture_view)
    GestureView mGestureView;
    @BindView(R.id.up2_bright_progress)
    ProgressBar mBrightProgress;

    @BindView(R.id.up2_remote_video_view_container1)
    FrameLayout mRemoteContainer1;
    @BindView(R.id.up2_remote_video_view_container2)
    FrameLayout mRemoteContainer2;
    @BindView(R.id.up2_remote_video_view_container3)
    FrameLayout mRemoteContainer3;

    @BindArray(R.array.beauty_levels)
    String[] mBeautyLevels;
    @BindArray(R.array.gift_animations)
    String[] mGiftAnimations;
    @BindArray(R.array.sensemestickers)
    String[] mSenseMeStickers;
    @BindArray(R.array.filters)
    String[] mFilters;
    @BindArray(R.array.zooms)
    String[] mZooms;

    private static StreamerEventHandler mSreamerEventHandler;
    private static final int MSG_PUSH_RTMP = 0;
    private static final int MSG_UPDATE_RTMP_INFO = 2;
    private static final int MSG_UPDATE_SYS_INFO = 3;

    private int mBeautyWhich = 3;
    private int mFilterWhich = 0;
    private int mFaceuWhich = 0;
    private int mAnimationWhich = 0;
    private List<String> mFaceuStickersList = new java.util.ArrayList<String>();
    private List<String> mAnimationsList = new java.util.ArrayList<String>();
    private LangFaceuConfig mFaceuConfig = new LangFaceuConfig();
    private LangBeautyhairConfig mBeautyHairConfig = new LangBeautyhairConfig(0.5f);
    private LangAnimationConfig mAnimationConfig = new LangAnimationConfig();
    private ILangCameraStreamer.LangCameraBeauty mBeautyLevel = ILangCameraStreamer.LangCameraBeauty.LANG_BEAUTY_LEVEL_3;
    private ILangCameraStreamer mCameraStreamer = null;

    // should only be modified under UI thread
    private final HashMap<Integer, SurfaceView> mUidsList = new HashMap<>();

    @Override
    protected int getLayoutId() {
        return R.layout.activity_up2;
    }

    @Override
    protected void init() {

        checkRequiredPermissions();
        preloadStickerPackage();
        preloadAnimationPackage();

        mSreamerEventHandler = new StreamerEventHandler(this);

        LangStreamerConfig streamerConfig = checkStreamerConfig();
        mCameraStreamer = LangMediaCameraStreamer.create();

        int result = mCameraStreamer.init(streamerConfig, mSnailMagicCameraView);
        mCameraStreamer.setOnEventListener(this);
        mCameraStreamer.setOnErrorListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mBeautyWhich == 0)
            mBeauty.setText("美颜/关");
        else
            mBeauty.setText("美颜/" + mBeautyWhich);

        //mCameraStreamer.enablePureAudio(true);
        mCameraStreamer.startPreview();

        String rtmpUrl = getIntent().getStringExtra("rtmp_url");
        mSreamerEventHandler.sendMessageDelayed(mSreamerEventHandler.obtainMessage(MSG_PUSH_RTMP, rtmpUrl), 2000);
    }

    @Override
    protected void onPause() {
        super.onPause();

        mCameraStreamer.stopStreaming();
        mCameraStreamer.stopPreview();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mCameraStreamer.release();
        mCameraStreamer.setOnErrorListener(null);
        mCameraStreamer.setOnEventListener(null);
        mCameraStreamer = null;
    }

    @OnClick({R.id.up2_back, R.id.up2_switch_camera, R.id.up2_switch_orientation, R.id.up2_show_debug_view, R.id.up2_gift_animation, R.id.up2_beauty_hair,
            R.id.up2_beauty, R.id.up2_filter, R.id.up2_water_mark, R.id.up2_mirror, R.id.up2_rtc,
            R.id.up2_scale, R.id.up2_torch, R.id.up2_voice, R.id.up2_record, R.id.up2_faceu})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.up2_back:
                finish(); // onPause(),onStop(),onDestory()
                break;
            case R.id.up2_show_debug_view:
                toggleDebugView();
                break;
            case R.id.up2_beauty_hair:
                changeBeautyhair();
                break;
            case R.id.up2_gift_animation:
                changeGiftAnimation();
                break;
            case R.id.up2_switch_camera:
                changeCamera();
                break;
            case R.id.up2_beauty:
                changeBeauty();
                break;
            case R.id.up2_filter:
                changeFilter();
                break;
            case R.id.up2_water_mark:
                changeWatermark();
                break;
            case R.id.up2_mirror:
                changeMirror();
                break;
            case R.id.up2_rtc:
                changeRtc();
                break;
            case R.id.up2_scale:
                changeScale();
                break;
            case R.id.up2_torch:
                changeTorch();
                break;
            case R.id.up2_voice:
                changeMute();
                break;
            case R.id.up2_record:
                changeRecord();
                break;
            case R.id.up2_faceu:
                changeFaceu();
                break;
        }
    }

    // implements ILangCameraStreamerOnErrorListener
    @Override
    public void onError(ILangCameraStreamer iLangCameraStreamer, ILangCameraStreamer.LangStreamerError error, int i) {
        Log.w(TAG, "onError: LangStreamerError:" + error.name());
        switch (error) {
            case LANG_ERROR_OPEN_CAMERA_FAIL:
                setErrorInfoText("camera open failed", Color.RED);
                break;
            case LANG_ERROR_OPEN_MIC_FAIL:
                setErrorInfoText("mic open failed", Color.RED);
                break;
            case LANG_ERROR_VIDEO_ENCODE_FAIL:
                setErrorInfoText("video encoder initialization failed", Color.RED);
                break;
            case LANG_ERROR_AUDIO_ENCODE_FAIL:
                setErrorInfoText("audio encoder initialization failed", Color.RED);
                break;
            case LANG_ERROR_PUSH_CONNECT_FAIL:
                updateLinkTipText("连接失败");
                break;
            case LANG_ERROR_UNSUPPORTED_FORMAT:
                setErrorInfoText("local record unsupport current format", Color.RED);
                break;
            case LANG_ERROR_RTC_EXCEPTION:
                setErrorInfoText(String.format(Locale.getDefault(), "rtc sdk error %d", i), Color.RED);
                iLangCameraStreamer.leaveRtcChannel();
                break;
            case LANG_ERROR_RECORD_FAIL:
                // check result error code.
                setErrorInfoText(String.format(Locale.getDefault(),"local record failed with result %d", i), Color.RED);
                break;
            case LANG_ERROR_LOAD_ANIMATON_FAIL:
                setErrorInfoText("animation loading failed", Color.RED);
                break;
        }
    }

    // implements ILangCameraStreamerOnEventListener
    @Override
    public void onEvent(ILangCameraStreamer streamer, ILangCameraStreamer.LangStreamerEvent event, int what) {
        Log.d(TAG, "onEvent: LangStreamerEvent:" + event.name());
        switch (event) {
            case LANG_EVENT_PUSH_CONNECTING:
                updateLinkTipText("连接中...");
                break;
            case LANG_EVENT_PUSH_RECONNECTING:
                updateLinkTipText("重连中...");
                updateRtmpNetworkInfoText("推流网络丢失", 0xFFFF0000);
                break;
            case LANG_EVENT_PUSH_CONNECT_SUCC:
                updateLinkTipText("连接成功");
                break;
            case LANG_EVENT_PUSH_DISCONNECT:
                updateLinkTipText("连接断开");
                break;
            case LANG_EVENT_PUSH_NETWORK_STRONG:
                updateRtmpNetworkInfoText("推流网络极好", 0xFF00FF00);
                break;
            case LANG_EVENT_PUSH_NETWORK_NORMAL:
                updateRtmpNetworkInfoText("推流网络良好", 0xFF808F00);
                break;
            case LANG_EVENT_PUSH_NETWORK_WEAK:
                updateRtmpNetworkInfoText("推流网络差", 0xFF803F80);
                break;
            case LANG_EVENT_RECORD_BEGIN:
                updateLocalRecordStatusText("录制开始");
                break;
            case LANG_EVENT_RECORD_END:
                updateLocalRecordStatusText("录制结束");
                break;
            case LANG_EVENT_RECORD_STATS_UPDATE:
                float timeMillSeconds = (float)what;
                updateLocalRecordStatusText(String.format(Locale.getDefault(),
                                "录制时间: %.2f秒", timeMillSeconds/1000.0f));
                break;
            case LANG_EVENT_RTC_CONNECTING:
                updateRtcStatusText("连麦: 本机连接中...");
                break;
            case LANG_EVENT_RTC_CONNECT_SUCC: {
                updateRtcStatusText("连麦: 本机成功加入");
                break;
            }
            case LANG_EVENT_RTC_DISCONNECT: {
                updateRtcStatusText("连麦: 本机成功退出");
                updateRtcMessageText("");
                updateRtcDebugInfo(null);
                mUidsList.clear();
                refreshRemoteViews();
                break;
            }
            case LANG_EVENT_RTC_NETWORK_LOST: {
                updateRtcStatusText("连麦: 本机重新连接中...");
                break;
            }
            case LANG_EVENT_RTC_NETWORK_TIMEOUT: {
                updateRtcStatusText("连麦: 本机已超时");
                break;
            }
            case LANG_EVENT_RTC_USER_JOINED: {
                int uid = what;
                doRenderRemoteUi(uid);
                updateRtcMessageText(String.format("连麦: 用户(0x%x)加入房间", uid));
                break;
            }
            case LANG_EVENT_RTC_USER_OFFLINE: {
                int uid = what;
                doRemoveRemoteUi(uid);
                updateRtcMessageText(String.format("连麦: 用户(0x%x)离开房间", uid));
                break;
            }
            case LANG_EVENT_ANIMATION_LOADING: {
                int progress = what;
                updateAnimationStatusText(String.format(Locale.getDefault(),
                        "动画: 加载进度%d%%", progress));
                break;
            }
            case LANG_EVENT_ANIMATION_LOAD_SUCC: {
                updateAnimationStatusText("动画: 加载成功");
                break;
            }
            case LANG_EVENT_ANIMATION_PLAYING: {
                int frameIndex = what;
                updateAnimationStatusText(String.format(Locale.getDefault(),
                        "动画: %d frames", frameIndex));
                break;
            }
            case LANG_EVENT_ANIMATION_PLAY_END: {
                updateAnimationStatusText("动画: 播放完毕");
                break;
            }
        }
    }

    @Override
    public void onEvent(ILangCameraStreamer streamer, ILangCameraStreamer.LangStreamerEvent event, Object obj) {
        Log.d(TAG, "onEvent: LangStreamerEvent:" + event.name());
        switch (event) {
            case LANG_EVENT_PUSH_STATS_UPDATE:
                updateRtmpDebugInfo((LangRtmpInfo)obj);
                break;
            case LANG_EVENT_FACE_UPDATE:
                Integer faceCount = (Integer)obj;
                setFaceuInfoText(String.format("人脸数目: %d", faceCount.intValue()), Color.RED);
                break;
            case LANG_EVENT_HAND_UPDATE:
                ILangCameraStreamer.LangFaceuGesture langGesture = (ILangCameraStreamer.LangFaceuGesture)obj;
                setFaceuInfoText(String.format("手势: %s", langGesture.name()), Color.RED);
                break;
            case LANG_EVENT_RTC_USER_VIDEO_RENDERED: {
                LangRtcUser rtcUser = (LangRtcUser)obj;
                int uid = rtcUser.mUid;
                int width = rtcUser.mWidth;
                int height = rtcUser.mHeight;
                updateRtcMessageText(String.format("连麦: 用户(0x%x)开始渲染画面分辨率(%dx%d)", uid, width, height));
                break;
            }
            case LANG_EVENT_RTC_VIDEO_SIZE_CHANGED: {
                LangRtcUser rtcUser = (LangRtcUser)obj;
                int uid = rtcUser.mUid;
                int width = rtcUser.mWidth;
                int height = rtcUser.mHeight;
                int rotation = rtcUser.mRotation;
                updateRtcMessageText(String.format("连麦: 用户(0x%x)渲染分辨率变化为(%dx%d)角度为(%d)", uid, width, height, rotation));
                break;
            }
            case LANG_EVENT_RTC_USER_AUDIO_MUTED:{
                LangRtcUser rtcUser = (LangRtcUser)obj;
                if (rtcUser.mAudioMuted) {
                    updateRtcMessageText(String.format("连麦: 用户(0x%x)开启静音", rtcUser.mUid));
                } else {
                    updateRtcMessageText(String.format("连麦: 用户(0x%x)开启声音", rtcUser.mUid));
                }
                break;
            }
            case LANG_EVENT_RTC_USER_VIDEO_MUTED: {
                LangRtcUser rtcUser = (LangRtcUser)obj;
                if (rtcUser.mVideoMuted) {
                    updateRtcMessageText(String.format("连麦: 用户(0x%x)开启黑屏", rtcUser.mUid));
                } else {
                    updateRtcMessageText(String.format("连麦: 用户(0x%x)关闭黑屏", rtcUser.mUid));
                }
                break;
            }
            case LANG_EVENT_RTC_AUDIO_ROUTE_CHANGE:
                break;
            case LANG_EVENT_RTC_STATS_UPDATE: {
                updateRtcDebugInfo((LangRtcInfo)obj);
                break;
            }
        }
    }

    private void updateLinkTipText(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mLinkTip != null)
                    mLinkTip.setText(text);
            }
        });
    }

    private void updateRtmpNetworkInfoText(final String text, final int color) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mRtmpNetworkInfo != null) {
                    mRtmpNetworkInfo.setText(text);
                    mRtmpNetworkInfo.setTextColor(color);
                }
            }
        });
    }

    private void setErrorInfoText(final String text, final int color) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mErrorInfo != null) {
                    mErrorInfo.setText(text);
                    mErrorInfo.setTextColor(color);
                }
            }
        });
    }

    private void setFaceuInfoText(final String text, final int color) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mFaceuHandInfo != null) {
                    mFaceuHandInfo.setText(text);
                    mFaceuHandInfo.setTextColor(color);
                }
            }
        });
    }

    private void updateRtmpDebugInfo(final LangRtmpInfo rtmpInfo) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mRtmpSpeedTextView.setText("rtmp speed: " + (rtmpInfo.uploadSpeed / 1024) + "kb/s");
                mRtmpFpsTextView.setText("rtmp fps: " + rtmpInfo.videoPushFrameCountPerSecond);
                mRtmpAudioCacheNumView.setText("audio cache: " + rtmpInfo.localBufferAudioCount + "frames");
                mRtmpVideoCacheNumView.setText("video cache: " + rtmpInfo.localBufferVideoCount + "frames");
                mRtmpAudioTotalPushCountsView.setText("total push audio: " + rtmpInfo.totalPushAudioFrameCount + "frames");
                mRtmpVideoTotalPushCountsView.setText("total push video: " + rtmpInfo.totalPushVideoFrameCount + "frames");
                mRtmpVideoDropCountsView.setText("drop: " + rtmpInfo.totalDiscardFrameCount + "frames");
                mPrevFpsTextView.setText("preview fps: " + rtmpInfo.previewFrameCountPerSecond);
            }
        });
    }

    private void updateLocalRecordStatusText(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mRecordMessageTextView.setText(text);
            }
        });
    }

    private void updateAnimationStatusText(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mAnimationMessageTextView.setText(text);
            }
        });
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

                SurfaceView surfaceV = mCameraStreamer.createRtcRenderView();
                mUidsList.put(uid, surfaceV);

                surfaceV.setTag(uid);
                surfaceV.setZOrderOnTop(true);
                surfaceV.setZOrderMediaOverlay(true);

                refreshRemoteViews();

                mCameraStreamer.setupRtcRemoteUser(uid, surfaceV);
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

    private void updateRtcStatusText(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mRtcConnectStatusTextView.setText(text);
            }
        });
    }

    private void updateRtcMessageText(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mRtcMessageTextView.setText(text);
            }
        });
    }

    private void updateRtcDebugInfo(final LangRtcInfo rtcInfo) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (rtcInfo != null) {
                    mRtcUsersTextView.setText(String.format("连麦: 在线(%d)人", rtcInfo.onlineUsers));
                    int txKBitrate = rtcInfo.txAudioKBitrate + rtcInfo.txVideoKBitrate;
                    int rxKBitrate = rtcInfo.rxAudioKBitrate + rtcInfo.rxVideoKBitrate;
                    mRtcTxTextView.setText(String.format("连麦: 上行速率(%.0f)KB/s", (float)txKBitrate/8));
                    mRtcRxTextView.setText(String.format("连麦: 下行速率(%.0f)KB/s", (float)rxKBitrate/8));
                } else {
                    mRtcUsersTextView.setText("");
                    mRtcTxTextView.setText("");
                    mRtcRxTextView.setText("");
                }
            }
        });
    }

    // handle UI event from user input
    private boolean showDebugView = false;
    private void toggleDebugView() {
        showDebugView = !showDebugView;
        if (showDebugView) {
            mDebugLinear.setVisibility(View.VISIBLE);
        } else {
            mDebugLinear.setVisibility(View.INVISIBLE);
        }
    }

    private boolean showBeautyHair = false;
    private void changeBeautyhair() {
        showBeautyHair = !showBeautyHair;
        if (showBeautyHair) {
            mBeautyHairConfig.setStartColor(0xFFB92B27);
            mBeautyHairConfig.setEndColor(0xFFB92B27);
            mBeautyHairConfig.setEnable(true);
        } else {
            mBeautyHairConfig.setEnable(false);
        }
        mCameraStreamer.setHairColors(mBeautyHairConfig);
    }

    private void changeGiftAnimation() {
        new AlertDialog.Builder(this).setTitle("choose animation")
                .setSingleChoiceItems(mGiftAnimations, mAnimationWhich,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                if (mAnimationWhich != which) {
                                    mAnimationWhich = which;
                                    if (which == 0) {
                                        mAnimationConfig.enable = false;
                                        mAnimationConfig.animationPath = null;
                                    }
                                    else {
                                        mAnimationConfig.enable = true;
                                        mAnimationConfig.animationPath = mAnimationsList.get(which-1);
                                    }
                                    mCameraStreamer.setMattingAnimation(mAnimationConfig);

                                }
                                dialog.dismiss();
                            }
                        }).setNegativeButton("取消", null).show();
    }

    private void changeCamera() {
        mCameraStreamer.switchCamera();
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
                                    mCameraStreamer.setBeauty(mBeautyLevel);
                                    if (mBeautyWhich == 0)
                                        mBeauty.setText("美颜/关");
                                    else
                                        mBeauty.setText("美颜/" + mBeautyWhich);
                                }

                                dialog.dismiss();
                            }
                        }).setNegativeButton("取消", null).show();
    }

    private void changeFilter() {
        new AlertDialog.Builder(this).setTitle("选择滤镜")
                .setSingleChoiceItems(mFilters, mFilterWhich,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                if (mFilterWhich != which) {
                                    ILangCameraStreamer.LangCameraFilter type = ILangCameraStreamer.LangCameraFilter.values()[which];
                                    mFilterWhich = which;
                                    mCameraStreamer.setFilter(type);
                                    if (mFilterWhich == 0)
                                        mFilter.setText("滤镜/关");
                                    else
                                        mFilter.setText("滤镜/" + mFilterWhich);
                                }
                                dialog.dismiss();
                            }
                        }).setNegativeButton("取消", null).show();
    }

    private void changeWatermark() {
        if (mWaterMark.getText().toString().contentEquals("水印/开")) {
            LangWatermarkConfig config = new LangWatermarkConfig();
            config.url = "";//"filter/snail_h.png";
            config.x = 0;
            config.y = 0;
            config.w = 250;
            config.h = 250;
            config.enable = false;
            mCameraStreamer.setWatermark(config);
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
            mCameraStreamer.setWatermark(config);
            mWaterMark.setText("水印/开");
        }
    }

    private int mScaleWhich = 0;
    private void changeScale() {
        new AlertDialog.Builder(this).setTitle("选择放大倍数")
                .setSingleChoiceItems(mZooms, mScaleWhich,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                if (mScaleWhich != which) {

                                    mScaleWhich = which;
                                    mCameraStreamer.setZoom(mScaleWhich + 1);
                                    mScale.setText("放大/" + (mScaleWhich + 1) + ".0");
                                }
                                dialog.dismiss();
                            }
                        }).setNegativeButton("取消", null).show();
    }

    private void changeMirror() {
        if (mMirror.getText().toString().contentEquals("镜像/关")) {
            mCameraStreamer.setMirror(true);
            mMirror.setText("镜像/开");
        } else if (mMirror.getText().toString().contentEquals("镜像/开")) {
            mCameraStreamer.setMirror(false);
            mMirror.setText("镜像/关");
        }
    }

    private void changeRtc() {
        if (mRtc.getText().toString().contentEquals("加入连麦")) {

            LangRtcConfig rtcConfig = new LangRtcConfig();
            rtcConfig.rtcVideoProfile = LangRtcConfig.RTC_VIDEO_PROFILE_360P;
            rtcConfig.channelName = "langlive-test";
            rtcConfig.encryptionKey = null;
            rtcConfig.encryptionMode = "AES-128-XTS";

            if (!mCameraStreamer.rtcAvailable(rtcConfig)) {
                mRtcConnectStatusTextView.setText("连麦: 当前设备不支持");
                return;
            }

            LangRtcConfig.RtcDisplayParams rtcDisplay = new LangRtcConfig.RtcDisplayParams();
            rtcDisplay.topOffsetPercent = 0.15f;
            rtcDisplay.subWidthPercentOnTwoWins = 0.5f;
            rtcDisplay.subHeightPercentOnTwoWins = 0.45f;
            mCameraStreamer.setRtcDisplayLayoutParams(rtcDisplay);

            /*
            SurfaceView surfaceV = magicEngine.createRtcRenderView();
            surfaceV.setZOrderOnTop(false);
            surfaceV.setZOrderMediaOverlay(false);
            mUidsList.put(0, surfaceV); // get first surface view
            */

            mCameraStreamer.joinRtcChannel(rtcConfig, null);

            mRtc.setText("离开连麦");
        } else if (mRtc.getText().toString().contentEquals("离开连麦")) {

            mCameraStreamer.leaveRtcChannel();

            mRtc.setText("加入连麦");
        }
    }

    private void changeTorch() {
        if (mTorch.getText().toString().contentEquals("手电/关")) {
            mCameraStreamer.setCameraToggleTorch(true);
            mTorch.setText("手电/开");
        } else if (mTorch.getText().toString().contentEquals("手电/开")) {
            mCameraStreamer.setCameraToggleTorch(false);
            mTorch.setText("手电/关");
        }
    }

    private void changeMute() {
        if (mVoice.getText().toString().contentEquals("静音/开")) {
            mCameraStreamer.setMute(false);
            mVoice.setText("静音/关");
        } else if (mVoice.getText().toString().contentEquals("静音/关")) {
            mCameraStreamer.setMute(true);
            mVoice.setText("静音/开");
        }
    }

    private void changeRecord() {
        if (mRecord.getText().toString().contentEquals("录制/关")) {
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                String folder = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "LangLive/record/mp4/";
                File folderFile = new File(folder);
                if (!folderFile.exists())
                    folderFile.mkdirs();
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS");
                mCameraStreamer.startRecording(folder + format.format(new Date()) + ".mp4");
                mRecord.setText("录制/开");
            } else {
                ToastUtils.show(this, "sdcard 不可用");
            }
        } else if (mRecord.getText().toString().contentEquals("录制/开")) {
            mCameraStreamer.stopRecording();

            mRecord.setText("录制/关");
        }
    }

    private void changeFaceu() {
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
                                    mCameraStreamer.setFaceu(mFaceuConfig);

                                    // optional invoke api.
                                    // if you prefer not to use default preset beauty level, you can use mFaceuConfig params
                                    // to modify specific detail params.
                                    mCameraStreamer.setBeauty(mBeautyLevel);
                                }
                                dialog.dismiss();
                            }
                        }).setNegativeButton("取消", null).show();
    }

    private static class StreamerEventHandler extends Handler {
        private WeakReference<UpActivity2> weakActivity;
        public StreamerEventHandler(UpActivity2 activity) {
            weakActivity = new WeakReference<UpActivity2>(activity) ;
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);

            int what = msg.what;
            Object obj = msg.obj;

            UpActivity2 activity = weakActivity.get();
            if (activity == null) {
                return;
            }
            switch (what) {
                case MSG_PUSH_RTMP:
                    ILangCameraStreamer cameraStreamer = activity.mCameraStreamer;
                    if (cameraStreamer != null) {
                        String rtmpUrl = (String)obj;
                        cameraStreamer.setBeauty(activity.mBeautyLevel);
                        cameraStreamer.setReconnectOption(50, 10);
                        cameraStreamer.startStreaming(rtmpUrl);
                        cameraStreamer.setAutoAdjustBitrate(true);
                    }
                    break;
                case MSG_UPDATE_RTMP_INFO:
                    break;
                case MSG_UPDATE_SYS_INFO:
                    break;
            }
        }
    }

    private LangStreamerConfig checkStreamerConfig() {

        LangStreamerConfig streamerConfig = new LangStreamerConfig();
        // audio config
        streamerConfig.audioBitrate = 256 * 1000;
        streamerConfig.audioSampleRate = 48000;
        streamerConfig.audioChannel = 2;

        // video config
        streamerConfig.videoBitrate = getIntent().getIntExtra("bt",1000) * 1000;
        streamerConfig.videoFPS = getIntent().getIntExtra("fps",24);
        streamerConfig.videoMaxKeyframeInterval = 4 * streamerConfig.videoFPS;
        String encoderType = getIntent().getStringExtra("encoder_type");
        if (encoderType.contains("720p")) {
            streamerConfig.videoResolution = ILangCameraStreamer.LangVideoResolution.LANG_VIDEO_RESOLUTION_720P;
        }else if (encoderType.contains("540p")) {
            streamerConfig.videoResolution = ILangCameraStreamer.LangVideoResolution.LANG_VIDEO_RESOLUTION_540P;
        }else if (encoderType.contains("480p")) {
            streamerConfig.videoResolution = ILangCameraStreamer.LangVideoResolution.LANG_VIDEO_RESOLUTION_480P;
        }else if (encoderType.contains("360p")) {
            streamerConfig.videoResolution = ILangCameraStreamer.LangVideoResolution.LANG_VIDEO_RESOLUTION_360P;
        }

        if(getIntent().getBooleanExtra("open_hardware_speedup",true)){
            streamerConfig.videoEncoderType = ILangCameraStreamer.LangVideoEncoderType.LANG_VIDEO_ENCODER_HARDWARE;
        }else {
            streamerConfig.videoEncoderType = ILangCameraStreamer.LangVideoEncoderType.LANG_VIDEO_ENCODER_OPENH264;
        }

        return streamerConfig;
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

    private void preloadStickerPackage() {
        for (int index = 1; index < mSenseMeStickers.length; index++) {
            String stickerSrcDir = "senseme_sticker";
            String stickerDstDir = "/LangLive/langARStickers";
            String stickerName = mSenseMeStickers[index] + ".zip";
            String stickerReadablePath = saveAssestsData(this, stickerSrcDir, stickerDstDir, stickerName);
            mFaceuStickersList.add(stickerReadablePath);
        }
    }

    private void preloadAnimationPackage() {
        for (int index = 1; index < mGiftAnimations.length; index++) {
            String animSrcDir = "webp";
            String animDstDir = "/LangLive/langAnimations";
            String animName = mGiftAnimations[index];
            String animReadablePath = saveAssestsData(this, animSrcDir, animDstDir, animName);
            mAnimationsList.add(animReadablePath);
        }
    }

    static String saveAssestsData(Context context, String srcfilePath, String dstPath, String name) {
        InputStream inputStream;
        try {
            inputStream = context.getResources().getAssets().open(srcfilePath + "/" + name);

//            File file = context.getExternalFilesDir("beautify");
//			File file = context.getCacheDir();
			File file = new File(Environment.getExternalStorageDirectory() + dstPath);
//            File file = new File("/sdcard/tempdir");
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
}
