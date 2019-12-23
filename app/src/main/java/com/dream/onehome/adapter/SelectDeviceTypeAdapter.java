package com.dream.onehome.adapter;

import android.app.Activity;
import android.content.Context;

import com.dream.onehome.R;
import com.dream.onehome.base.BaseViewHolder;
import com.dream.onehome.base.CommonAdapter;
import com.dream.onehome.base.RVBaseAdapter;
import com.dream.onehome.base.RVBaseHolder;
import com.dream.onehome.bean.DeviceTypeBean;
import com.dream.onehome.databinding.GvitemDevicetypeBinding;
import com.dream.onehome.utils.StringUtils;

import java.util.List;

/**
 * Time:2019/12/21
 * Author:TiaoZi
 */
public class SelectDeviceTypeAdapter extends CommonAdapter<DeviceTypeBean> {

    public SelectDeviceTypeAdapter(Activity context, List<DeviceTypeBean> mDatas, int itemLayoutId) {
        super(context, mDatas, itemLayoutId);
    }

    @Override
    public void convert(BaseViewHolder holder, DeviceTypeBean deviceTypeBean, int position) {
        holder.setText(R.id.typesName_tv,StringUtils.decode(deviceTypeBean.getDevice_name()));
        GvitemDevicetypeBinding bindingView = holder.getBindingView();
        if (bindingView != null){
            bindingView.setDeviceTypeBean(deviceTypeBean);
        }
    }
}
