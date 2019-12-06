package com.dream.onehome.ui.Activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.dream.onehome.R;
import com.dream.onehome.base.BaseActivity;

import butterknife.ButterKnife;
import butterknife.OnClick;

public class AddDeviceActivity extends BaseActivity {
    @Override
    public int getLayoutId() {
        return R.layout.activity_addstep_one;
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


    @OnClick({R.id.back_iv, R.id.sure_tv})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.back_iv:
                onBackPressed();
                break;
            case R.id.sure_tv:
                Intent intent = new Intent(this,ConnectDeviceActivity.class);
                startActivity(intent);
                break;
        }
    }
}
