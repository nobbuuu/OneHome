package com.dream.onehome.adapter;

import android.app.Activity;
import android.widget.ImageView;

import com.dream.onehome.R;
import com.dream.onehome.base.BaseViewHolder;
import com.dream.onehome.base.CommonAdapter;
import com.dream.onehome.bean.DeviceTypeBean;
import com.dream.onehome.databinding.GvitemDevicetypeBinding;
import com.dream.onehome.utils.StringUtils;

import java.util.List;

/**
 * Time:2019/12/21
 * Author:TiaoZi
 */
public class SelectDeviceTypeAdapter extends CommonAdapter<DeviceTypeBean> {
    private int [] resId = new int[]{R.drawable.airtiao,R.drawable.tv,R.drawable.box,R.drawable.dvd,R.drawable.fan,R.drawable.airpurifier,R.drawable.iptv,
            R.drawable.projector, R.drawable.speakers,R.drawable.waterheater,R.drawable.lightbulb,R.drawable.yaokongqi};
    public SelectDeviceTypeAdapter(Activity context, List<DeviceTypeBean> mDatas, int itemLayoutId) {
        super(context, mDatas, itemLayoutId);
    }

    @Override
    public void convert(BaseViewHolder holder, DeviceTypeBean deviceTypeBean, int position) {
        String name = StringUtils.decode(deviceTypeBean.getDevice_name());
        if (name.contains("IPTV")){
            holder.setText(R.id.typesName_tv, "网络盒子");
        }else if (name.contains("功放")){
            holder.setText(R.id.typesName_tv, "音响");
        }else {
            holder.setText(R.id.typesName_tv, name);
        }
        GvitemDevicetypeBinding bindingView = holder.getBindingView();

        int id = deviceTypeBean.getId();
        holder.setImageResource(R.id.type_iv,resId[id-1]);
        if (bindingView != null) {
            bindingView.setDeviceTypeBean(deviceTypeBean);
        }
    }
}
