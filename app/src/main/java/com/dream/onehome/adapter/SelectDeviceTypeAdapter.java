package com.dream.onehome.adapter;

import android.content.Context;

import com.dream.onehome.base.RVBaseAdapter;
import com.dream.onehome.base.RVBaseHolder;
import com.dream.onehome.bean.DeviceTypeBean;
import com.dream.onehome.databinding.GvitemDevicetypeBinding;

import java.util.List;

/**
 * Time:2019/12/21
 * Author:TiaoZi
 */
public class SelectDeviceTypeAdapter extends RVBaseAdapter<DeviceTypeBean> {

    public SelectDeviceTypeAdapter(Context context, List<DeviceTypeBean> data, int layoutId) {
        super(context, data, layoutId);
    }

    @Override
    public void onBind(RVBaseHolder holder, DeviceTypeBean deviceTypeBean, int position) {
        GvitemDevicetypeBinding bindingView = holder.getBindingView();
        if (bindingView != null){
            bindingView.setDeviceTypeBean(deviceTypeBean);
        }
    }
}
