package com.dream.onehome.bean;

/**
 * Time:
 * Author:TiaoZi
 */
public class KeyIrCodeBean {

    /**
     * conoff : 开
     * cmode : 制冷
     * ctemp : 24
     * cwind : 自动
     * cwinddir : 自动
     * irdata : 63,2D,32,C0,01,7D,53,5D,E9,34,65,F8,B7,0F,DE,60,3D,0E,D4,5D,BE,E1,CE,14,83,F3,5E,D1,EE,00,57,6C,B6,AB,1F,13,52,B8,86,B4,4F,5C,97,51,1A,B9,E6,EF,5E,11,24,F7,CD,93,D1,51,B9,D5,8E,17,0C,E8,23,E8,C5,6A,75,E7,0A,1B,99,54
     */

    private String conoff;
    private String cmode;
    private int ctemp;
    private String cwind;
    private String cwinddir;
    private String irdata;

    public String getConoff() {
        return conoff;
    }

    public void setConoff(String conoff) {
        this.conoff = conoff;
    }

    public String getCmode() {
        return cmode;
    }

    public void setCmode(String cmode) {
        this.cmode = cmode;
    }

    public int getCtemp() {
        return ctemp;
    }

    public void setCtemp(int ctemp) {
        this.ctemp = ctemp;
    }

    public String getCwind() {
        return cwind;
    }

    public void setCwind(String cwind) {
        this.cwind = cwind;
    }

    public String getCwinddir() {
        return cwinddir;
    }

    public void setCwinddir(String cwinddir) {
        this.cwinddir = cwinddir;
    }

    public String getIrdata() {
        return irdata;
    }

    public void setIrdata(String irdata) {
        this.irdata = irdata;
    }
}
