package com.dream.onehome.constract;


import com.dream.onehome.base.BaseContract;
import com.dream.onehome.bean.EmptyBean;

/**
 * @author lfh.
 * @date 2016/8/6.
 */
public interface CitySelectContract {

    interface View extends BaseContract.BaseView {

        void showCitySelectData(EmptyBean dataBean);

    }

    interface Presenter<T> extends BaseContract.BasePresenter<T> {
        void getProvinceData();
    }
}
