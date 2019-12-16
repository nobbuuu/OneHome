package com.dream.onehome.ui.Activity;

import android.view.View;

import com.dream.onehome.R;
import com.dream.onehome.base.BaseActivity;

import butterknife.OnClick;

public class ConnectDeviceActivity extends BaseActivity {
    @Override
    public int getLayoutId() {
        return R.layout.activity_connectdevice;
    }

    @Override
    public void initView() {

    }

    @Override
    public void OnResume() {

    }

    @Override
    public void loadDatas() {

    }

    @Override
    public void eventListener() {

    }


    @OnClick({R.id.back_iv})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.back_iv:
                onBackPressed();
                break;
        }
    }
}
