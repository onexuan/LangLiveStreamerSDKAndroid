package com.snail.scdemo;

import android.content.Intent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.lang.lang.R;

import com.snail.scdemo.utils.StringUtils;
import com.snail.scdemo.utils.ToastUtils;

import net.lang.streamer.LangCameraStreamer;
import net.lang.streamer.config.LangRtcConfig;

import java.util.ArrayList;

import butterknife.BindView;
import butterknife.OnClick;

public class MainActivity extends BaseActivity {

    @BindView(R.id.app_ver)
    TextView mAppVer;
    @BindView(R.id.live_url)
    EditText mLiveUrl;

    @BindView(R.id.spinner2)
    Spinner mEncoderType;
    @BindView(R.id.edit_sp)
    EditText mSp;
    @BindView(R.id.edit_fps)
    EditText mFps;
    @BindView(R.id.set_yjjs_checkbox)
    CheckBox mCheckBox;
    @BindView(R.id.set_gb_checkbox)
    CheckBox mGraphicBufferSupport;
    @BindView(R.id.set_gb_checkbox_edit)
    TextView mGraphicBufferSupportView;

    @BindView(R.id.edit_rtc_url)
    EditText mRtcUrl;
    @BindView(R.id.spinner_rtc)
    Spinner mRtcConfigure;
    @BindView(R.id.mix_mode)
    Spinner mRtcMixMode;

    private int mCurSelected = 0;
    public static final int SCANNER_REQUEST = 0;
    private String mEncoderTypeString = "540p";
    private String mRtcConfString = "640x360/30fps/600kbps";
    private ArrayAdapter<String> adapter;
    private ArrayAdapter<String> adapter2;
    private ArrayAdapter<String> adapter3;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_main;
    }

    @Override
    protected void init() {
        mAppVer.setText("SDK版本：" + LangCameraStreamer.getVersion());
        ArrayList<String> list = new ArrayList<>();
        list.add("720p");
        list.add("540p");
        list.add("480p");
        list.add("360p");
        adapter = new ArrayAdapter<>(this,android.R.layout.simple_spinner_item, list);
        mEncoderType.setAdapter(adapter);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mEncoderTypeString = list.get(mEncoderType.getSelectedItemPosition());

        mEncoderType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                mEncoderTypeString = adapter.getItem(i);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        mLiveUrl.setText("rtmp://172.16.16.213/live/8");

        ArrayList<String> rtcConfList = new ArrayList<>();
        rtcConfList.add("320x240/15fps/200kbps");
        rtcConfList.add("640x360/15fps/400kbps");
        rtcConfList.add("640x360/30fps/600kbps");
        rtcConfList.add("640x480/15fps/500kbps");
        rtcConfList.add("1280x720/15fps/1130kbps");
        adapter2 = new ArrayAdapter<>(this,android.R.layout.simple_spinner_item, rtcConfList);
        mRtcConfigure.setAdapter(adapter2);
        adapter2.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mRtcConfString = rtcConfList.get(mRtcConfigure.getSelectedItemPosition());

        mRtcConfigure.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mRtcConfString = adapter2.getItem(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        mRtcUrl.setText("langlive-test");


        ArrayList<String> mixModes = new ArrayList<>();
        mixModes.add("rtc mix mode：Server");
        mixModes.add("rtc mix mode：Local");

        adapter3 = new ArrayAdapter<>(this,android.R.layout.simple_spinner_item, mixModes);
        mRtcMixMode.setAdapter(adapter3);
        adapter3.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        LangRtcConfig.localMixed = (mRtcMixMode.getSelectedItemPosition() == 1);

        mRtcMixMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                LangRtcConfig.localMixed = (position == 1);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    @OnClick({R.id.scanner, R.id.start_live, R.id.set_yjjs, R.id.set_gb, R.id.start_rtc_audience})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.scanner: {
                //startActivityForResult(new Intent(this, ScannerActivity.class), SCANNER_REQUEST);
                String url = mLiveUrl.getText().toString();
                if (StringUtils.isBlank(url)) {
                    ToastUtils.show(this, "请输入推流地址");
                    return;
                }
                String bt = mSp.getText().toString();
                String fps = mFps.getText().toString();
                if (StringUtils.isBlank(bt) || StringUtils.isBlank(fps)) {
                    ToastUtils.show(this, "比特率和fps");
                    return;
                }
                Intent intent = new Intent(MainActivity.this, UpActivity.class);
                intent.putExtra("video_set", mCurSelected);
                intent.putExtra("rtmp_url", url);
                intent.putExtra("encoder_type", mEncoderTypeString);
                intent.putExtra("bt", Integer.parseInt(bt));
                intent.putExtra("fps", Integer.parseInt(fps));
                intent.putExtra("open_hardware_speedup", mCheckBox.isChecked());
                intent.putExtra("gb_enable", mGraphicBufferSupport.isChecked());
                intent.putExtra("rtc_conf", mRtcConfString);

                MainActivity.this.startActivity(intent);
                break;
            }
            case R.id.start_live: {
                String url = mLiveUrl.getText().toString();
                if (StringUtils.isBlank(url)) {
                    ToastUtils.show(this, "请输入推流地址");
                    return;
                }
                String bt = mSp.getText().toString();
                String fps = mFps.getText().toString();
                if (StringUtils.isBlank(bt) || StringUtils.isBlank(fps)) {
                    ToastUtils.show(this, "比特率和fps");
                    return;
                }
                Intent intent = new Intent(MainActivity.this, UpActivity2.class);
                intent.putExtra("video_set", mCurSelected);
                intent.putExtra("rtmp_url", url);
                intent.putExtra("encoder_type", mEncoderTypeString);
                intent.putExtra("bt", Integer.parseInt(bt));
                intent.putExtra("fps", Integer.parseInt(fps));
                intent.putExtra("open_hardware_speedup", mCheckBox.isChecked());
                intent.putExtra("gb_enable", mGraphicBufferSupport.isChecked());
                intent.putExtra("rtc_conf", mRtcConfString);

                MainActivity.this.startActivity(intent);
                break;
            }
            case R.id.set_yjjs: {
                boolean v = !mCheckBox.isChecked();
                mCheckBox.setChecked(v);
                break;
            }
            case R.id.set_gb: {
                //boolean v = !mGraphicBufferSupport.isChecked();
                //mGraphicBufferSupport.setChecked(v);
                break;
            }
            case R.id.start_rtc_audience: {
                String room = mRtcUrl.getText().toString();
                if (StringUtils.isBlank(room)) {
                    ToastUtils.show(this, "请输入连麦房间名称");
                    return;
                }
                Intent intent = new Intent(MainActivity.this, VideoChatActivity.class);
                intent.putExtra("room_name", room);

                MainActivity.this.startActivity(intent);
                break;
            }
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SCANNER_REQUEST && resultCode == 1 && data != null) {
            mLiveUrl.setText(data.getStringExtra("url"));
        }
    }

}
