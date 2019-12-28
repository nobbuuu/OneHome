package com.dream.onehome.adapter;

import android.content.Context;
import android.content.Intent;
import android.view.View;

import androidx.databinding.ViewDataBinding;

import com.dream.onehome.R;
import com.dream.onehome.base.RVBaseAdapter;
import com.dream.onehome.base.RVBaseHolder;
import com.dream.onehome.bean.BrandBean;
import com.dream.onehome.bean.RemoteControlBean;
import com.dream.onehome.databinding.RvitemBrandBinding;
import com.dream.onehome.ui.Activity.AirConditionActivity;
import com.dream.onehome.ui.Activity.MainCtrolerActivity;
import com.dream.onehome.utils.StringUtils;

import java.util.List;

/**
 * Time:2019/12/23
 * Author:TiaoZi
 */
public class BrandAdapter extends RVBaseAdapter<BrandBean> {

    private String deviceId;

    public BrandAdapter(Context context, List<BrandBean> data, int layoutId) {
        super(context, data, layoutId);
    }

    @Override
    public void onBind(RVBaseHolder holder, BrandBean brandBean, int position) {
        holder.setText(R.id.brandName_tv, StringUtils.decode(brandBean.getBn()));
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(context, MainCtrolerActivity.class);
                switch (deviceId){
                    case "1":
                        intent = new Intent(context, AirConditionActivity.class);
                        break;
                    case "2":
                        intent = new Intent(context, MainCtrolerActivity.class);
                        break;
                }
                intent.putExtra("brand_id",String.valueOf(brandBean.getId()));
                intent.putExtra("device_id",deviceId);
                context.startActivity(intent);
            }
        });
    }

    public void setDeviceId (String deviceId){
        this.deviceId = deviceId;
    }
}
