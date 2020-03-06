package com.snail.scdemo;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.journeyapps.barcodescanner.CompoundBarcodeView;
import com.snail.scdemo.utils.CustomCaptureManager;
import com.snail.scdemo.utils.DensityUtils;
import com.snail.scdemo.utils.LogUtils;
import com.snail.scdemo.utils.ScreenUtils;
import com.snail.scdemo.utils.ToastUtils;
import com.snail.scdemo.utils.Utils;
import com.snail.scdemo.view.CustomViewfinderView;
import com.lang.lang.R;

import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.OnClick;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;


/**
 * Custom Scannner Activity extending from Activity to display a custom layout form scanner view.
 */
public class ScannerActivity extends BaseActivity implements CustomCaptureManager.ScanCompleteListener {
    private static final long DELAY_FINISH_TIME = 3;

    private CustomCaptureManager capture;
    @BindView(R.id.zxing_barcode_scanner)
    CompoundBarcodeView barcodeScannerView;
    @BindView(R.id.zxing_viewfinder_view)
    CustomViewfinderView mCustomViewfinderView;
    @BindView(R.id.scanner_tip_text)
    TextView mTip;
    //public boolean isBack = true;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        mCustomViewfinderView.setBorderColor(getResources().getColor(R.color.positive_normal_color));
        capture = new CustomCaptureManager(this, barcodeScannerView);
        capture.setScanCompleteListener(this);
        capture.initializeFromIntent(getIntent(), savedInstanceState);
        capture.decode();
    }


    @Override
    protected void onPause() {
        super.onPause();
        LogUtils.e("cc onPause");
        capture.onPause();
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_scanner;
    }
    @Override
    protected void init() {
        mTip.post(new Runnable() {
            @Override
            public void run() {
                RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) mTip.getLayoutParams();
                params.bottomMargin = ScreenUtils.getScreenHeight(ScannerActivity.this) / 2 - DensityUtils.dp2px(ScannerActivity.this, 160);
                mTip.setLayoutParams(params);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        capture.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        capture.onSaveInstanceState(outState);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return barcodeScannerView.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event);
    }


    @Override
    public void onScanComplete(Intent intent) {
        IntentResult result = IntentIntegrator.parseActivityResult(IntentIntegrator.REQUEST_CODE, RESULT_OK, intent);
        if (result != null) {
            if (result.getContents() == null) {
                LogUtils.d("Cancelled scan");
            } else {
                String qr_url = result.getContents();
                if (Utils.isVideoUrl(qr_url)) {
                    Intent intentRes = new Intent();
                    intentRes.putExtra("url", qr_url);
                    setResult(1, intentRes);
                    //跳转播放界面
                    finish();
                } else {
                    mCustomViewfinderView.setBorderColor(getResources().getColor(R.color.invalid_live_url_color));
                    ToastUtils.show(this, "请扫描正确的推流地址");

                    Observable.timer(DELAY_FINISH_TIME, TimeUnit.SECONDS).observeOn(AndroidSchedulers.mainThread()).subscribe(new Action1<Long>() {
                        @Override
                        public void call(Long aLong) {
                            capture.decode();
                            capture.onResume();
                            mCustomViewfinderView.setBorderColor(getResources().getColor(R.color.positive_normal_color));
                        }
                    });
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        LogUtils.e("cc onResume");
        capture.onResume();
    }

    @OnClick(R.id.scanner_cancel)
    public void onClickCancel() {

        finish();
    }

  /*  @Override
    public void finish() {
        capture.onPause();
        capture.onDestroy();
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        super.finish();
    }*/
}
