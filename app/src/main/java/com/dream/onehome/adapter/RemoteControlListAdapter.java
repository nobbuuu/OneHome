package com.dream.onehome.adapter;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.android.volley.Response;
import com.aylanetworks.aylasdk.AylaAPIRequest;
import com.aylanetworks.aylasdk.AylaDatum;
import com.aylanetworks.aylasdk.AylaDevice;
import com.aylanetworks.aylasdk.AylaNetworks;
import com.aylanetworks.aylasdk.AylaSessionManager;
import com.aylanetworks.aylasdk.error.AylaError;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.dream.onehome.R;
import com.dream.onehome.base.NoDoubleClickListener;
import com.dream.onehome.base.RVBaseAdapter;
import com.dream.onehome.base.RVBaseHolder;
import com.dream.onehome.bean.RemoteControlBean;
import com.dream.onehome.common.Const;
import com.dream.onehome.constract.IDialogLisrener;
import com.dream.onehome.dialog.DialogUtils;
import com.dream.onehome.ui.Activity.AirConditionActivity;
import com.dream.onehome.ui.Activity.AirFilterActivity;
import com.dream.onehome.ui.Activity.CustomRemoteActivity;
import com.dream.onehome.ui.Activity.FanActivity;
import com.dream.onehome.ui.Activity.LampActivity;
import com.dream.onehome.ui.Activity.MainCtrolerActivity;
import com.dream.onehome.ui.Activity.SoundActivity;
import com.dream.onehome.ui.Activity.WaterHeaterActivity;
import com.dream.onehome.utils.LogUtils;
import com.dream.onehome.utils.SpUtils;
import com.dream.onehome.utils.ToastUtils;
import com.google.gson.Gson;

import java.util.List;

import static com.dream.onehome.dialog.DialogUtils.setDialogGravity;

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
        final String name = remoteControlBean.getName();
        if (name.equals("功放")){
            holder.setText(R.id.rmcName_tv, "音响");
        }else if (name.equals("IPTV")){
            holder.setText(R.id.rmcName_tv, "网络盒子");
        }else {
            holder.setText(R.id.rmcName_tv, name);
        }
        holder.setText(R.id.brandName_tv,remoteControlBean.getBrandName());
        int index = Integer.valueOf(remoteControlBean.getType());
        holder.setImageResource(R.id.rmcicon_iv,resId[index-1]);
        LogUtils.d(remoteControlBean.getKfid());
        holder.itemView.setOnClickListener(new NoDoubleClickListener() {
            @Override
            public void onNoDoubleClick(View view) {
                Intent intent = new Intent(context, MainCtrolerActivity.class);
                switch (remoteControlBean.getType()){
                    case "1":
                        intent = new Intent(context, AirConditionActivity.class);
                        break;
                    case "4":
                        break;
                    case "5":
                        intent = new Intent(context, FanActivity.class);
                        break;
                    case "6":
                        intent = new Intent(context, AirFilterActivity.class);
                        break;
                    case "9":
                        intent = new Intent(context, SoundActivity.class);
                        break;
                    case "10":
                        intent = new Intent(context, WaterHeaterActivity.class);
                        break;
                    case "11":
                        intent = new Intent(context, LampActivity.class);
                        break;
                    case "12":
                        intent = new Intent(context, CustomRemoteActivity.class);
                        break;
                }
                intent.putExtra(Const.kfid,remoteControlBean.getKfid());
                intent.putExtra(Const.device_id,remoteControlBean.getType());
                intent.putExtra(Const.deviceName, name);
                context.startActivity(intent);
            }
        });

        holder.setOnClickListener(R.id.edtname_iv, new NoDoubleClickListener() {
            @Override
            public void onNoDoubleClick(View view) {
                getEdtDialog(context,remoteControlBean).show();
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


    private EditText nameEdt;
    private Dialog getEdtDialog(Context activity,RemoteControlBean bean) {
        Dialog dialog = new Dialog(activity, R.style.ActionSheetDialogStyle);
        dialog.setContentView(R.layout.dialog_btnname);
        dialog.setCancelable(true);
        setDialogGravity(dialog, Gravity.CENTER,0.9);
        nameEdt = dialog.findViewById(R.id.name_edt);
        TextView cancelTv = dialog.findViewById(R.id.cancle_tv);
        TextView sureTv = dialog.findViewById(R.id.sure_tv);
        TextView nameTv = dialog.findViewById(R.id.name_tv);
        nameTv.setText("遥控器名称");
        nameEdt.setText(bean.getName());
        nameEdt.setSelection(bean.getName().length());
        sureTv.setText("确定");
        cancelTv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        sureTv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String newName = nameEdt.getText().toString();
                bean.setName(newName);
                aylaDevice.updateDatum(bean.getKfid(), new Gson().toJson(bean), new Response.Listener<AylaDatum>() {
                    @Override
                    public void onResponse(AylaDatum response) {
                        notifyDataSetChanged();
                        dialog.dismiss();
                    }
                }, new ErrorListener() {
                    @Override
                    public void onErrorResponse(AylaError aylaError) {
                        dialog.dismiss();
                        ToastUtils.Toast_long(aylaError.getMessage());
                    }
                });
            }
        });
        return dialog;
    }
}
