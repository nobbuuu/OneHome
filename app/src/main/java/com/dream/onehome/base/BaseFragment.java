package com.dream.onehome.base;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.dream.onehome.dialog.DialogUtils;

import butterknife.ButterKnife;
import butterknife.Unbinder;

/**
 * Created by Administrator on 2016/8/10 0010.
 */
public abstract class BaseFragment extends Fragment {
    protected View rootView;
    public Bundle savedInstanceState;
    public Dialog mLoadingDialog;
    private static final int PERMISSION_REQUESTCODE = 100;
    private Unbinder bind;
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        this.savedInstanceState = savedInstanceState;
        View inflate = inflater.inflate(getLayoutId(), container, false);
        return inflate;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        rootView = view;
        bind = ButterKnife.bind(this, rootView);
        mLoadingDialog = DialogUtils.initLoadingDialog(getContext());
       /* BaseComponent build = DaggerBaseComponent.builder()
                .appComponent(OneHomeAplication.getInstance().getAppComponent())
                .build();*/
//        initDaggerView(build);
        initView();
        onEvents();
        initDta();
    }

    @Override
    public void onResume() {
        super.onResume();
        resume();
    }

    public abstract void initView();
    public abstract int getLayoutId();
    public abstract void resume();

    public abstract void onEvents();

    public abstract void initDta();

//    public abstract void initDaggerView(BaseComponent build);

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (bind != null) {
            bind.unbind();
        }
    }
}
