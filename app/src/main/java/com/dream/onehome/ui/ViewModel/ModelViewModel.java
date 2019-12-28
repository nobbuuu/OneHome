package com.dream.onehome.ui.ViewModel;

import android.app.Application;

import androidx.annotation.NonNull;

import com.dream.onehome.base.BaseViewModel;
import com.dream.onehome.base.NoBaseBeanObserver;
import com.dream.onehome.bean.KeyIrCodeBean;
import com.dream.onehome.bean.KeysBean;
import com.dream.onehome.bean.ModelBean;
import com.dream.onehome.common.Const;
import com.dream.onehome.constract.IResultLisrener;
import com.dream.onehome.http.NetWorkManager;

import java.util.List;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/**
 * 没有viewModel的情况
 */
public class ModelViewModel extends BaseViewModel {

    public ModelViewModel(@NonNull Application application) {
        super(application);
    }

    public void getModellist (IResultLisrener<List<ModelBean>> lisrener,String device_id,String brand_id) {

        NetWorkManager.getRequest().getModellist(Const.MAC,device_id,brand_id)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new NoBaseBeanObserver<List<ModelBean>>() {
                    @Override
                    public void onSuccess(List<ModelBean> results) {
                        lisrener.onResults(results);
                    }

                    @Override
                    public void onSubscribe(Disposable d) {
                        compositeDisposable.add(d);
                    }
                });
    }

    public void getKeylist (String kfid,IResultLisrener<KeysBean> lisrener){
        NetWorkManager.getRequest().getKeyList(Const.MAC,kfid)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new NoBaseBeanObserver<KeysBean>() {
                    @Override
                    public void onSuccess(KeysBean results) {
                        lisrener.onResults(results);
                    }

                    @Override
                    public void onSubscribe(Disposable d) {
                        compositeDisposable.add(d);
                    }
                });
    }

    public void getKeyCode (String kfid,String keyid,IResultLisrener<KeyIrCodeBean> lisrener) {

        NetWorkManager.getRequest().getKeyCode(Const.MAC,kfid,keyid)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new NoBaseBeanObserver<KeyIrCodeBean>() {
                    @Override
                    public void onSuccess(KeyIrCodeBean results) {
                        lisrener.onResults(results);
                    }

                    @Override
                    public void onSubscribe(Disposable d) {
                        compositeDisposable.add(d);
                    }
                });

    }

}
