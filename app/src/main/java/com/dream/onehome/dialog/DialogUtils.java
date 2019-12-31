package com.dream.onehome.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.view.View;
import android.widget.TextView;

import com.dream.onehome.R;
import com.dream.onehome.bean.RemoteControlBean;
import com.dream.onehome.constract.IDialogLisrener;

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
}
