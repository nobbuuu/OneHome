package com.dream.onehome.base;


import android.util.Log;

import com.dream.onehome.R;
import com.dream.onehome.common.Const;
import com.dream.onehome.utils.LogUtils;
import com.dream.onehome.utils.ResourcesUtils;
import com.dream.onehome.utils.ToastUtils;

import io.reactivex.Observer;

/**
 * 响应实体类型result为对象类型
 * Created by Administrator on 2017/12/13 0013.
 */

public abstract class NoBaseBeanObserver<T> implements Observer<T> {

     @Override
    public void onComplete() {

    }

    @Override
    public void onError(Throwable e) {
        Log.e("onError","errormsg = " + e.getMessage());
        if (e.toString().contains("timeout")){
            ToastUtils.Toast_long(ResourcesUtils.getString(R.string.timeout));
        }else if (e.toString().contains("Failed to connect")){
            ToastUtils.Toast_long(ResourcesUtils.getString(R.string.noNetwork));
        } else {
            ToastUtils.Toast_long(ResourcesUtils.getString(R.string.failconnect));
        }
    }


    @Override
    public void onNext(T tBaseBean) {
        onSuccess(tBaseBean);

        /*if (tBaseBean!=null){
            if (tBaseBean.getErrorCode().equals(Const.SUCCESS)) {
                T results = tBaseBean.getResults();
                onSuccess(results);
            } else {
//            ToastUtils.Toast_short(tBaseBean.getErrorMsg());
                String errorCode = tBaseBean.getErrorCode();
                String errorMsg = tBaseBean.getErrorMsg();
                onCodeError(errorCode, errorMsg);
            }
        }*/
    }

    /**
     * 响应吗错误
     *
     * @param errorCode
     * @param errorMsg
     */
    private void onCodeError(String errorCode, String errorMsg) {
//        ToastUtils.Toast_short(errorMsg);
        LogUtils.e("errorLog","errorCode = " + errorCode + "  errorMsg = " + errorMsg);
        if (errorCode.equals("-10107")){//token失效
            //重新登录
        }else {
            ToastUtils.Toast_long(errorMsg);
        }
    }

    public abstract void onSuccess(T results);
}
