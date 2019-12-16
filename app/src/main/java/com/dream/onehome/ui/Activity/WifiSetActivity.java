package com.dream.onehome.ui.Activity;

import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import com.dream.onehome.R;
import com.dream.onehome.base.BaseActivity;

/**
 * Time:2019/12/14
 * Author:TiaoZi
 */
public class WifiSetActivity extends BaseActivity {
    @Override
    public int getLayoutId() {
        return R.layout.activity_wifiset;
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
    private String getConnectWifiSsid(){
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        Log.d("wifiInfo", wifiInfo.toString());
        Log.d("SSID",wifiInfo.getSSID());
        return wifiInfo.getSSID();
    }
}
