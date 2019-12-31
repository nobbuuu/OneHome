package com.dream.onehome.base;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.dream.onehome.dialog.DialogUtils;
import com.dream.onehome.ui.ViewModel.ModelViewModel;

import butterknife.ButterKnife;
import butterknife.Unbinder;

/**
 * Created by Administrator on 2017/4/7/007.
 * 遥控器父类
 */
public abstract class BaseMasterActivity extends AppCompatActivity {

    public Activity mActivity;
    public Context mContext;
    private Unbinder mUnbinder;
    public Dialog mLoading;

    public ModelViewModel viewModel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().hide();
        initIntent();
        setContentView(getLayoutId());
        mUnbinder = ButterKnife.bind(this);
        this.mActivity = this;
        this.mContext = this;
        viewModel = new ModelViewModel(getApplication());
        initView();
        loadDatas();
        eventListener();
        mLoading = DialogUtils.initLoadingDialog(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        OnResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mUnbinder.unbind();
    }

    public abstract int getLayoutId();

    protected abstract void initIntent();
    public abstract void initView();
    public abstract void OnResume();
    public abstract void loadDatas();

    public abstract void eventListener();


}
