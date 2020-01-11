package com.dream.onehome.ui.Activity;

import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;

import com.dream.onehome.R;
import com.dream.onehome.adapter.BrandAdapter;
import com.dream.onehome.base.BaseMVVMActivity;
import com.dream.onehome.bean.BrandBean;
import com.dream.onehome.bean.LatLng;
import com.dream.onehome.common.Const;
import com.dream.onehome.constract.IResultLisrener;
import com.dream.onehome.databinding.ActivitySelectbrandBinding;
import com.dream.onehome.ui.ViewModel.BrandViewModel;
import com.dream.onehome.utils.LocationUtil;
import com.dream.onehome.utils.LogUtils;
import com.dream.onehome.utils.SpUtils;
import com.dream.onehome.utils.ToastUtils;
import com.dream.onehome.utils.annotations.ContentView;

import java.util.List;
import java.util.Locale;

/**
 * Time:2019/12/23
 * Author:TiaoZi
 */
@ContentView(R.layout.activity_selectbrand)
public class BrandActivity extends BaseMVVMActivity<BrandViewModel, ActivitySelectbrandBinding> {

    private int device_id;
    private String deviceName;
    @Override
    protected void initIntent() {

        Intent intent = getIntent();
        device_id = intent.getIntExtra(Const.device_id,0);
        deviceName = intent.getStringExtra(Const.deviceName);

        String latitude = (String) SpUtils.getParam(Const.Latitude, "");
        String longitude = (String) SpUtils.getParam(Const.Longitude, "");

        if (!latitude.isEmpty()&& !longitude.isEmpty()){
            new CityName().execute(new LatLng(Double.valueOf(longitude),Double.valueOf(latitude)));
        }else {
            mLoadingDialog.show();
            LocationUtil.getCurrentLocation(this, new LocationUtil.LocationCallBack() {
                @Override
                public void onSuccess(Location location) {
                    LogUtils.d("location","Latitude = " + location.getLatitude() + ", Longitude = " + location.getLongitude());
                    SpUtils.savaUserInfo(Const.Latitude,String.valueOf(location.getLatitude()));
                    SpUtils.savaUserInfo(Const.Longitude,String.valueOf(location.getLongitude()));
                    new CityName().execute(new LatLng(location.getLongitude(),location.getLatitude()));
                }

                @Override
                public void onFail(String msg) {
                    ToastUtils.Toast_long(msg);
                }
            });
        }


    }

    private void loadData(String address) {
        if (device_id == 3){
            viewModel.getBrandList(String.valueOf(device_id),address, new IResultLisrener<List<BrandBean>>() {
                @Override
                public void onResults(List<BrandBean> data) {
                    BrandAdapter brandAdapter = new BrandAdapter(BrandActivity.this, data, R.layout.rvitem_brand);
                    brandAdapter.setDevice(String.valueOf(device_id),deviceName);
                    bindingView.brandRv.setAdapter(brandAdapter);
                    mLoadingDialog.dismiss();
                }
            });
        }else {
            viewModel.getBrandList(String.valueOf(device_id), new IResultLisrener<List<BrandBean>>() {
                @Override
                public void onResults(List<BrandBean> data) {
                    BrandAdapter brandAdapter = new BrandAdapter(BrandActivity.this, data, R.layout.rvitem_brand);
                    brandAdapter.setDevice(String.valueOf(device_id),deviceName);
                    bindingView.brandRv.setAdapter(brandAdapter);
                    mLoadingDialog.dismiss();
                }
            });
        }
    }

    // 获取地址信息
    private String getAddress(LatLng location) {
        String address = "昆明";
        List<Address> result = null;
        try {
            if (location != null) {
                Geocoder gc = new Geocoder(this, Locale.getDefault());
                result = gc.getFromLocation(location.getLatitude(),
                        location.getLongitude(), 1);
//                LogUtils.d("location","result = " + result.toString());
                if (result.size()>0){
                    address = result.get(0).getLocality();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        LogUtils.d("address","address = " + address);
        return address;
    }

    private class CityName extends AsyncTask<LatLng,Integer,String>{

        @Override
        protected String doInBackground(LatLng... locations) {
            return getAddress(locations[0]);
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            loadData(s);
        }
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
