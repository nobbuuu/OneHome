package com.dream.onehome.adapter;

import android.content.Context;
import android.content.Intent;
import android.view.View;

import com.android.volley.Response;
import com.aylanetworks.aylasdk.AylaAPIRequest;
import com.aylanetworks.aylasdk.AylaDevice;
import com.aylanetworks.aylasdk.error.AylaError;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.dream.onehome.R;
import com.dream.onehome.base.RVBaseAdapter;
import com.dream.onehome.base.RVBaseHolder;
import com.dream.onehome.bean.BrandBean;
import com.dream.onehome.common.Const;
import com.dream.onehome.common.OneHomeAplication;
import com.dream.onehome.constract.IDialogLisrener;
import com.dream.onehome.dialog.DialogUtils;
import com.dream.onehome.ui.Activity.AirConditionActivity;
import com.dream.onehome.ui.Activity.MainCtrolerActivity;
import com.dream.onehome.ui.Activity.RemoteControlListActivity;
import com.dream.onehome.utils.LogUtils;
import com.dream.onehome.utils.SpUtils;
import com.dream.onehome.utils.StringUtils;

import java.io.Serializable;
import java.util.List;

/**
 * Time:2019/12/23
 * Author:TiaoZi
 */
public class DeviceAdapter extends RVBaseAdapter<AylaDevice> {

    private ILongClickListener mListener;
    public DeviceAdapter(Context context, List<AylaDevice> data, int layoutId) {
        super(context, data, layoutId);
    }

    @Override
    public void onBind(RVBaseHolder holder, AylaDevice deviceBean, int position) {
        holder.setText(R.id.rcname_tv,"万能遥控器"+(position+1));
        if (deviceBean.isOnline()){
            holder.setText(R.id.status_tv,"在线");
        }else {
            holder.setText(R.id.status_tv,"离线");
        }
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SpUtils.savaUserInfo(Const.DSN,deviceBean.getDsn());
                Intent intent = new Intent(context, RemoteControlListActivity.class);
                intent.putExtra(Const.DSN,deviceBean.getDsn());
                context.startActivity(intent);
            }
        });

        holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                mListener.onLongClick(deviceBean);
                return true;
            }
        });
    }

    public interface ILongClickListener{
        void onLongClick(AylaDevice device);
    }

    public void setLongListener(ILongClickListener longListener){
        mListener = longListener;
    }
}
