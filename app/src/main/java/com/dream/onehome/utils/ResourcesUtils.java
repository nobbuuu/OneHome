package com.dream.onehome.utils;

import android.graphics.drawable.Drawable;

import com.dream.onehome.common.OneHomeAplication;


/**
 * Created by Administrator on 2017/8/7 0007.
 */
public class ResourcesUtils {

    public static int getColor(int resId){
        return OneHomeAplication.getInstance().getResources().getColor(resId);
    }
    public static Drawable getDrable(int resId){
        return OneHomeAplication.getInstance().getResources().getDrawable(resId);
    }
    public static String getString(int resId){
        return OneHomeAplication.getInstance().getResources().getString(resId);
    }
}
