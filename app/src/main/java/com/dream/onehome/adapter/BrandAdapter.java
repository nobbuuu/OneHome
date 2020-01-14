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
import com.dream.onehome.common.Const;
import com.dream.onehome.databinding.RvitemBrandBinding;
import com.dream.onehome.ui.Activity.AirConditionActivity;
import com.dream.onehome.ui.Activity.AirFilterActivity;
import com.dream.onehome.ui.Activity.FanActivity;
import com.dream.onehome.ui.Activity.LampActivity;
import com.dream.onehome.ui.Activity.MainCtrolerActivity;
import com.dream.onehome.ui.Activity.SoundActivity;
import com.dream.onehome.ui.Activity.WaterHeaterActivity;
import com.dream.onehome.utils.StringUtils;

import java.util.List;

/**
 * Time:2019/12/23
 * Author:TiaoZi
 */
public class BrandAdapter extends RVBaseAdapter<BrandBean> {

    private String deviceId;
    private String deviceName;

    public BrandAdapter(Context context, List<BrandBean> data, int layoutId) {
        super(context, data, layoutId);
    }

    @Override
    public void onBind(RVBaseHolder holder, BrandBean brandBean, int position) {
        final String brandName = StringUtils.decode(brandBean.getBn());
        holder.setText(R.id.brandName_tv, brandName);
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(context, MainCtrolerActivity.class);
                switch (deviceId){
                    case "1":
                        intent = new Intent(context, AirConditionActivity.class);
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
                }
                intent.putExtra(Const.brand_id,String.valueOf(brandBean.getId()));
                intent.putExtra(Const.device_id,deviceId);
                intent.putExtra(Const.deviceName,deviceName);
                intent.putExtra(Const.brandName, brandName);
                context.startActivity(intent);
            }
        });
    }

    public void setDevice (String deviceId,String deviceName){
        this.deviceId = deviceId;
        this.deviceName = deviceName;
    }
}
