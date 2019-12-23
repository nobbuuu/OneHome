package com.dream.onehome.adapter;

import android.content.Context;

import androidx.databinding.ViewDataBinding;

import com.dream.onehome.R;
import com.dream.onehome.base.RVBaseAdapter;
import com.dream.onehome.base.RVBaseHolder;
import com.dream.onehome.bean.BrandBean;
import com.dream.onehome.bean.RemoteControlBean;
import com.dream.onehome.databinding.RvitemBrandBinding;
import com.dream.onehome.utils.StringUtils;

import java.util.List;

/**
 * Time:2019/12/23
 * Author:TiaoZi
 */
public class BrandAdapter extends RVBaseAdapter<BrandBean> {

    public BrandAdapter(Context context, List<BrandBean> data, int layoutId) {
        super(context, data, layoutId);
    }

    @Override
    public void onBind(RVBaseHolder holder, BrandBean brandBean, int position) {
        holder.setText(R.id.brandName_tv, StringUtils.decode(brandBean.getBn()));
    }
}
