package com.dream.onehome.base;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.ViewDataBinding;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.ViewModelProviders;

import com.dream.onehome.utils.ClassUtil;
import com.dream.onehome.utils.ToastUtils;
import com.dream.onehome.utils.annotations.InjectManager;

/**
 * "浪小白" 创建 2019/8/13.
 * 界面名称以及功能:
 */
public abstract class BaseMVVMFragment<VM extends AndroidViewModel, SV extends ViewDataBinding> extends Fragment {
    // ViewModel
    protected VM viewModel;
    // 布局view
    protected SV bindingView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        bindingView = DataBindingUtil.inflate(inflater, InjectManager.inject(this), null, false);
        return bindingView.getRoot();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        initViewModel();
        initView();
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
    protected void toast(String string){
        ToastUtils.Toast_long(string);
    }

    public abstract void initView();

}
