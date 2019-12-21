package com.dream.onehome.ui.ViewModel;

import android.app.Application;

import androidx.annotation.NonNull;

import com.dream.onehome.base.BaseObserver;
import com.dream.onehome.base.BaseViewModel;
import com.dream.onehome.bean.DeviceTypeBean;
import com.dream.onehome.databinding.ActivitySelectDevicetypeBinding;
import com.dream.onehome.http.NetWorkManager;

import java.util.List;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/**
 * 没有viewModel的情况
 */
public class SelectDeviceTypeModel extends BaseViewModel {

    public SelectDeviceTypeModel(@NonNull Application application) {
        super(application);
    }

    public void getDeviceTypes(ActivitySelectDevicetypeBinding binding){
        NetWorkManager.getRequest().getDeviceType()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new BaseObserver<List<DeviceTypeBean>>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        compositeDisposable.add(d);
                    }

                    @Override
                    public void onSuccess(List<DeviceTypeBean> results) {
                        binding.typeGv.getAdapter().notify();
                    }
                });


    }

}
