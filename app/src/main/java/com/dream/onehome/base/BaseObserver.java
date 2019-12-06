package com.dream.onehome.base;


import com.dream.onehome.R;
import com.dream.onehome.common.Const;
import com.dream.onehome.utils.ResourcesUtils;
import com.dream.onehome.utils.ToastUtils;

import io.reactivex.Observer;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;

/**
 * 响应实体类型result为对象类型
 * Created by Administrator on 2017/12/13 0013.
 */

public abstract class BaseObserver<T> implements Observer<BaseBean<T>> {

    private BaseContract.BaseView mBaseView;
    protected CompositeDisposable mCompositeSubscription;

    protected void unSubscribe() {
        if (mCompositeSubscription != null) {
            mCompositeSubscription.dispose();
        }
    }

    protected void addSubscrebe(Disposable disposable) {
        if (mCompositeSubscription == null) {
            mCompositeSubscription = new CompositeDisposable();
        }
        mCompositeSubscription.add(disposable);
    }

    @Override
    public void onSubscribe(Disposable d) {
        addSubscrebe(d);
    }

    public BaseObserver(BaseContract.BaseView baseView) {
        mBaseView = baseView;
    }

    @Override
    public void onNext(BaseBean<T> tBaseBean) {
        if (tBaseBean!=null){
            if (tBaseBean.getErrorCode().equals(Const.SUCCESS)) {
                T results = tBaseBean.getResults();
                onSuccess(results);
            } else {
//            ToastUtils.Toast_short(tBaseBean.getErrorMsg());
                String errorCode = tBaseBean.getErrorCode();
                String errorMsg = tBaseBean.getErrorMsg();
                onCodeError(errorCode, errorMsg);
            }
        }
    }


    @Override
    public void onError(Throwable e) {
        mBaseView.showError();
        if (e.toString().contains("timeout")){
            ToastUtils.Toast_short(ResourcesUtils.getString(R.string.timeout));
        }else if (e.toString().contains("Failed to connect")){
            ToastUtils.Toast_short(ResourcesUtils.getString(R.string.noNetwork));
        } else {
            ToastUtils.Toast_short(ResourcesUtils.getString(R.string.failconnect));
        }
    }
    /**
     * 响应码错误
     *
     * @param errorCode
     * @param errorMsg
     */
    private void onCodeError(String errorCode, String errorMsg) {
//        ToastUtils.Toast_short(errorMsg);
        mBaseView.showError();
        if (errorCode.equals("-10107")){//token失效
            //重新登录
//            CommonAction.clearUserData();
//            EventBus.getDefault().post(new LoginBus(Const.reLogin));
        }else {
            ToastUtils.Toast_short(errorMsg);
        }
    }

    @Override
    public void onComplete() {
        mBaseView.complete();
        unSubscribe();
    }

    public abstract void onSuccess(T results);


}
