package com.dream.onehome.ui.ViewModel;

import android.app.Application;

import androidx.annotation.NonNull;

import com.dream.onehome.base.BaseViewModel;
import com.dream.onehome.base.NoBaseBeanObserver;
import com.dream.onehome.bean.BrandBean;
import com.dream.onehome.common.Const;
import com.dream.onehome.constract.IResultLisrener;
import com.dream.onehome.http.NetWorkManager;

import java.util.List;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class BrandViewModel extends BaseViewModel {

    public BrandViewModel(@NonNull Application application) {
        super(application);
    }

    public void getBrandList(String deviceID, IResultLisrener<List<BrandBean>> lisrener){

        NetWorkManager.getRequest().getBrandList(Const.MAC,deviceID)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new NoBaseBeanObserver<List<BrandBean>>() {
                    @Override
                    public void onSuccess(List<BrandBean> results) {
                        lisrener.onResults(results);
                    }

                    @Override
                    public void onSubscribe(Disposable d) {

                    }
                });

    }
    public void getBrandList(String deviceID,String mcity, IResultLisrener<List<BrandBean>> lisrener){

        NetWorkManager.getRequest().getBrandList(Const.MAC,deviceID,mcity)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new NoBaseBeanObserver<List<BrandBean>>() {
                    @Override
                    public void onSuccess(List<BrandBean> results) {
                        lisrener.onResults(results);
                    }

                    @Override
                    public void onSubscribe(Disposable d) {

                    }
                });

    }

}
