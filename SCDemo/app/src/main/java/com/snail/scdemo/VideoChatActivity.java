package com.snail.scdemo;

import android.content.Intent;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.lang.lang.R;
import com.snail.scdemo.utils.LogUtils;

import net.lang.streamer.ILangRtcAudience;
import net.lang.streamer.LangRtcAudienceProxy;
import net.lang.streamer.LangRtcInfo;
import net.lang.streamer.config.LangRtcConfig;

import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import butterknife.BindView;
import butterknife.OnClick;

/**
 * Created by lang on 2018/4/8.
 */

public class VideoChatActivity extends BaseActivity implements ILangRtcAudience.IRtcAudienceEventListener {

    @BindView(R.id.rtc_audience_debugLinear)
    LinearLayout mDebugLinear;
    @BindView(R.id.rtc_audience_localConnectStatusDebug)
    TextView mRtcConnectStatusTextView;
    @BindView(R.id.rtc_audience_messageDebug)
    TextView mRtcMessageTextView;
    @BindView(R.id.rtc_audience_joinedUsersDebug)
    TextView mRtcUsersTextView;
    @BindView(R.id.rtc_audience_txBandwidthDebug)
    TextView mRtcTxTextView;
    @BindView(R.id.rtc_audience_rxBandwidthDebug)
    TextView mRtcRxTextView;

    @BindView(R.id.broadcaster_video_view_container1)
    FrameLayout mRemoteContainer1;
    @BindView(R.id.broadcaster_video_view_container2)
    FrameLayout mRemoteContainer2;
    @BindView(R.id.broadcaster_video_view_container3)
    FrameLayout mRemoteContainer3;

    private String mRtcRoomUrl;
    private ILangRtcAudience mRtcAudience;

    // should only be modified under UI thread
    private final HashMap<Integer, SurfaceView> mUidsList = new HashMap<>();


    @Override
    protected int getLayoutId() {
        return R.layout.activity_rtc_audience;
    }

    @Override
    protected void init() {
        mRtcRoomUrl = getIntent().getStringExtra("room_name");

        mRtcAudience = LangRtcAudienceProxy.newProxyInstance(this);
        if (mRtcAudience != null) {
            mRtcAudience.setEventListener(this);
        }
        if (mRtcAudience != null) {
            Timer rtcJoinTimer = new Timer();
            rtcJoinTimer.schedule(new TimerTask() {
                public void run() {
                    joinRoom();
                }
            }, 1000);
        }
        //mDebugLinear.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mRtcAudience != null) {
            mRtcAudience.release();
        }
    }

    private void joinRoom() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isFinishing()) {
                    return;
                }
                LangRtcConfig rtcConfig = new LangRtcConfig();
                rtcConfig.channelName = mRtcRoomUrl;
                rtcConfig.encryptionKey = null;
                rtcConfig.encryptionMode = "AES-128-XTS";
                rtcConfig.audience = true;
                mRtcAudience.joinRtcChannel(rtcConfig);
            }
        });
    }

    @Override
    public void onEvent(ILangRtcAudience audience, ILangRtcAudience.RtcAudienceEvent event, Object obj) {
        switch (event) {
            case AUDIENCE_EVENT_RTC_CONNECTING: {
                mRtcConnectStatusTextView.setText("连麦: 观众连接中...");
                break;
            }
            case AUDIENCE_EVENT_RTC_CONNECT_SUCC: {
                mRtcConnectStatusTextView.setText("连麦: 观众已加入...");
                break;
            }
            case AUDIENCE_EVENT_RTC_DISCONNECT: {
                mRtcConnectStatusTextView.setText("连麦: 观众离开...");
                break;
            }
            case AUDIENCE_EVENT_RTC_USER_JOINED: {
                Object[] joinedData = (Object[]) obj;
                int uid = (int) joinedData[0];
                doRenderRemoteUi(uid);
                mRtcMessageTextView.setText(String.format("连麦: 主播(0x%x)加入房间", uid));
                break;
            }
            case AUDIENCE_EVENT_RTC_USER_OFFLINE: {
                Object[] offlineData = (Object[]) obj;
                int uid = (int) offlineData[0];
                doRemoveRemoteUi(uid);
                mRtcMessageTextView.setText(String.format("连麦: 主播(0x%x)离开房间", uid));
                break;
            }
            case AUDIENCE_EVENT_RTC_USER_AUDIO_MUTED: {
                break;
            }
            case AUDIENCE_EVENT_RTC_USER_VIDEO_MUTED: {
                break;
            }
            case AUDIENCE_EVENT_RTC_AUDIO_ROUTE_CHANGE: {
                break;
            }
            case AUDIENCE_EVENT_RTC_STATS_UPDATE: {
                LangRtcInfo rtcInfo = (LangRtcInfo)obj;
                mRtcUsersTextView.setText(String.format("连麦: 参与(%d)人", rtcInfo.onlineUsers));
                int txKBitrate = rtcInfo.txAudioKBitrate + rtcInfo.txVideoKBitrate;
                int rxKBitrate = rtcInfo.rxAudioKBitrate + rtcInfo.rxVideoKBitrate;
                mRtcTxTextView.setText(String.format("连麦: 上行速率(%.0f)KB/s", (float)txKBitrate/8));
                mRtcRxTextView.setText(String.format("连麦: 下行速率(%.0f)KB/s", (float)rxKBitrate/8));
                break;
            }
            case AUDIENCE_EVENT_RTC_USER_VIDEO_RENDERED: {
                break;
            }
            case AUDIENCE_EVENT_RTC_VIDEO_SIZE_CHANGED: {
                break;
            }
            case AUDIENCE_EVENT_RTC_NETWORK_LOST: {
                break;
            }
            case AUDIENCE_EVENT_RTC_NETWORK_TIMEOUT: {
                break;
            }
            case AUDIENCE_EVENT_RTC_EXCEPTION: {
                break;
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

                SurfaceView surfaceV = mRtcAudience.createRtcRenderView();
                mUidsList.put(uid, surfaceV);

                surfaceV.setTag(uid);
                surfaceV.setZOrderOnTop(true);
                surfaceV.setZOrderMediaOverlay(true);

                mRtcAudience.setupRtcRemoteUser(uid, surfaceV);

                refreshRemoteViews();
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

    private FrameLayout currentAvailableLayout() {

        if (mRemoteContainer1.getChildCount() < 1)
            return mRemoteContainer1;

        if (mRemoteContainer2.getChildCount() < 1)
            return mRemoteContainer2;

        if (mRemoteContainer3.getChildCount() < 1)
            return mRemoteContainer3;

        return null;
    }
}
