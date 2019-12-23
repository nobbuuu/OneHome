package com.dream.onehome.adapter;

import android.content.Context;

import com.dream.onehome.base.RVBaseAdapter;
import com.dream.onehome.base.RVBaseHolder;
import com.dream.onehome.bean.RemoteControlBean;

import java.util.List;

/**
 * Time:2019/12/23
 * Author:TiaoZi
 */
public class RemoteControlListAdapter extends RVBaseAdapter<RemoteControlBean> {

    public RemoteControlListAdapter(Context context, List<RemoteControlBean> data, int layoutId) {
        super(context, data, layoutId);
    }

    @Override
    public void onBind(RVBaseHolder holder, RemoteControlBean remoteControlBean, int position) {

    }
}
