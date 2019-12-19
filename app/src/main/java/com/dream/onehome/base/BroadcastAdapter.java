package com.dream.onehome.base;

import android.content.Context;

import com.dream.onehome.bean.EmptyBean;

import java.util.List;

public class BroadcastAdapter extends RVBaseAdapter<EmptyBean> {

    public BroadcastAdapter(Context context, List<EmptyBean> data, int layoutId) {
        super(context, data, layoutId);
    }

    @Override
    public void onBind(RVBaseHolder holder, EmptyBean baseBean, int position) {

    }
}
