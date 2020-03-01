package com.dream.onehome.adapter;

import android.app.Activity;

import com.dream.onehome.R;
import com.dream.onehome.base.BaseViewHolder;
import com.dream.onehome.base.CommonAdapter;

import java.util.List;

public class ExtRvAdapter extends CommonAdapter<String> {

        public ExtRvAdapter(Activity context, List<String> mDatas, int itemLayoutId) {
            super(context, mDatas, itemLayoutId);
        }

        @Override
        public void convert(BaseViewHolder holder, String s, int position) {
            holder.setText(R.id.extname_tv,s);
        }
    }