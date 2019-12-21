package com.dream.onehome.ui.Activity;

import android.content.Intent;
import android.location.Location;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.android.volley.Response;
import com.aylanetworks.aylasdk.AylaAPIRequest;
import com.aylanetworks.aylasdk.AylaDevice;
import com.aylanetworks.aylasdk.AylaNetworks;
import com.aylanetworks.aylasdk.AylaSessionManager;
import com.aylanetworks.aylasdk.error.AylaError;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.aylanetworks.aylasdk.setup.AylaRegistration;
import com.aylanetworks.aylasdk.setup.AylaRegistrationCandidate;
import com.aylanetworks.aylasdk.setup.AylaSetup;
import com.aylanetworks.aylasdk.setup.AylaSetupDevice;
import com.aylanetworks.aylasdk.setup.AylaWifiStatus;
import com.dream.onehome.R;
import com.dream.onehome.base.BaseActivity;
import com.dream.onehome.common.Const;
import com.dream.onehome.customview.CircularProgressView;
import com.dream.onehome.utils.ActivityUtils;
import com.dream.onehome.utils.LocationUtil;
import com.dream.onehome.utils.SpUtils;
import com.dream.onehome.utils.ToastUtils;

import butterknife.BindView;
import butterknife.OnClick;

public class ConnectDeviceActivity extends BaseActivity {

    private static final String TAG = "WIFILog";

    private String wifiName = "";
    private String wifiPwd = "";
    private String aylaWifi = "";

    private Location mLocation;

    @BindView(R.id.circle_pro)
    CircularProgressView mProgressView;

    @BindView(R.id.progress_tv)
    TextView mProgressTv;

    @BindView(R.id.sure_tv)
    Button mSurebtn;

    @BindView(R.id.step_lay)
    ConstraintLayout mSteplay;

    private AylaSetup aylaSetup;
    private AylaSessionManager mSessionManager;
    private boolean isConnectNewDevice = false;
    public static boolean isConnectSuccess = false;
    private int mCurrentProgress = 0;
    private int reBindingNum;

    @Override
    public int getLayoutId() {
        return R.layout.activity_connectdevice;
    }

    @Override
    public void initView() {

        Intent intent = getIntent();

        if (intent != null){
            wifiName = intent.getStringExtra(Const.WiFiName);
            wifiPwd = intent.getStringExtra(Const.WiFiPwd);
            aylaWifi = intent.getStringExtra(Const.AylaWifi);

            LocationUtil.getCurrentLocation(this, new LocationUtil.LocationCallBack() {
                @Override
                public void onSuccess(Location location) {
                    Log.d(TAG,"Longitude = " + location.getLongitude() +  "  Latitude = " + location.getLatitude());
                    mLocation = location;
                    connectDeviceService();
                }

                @Override
                public void onFail(String msg) {
                    Log.e(TAG,"errorMsg = " + msg);

                }
            });

            connectNewDevice(aylaWifi);

            /*DisposableObserver<Integer> disposableObserver = new DisposableObserver<Integer>() {
                    @Override
                    public void onNext(Integer o) {

                        mProgressView.setProgress(o);

                        mProgressTv.setText(o + "%");
                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onComplete() {

                    }
                };
                Observable.create(new ObservableOnSubscribe<Integer>() {
                    @Override
                    public void subscribe(ObservableEmitter<Integer> e) throws Exception {
                        //耗时操作

                        connectNewDevice(aylaWifi);

                        for (int i = 0; i <= 100; i++) {
                            if (i % 20 == 0) {
                                try {
                                    Thread.sleep(1500); //模拟连接过程。
                                } catch (InterruptedException exception) {
                                    if (!e.isDisposed()) {
                                        e.onError(exception);
                                    }
                                }
                                e.onNext(i);
                            }
                        }
                        e.onComplete();

                    }
                }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                        .subscribe(disposableObserver);

                mCompositeDisposable.add(disposableObserver);*/

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
                ActivityUtils.getManager().finishActivity(WifiSetActivity.class);
                if (isConnectSuccess){
                    ActivityUtils.getManager().finishActivity(AddDeviceActivity.class);
                }
                onBackPressed();
                break;
        }
    }

    private void connectNewDevice(String aylaWifi) {
        try {
             mSessionManager = AylaNetworks.sharedInstance().getSessionManager(Const.APP_NAME);
            if (mSessionManager != null){
                aylaSetup = new AylaSetup(getBaseContext(),mSessionManager);
                aylaSetup.connectToNewDevice(aylaWifi, 10, new Response.Listener<AylaSetupDevice>() {
                    @Override
                    public void onResponse(AylaSetupDevice response) {
                        Log.d(TAG,"method  connectToNewDevice  onResponse...");
                        onAylaRequestSuccess();
                        isConnectNewDevice = true;
                        connectDeviceService();


                    }
                }, new ErrorListener() {
                    @Override
                    public void onErrorResponse(AylaError error) {
                        Log.e(TAG,"AylaError  = " + error.getMessage());
                    }
                });
            }else {
                ToastUtils.Toast_long("aylaSessionManager 初始化失败！");
                Log.e(TAG,"aylaSessionManager  = " + mSessionManager);

            }

        } catch (AylaError aylaError) {
            aylaError.printStackTrace();
            onAylaEroor(aylaError);
        }
    }

