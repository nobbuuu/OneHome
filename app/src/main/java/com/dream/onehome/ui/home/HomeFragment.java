package com.dream.onehome.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;

import com.dream.onehome.R;
import com.dream.onehome.base.BaseFragment;
import com.dream.onehome.ui.Activity.AddDeviceActivity;
import com.dream.onehome.ui.Activity.ConnectDeviceActivity;
import com.dream.onehome.ui.Activity.WifiSetActivity;

import butterknife.BindView;
import butterknife.OnClick;

public class HomeFragment extends BaseFragment {

    @BindView(R.id.addimg_iv)
    ImageView addimgIv;
    private HomeViewModel homeViewModel;

    @Override
    public void initView() {

    }

    @Override
    public int getLayoutId() {
        return R.layout.fragment_home;
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


    @OnClick(R.id.addimg_iv)
    public void onViewClicked() {
        Intent intent = new Intent(getActivity(), WifiSetActivity.class);
        startActivity(intent);
    }
}