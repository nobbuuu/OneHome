package com.dream.onehome.ui.Activity;

import android.content.Intent;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import com.dream.onehome.R;
import com.dream.onehome.base.BaseActivity;
import com.dream.onehome.common.Const;
import com.dream.onehome.utils.WifiUtils;

import butterknife.BindView;
import butterknife.OnClick;

public class WifiChangeActivity extends BaseActivity {

    private static final String TAG = "WIFILog";


    private String wifiName = "";
    private String wifiPwd = "";

    @BindView(R.id.currentwifi_tv)
    TextView currentwifiTv;

    @Override
    public int getLayoutId() {
        return R.layout.activity_chosewifi;
    }

    @Override
    public void initView() {

        Intent intent = getIntent();
        if (intent  != null){
            wifiName = intent.getStringExtra("wifiName");
            wifiPwd = intent.getStringExtra("wifiPwd");

            currentwifiTv.setText("当前Wi-Fi：" + wifiName);
        }
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


    @OnClick({R.id.back_iv,R.id.sure_tv})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.back_iv:
                onBackPressed();
                break;
            case R.id.sure_tv:
                startActivityForResult(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS),1217);

                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1217){
            Intent intent = new Intent(this, ConnectDeviceActivity.class);
            intent.putExtra(Const.WiFiName, wifiName);
            intent.putExtra(Const.WiFiPwd, wifiPwd);
            intent.putExtra(Const.AylaWifi, WifiUtils.getConnectWifiSsid());
            startActivity(intent);
            finish();
        }
    }
}




