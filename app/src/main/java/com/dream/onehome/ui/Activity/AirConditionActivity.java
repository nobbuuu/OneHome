package com.dream.onehome.ui.Activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

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
import com.dream.onehome.bean.KeyIrCodeBean;
import com.dream.onehome.bean.KeysBean;
import com.dream.onehome.bean.ModelBean;
import com.dream.onehome.bean.RemoteControlBean;
import com.dream.onehome.common.Const;
import com.dream.onehome.constract.IResultLisrener;
import com.dream.onehome.databinding.ActivtiyAirconditionBinding;
import com.dream.onehome.ui.ViewModel.ModelViewModel;
import com.dream.onehome.utils.ActivityUtils;
import com.dream.onehome.utils.LogUtils;
import com.dream.onehome.utils.SpUtils;
import com.dream.onehome.utils.ToastUtils;
import com.dream.onehome.utils.annotations.ContentView;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;

/**
 * Time:2019/12/26
 * Author:TiaoZi
 */
@ContentView(R.layout.activtiy_aircondition)
public class AirConditionActivity extends BaseMVVMActivity<ModelViewModel, ActivtiyAirconditionBinding> implements View.OnClickListener {

    private List<ModelBean> modelList = new ArrayList<>();
    private List<String> mKeylist = new ArrayList<>();
    private List<String> mKeyvalues = new ArrayList<>();

    private int index = 1;

    private AylaSessionManager mSessionManager;
    private AylaDevice mAylaDevice;

    private static final String TAG = "AylaLog";

    private AylaProperty mAylaProperty;

    private String mKfid;//遥控器id

    private boolean isView;
    private boolean isAdded;
    private Gson mGson = new Gson();

    private RemoteControlBean mControlBean = new RemoteControlBean();

