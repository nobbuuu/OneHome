package com.dream.onehome.base;

import android.app.Dialog;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.ViewDataBinding;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.ViewModelProviders;

import com.dream.onehome.dialog.DialogUtils;
import com.dream.onehome.utils.ClassUtil;
import com.dream.onehome.utils.ToastUtils;
import com.dream.onehome.utils.annotations.InjectManager;
import com.gyf.immersionbar.BarHide;
import com.gyf.immersionbar.ImmersionBar;

/**
 * "浪小白" 创建 2019/8/13.
 * 界面名称以及功能:
 */
public abstract class BaseMVVMActivity<VM extends AndroidViewModel, SV extends ViewDataBinding> extends AppCompatActivity {
    // ViewModel
    protected VM viewModel;
    // 布局view
    protected SV bindingView;

    public Dialog mLoadingDialog;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().hide();
        mLoadingDialog = DialogUtils.initLoadingDialog(this);
        bindingView = DataBindingUtil.setContentView(this, InjectManager.inject(this));
        //沉浸式
        /*if (ImmersionBar.hasNotchScreen(this)) {//如果有刘海屏则让布局不与状态栏重合，如果没有刘海屏则全屏布局
            ImmersionBar.with(this).statusBarDarkFont(true).hideBar(BarHide.FLAG_HIDE_NAVIGATION_BAR).fitsSystemWindows(true).statusBarDarkFont(true).keyboardEnable(true).init();
        } else {
            ImmersionBar.with(this).statusBarDarkFont(true).hideBar(BarHide.FLAG_HIDE_NAVIGATION_BAR).keyboardEnable(true).init();
        }*/
        initViewModel();
        initIntent();
        initView(savedInstanceState);
        onEvent();

    }

    /**
     * 初始化ViewModel
     */
    private void initViewModel() {
        Class<VM> viewModelClass = ClassUtil.getViewModel(this);
        if (viewModelClass != null) {
            this.viewModel = ViewModelProviders.of(this).get(viewModelClass);
        }
    }

    protected void toast(String string) {
        ToastUtils.Toast_long(string);
    }

    //初始化获取Intent数据
    protected abstract void initIntent();

    protected abstract void onEvent();

    //初始化视图
    protected abstract void initView(Bundle savedInstanceState);

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bindingView != null) {//解除Databinding订阅
            bindingView.unbind();
            bindingView = null;
        }
    }

    /**
     * 重置App界面的字体大小，fontScale 值为 1 代表默认字体大小
     *
     * @return 重置继承该activity子类的文字大小，使它不受系统字体大小限制
     */
    @Override
    public Resources getResources() {
        Resources res = super.getResources();
        Configuration config = res.getConfiguration();
        config.fontScale = 1;
        res.updateConfiguration(config, res.getDisplayMetrics());
        return res;
    }

}
