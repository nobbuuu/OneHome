package com.dream.onehome.http;

import com.dream.onehome.base.BaseBean;
import com.dream.onehome.base.BaseObserver;
import com.dream.onehome.bean.BrandBean;
import com.dream.onehome.bean.DeviceTypeBean;
import com.dream.onehome.bean.EmptyBean;
import com.dream.onehome.bean.KeyIrCodeBean;
import com.dream.onehome.bean.KeysBean;
import com.dream.onehome.bean.ModelBean;

import java.util.List;

import io.reactivex.Observable;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface ApiService {

    // 填上需要访问的服务器地址
    public static String HOST = "http://www.huilink.com.cn/dk2018/";

    @POST("api/equipment/getActivity")
    Observable<BaseObserver<List<EmptyBean>>> getActivity();

    @FormUrlEncoded
    @POST("api/equipment/getAd")
    Observable<BaseBean<List<EmptyBean>>> getAdList(@Field("id") String id);

    @GET("getdevicelist.asp")
    Observable<List<DeviceTypeBean>> getDeviceType();

    @FormUrlEncoded
    @POST("getbrandlist.asp")
    Observable<List<BrandBean>> getBrandList(@Field("mac") String mac,@Field("device_id") String device_id);

    @FormUrlEncoded
    @POST("getbrandlist.asp")
    Observable<List<BrandBean>> getBrandList(@Field("mac") String mac,@Field("device_id") String device_id,@Field("mcity") String mcity);

    @FormUrlEncoded
    @POST("getmodellist.asp")
    Observable<List<ModelBean>> getModellist(@Field("mac") String mac, @Field("device_id") String device_id, @Field("brand_id") String brand_id);

    @FormUrlEncoded
    @POST("getkeylist.asp")
    Observable<KeysBean> getKeyList(@Field("mac") String mac, @Field("kfid") String kfid);

    @FormUrlEncoded
    @POST("keyevent.asp")
    Observable<KeyIrCodeBean> getKeyCode(@Field("mac") String mac, @Field("kfid") String kfid, @Field("keyid") String keyid);

    @FormUrlEncoded
    @POST("keyevent.asp")
    Observable<KeyIrCodeBean> getKeyCode(@Field("mac") String mac);




}
