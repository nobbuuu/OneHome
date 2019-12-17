package com.dream.onehome.ui.Activity;

import android.Manifest;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.android.volley.Response;
import com.aylanetworks.aylasdk.AylaNetworks;
import com.aylanetworks.aylasdk.error.AylaError;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.aylanetworks.aylasdk.setup.AylaSetup;
import com.aylanetworks.aylasdk.setup.AylaSetupDevice;
import com.dream.onehome.R;
import com.dream.onehome.base.BaseActivity;
import com.dream.onehome.common.Const;
import com.dream.onehome.receiver.NetBroadcastReceiver;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * Time:2019/12/14
 * Author:TiaoZi
 */
public class WifiSetActivity extends BaseActivity {

    private static final String TAG = "WIFILog";

    @BindView(R.id.line_wifi)
    View mlinewifi;

    @BindView(R.id.line_pwd)
    View mlinepwd;

    @BindView(R.id.wifiname_tv)
    EditText mWifiNameEdt;

    @BindView(R.id.wifipwd_tv)
    EditText mWifiPwdEdt;

    @BindView(R.id.sure_tv)
    Button mSureTv;

    private NetBroadcastReceiver receiver;

    @Override
    public int getLayoutId() {
        return R.layout.activity_wifiset;
    }

    @Override
    public void initView() {

        requestPermission();

        receiver = new NetBroadcastReceiver();
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        this.registerReceiver(receiver, filter);

    }

    @Override
    public void OnResume() {
        receiver.setNetConnectedListener(new NetBroadcastReceiver.NetConnectedListener() {
            @Override
            public void netContent(boolean isConnected, NetworkInfo networkInfo) {
                //在此处处理具体业务即可

                Log.d(TAG, "netconnected = " + isConnected);
                Log.d(TAG, "networkInfo = " + networkInfo.toString());
                if (isConnected) {
                    getConnectWifiSsid();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.unregisterReceiver(receiver);
    }

    @Override
    public void loadDatas() {

    }

    @Override
    public void eventListener() {

        mWifiNameEdt.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    mlinewifi.setBackgroundColor(getResources().getColor(R.color.color_them));
                    mlinepwd.setBackgroundColor(getResources().getColor(R.color.color666));

                }
            }
        });
        mWifiPwdEdt.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    mlinewifi.setBackgroundColor(getResources().getColor(R.color.color666));
                    mlinepwd.setBackgroundColor(getResources().getColor(R.color.color_them));

                }
            }
        });

        mWifiPwdEdt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() != 0) {
                    mSureTv.setBackgroundResource(R.drawable.select_surebtn);
                    mSureTv.setEnabled(true);
                } else {
                    mSureTv.setBackgroundResource(R.drawable.shape_voice_unenable_btn);
                    mSureTv.setEnabled(false);

                }
            }
        });

        mSureTv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String wifiName = mWifiNameEdt.getText().toString();
                String wifiPwd = mWifiPwdEdt.getText().toString();

                Intent intent = new Intent(getBaseContext(), ConnectDeviceActivity.class);
                intent.putExtra("wifiName", wifiName);
                intent.putExtra("wifiPwd", wifiPwd);
                startActivity(intent);
            }
        });

    }

    private String getConnectWifiSsid() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        Log.d(TAG, "connect wifi name = " + wifiInfo.getSSID());
        List<WifiConfiguration> networks = wifiManager.getConfiguredNetworks();

        if (networks.size() > 0) {
//            String name = wifiInfo.getSSID().replace("\"", "");
            String name = networks.get(networks.size() - 1).SSID.replace("\"", "");
            mWifiNameEdt.setText(name);
        }

        for (int i = 0; i < networks.size(); i++) {

            WifiConfiguration wifiConfiguration = networks.get(i);
            Log.d(TAG, "wifi name = " + wifiConfiguration.SSID);
        }


        return mWifiNameEdt.getText().toString();
    }

    public void requestPermission() {
        // checkSelfPermission 判断是否已经申请了此权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            //如果应用之前请求过此权限但用户拒绝了请求，shouldShowRequestPermissionRationale将返回 true。
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_COARSE_LOCATION)) {

            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,}, 121);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

    }

    @OnClick({R.id.back_iv, R.id.pwdvisible_iv, R.id.wifichange_iv, R.id.sure_tv})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.back_iv:
                onBackPressed();
                break;
            case R.id.pwdvisible_iv:

                break;
            case R.id.wifichange_iv:
                startActivityForResult(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS),1217);

                break;
            case R.id.sure_tv:

                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1217){
            String connectWifiSsid = getConnectWifiSsid();
            try {
                AylaSetup aylaSetup = new AylaSetup(getBaseContext(), AylaNetworks.sharedInstance().getSessionManager(Const.APP_NAME));
                aylaSetup.connectToNewDevice(connectWifiSsid, 30, new Response.Listener<AylaSetupDevice>() {
                    @Override
                    public void onResponse(AylaSetupDevice response) {
                        Log.d(TAG,"method  connectToNewDevice  onResponse...");
                    }
                }, new ErrorListener() {
                    @Override
                    public void onErrorResponse(AylaError error) {
                        Log.e(TAG,"AylaError  = " + error.getMessage());
                    }
                });

            } catch (AylaError aylaError) {
                aylaError.printStackTrace();
                Log.e(TAG,"AylaError  = " + aylaError.getMessage());
            }

        }
    }
}
