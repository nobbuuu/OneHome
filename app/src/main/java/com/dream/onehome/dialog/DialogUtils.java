package com.dream.onehome.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import com.dream.onehome.R;
import com.dream.onehome.adapter.ExtGvAdapter;
import com.dream.onehome.constract.IDialogLisrener;

import java.util.List;

/**
 * Time:2019/12/13
 * Author:TiaoZi
 */
public class DialogUtils {

    public static Dialog initLoadingDialog(Context activity) {
        Dialog dialog = new Dialog(activity);
        dialog.setContentView(R.layout.dialog_loading);
        dialog.setCancelable(true);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        TextView msg = (TextView) dialog.findViewById(R.id.id_tv_loadingmsg);
        msg.setText("Loading...");
        return dialog;
    }

    public static Dialog getDeleteDialog(Context activity, IDialogLisrener lisrener) {
        Dialog dialog = new Dialog(activity,R.style.ActionSheetDialogStyle);
        dialog.setContentView(R.layout.dialog_tip);
        dialog.setCancelable(true);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        TextView cancelTv = dialog.findViewById(R.id.cancle_tv);
        TextView sureTv = dialog.findViewById(R.id.sure_tv);
        cancelTv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                lisrener.onCancel();
                dialog.dismiss();
            }
        });

        sureTv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                lisrener.onSure();
                dialog.dismiss();
            }
        });
        return dialog;
    }

    public static Dialog getExtDialog(Activity activity, List<String> keyList){
        Dialog dialog = new Dialog(activity, R.style.ActionSheetDialogStyle);
        dialog.setContentView(R.layout.dialog_extention);
        dialog.setCancelable(false);
        ImageView closeIv = dialog.findViewById(R.id.closeiv);
        GridView extGv = dialog.findViewById(R.id.extention_gv);
        extGv.setAdapter(new ExtGvAdapter(activity,keyList,R.layout.rvitem_extention));
        closeIv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        extGv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

            }
        });
        setDialogGravity(dialog, Gravity.BOTTOM,0.99);
        return dialog;
    }

    private static void setDialogGravity(Dialog dialog, int gravity, double percentParent) {
        // 设置宽度为屏宽、靠近屏幕底部。
        Window window = dialog.getWindow();
        window.setBackgroundDrawableResource(android.R.color.transparent);
        WindowManager.LayoutParams wlp = window.getAttributes();
        Display d = window.getWindowManager().getDefaultDisplay();//获取屏幕宽
        wlp.width = (int) (d.getWidth()*percentParent);//宽度按屏幕大小的百分比设置
        wlp.y = 50; //如果是底部显示，则距离底部的距离是20
        wlp.gravity = gravity;
        window.setAttributes(wlp);
    }
}
