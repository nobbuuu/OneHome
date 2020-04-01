package com.dream.onehome.ui.Activity;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;

import androidx.fragment.app.Fragment;

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
import com.dream.onehome.base.BaseFraPagerAdapter;
import com.dream.onehome.base.BaseMVVMActivity;
import com.dream.onehome.bean.KeyIrCodeBean;
import com.dream.onehome.bean.KeysBean;
import com.dream.onehome.bean.ModelBean;
import com.dream.onehome.bean.RemoteControlBean;
import com.dream.onehome.common.Const;
import com.dream.onehome.constract.IClickLisrener;
import com.dream.onehome.constract.IResultLisrener;
import com.dream.onehome.databinding.ActivityFanBinding;
import com.dream.onehome.databinding.ActivityMaterBinding;
import com.dream.onehome.dialog.ExtDialog;
import com.dream.onehome.ui.ViewModel.ModelViewModel;
import com.dream.onehome.ui.fragment.MenuFragment;
import com.dream.onehome.ui.fragment.NumberFragment;
import com.dream.onehome.utils.ActivityUtils;
import com.dream.onehome.utils.SpUtils;
import com.dream.onehome.utils.ToastUtils;
import com.dream.onehome.utils.annotations.ContentView;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

/**
 * Time:2019/12/11
 * Author:TiaoZi
 * <p>
 * 电视主控界面
 */
@ContentView(R.layout.activity_fan)
public class FanActivity extends BaseMVVMActivity<ModelViewModel, ActivityFanBinding> implements View.OnClickListener {


    private List<ModelBean> modelList = new ArrayList<>();
    private List<String> mKeylist = new ArrayList<>();
    private List<String> mKeyvalues = new ArrayList<>();

    private AylaSessionManager mSessionManager;
    private AylaDevice mAylaDevice;

    private int index = 1;

    private static final String TAG = "AylaLog";
    private AylaProperty mAylaProperty;
    private String mDeviceId;
    private String mBrandId;
    private String mKfid;

    private RemoteControlBean mControlBean = new RemoteControlBean();
    private boolean isAdded;
    private boolean isOpen;
    private ObjectAnimator mAnimator;

    @Override
    protected void initIntent() {
        Intent intent = getIntent();
        mDeviceId = intent.getStringExtra(Const.device_id);
        mBrandId = intent.getStringExtra(Const.brand_id);
        mKfid = intent.getStringExtra(Const.kfid);
        String deviceName = intent.getStringExtra(Const.deviceName);
        String brandName = intent.getStringExtra(Const.brandName);
        if (mDeviceId != null) {
            mControlBean.setType(mDeviceId);
        }
        if (deviceName != null && brandName != null) {
            mControlBean.setName(deviceName);
            mControlBean.setBrandName(brandName);
        }

        mSessionManager = AylaNetworks.sharedInstance().getSessionManager(Const.APP_NAME);
        if (mSessionManager != null) {
            String dsn = (String) SpUtils.getParam(Const.DSN, "");
            if (!dsn.isEmpty()) {
                mAylaDevice = mSessionManager.getDeviceManager().deviceWithDSN(dsn);
                mAylaProperty = mAylaDevice.getProperty(Const.IR_Send_code);
                Log.e(TAG, "mAylaProperty  = " + mAylaProperty);
            }

        } else {
            ToastUtils.Toast_long("aylaSessionManager 初始化失败！");
            Log.e(TAG, "aylaSessionManager  = " + mSessionManager);
        }

        initAnimator();
    }

    private void initAnimator() {
        mAnimator = ObjectAnimator.ofFloat(bindingView.leafIv, "rotation", 0, 360f);
        mAnimator.setDuration(3000);
        mAnimator.setRepeatCount(Animation.INFINITE);
        mAnimator.setRepeatMode(ObjectAnimator.RESTART);
        mAnimator.setInterpolator(new LinearInterpolator());
    }

