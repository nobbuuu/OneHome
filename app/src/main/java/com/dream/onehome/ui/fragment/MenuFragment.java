package com.dream.onehome.ui.fragment;

import android.view.View;

import com.dream.onehome.R;
import com.dream.onehome.base.BaseFragment;
import com.dream.onehome.constract.IClickLisrener;

import butterknife.OnClick;

/**
 * Time:2019/12/24
 * Author:TiaoZi
 */
public class MenuFragment extends BaseFragment {

    private IClickLisrener mLisrener;

    @Override
    public void initView() {


    }

    @Override
    public int getLayoutId() {
        return R.layout.fragment_menu;
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

    public void setOnViewClickListener(IClickLisrener listener) {
        mLisrener = listener;
    }

    @OnClick({R.id.menu, R.id.home, R.id.sure, R.id.topiv, R.id.leftiv, R.id.rightiv, R.id.downiv})
    public void onViewClicked(View view) {
        mLisrener.onClick(view.getId());
    }
}
