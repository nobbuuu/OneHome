package com.dream.onehome.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.widget.TextView;

import com.dream.onehome.R;

/**
 * Time:2019/12/13
 * Author:TiaoZi
 */
public class LoadingDialog {

    public static Dialog initLoadingDialog(Activity activity) {
        Dialog dialog = new Dialog(activity);
        dialog.setContentView(R.layout.dialog_loading);
        dialog.setCancelable(true);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        TextView msg = (TextView) dialog.findViewById(R.id.id_tv_loadingmsg);
        msg.setText("Loading...");
        return dialog;
    }
}
