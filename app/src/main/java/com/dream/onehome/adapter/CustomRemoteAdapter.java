package com.dream.onehome.adapter;

import android.app.Activity;
import android.view.View;

import com.dream.onehome.R;
import com.dream.onehome.base.BaseViewHolder;
import com.dream.onehome.base.CommonAdapter;
import com.dream.onehome.bean.CustomItemBean;
import com.dream.onehome.constract.IGvItemClickLisrener;
import com.dream.onehome.listener.NoDoubleClickListener;

import java.util.List;

/**
 * Time:2020/02/29
 * Author:TiaoZi
 */
public class CustomRemoteAdapter extends CommonAdapter<CustomItemBean> {

    private IGvItemClickLisrener<CustomItemBean> mLisrener;
    public CustomRemoteAdapter(Activity context, List<CustomItemBean> mDatas, int itemLayoutId) {
        super(context, mDatas, itemLayoutId);
    }

    @Override
    public void convert(BaseViewHolder holder, CustomItemBean customItemBean, int position) {
        holder.setText(R.id.keyname_tv, customItemBean.getKeyName());
        holder.setOnClickListener(R.id.keyname_tv, new NoDoubleClickListener() {
            @Override
            public void onNoDoubleClick(View view) {
                mLisrener.onItemClick(customItemBean,position);
            }
        });
    }

    public void setOnIGvItemListener(IGvItemClickLisrener<CustomItemBean> listener){
        mLisrener = listener;
    }

}