    private void connectDeviceService(){


        if (!wifiName.isEmpty()&&!wifiPwd.isEmpty()&&mLocation!=null && isConnectNewDevice){

            final String setupToken = getRandomToken();
            aylaSetup.connectDeviceToService(wifiName, wifiPwd, setupToken, mLocation.getLatitude(), mLocation.getLongitude(), 10,
                    new Response.Listener<AylaWifiStatus>() {
                        @Override
                        public void onResponse(AylaWifiStatus response) {

                            final String dsn = response.getDsn();

                            Log.d(TAG,"connectDeviceToService  success ....");
                            onAylaRequestSuccess();

                            aylaSetup.reconnectToOriginalNetwork(10, new Response.Listener<AylaAPIRequest.EmptyResponse>() {
                                @Override
                                public void onResponse(AylaAPIRequest.EmptyResponse response) {


                                    Log.d(TAG,"reconnectToOriginalNetwork  success ....");
                                    onAylaRequestSuccess();


                                    aylaSetup.confirmDeviceConnected(10, dsn, setupToken, new Response.Listener<AylaSetupDevice>() {
                                        @Override
                                        public void onResponse(AylaSetupDevice response) {

                                            Log.d(TAG,"confirmDeviceConnected  success ....");

                                            onAylaRequestSuccess();

                                            bindingDevice(response, dsn, setupToken);

                                        }
                                    }, new ErrorListener() {
                                        @Override
                                        public void onErrorResponse(AylaError aylaError) {
                                            onAylaEroor(aylaError);
                                        }
                                    });


                                }
                            }, new ErrorListener() {
                                @Override
                                public void onErrorResponse(AylaError aylaError) {
                                    onAylaEroor(aylaError);
                                }
                            });

                        }
                    }, new ErrorListener() {
                        @Override
                        public void onErrorResponse(AylaError aylaError) {
                            onAylaEroor(aylaError);
                        }
                    });
        }
    }

    private void bindingDevice(AylaSetupDevice response, String dsn, String setupToken) {
        final AylaRegistration aylaRegistration = mSessionManager.getDeviceManager().getAylaRegistration();
        if (aylaRegistration!=null){
            AylaRegistrationCandidate candidate = new AylaRegistrationCandidate();
            candidate.setDsn(dsn);
            candidate.setLanIp(response.getLanIp());
            candidate.setSetupToken(setupToken);
            if(response.getRegToken() != null){
                candidate.setRegistrationToken(response.getRegToken());
            }
            candidate.setRegistrationType(response.getRegistrationType());
            //绑定设备
            aylaRegistration.registerCandidate(candidate, new Response.Listener<AylaDevice>() {
                @Override
                public void onResponse(AylaDevice response) {
                    Log.d(TAG,"registerCandidate  success ....");
                    onAylaRequestSuccess();

                }
            }, new ErrorListener() {
                @Override
                public void onErrorResponse(AylaError aylaError) {

                    SystemClock.sleep(1000);

                    bindingDevice(response,dsn,setupToken);

                    if (reBindingNum == 60){
                        onAylaEroor(aylaError);
                    }
                    reBindingNum++;

                }
            });
        }
    }

    private void onAylaEroor(AylaError aylaError){
        Log.e(TAG,"aylaError  = " + aylaError.getMessage());
        ToastUtils.Toast_long(aylaError.getMessage());

        mSteplay.setVisibility(View.GONE);
        mSurebtn.setText("重试");
        mSurebtn.setBackgroundResource(R.drawable.select_reset);
        mSurebtn.setVisibility(View.VISIBLE);
    }

    private void onAylaRequestSuccess (){

        mCurrentProgress = mCurrentProgress + 20;
        mProgressView.setProgress(mCurrentProgress);
        mProgressTv.setText(mCurrentProgress+"%");

        if (mCurrentProgress == 100){
            mSteplay.setVisibility(View.GONE);
            mSurebtn.setVisibility(View.VISIBLE);
            isConnectSuccess = true;
        }

    }

    private String getRandomToken (){
        String strRand="" ;
        for(int i=0;i<8;i++){
            strRand += String.valueOf((int)(Math.random() * 10)) ;
        }

        Log.d(TAG,"Rand = "+strRand);
        SpUtils.savaUserInfo(Const.SETUP_TOKEN,strRand);

        return strRand;
    }
}
