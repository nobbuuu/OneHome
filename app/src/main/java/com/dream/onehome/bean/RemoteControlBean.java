package com.dream.onehome.bean;

import java.util.List;

/**
 * Time:2019/12/20
 * Author:TiaoZi
 */
public class RemoteControlBean {

    private String type;
    private String name;
    private String brandName;
    private String mKfid;

    private List<CustomItemBean> keysList;

    public List<CustomItemBean> getKeysList() {
        return keysList;
    }

    public void setKeysList(List<CustomItemBean> keysList) {
        this.keysList = keysList;
    }

    public RemoteControlBean() {

    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBrandName() {
        return brandName;
    }

    public void setBrandName(String brandName) {
        this.brandName = brandName;
    }

    public String getKfid() {
        return mKfid;
    }

    public void setKfid(String kfid) {
        mKfid = kfid;
    }
}