    @Override
    protected void onEvent() {

        bindingView.backIv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });
        bindingView.chosemodelTv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (index >= modelList.size()) {
                    index = 0;
                }
                mKfid = modelList.get(index++).getId();
                refreshModel(mKfid);
            }
        });

        bindingView.swichIv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mAylaProperty != null) {
                    sendIrcode("电源");
                }
            }
        });

        bindingView.addremotecontrolTv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mLoadingDialog.show();
                String value = new Gson().toJson(mControlBean);
                mAylaDevice.createDatum(mKfid, value, new Response.Listener<AylaDatum>() {
                    @Override
                    public void onResponse(AylaDatum response) {
                        Log.d(TAG, "response = " + response.getValue());
                        bindingView.addsureLay.setVisibility(View.GONE);
                        mLoadingDialog.dismiss();
                        isAdded = true;
                    }
                }, new ErrorListener() {
                    @Override
                    public void onErrorResponse(AylaError aylaError) {
                        Log.e(TAG, "aylaError = " + aylaError.getMessage());
                        bindingView.addsureLay.setVisibility(View.GONE);
                        mLoadingDialog.dismiss();
                    }
                });
            }
        });

        bindingView.saowIv.setOnClickListener(this);
        bindingView.windspeedIv.setOnClickListener(this);
        bindingView.modeIv.setOnClickListener(this);
        bindingView.anionIv.setOnClickListener(this);
        bindingView.timewindIv.setOnClickListener(this);
    }

    private void updateIrCode(String irCode,KeyIrCodeBean data) {
        Log.d(TAG, "irCode = " + irCode);
        if (irCode != null && !irCode.isEmpty()) {
            mAylaProperty.createDatapoint(irCode, null, new Response.Listener<AylaDatapoint>() {
                @Override
                public void onResponse(AylaDatapoint response) {
                    ToastUtils.Toast_long("码率上传成功！");
                    Log.e(TAG, "action  = 码率上传成功！");
                    refreshUI(data);
                }
            }, new ErrorListener() {
                @Override
                public void onErrorResponse(AylaError aylaError) {
                    ToastUtils.Toast_long("码率上传失败！");
                    Log.e(TAG, "aylaError  = " + aylaError.getMessage());

                }
            });
        } else {
            ToastUtils.Toast_long("码率获取失败！");
            Log.e(TAG, "action  = 码率获取失败！！");
        }
    }

    private void refreshUI(KeyIrCodeBean data){

        String cwind = data.getCwind();
        if (cwind.contains("自动")) {
            bindingView.windspeedstatuIv.setImageResource(R.mipmap.nowinds);
            mAnimator.setDuration(3000);
        } else if (cwind.contains("低")) {
            bindingView.windspeedstatuIv.setImageResource(R.mipmap.onewinds);
            mAnimator.setDuration(2000);
        } else if (cwind.contains("中")) {
            bindingView.windspeedstatuIv.setImageResource(R.mipmap.twowinds);
            mAnimator.setDuration(1000);
        } else if (cwind.contains("高")) {
            bindingView.windspeedstatuIv.setImageResource(R.mipmap.threewinds);
            mAnimator.setDuration(500);
        }

        String conoff = data.getConoff();
        if (conoff.equals("关")){
            mAnimator.end();
        }else {
            mAnimator.start();
        }


    }

    private boolean isView = true;
    @Override
    protected void initView(Bundle savedInstanceState) {

        if (mKfid != null){
            bindingView.addsureLay.setVisibility(View.GONE);
            isView = false;
        }
        if (mDeviceId != null && mBrandId != null) {
            viewModel.getModellist(new IResultLisrener<List<ModelBean>>() {
                @Override
                public void onResults(List<ModelBean> data) {
                    if (data.size() > 0) {
                        modelList.clear();
                        modelList.addAll(data);
                        bindingView.chosemodelTv.setText("下一个（1 / " + data.size() + "）");
                    }

                    if (data.size() > 0) {
                        String kfid = data.get(0).getId();
                        refreshModel(kfid);
                    }
                }
            }, mDeviceId, mBrandId);
        } else if (mKfid != null) {
            refreshModel(mKfid);
        }

    }

    private void refreshModel(String kfid) {
        mControlBean.setKfid(kfid);
        mKfid = kfid;
        viewModel.getKeylist(kfid, new IResultLisrener<KeysBean>() {
            @Override
            public void onResults(KeysBean data) {
                if (!isView){
                    bindingView.chosemodelTv.setText("下一个（" + index + " / " + modelList.size() + "）");
                }
                List<String> keylist = data.getKeylist();
                if (keylist != null) {
                    mKeylist.clear();
                    mKeylist.addAll(keylist);
                }
                List<String> keyvalue = data.getKeyvalue();

                if (keyvalue != null) {
                    mKeyvalues.clear();
                    mKeyvalues.addAll(keyvalue);
                }
            }
        });
    }

    private void sendIrcode(String keyName) {
        int keyid = 0;
        for (int i = 0; i < mKeylist.size(); i++) {
            String keyStr = mKeylist.get(i);
            if (keyStr.contains(keyName)) {
                keyid = i;
            }
        }
        int finalKeyid = keyid;
        viewModel.getKeyCode(mKfid,String.valueOf(keyid),new IResultLisrener<KeyIrCodeBean>() {
            @Override
            public void onResults(KeyIrCodeBean data) {
                updateIrCode(mKeyvalues.get(finalKeyid),data);
            }
        });
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.saow_iv:
                sendIrcode("摆风");
                break;
            case R.id.windspeed_iv:
                sendIrcode("风类");
                break;
            case R.id.mode_iv:
                sendIrcode("风速");
                break;
            case R.id.anion_iv:
                sendIrcode("负离子类");
                break;
            case R.id.timewind_iv:
                sendIrcode("定时");
                break;
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        if (isAdded) {
            ActivityUtils.getManager().finishActivity(SelectDeviceTypeActivity.class);
            ActivityUtils.getManager().finishActivity(BrandActivity.class);
        }
    }
}
