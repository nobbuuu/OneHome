package com.dream.onehome.utils;

/**
 * Created by Administrator on 2016/8/4.
 */
import android.content.Context;
import android.content.SharedPreferences;

import com.dream.onehome.common.OneHomeAplication;


/**
 * Created by Administrator on 2016/4/23.
 */
public class SpUtils {
    private static final String NAME = "share_data";

    private static final Context context = OneHomeAplication.getInstance();

    /**
     * 保存数据的方法，首先拿到数据的具体类型，然后根据不同类型，使用不同保存方法
     * @param object
     */
    public static void setParam(String key,Object object) {
        SharedPreferences sp = context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
        String type = object.getClass().getSimpleName();

        SharedPreferences.Editor editor = sp.edit();

        if (type.equals("String")) {
            editor.putString(key, (String)object);
        }else if (type.equals("Integer")) {
            editor.putInt(key, (Integer)object);
        }else if (type.equals("Boolean")) {
            editor.putBoolean(key, (Boolean)object);
        }else if (type.equals("Long")) {
            editor.putLong(key, (Long)object);
        }else if (type.equals("Float")){
            editor.putFloat(key, (Float)object);
        }
        editor.commit();
    }

    /**
     * 得到保存数据的方法，根据默认值获得保存值具体类型，再根据具体类型调用具体的方法获取值
     * @param key
     * @param object
     * @return
     */

    public static Object getParam(String key,Object object) {
        SharedPreferences sp = context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
        String type = object.getClass().getSimpleName();
        if (type.equals("String")) {
            return sp.getString(key, (String)object);
        }else if (type.equals("Integer")) {
            return sp.getInt(key, (Integer)object);
        }else if (type.equals("Boolean")) {
            return sp.getBoolean(key, (Boolean)object);
        }else if (type.equals("Long")) {
            return sp.getLong(key, (Long)object);
        }else if (type.equals("Float")){
            return sp.getFloat(key, (Float)object);
        }
        return null;
    }
    public static void Clear(){
        SharedPreferences sp = OneHomeAplication.getInstance().getSharedPreferences(NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor edit = sp.edit();

        edit.clear();
        edit.commit();
    }

    public static void savaUserInfo(String key, Object info) {
        if (info != null) {
            SpUtils.setParam(key, info);
        }
    }
}
