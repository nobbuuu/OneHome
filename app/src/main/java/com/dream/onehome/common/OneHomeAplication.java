package com.dream.onehome.common;

import android.app.Application;

import com.dream.onehome.utils.AppUtils;

public class OneHomeAplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        getInstance();
        AppUtils.init(this);
    }

    private static OneHomeAplication mApplication;

    private OneHomeAplication() {

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
