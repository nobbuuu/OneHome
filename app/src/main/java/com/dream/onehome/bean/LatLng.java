package com.dream.onehome.bean;

/**
 * Time:
 * Author:TiaoZi
 */
public class LatLng {

    private double longitude;
    private double latitude;

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public LatLng(double longitude, double latitude) {
        this.longitude = longitude;
        this.latitude = latitude;

    }
}
