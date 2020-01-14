package com.dream.onehome.common;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.multidex.MultiDex;
import androidx.multidex.MultiDexApplication;

import com.aylanetworks.aylasdk.AylaNetworks;
import com.aylanetworks.aylasdk.AylaSystemSettings;
import com.dream.onehome.http.NetWorkManager;
import com.dream.onehome.utils.AppUtils;
import com.dream.onehome.utils.ActivityUtils;
import com.dream.onehome.utils.SP;

public class OneHomeAplication extends MultiDexApplication implements Application.ActivityLifecycleCallbacks {

    private static OneHomeAplication mApplication;

    private ActivityUtils mActivityManager;
    @Override
    public void onCreate() {
        super.onCreate();
        mApplication = this;
        mActivityManager = ActivityUtils.getManager();
        AppUtils.init(this);
        SP.init(this);
        // 初始化MultiDex
        MultiDex.install(this);
        NetWorkManager.getInstance().init();

        //ayla init
        AylaSystemSettings aylaSystemSettings = new AylaSystemSettings();
        aylaSystemSettings.appId = Const.APP_ID;
        aylaSystemSettings.appSecret = Const.AYLA_SECRET;
        aylaSystemSettings.context = this;
        aylaSystemSettings.cloudProvider = AylaSystemSettings.CloudProvider.SUNSEA;
        aylaSystemSettings.serviceType = AylaSystemSettings.ServiceType.Development;
        aylaSystemSettings.serviceLocation = AylaSystemSettings.ServiceLocation.China;
        aylaSystemSettings.defaultNetworkTimeoutMs = aylaSystemSettings.defaultNetworkTimeoutMs*2;
        AylaNetworks.initialize(aylaSystemSettings);

        registerActivityLifecycleCallbacks(this);
        Log.d(getClass().getName(),"onCreate...");
    }

    public static OneHomeAplication getInstance (){
        if (mApplication == null){
            synchronized (OneHomeAplication.class){
                if (mApplication == null){
                    mApplication = new OneHomeAplication();
                }
            }
        }
        return mApplication;
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {

        mActivityManager.addActivity(activity);

    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {

    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {

    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {

    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {

    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {

    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {

        mActivityManager.finishActivity(activity,true);

    }
}
