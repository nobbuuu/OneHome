package com.dream.onehome.ui.Activity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.fragment.app.Fragment;

import com.android.volley.Response;
import com.aylanetworks.aylasdk.AylaDatapoint;
import com.aylanetworks.aylasdk.AylaDevice;
import com.aylanetworks.aylasdk.AylaNetworks;
import com.aylanetworks.aylasdk.AylaProperty;
import com.aylanetworks.aylasdk.AylaSessionManager;
import com.aylanetworks.aylasdk.error.AylaError;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.dream.onehome.R;
import com.dream.onehome.base.BaseFraPagerAdapter;
import com.dream.onehome.base.BaseMVVMActivity;
import com.dream.onehome.base.BaseMasterActivity;
import com.dream.onehome.bean.KeysBean;
import com.dream.onehome.bean.ModelBean;
import com.dream.onehome.common.Const;
import com.dream.onehome.constract.IResultLisrener;
import com.dream.onehome.databinding.ActivityMaterBinding;
import com.dream.onehome.ui.ViewModel.ModelViewModel;
import com.dream.onehome.ui.fragment.MenuFragment;
import com.dream.onehome.ui.fragment.NumberFragment;
import com.dream.onehome.utils.SpUtils;
import com.dream.onehome.utils.ToastUtils;
import com.dream.onehome.utils.annotations.ContentView;

import java.util.ArrayList;
import java.util.List;

/**
 * Time:2019/12/11
 * Author:TiaoZi
 *
 * 电视主控界面
 */
@ContentView(R.layout.activity_mater)
public class MainCtrolerActivity extends BaseMVVMActivity<ModelViewModel, ActivityMaterBinding> {


    private List<ModelBean> modelList = new ArrayList<>();
    private List<String> mKeylist = new ArrayList<>();
    private List<String> mKeyvalues = new ArrayList<>();

    private AylaSessionManager mSessionManager;

    private int index = 0;

    private static final String TAG = "AylaLog";
    private AylaProperty mAylaProperty;
    private String mDeviceId;
    private String mBrandId;

    @Override
    protected void initIntent() {

        Intent intent = getIntent();
        if (intent != null) {
            mDeviceId = intent.getStringExtra("device_id");
            mBrandId = intent.getStringExtra("brand_id");
        }

        mSessionManager =  AylaNetworks.sharedInstance().getSessionManager(Const.APP_NAME);
        if (mSessionManager != null){
            String dsn = (String) SpUtils.getParam(Const.DSN, "");
            if (!dsn.isEmpty()){
                AylaDevice aylaDevice = mSessionManager.getDeviceManager().deviceWithDSN(dsn);
                mAylaProperty = aylaDevice.getProperty(Const.IR_Send_code);
                Log.e(TAG,"mAylaProperty  = " + mAylaProperty);
            }

        }else {
            ToastUtils.Toast_long("aylaSessionManager 初始化失败！");
            Log.e(TAG,"aylaSessionManager  = " + mSessionManager);
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
                if (index < modelList.size()) {
                    String kfid = modelList.get(index).getId();
                    refreshModel(kfid);
                }
            }
        });

        bindingView.swichIv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mAylaProperty != null){
                    String irCode = mKeyvalues.get(0);
                    Log.d(TAG,"irCode = " + irCode);
                    if (irCode != null && !irCode.isEmpty()){
                        mAylaProperty.createDatapoint(irCode, null, new Response.Listener<AylaDatapoint>() {
                            @Override
                            public void onResponse(AylaDatapoint response) {
                                ToastUtils.Toast_long("开关码率上传成功！");
                                Log.e(TAG,"aylaSessionManager  = " + mSessionManager);
                            }
                        }, new ErrorListener() {
                            @Override
                            public void onErrorResponse(AylaError aylaError) {
                                ToastUtils.Toast_long("开关码率上传失败！");
                                Log.e(TAG,"aylaError  = " + aylaError.getMessage());

                            }
                        });
                    }
                }
            }
        });
    }

    @Override
    protected void initView(Bundle savedInstanceState) {

        if (mDeviceId.equals("2")){//电视
            initViewPager();
        }

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

    }

    private void refreshModel(String kfid) {
        viewModel.getKeylist(kfid, new IResultLisrener<KeysBean>() {
            @Override
            public void onResults(KeysBean data) {
                index++;
                bindingView.chosemodelTv.setText("下一个（" + index + " / " + modelList.size() + "）");
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

    private void initViewPager() {
        List<Fragment> fragments = new ArrayList<>();
        NumberFragment numberFragment = new NumberFragment();
        MenuFragment menuFragment = new MenuFragment();
        fragments.add(numberFragment);
        fragments.add(menuFragment);

        BaseFraPagerAdapter pagerAdapter = new BaseFraPagerAdapter(getSupportFragmentManager(), fragments);
        bindingView.masterVp.setAdapter(pagerAdapter);
        bindingView.masterTab.setupWithViewPager(bindingView.masterVp);
        bindingView.masterTab.getTabAt(0).setText("123");
        bindingView.masterTab.getTabAt(1).setText("菜单");
    }
}
