package com.dream.onehome.ui.ViewModel;

import android.app.Application;

import androidx.annotation.NonNull;

import com.dream.onehome.base.BaseViewModel;
import com.dream.onehome.base.NoBaseBeanObserver;
import com.dream.onehome.bean.DeviceTypeBean;
import com.dream.onehome.constract.IResultLisrener;
import com.dream.onehome.http.NetWorkManager;

import java.util.List;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/**
 * 没有viewModel的情况
 */
public class CustomRemoteModel extends BaseViewModel {

    public CustomRemoteModel(@NonNull Application application) {
        super(application);
    }

    public void getDeviceTypes(IResultLisrener<List<DeviceTypeBean>> lisrener) {
        NetWorkManager.getRequest().getDeviceType()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new NoBaseBeanObserver<List<DeviceTypeBean>>() {
                    @Override
                    public void onSuccess(List<DeviceTypeBean> results) {
                        lisrener.onResults(results);
                    }

                    @Override
                    public void onSubscribe(Disposable d) {
                        compositeDisposable.add(d);
                    }
                });


    }

}
