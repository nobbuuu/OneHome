package com.dream.onehome.adapter;

import android.content.Context;
import android.content.Intent;
import android.view.View;

import com.android.volley.Response;
import com.aylanetworks.aylasdk.AylaAPIRequest;
import com.aylanetworks.aylasdk.AylaDevice;
import com.aylanetworks.aylasdk.AylaNetworks;
import com.aylanetworks.aylasdk.AylaSessionManager;
import com.aylanetworks.aylasdk.error.AylaError;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.dream.onehome.R;
import com.dream.onehome.base.RVBaseAdapter;
import com.dream.onehome.base.RVBaseHolder;
import com.dream.onehome.bean.RemoteControlBean;
import com.dream.onehome.common.Const;
import com.dream.onehome.constract.IDialogLisrener;
import com.dream.onehome.dialog.DialogUtils;
import com.dream.onehome.ui.Activity.AirConditionActivity;
import com.dream.onehome.ui.Activity.MainCtrolerActivity;
import com.dream.onehome.utils.LogUtils;
import com.dream.onehome.utils.SpUtils;

import java.util.List;

/**
 * Time:2019/12/23
 * Author:TiaoZi
 */
public class RemoteControlListAdapter extends RVBaseAdapter<RemoteControlBean> {

    private int [] resId = new int[]{R.drawable.airtiao,R.drawable.tv,R.drawable.box,R.drawable.dvd,R.drawable.fan,R.drawable.airpurifier,R.drawable.iptv,
            R.drawable.projector, R.drawable.speakers,R.drawable.waterheater,R.drawable.lightbulb,R.drawable.socket,R.drawable.sweeper};
    private AylaDevice aylaDevice;
    private IActionListener mListener;
    public RemoteControlListAdapter(Context context, List<RemoteControlBean> data, int layoutId) {
        super(context, data, layoutId);
        AylaSessionManager sessionManager = AylaNetworks.sharedInstance().getSessionManager(Const.APP_NAME);
        if (sessionManager != null){
            String dsn = (String) SpUtils.getParam(Const.DSN, "");
            if (!dsn.isEmpty()){
                aylaDevice = sessionManager.getDeviceManager().deviceWithDSN(dsn);
            }
        }
    }

    @Override
    public void onBind(RVBaseHolder holder, RemoteControlBean remoteControlBean, int position) {
        holder.setText(R.id.rmcName_tv,remoteControlBean.getName());
        holder.setText(R.id.brandName_tv,remoteControlBean.getBrandName());
        int index = Integer.valueOf(remoteControlBean.getType());
        holder.setImageResource(R.id.rmcicon_iv,resId[index-1]);
        LogUtils.d(remoteControlBean.getKfid());
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(context, AirConditionActivity.class);
                switch (remoteControlBean.getType()){
                    case "1":
                        intent = new Intent(context, AirConditionActivity.class);
                        break;
                    case "2":
                        intent = new Intent(context, MainCtrolerActivity.class);
                        break;
                }
                intent.putExtra(Const.kfid,remoteControlBean.getKfid());
                context.startActivity(intent);
            }
        });

        holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                DialogUtils.getDeleteDialog(context, new IDialogLisrener() {
                    @Override
                    public void onCancel() {}

                    @Override
                    public void onSure() {
                        aylaDevice.deleteDatum(remoteControlBean.getKfid(), new Response.Listener<AylaAPIRequest.EmptyResponse>() {
                            @Override
                            public void onResponse(AylaAPIRequest.EmptyResponse response) {
                                LogUtils.d("删除成功");
                                mListener.onResult();
                            }
                        }, new ErrorListener() {
                            @Override
                            public void onErrorResponse(AylaError aylaError) {
                                LogUtils.e(aylaError.getMessage());
                            }
                        });
                    }
                }).show();
                return false;
            }
        });
    }

    public interface IActionListener {
        void onResult();
    }

    public void setDeleteListener(IActionListener listener){
        mListener = listener;
    }

}