    @Override
    protected void initIntent() {

        mSessionManager = AylaNetworks.sharedInstance().getSessionManager(Const.APP_NAME);
        if (mSessionManager != null) {
            String dsn = (String) SpUtils.getParam(Const.DSN, "");
            if (!dsn.isEmpty()) {
                mAylaDevice = mSessionManager.getDeviceManager().deviceWithDSN(dsn);
                mAylaProperty = mAylaDevice.getProperty(Const.IR_Send_code);
            }

        } else {
            ToastUtils.Toast_long("aylaSessionManager 初始化失败！");
            Log.e(TAG, "aylaSessionManager  = " + mSessionManager);
        }
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

        bindingView.swichIv.setOnClickListener(this);
        bindingView.addimgIv.setOnClickListener(this);
        bindingView.lessIv.setOnClickListener(this);
        bindingView.modeIv.setOnClickListener(this);
        bindingView.windspeedIv.setOnClickListener(this);
        bindingView.winddirectionIv.setOnClickListener(this);
        bindingView.unknowIv.setOnClickListener(this);

        bindingView.swichLay.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()){
                    case MotionEvent.ACTION_DOWN:
                        bindingView.swichLay.setBackgroundResource(R.drawable.shape_corner_gray);
                        bindingView.swichIv.setBackgroundResource(R.mipmap.power_icon_pressed);
                        break;
                    case MotionEvent.ACTION_UP:
                        bindingView.swichLay.setBackgroundResource(R.drawable.shape_corner_default);
                        bindingView.swichIv.setBackgroundResource(R.drawable.swich);
                        break;
                }
                return false;
            }
        });

        //添加遥控器
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
                        Log.d(TAG, "aylaError = " + aylaError.getMessage());
                        mLoadingDialog.dismiss();
                    }
                });
            }
        });
    }

    private void updateIrCode(String irCode, KeyIrCodeBean data) {
        mAylaProperty.createDatapoint(irCode, null, new Response.Listener<AylaDatapoint>() {
            @Override
            public void onResponse(AylaDatapoint response) {
                refreshUI(data);
//                ToastUtils.Toast_long("码率上传成功！");
                Log.e(TAG, "aylaSessionManager  = " + mSessionManager);
            }
        }, new ErrorListener() {
            @Override
            public void onErrorResponse(AylaError aylaError) {
                ToastUtils.Toast_long("码率上传失败！");
                Log.e(TAG, "aylaError  = " + aylaError.getMessage());

            }
        });
    }

    private void refreshUI(KeyIrCodeBean data) {
        bindingView.temperatureTv.setText(String.valueOf(data.getCtemp()));
        bindingView.modevalueTv.setText(data.getCmode() + "模式");
        String cwind = data.getCwind();
        if (cwind.contains("自动")) {
            bindingView.windspeedvalueIv.setImageResource(R.mipmap.nowinds);
        } else if (cwind.contains("低")) {
            bindingView.windspeedvalueIv.setImageResource(R.mipmap.onewinds);
        } else if (cwind.contains("中")) {
            bindingView.windspeedvalueIv.setImageResource(R.mipmap.twowinds);
        } else if (cwind.contains("高")) {
            bindingView.windspeedvalueIv.setImageResource(R.mipmap.threewinds);
        }
        String cwinddir = data.getCwinddir();
        if (cwinddir.contains("风向1")) {
            bindingView.winddirectionvalue2Iv.setVisibility(View.GONE);
            bindingView.winddirectionvalue1Iv.setImageResource(R.mipmap.ventilation);
        } else if (cwinddir.contains("风向2")) {
            bindingView.winddirectionvalue2Iv.setVisibility(View.GONE);
            bindingView.winddirectionvalue1Iv.setImageResource(R.mipmap.horizontal);
        } else if (cwinddir.contains("自动")) {
            bindingView.winddirectionvalue2Iv.setVisibility(View.GONE);
            bindingView.winddirectionvalue1Iv.setImageResource(R.mipmap.winddirection);
        } else {
            bindingView.winddirectionvalue2Iv.setVisibility(View.VISIBLE);
            bindingView.winddirectionvalue1Iv.setImageResource(R.mipmap.ventilation);
            bindingView.winddirectionvalue2Iv.setImageResource(R.mipmap.horizontal);
        }
//        bindingView.signIv.setImageResource(R.drawable.shape_circle_gray);
    }

    @Override
    protected void initView(Bundle savedInstanceState) {

        Intent intent = getIntent();
        String device_id = intent.getStringExtra(Const.device_id);
        String brand_id = intent.getStringExtra(Const.brand_id);
        String deviceName = intent.getStringExtra(Const.deviceName);
        String brandName = intent.getStringExtra(Const.brandName);
        if (device_id != null && brand_id != null) {
            viewModel.getModellist(new IResultLisrener<List<ModelBean>>() {
                @Override
                public void onResults(List<ModelBean> data) {
                    if (data.size() > 0) {
                        modelList.clear();
                        modelList.addAll(data);
                        bindingView.chosemodelTv.setText("下一个（1 / " + data.size() + "）");
                    }

                    if (data.size() > 0) {
                        mKfid = data.get(0).getId();
                        refreshModel(mKfid);
                    }
                    mControlBean.setType(device_id);
                    mControlBean.setName(deviceName);
                    mControlBean.setBrandName(brandName);
                }
            }, device_id, brand_id);
        } else {
            isView = true;
            bindingView.addsureLay.setVisibility(View.GONE);
            mKfid = intent.getStringExtra(Const.kfid);
            if (mKfid != null) {
                refreshModel(mKfid);
            }
        }
    }

    private void refreshModel(String kfid) {
        if (isView) {
            String keyListJson = (String) SpUtils.getParam(Const.KeyList, "");
            if (!keyListJson.isEmpty()) {
                mKeylist = mGson.fromJson(keyListJson, new TypeToken<List<String>>() {
                }.getType());
            }
            remoteDevice();
        } else {
            viewModel.getKeylist(kfid, new IResultLisrener<KeysBean>() {
                @Override
                public void onResults(KeysBean data) {

                    List<String> keylist = data.getKeylist();
                    if (keylist != null) {
                        mKeylist.clear();
                        mKeylist.addAll(keylist);
                        SpUtils.savaUserInfo(Const.KeyList, new Gson().toJson(keylist));
                        remoteDevice();
                    }
                    if (!isView) {
                        bindingView.chosemodelTv.setText("下一个（" + index + " / " + modelList.size() + "）");
                    }
                    List<String> keyvalue = data.getKeyvalue();
                    if (keyvalue != null) {
                        mKeyvalues.clear();
                        mKeyvalues.addAll(keyvalue);
                    }
                    mControlBean.setKfid(kfid);
                }
            });
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.swich_iv:
                remoteDevice(getKeyId("电源"));
                break;
            case R.id.addimg_iv:
                remoteDevice(getKeyId("温度+"));
                break;
            case R.id.less_iv:
                remoteDevice(getKeyId("温度-"));
                break;
            case R.id.mode_iv:
                remoteDevice(getKeyId("模式"));
                break;
            case R.id.windspeed_iv:
                remoteDevice(getKeyId("风速"));
                break;
            case R.id.winddirection_iv:
                remoteDevice(getKeyId("风向"));
                break;
        }
//        bindingView.signIv.setImageResource(R.drawable.shape_circle_orange);
    }

    private void remoteDevice(String keyId) {
        viewModel.getKeyCode(mKfid, keyId, new IResultLisrener<KeyIrCodeBean>() {
            @Override
            public void onResults(KeyIrCodeBean data) {
                if (mAylaProperty != null) {
                    String irCode = data.getIrdata();
                    Log.d(TAG, "irCode = " + irCode);
                    if (irCode != null && !irCode.isEmpty()) {
                        updateIrCode(irCode, data);
                    } else {
                        ToastUtils.Toast_long("");
                    }
                }
            }
        });
    }

    private void remoteDevice() {
        viewModel.getKeyCode(new IResultLisrener<KeyIrCodeBean>() {
            @Override
            public void onResults(KeyIrCodeBean data) {
                refreshUI(data);
            }
        });
    }

    private String getKeyId(String keyName) {
        for (int i = 0; i < mKeylist.size(); i++) {
            String keyStr = mKeylist.get(i);
            if (keyStr.contains(keyName)) {
                return String.valueOf(i);
            }
        }
        return null;
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        if (!isView && isAdded) {
            ActivityUtils.getManager().finishActivity(SelectDeviceTypeActivity.class);
            ActivityUtils.getManager().finishActivity(BrandActivity.class);
        }
    }
}
