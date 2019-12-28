package com.dream.onehome.adapter;

import android.content.Context;
import android.content.Intent;
import android.view.View;

import com.aylanetworks.aylasdk.AylaDevice;
import com.dream.onehome.R;
import com.dream.onehome.base.RVBaseAdapter;
import com.dream.onehome.base.RVBaseHolder;
import com.dream.onehome.bean.BrandBean;
import com.dream.onehome.common.Const;
import com.dream.onehome.ui.Activity.AirConditionActivity;
import com.dream.onehome.ui.Activity.MainCtrolerActivity;
import com.dream.onehome.ui.Activity.RemoteControlListActivity;
import com.dream.onehome.utils.SpUtils;
import com.dream.onehome.utils.StringUtils;

import java.util.List;

/**
 * Time:2019/12/23
 * Author:TiaoZi
 */
public class DeviceAdapter extends RVBaseAdapter<AylaDevice> {

    public DeviceAdapter(Context context, List<AylaDevice> data, int layoutId) {
        super(context, data, layoutId);
    }

    @Override
    public void onBind(RVBaseHolder holder, AylaDevice deviceBean, int position) {

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SpUtils.savaUserInfo(Const.DSN,deviceBean.getDsn());
                context.startActivity(new Intent(context, RemoteControlListActivity.class));
            }
        });
    }

}
