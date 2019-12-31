package com.dream.onehome.ui.fragment;

import android.view.View;

import com.dream.onehome.R;
import com.dream.onehome.base.BaseFragment;

import butterknife.OnClick;

/**
 * Time:2019/12/24
 * Author:TiaoZi
 */
public class NumberFragment extends BaseFragment {

    private onViewClickListener mListener;

    @Override
    public void initView() {

    }

    @Override
    public int getLayoutId() {
        return R.layout.fragment_number;
    }

    @Override
    public void resume() {

    }

    @Override
    public void onEvents() {

    }

    @Override
    public void initDta() {

    }


    @OnClick({R.id.one, R.id.two, R.id.three, R.id.four, R.id.five, R.id.six, R.id.seven, R.id.eight, R.id.night, R.id.zaro,R.id.collect_iv,R.id.return_iv})
    public void onViewClicked(View view) {
        int id = view.getId();
        mListener.onClick(id);
    }

    public interface onViewClickListener {
        void onClick(int viewId);
    }

    public void setOnViewClickListener(onViewClickListener listener) {
        mListener = listener;
    }
}
