package com.dream.onehome.ui.Activity;

import android.content.Intent;
import android.os.Bundle;
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
import com.dream.onehome.bean.KeyIrCodeBean;
import com.dream.onehome.bean.KeysBean;
import com.dream.onehome.bean.ModelBean;
import com.dream.onehome.bean.RemoteControlBean;
import com.dream.onehome.common.Const;
import com.dream.onehome.constract.IResultLisrener;
import com.dream.onehome.databinding.ActivityAirfilterBinding;
import com.dream.onehome.databinding.ActivtiyAirconditionBinding;
import com.dream.onehome.dialog.ExtDialog;
import com.dream.onehome.ui.ViewModel.ModelViewModel;
import com.dream.onehome.utils.ActivityUtils;
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
@ContentView(R.layout.activity_airfilter)
public class AirFilterActivity extends BaseMVVMActivity<ModelViewModel, ActivityAirfilterBinding> implements View.OnClickListener {

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
    private KeysBean mKeysBean = new KeysBean();
    private ExtDialog mExtDialog;
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
        bindingView.autoTv.setOnClickListener(this);
        bindingView.sleepTv.setOnClickListener(this);
        bindingView.windspeedTv.setOnClickListener(this);
        bindingView.extentTv.setOnClickListener(this);

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

    private void updateIrCode(String irCode) {
        mAylaProperty.createDatapoint(irCode, null, new Response.Listener<AylaDatapoint>() {
            @Override
            public void onResponse(AylaDatapoint response) {
                refreshUI();
//                ToastUtils.Toast_long("码率上传成功！");
                Log.e(TAG, "aylaSessionManager  = " + mSessionManager);
            }
        }, new ErrorListener() {
            @Override
            public void onErrorResponse(AylaError aylaError) {
//                ToastUtils.Toast_long("码率上传失败！");
                Log.e(TAG, "aylaError  = " + aylaError.getMessage());

            }
        });
    }

    private void refreshUI() {

    }

    @Override
    protected void initView(Bundle savedInstanceState) {

        mExtDialog = new ExtDialog(this);
        Intent intent = getIntent();
        String device_id = intent.getStringExtra(Const.device_id);
        String brand_id = intent.getStringExtra(Const.brand_id);
        String deviceName = intent.getStringExtra(Const.deviceName);
        String brandName = intent.getStringExtra(Const.brandName);
        mKfid = intent.getStringExtra(Const.kfid);
        if (mKfid == null) {
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
            refreshModel(mKfid);
        }
    }

    private void refreshModel(String kfid) {
        viewModel.getKeylist(kfid, new IResultLisrener<KeysBean>() {
            @Override
            public void onResults(KeysBean data) {
                List<String> keylist = data.getKeylist();
                List<String> keyvalue = data.getKeyvalue();
                if (keylist != null) {
                    mKeylist.clear();
                    mKeylist.addAll(keylist);
//                    SpUtils.savaUserInfo(Const.KeyList,new Gson().toJson(keylist));
                    remoteDevice();
                }
                if (!isView) {
                    bindingView.chosemodelTv.setText("下一个（" + index + " / " + modelList.size() + "）");
                }
                initExtentionData(keylist, keyvalue);
                if (keyvalue != null) {
                    mKeyvalues.clear();
                    mKeyvalues.addAll(keyvalue);
                }
                mControlBean.setKfid(kfid);
            }
        });
    }

    private void initExtentionData(List<String> keylist, List<String> keyvalue) {
        String[] mainNames = new String[]{"电源", "AUTO", "睡眠", "风速"};
        List<Integer> indexList = new ArrayList<>();
        List<String> tempList = new ArrayList<>();
        for (int i = 0; i < keylist.size(); i++) {
            boolean isInclude = false;
            for (int j = 0; j < mainNames.length; j++) {
                if (keylist.get(i).equals(mainNames[j])) {
                    isInclude = true;
                    break;
                }
            }
            if (!isInclude) {
                tempList.add(keylist.get(i));
                indexList.add(i);
            }
        }
        mKeysBean.setKeylist(tempList);
        List<String> tempValueList = new ArrayList<>();
        for (int i = 0; i < keyvalue.size(); i++) {
            for (int j = 0; j < indexList.size(); j++) {
                if (i == indexList.get(j)) {
                    tempValueList.add(keyvalue.get(i));
                    break;
                }
            }
        }
        mKeysBean.setKeyvalue(tempValueList);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.swich_iv:
                updateIrCode(getKeyId("电源"));
                break;
            case R.id.auto_tv:
                updateIrCode(getKeyId("AUTO"));
                break;
            case R.id.sleep_tv:
                updateIrCode(getKeyId("睡眠"));
                break;
            case R.id.windspeed_tv:
                updateIrCode(getKeyId("风速"));
                break;
            case R.id.extent_tv://扩展
                mExtDialog.setData(mKeysBean);
                mExtDialog.show();
                break;
        }
//        bindingView.signIv.setImageResource(R.drawable.shape_circle_orange);
    }

    private void remoteDevice() {
        viewModel.getKeyCode(new IResultLisrener<KeyIrCodeBean>() {
            @Override
            public void onResults(KeyIrCodeBean data) {
                refreshUI();
            }
        });
    }

    private String getKeyId(String keyName) {
        for (int i = 0; i < mKeylist.size(); i++) {
            String keyStr = mKeylist.get(i);
            if (keyStr.contains(keyName)) {
                return mKeyvalues.get(i);
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
