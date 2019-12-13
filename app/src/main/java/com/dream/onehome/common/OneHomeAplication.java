package com.dream.onehome.common;

import android.app.Application;
import android.util.Log;

import androidx.multidex.MultiDex;
import androidx.multidex.MultiDexApplication;

import com.aylanetworks.aylasdk.AylaNetworks;
import com.aylanetworks.aylasdk.AylaSystemSettings;
import com.dream.onehome.utils.AppUtils;

public class OneHomeAplication extends MultiDexApplication {

    private static OneHomeAplication mApplication;
    @Override
    public void onCreate() {
        super.onCreate();
        mApplication = this;
        AppUtils.init(this);
        // 初始化MultiDex
        MultiDex.install(this);
        AylaSystemSettings aylaSystemSettings = new AylaSystemSettings();
        aylaSystemSettings.appId = Const.APP_ID;
        aylaSystemSettings.appSecret = Const.AYLA_SECRET;
        aylaSystemSettings.context = this;
        AylaNetworks.initialize(aylaSystemSettings);
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
}
