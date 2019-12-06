package com.dream.onehome.utils;

import android.app.Activity;
import android.graphics.drawable.BitmapDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.PopupWindow;

/**
 * Created by sunnetdesign3 on 2016/12/16.
 */
public class PopWindowUtil {
    public static void openPopWindow_Bottm(PopupWindow popupWindow, View contentView) {
        //底部显示
        popupWindow.showAtLocation(contentView, Gravity.BOTTOM, 0, 0);
    }

    public static void openPopWindow_Center(PopupWindow popupWindow, View contentView) {
        //底部显示
        popupWindow.showAtLocation(contentView, Gravity.CENTER, 0, 0);
    }

    public static void openPopWindow_Top(PopupWindow popupWindow, View contentView) {
        //顶部弹出显示
//        popupWindow.showAtLocation(contentView, Gravity.BOTTOM, 0, 0);
        popupWindow.showAtLocation(contentView, Gravity.TOP, 0, 0);
    }

    //屏幕亮度设置
    public static void setscreenDimmed(Activity activity, float num) {
        WindowManager.LayoutParams params = activity.getWindow().getAttributes();
        params.alpha = num;
        activity.getWindow().setAttributes(params);
    }

    public static void closePopwindow(PopupWindow popupWindow, View contentView) {
        if (popupWindow != null && popupWindow.isShowing()) {
            popupWindow.dismiss();
            popupWindow = null;
        }
    }

    /**
     * 设置添加屏幕的背景透明度
     **/
    public static void setBackgroundAlpaha(Activity context, float bgAlpha) {
        context.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams lp = context.getWindow().getAttributes();
        lp.alpha = bgAlpha;
        context.getWindow().setAttributes(lp);
    }

    public static PopupWindow getPopupWindow(final Activity activity, View contentView, int style,int width,int height) {

        //设置弹出框的宽度和高度
        final PopupWindow popupWindow = new PopupWindow(contentView, width,height,true);
        popupWindow.setFocusable(true);// 取得焦点
        //注意  要是点击外部空白处弹框消息  那么必须给弹框设置一个背景色  不然是不起作用的
        popupWindow.setBackgroundDrawable(new BitmapDrawable());
//        PopWindowUtil.backgroundAlpaha(activity, 0.5f);
        //设置可以点击
        popupWindow.setTouchable(true);
        popupWindow.setFocusable(true);
        //点击外部消失
        popupWindow.setOutsideTouchable(true);

        //进入退出的动画
        popupWindow.setAnimationStyle(style);
        return popupWindow;

    }
}
