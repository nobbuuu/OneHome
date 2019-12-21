package com.dream.onehome.ui.Activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.dream.onehome.R;
import com.dream.onehome.base.BaseMVVMActivity;
import com.dream.onehome.base.NoViewModel;
import com.dream.onehome.databinding.ActivityRemotecontrollistBinding;
import com.dream.onehome.utils.annotations.ContentView;

/**
 * Time:2019/12/20
 * Author:TiaoZi
 */
@ContentView(R.layout.activity_remotecontrollist)
public class RemoteControlListActivity extends BaseMVVMActivity<NoViewModel, ActivityRemotecontrollistBinding> {


    @Override
    protected void initIntent() {

    }

    @Override
    protected void onEvent() {
        bindingView.addimgIv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getBaseContext(),SelectDeviceTypeActivity.class));
            }
        });
    }

    @Override
    protected void initView(Bundle savedInstanceState) {


    }
}
