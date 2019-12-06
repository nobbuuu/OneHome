package com.dream.onehome.utils;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.WindowManager;

import com.dream.onehome.common.OneHomeAplication;


/**
 * Created by Administrator on 2017/4/12.
 */
public class DensityUtil {


    public static int dip2px(Context context, float dpValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }


    public static int px2dip(Context context, float pxValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        Log.e("tag","density="+scale);
        return (int) (pxValue / scale + 0.5f);
    }
    public static int sp2px(Context context, float dpValue) {
        final float scale = context.getResources().getDisplayMetrics().scaledDensity;
        return (int) (dpValue * scale + 0.5f);
    }


    public static int px2sp(Context context, float pxValue) {
        final float scale = context.getResources().getDisplayMetrics().scaledDensity;
        Log.e("tag","scaledDensity="+scale);
        return (int) (pxValue / scale + 0.5f);
    }
    public static int getScreenWidth(Activity context) {
        WindowManager wm = context.getWindowManager();
        int width = wm.getDefaultDisplay().getWidth();
        return width;
    }
    public static int getScreenHeight(Activity context) {
        WindowManager wm = context.getWindowManager();
        int height = wm.getDefaultDisplay().getHeight();
        return height;
    }

    public static int getScreenWidth(){
        WindowManager wm = (WindowManager) OneHomeAplication.getInstance().getSystemService(Context.WINDOW_SERVICE);
        return wm.getDefaultDisplay().getWidth();
    }
    public static int getScreenHeight(){
        WindowManager wm = (WindowManager) OneHomeAplication.getInstance().getSystemService(Context.WINDOW_SERVICE);
        return wm.getDefaultDisplay().getHeight();
    }


}

