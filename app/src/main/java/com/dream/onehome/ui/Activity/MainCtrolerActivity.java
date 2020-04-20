package com.dream.onehome.ui.Activity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

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
import com.dream.onehome.bean.KeysBean;
import com.dream.onehome.bean.ModelBean;
import com.dream.onehome.bean.RemoteControlBean;
import com.dream.onehome.common.Const;
import com.dream.onehome.constract.IClickLisrener;
import com.dream.onehome.constract.IResultLisrener;
import com.dream.onehome.databinding.ActivityMaterBinding;
import com.dream.onehome.ui.ViewModel.ModelViewModel;
import com.dream.onehome.ui.fragment.ExtensionFragment;
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
@ContentView(R.layout.activity_mater)
public class MainCtrolerActivity extends BaseMVVMActivity<ModelViewModel, ActivityMaterBinding> implements View.OnClickListener {


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

    private int[] numIds = new int[]{R.id.zaro, R.id.one, R.id.two, R.id.three, R.id.four, R.id.five, R.id.six, R.id.seven, R.id.eight, R.id.night};
    private RemoteControlBean mControlBean = new RemoteControlBean();
    private boolean isAdded;
    private ExtensionFragment mExtensionFragment;

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
            switch (mDeviceId) {
                case "3":
                    bindingView.centerTv.setText("机顶盒");
                    break;
                case "4":
                    bindingView.centerTv.setText("DVD");
                    bindingView.channelLay.setVisibility(View.GONE);
                    bindingView.centerLay.setVisibility(View.VISIBLE);
                    break;

                case "7":
                    bindingView.centerTv.setText("网络盒子");
                    break;
                case "8":
                    bindingView.centerTv.setText("投影仪");
                    break;
            }
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
                    String irCode = getIrCode("电源");
                    updateIrCode(irCode);
                }
            }
        });

        bindingView.mainIv.setOnClickListener(this);
        bindingView.muteIv.setOnClickListener(this);
        bindingView.addvoiceIv.setOnClickListener(this);
        bindingView.lessvoiceIv.setOnClickListener(this);
        bindingView.addchanelIv.setOnClickListener(this);
        bindingView.lesschanelIv.setOnClickListener(this);

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
                        bindingView.masterTab.setVisibility(View.VISIBLE);
                        bindingView.masterVp.setVisibility(View.VISIBLE);
                        initViewPager();
                        mLoadingDialog.dismiss();
                        isAdded = true;
                    }
                }, new ErrorListener() {
                    @Override
                    public void onErrorResponse(AylaError aylaError) {
                        Log.e(TAG, "aylaError = " + aylaError.getMessage());
                        bindingView.addsureLay.setVisibility(View.GONE);
                        bindingView.masterTab.setVisibility(View.VISIBLE);
                        bindingView.masterVp.setVisibility(View.VISIBLE);
                        initViewPager();
                        mLoadingDialog.dismiss();
                    }
                });
            }
        });
    }

    private void updateIrCode(String irCode) {
        Log.d(TAG, "irCode = " + irCode);
        if (irCode != null && !irCode.isEmpty()) {
            mAylaProperty.createDatapoint(irCode, null, new Response.Listener<AylaDatapoint>() {
                @Override
                public void onResponse(AylaDatapoint response) {
//                    ToastUtils.Toast_long("发送成功！");
                    Log.e(TAG, "action  = 码率上传成功！");
                }
            }, new ErrorListener() {
                @Override
                public void onErrorResponse(AylaError aylaError) {
//                    ToastUtils.Toast_long("发送失败");
                    Log.e(TAG, "aylaError  = " + aylaError.getMessage());

                }
            });
        } else {
            ToastUtils.Toast_long("暂不支持");
            Log.e(TAG, "action  = 码库获取失败！！");

        }
    }

    private boolean isView = true;
    @Override
    protected void initView(Bundle savedInstanceState) {

        if (mKfid == null) {
            bindingView.masterTab.setVisibility(View.GONE);
            bindingView.masterVp.setVisibility(View.GONE);
        } else {
            initViewPager();
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
                KeysBean extBean = new KeysBean();
                int tempdex = 0;
                List<String> keylist = data.getKeylist();
                List<String> keyvalue = data.getKeyvalue();
                if (keylist == null || keyvalue == null) {
                    if (keylist.size() != keyvalue.size()) {
                        ToastUtils.Toast_short("码库键值长度不对等");
                    }
                    return;
                }

                mKeylist.clear();
                mKeylist.addAll(keylist);
                for (int i = 0; i < keylist.size(); i++) {
                    if (keylist.get(i).equals("0")) {
                        if (keylist.size() > (i + 1)) {
                            List<String> subList = keylist.subList(i + 1, keylist.size() - 1);
                            extBean.setKeylist(subList);
                            tempdex = i + 1;
                        }
                        break;
                    }
                }

                mKeyvalues.clear();
                mKeyvalues.addAll(keyvalue);
                List<String> subList = keyvalue.subList(tempdex, keyvalue.size() - 1);
                extBean.setKeyvalue(subList);
                if (mExtensionFragment != null) {
                    mExtensionFragment.setData(extBean);
                }
            }
        });
    }

    private String getIrCode(String keyName) {
        for (int i = 0; i < mKeylist.size(); i++) {
            String keyStr = mKeylist.get(i);
            if (keyStr.contains(keyName)) {
                Log.d(TAG, "keyName = " + keyName + "; keyStr = " + keyStr);
                Log.d(TAG, "index = " + i);
                return mKeyvalues.get(i);
            }
        }
        return null;
    }

    private void initViewPager() {
        List<Fragment> fragments = new ArrayList<>();
        NumberFragment numberFragment = new NumberFragment();
        MenuFragment menuFragment = new MenuFragment();
        mExtensionFragment = new ExtensionFragment();
        fragments.add(numberFragment);
        fragments.add(menuFragment);
        fragments.add(mExtensionFragment);

        BaseFraPagerAdapter pagerAdapter = new BaseFraPagerAdapter(getSupportFragmentManager(), fragments);
        bindingView.masterVp.setAdapter(pagerAdapter);
        bindingView.masterVp.setOffscreenPageLimit(3);
        bindingView.masterTab.setupWithViewPager(bindingView.masterVp);
        bindingView.masterTab.getTabAt(0).setText("123");
        bindingView.masterTab.getTabAt(1).setText("菜单");
        bindingView.masterTab.getTabAt(2).setText("扩展");

        numberFragment.setOnViewClickListener(new NumberFragment.onViewClickListener() {
            @Override
            public void onClick(int viewId) {
                if (viewId == R.id.return_iv) {
                    updateIrCode(getIrCode("返回"));
                } else {
                    for (int i = 0; i < numIds.length; i++) {
                        if (viewId == numIds[i]) {
                            updateIrCode(getIrCode(String.valueOf(i)));
                            break;
                        }
                    }
                }
            }
        });

        menuFragment.setOnViewClickListener(new IClickLisrener() {
            @Override
            public void onClick(int viewId) {
                switch (viewId) {
                    case R.id.menu:
                        updateIrCode(getIrCode("菜单"));
                        break;
                    case R.id.home:
                        updateIrCode(getIrCode("首页"));
                        break;
                    case R.id.backiv:
                        updateIrCode(getIrCode("返回"));
                        break;
                    case R.id.nextiv:
                        updateIrCode(getIrCode("收藏"));
                        break;
                    case R.id.sure:
                        updateIrCode(getIrCode("OK"));
                        break;
                    case R.id.leftiv:
                        updateIrCode(getIrCode("左"));
                        break;
                    case R.id.topiv:
                        updateIrCode(getIrCode("上"));
                        break;
                    case R.id.rightiv:
                        updateIrCode(getIrCode("右"));
                        break;
                    case R.id.downiv:
                        updateIrCode(getIrCode("下"));
                        break;
                }
            }
        });
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.main_iv:
                updateIrCode(getIrCode("首页"));
                break;
            case R.id.mute_iv:
                updateIrCode(getIrCode("静音"));
                break;
            case R.id.addvoice_iv:
                updateIrCode(getIrCode("音量+"));
                break;
            case R.id.lessvoice_iv:
                updateIrCode(getIrCode("音量-"));
                break;
            case R.id.addchanel_iv:
                updateIrCode(getIrCode("频道+"));
                break;
            case R.id.lesschanel_iv:
                updateIrCode(getIrCode("频道-"));
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
