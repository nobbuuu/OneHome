package com.dream.onehome.ui.Activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;

import com.android.volley.Response;
import com.aylanetworks.aylasdk.AylaDatapoint;
import com.aylanetworks.aylasdk.AylaDatum;
import com.aylanetworks.aylasdk.AylaDevice;
import com.aylanetworks.aylasdk.AylaNetworks;
import com.aylanetworks.aylasdk.AylaProperty;
import com.aylanetworks.aylasdk.AylaSessionManager;
import com.aylanetworks.aylasdk.error.AylaError;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.dream.onehome.R;
import com.dream.onehome.base.BaseMVVMActivity;
import com.dream.onehome.base.NoDoubleClickListener;
import com.dream.onehome.common.Const;
import com.dream.onehome.databinding.ActivityLearnBinding;
import com.dream.onehome.ui.ViewModel.ModelViewModel;
import com.dream.onehome.utils.LogUtils;
import com.dream.onehome.utils.SP;
import com.dream.onehome.utils.SpUtils;
import com.dream.onehome.utils.ToastUtils;
import com.dream.onehome.utils.annotations.ContentView;

import java.util.Map;

/**
 * Time:20200228
 * Author:TiaoZi
 */
@ContentView(R.layout.activity_learn)
public class LearnActivity extends BaseMVVMActivity<ModelViewModel, ActivityLearnBinding> {

    private AylaSessionManager mSessionManager;
    private AylaDevice mAylaDevice;

    private static final String TAG = "AylaLog";

    private AylaProperty mAylaProperty;
    private boolean isFetch = true;
    private String tempCode = SP.get("ircode", "");

    @Override
    protected void initIntent() {
        mSessionManager = AylaNetworks.sharedInstance().getSessionManager(Const.APP_NAME);
        if (mSessionManager != null) {
            String dsn = (String) SpUtils.getParam(Const.DSN, "");
            if (!dsn.isEmpty()) {
                mAylaDevice = mSessionManager.getDeviceManager().deviceWithDSN(dsn);
                if (mAylaDevice != null){
                    resetWorkMode(1);
                }
            }

        } else {
            ToastUtils.Toast_long("aylaSessionManager 初始化失败！");
            Log.e(TAG, "aylaSessionManager  = " + mSessionManager);
        }
    }

    private void resetWorkMode(int mode) {
        mAylaProperty = mAylaDevice.getProperty(Const.Working_Mode);
        if (mAylaProperty != null){
            mAylaProperty.createDatapoint(String.valueOf(mode), null, new Response.Listener<AylaDatapoint>() {
                @Override
                public void onResponse(AylaDatapoint response) {
                    mAylaProperty = mAylaDevice.getProperty(Const.IR_Learn_code);
                    if (isFetch){
                        fetchIrCode();
                    }else {
                        Intent intent = getIntent();
                        intent.putExtra("irCode", tempCode);
                        setResult(010, intent);
                        finish();
                    }
                }
            }, new ErrorListener() {
                @Override
                public void onErrorResponse(AylaError aylaError) {
                    ToastUtils.Toast_long(aylaError.getMessage());
                    Log.e(TAG, "aylaError  = " + aylaError.getMessage());
                }
            });
        }
    }

    private void fetchIrCode() {
        mAylaProperty.fetchDatapoints(1, null, null, new Response.Listener<AylaDatapoint[]>() {
            @Override
            public void onResponse(AylaDatapoint[] response) {
                if (response.length != 0) {
                    Log.e(TAG, "response.size() = " + response.length);
                    AylaDatapoint aylaDatapoint = response[0];
                    Log.e(TAG, "fetchDatapoints  response = " + aylaDatapoint.toString());
                    String irCode = (String) aylaDatapoint.getValue();
                    if (irCode != null && !tempCode.equals(irCode)) {
                        LogUtils.e(TAG, "irCode = " + irCode);
                        isFetch = false;
                        tempCode = irCode;
                        SP.put("ircode", irCode);
                        resetWorkMode(0);
                    }
                }
                if (isFetch) {
                    SystemClock.sleep(1500);
                    fetchIrCode();
                }
            }
        }, new ErrorListener() {
            @Override
            public void onErrorResponse(AylaError aylaError) {
                LogUtils.e(aylaError.getMessage());
            }
        });
    }

    @Override
    protected void onEvent() {
        bindingView.backIv.setOnClickListener(new NoDoubleClickListener() {
            @Override
            public void onNoDoubleClick(View view) {
                onBackPressed();
            }
        });
    }

    @Override
    protected void initView(Bundle savedInstanceState) {

    }
}
