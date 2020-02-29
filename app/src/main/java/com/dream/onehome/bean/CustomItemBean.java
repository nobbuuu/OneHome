package com.dream.onehome.bean;

/**
 * Time:2020/02/29
 * Author:TiaoZi
 */
public class CustomItemBean {
    private String keyName;
    private String irCode;

    public String getIrCode() {
        return irCode;
    }

    public void setIrCode(String irCode) {
        this.irCode = irCode;
    }

    public CustomItemBean(String keyName, String irCode) {
        this.keyName = keyName;
        this.irCode = irCode;
    }

    public String getKeyName() {
        return keyName;
    }

    public void setKeyName(String keyName) {
        this.keyName = keyName;
    }
}
