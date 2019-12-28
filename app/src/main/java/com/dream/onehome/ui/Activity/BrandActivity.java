package com.dream.onehome.ui.Activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.dream.onehome.R;
import com.dream.onehome.adapter.BrandAdapter;
import com.dream.onehome.base.BaseMVVMActivity;
import com.dream.onehome.bean.BrandBean;
import com.dream.onehome.constract.IResultLisrener;
import com.dream.onehome.databinding.ActivitySelectbrandBinding;
import com.dream.onehome.ui.ViewModel.BrandViewModel;
import com.dream.onehome.utils.annotations.ContentView;

import java.util.List;

/**
 * Time:2019/12/23
 * Author:TiaoZi
 */
@ContentView(R.layout.activity_selectbrand)
public class BrandActivity extends BaseMVVMActivity<BrandViewModel, ActivitySelectbrandBinding> {

    @Override
    protected void initIntent() {

        Intent intent = getIntent();
        int device_id = intent.getIntExtra("device_id",0);

        viewModel.getBrandList(String.valueOf(device_id), new IResultLisrener<List<BrandBean>>() {
            @Override
            public void onResults(List<BrandBean> data) {
                BrandAdapter brandAdapter = new BrandAdapter(BrandActivity.this, data, R.layout.rvitem_brand);
                brandAdapter.setDeviceId(String.valueOf(device_id));
                bindingView.brandRv.setAdapter(brandAdapter);
            }
        });

    }

    @Override
    protected void onEvent() {

        bindingView.backIv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

    }

    @Override
    protected void initView(Bundle savedInstanceState) {

    }
}
