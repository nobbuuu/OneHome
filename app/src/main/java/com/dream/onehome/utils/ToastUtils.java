package com.dream.onehome.utils;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.widget.Toast;

import com.dream.onehome.common.OneHomeAplication;


/**
 * Created by sunnetdesign3 on 2017/2/10.
 */
public class ToastUtils {
    private static Toast mToast;
    private static Handler mHandler = new Handler();
    private static Runnable r = new Runnable() {
        public void run() {
            mToast.cancel();
        }
    };
    public static void Toast_short(final Activity context, final String content){
        if("main".equals(Thread.currentThread().getName())){
            Toast.makeText(context, content, Toast.LENGTH_SHORT).show();
        }else {
            context.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context, content, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
    public static void Toast_long(final Activity context, final String content){
        if("main".equals(Thread.currentThread().getName())){
            Toast.makeText(context, content, Toast.LENGTH_LONG).show();
        }else {
            context.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context, content, Toast.LENGTH_LONG).show();
                }
            });
        }
    }
    public static void Toast_short(String content){

        mHandler.removeCallbacks(r);
        if (mToast != null)
            mToast.setText(content);
        else
            mToast = Toast.makeText(OneHomeAplication.getInstance(), content, Toast.LENGTH_SHORT);
        mHandler.postDelayed(r, 1000);

        mToast.show();
    }
    public static void Toast_long(Context context,String content){
        Toast.makeText(OneHomeAplication.getInstance(),content,Toast.LENGTH_LONG).show();
    }

    public static void Toast_long(String content){
        mHandler.removeCallbacks(r);
        if (mToast != null)
            mToast.setText(content);
        else
            mToast = Toast.makeText(OneHomeAplication.getInstance(), content, Toast.LENGTH_LONG);
        mHandler.postDelayed(r, 1000);

        mToast.show();
    }
}
