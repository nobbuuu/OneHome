package com.dream.onehome.ui.fragment;

import android.util.Log;
import android.widget.GridView;

import com.dream.onehome.R;
import com.dream.onehome.adapter.ExtGvAdapter;
import com.dream.onehome.base.BaseFragment;
import com.dream.onehome.bean.KeysBean;

import java.util.List;

import butterknife.BindView;

/**
 * Time:2020/02/19
 * Author:TiaoZi
 */
public class ExtensionFragment extends BaseFragment {

    @BindView(R.id.extention_rv)
    GridView extentionRv;

    private ExtGvAdapter mExtRvAdapter;
    @Override
    public void initView() {
        Log.d("axiba","extentionRv = "+ extentionRv);
    }

    public ExtensionFragment(){

    }

    @Override
    public int getLayoutId() {
        return R.layout.fragment_extention;
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

    public void setData(KeysBean extBean){
        List<String> keylist = extBean.getKeylist();
        mExtRvAdapter = new ExtGvAdapter(getActivity(),keylist,R.layout.rvitem_extention);
        extentionRv.setAdapter(mExtRvAdapter);
    }

}
