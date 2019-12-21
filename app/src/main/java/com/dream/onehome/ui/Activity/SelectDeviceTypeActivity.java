package com.dream.onehome.ui.Activity;

import android.content.Intent;
import android.os.Bundle;
import com.dream.onehome.R;
import com.dream.onehome.base.BaseMVVMActivity;
import com.dream.onehome.databinding.ActivitySelectDevicetypeBinding;
import com.dream.onehome.ui.ViewModel.SelectDeviceTypeModel;
import com.dream.onehome.utils.annotations.ContentView;

/**
 * Time:2019/12/21
 * Author:TiaoZi
 */
@ContentView(R.layout.activity_select_devicetype)
public class SelectDeviceTypeActivity extends BaseMVVMActivity<SelectDeviceTypeModel, ActivitySelectDevicetypeBinding> {



    @Override
    protected void initIntent() {

        Intent intent = getIntent();

    }

    @Override
    protected void onEvent() {

    }

    @Override
    protected void initView(Bundle savedInstanceState) {

    }

}
