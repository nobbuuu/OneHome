package com.aylanetworks.aylasdk.setup;
/*
 * Android_AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import android.text.TextUtils;

import com.aylanetworks.aylasdk.AylaDevice;
import com.aylanetworks.aylasdk.AylaLog;
import com.aylanetworks.aylasdk.change.Change;
import com.aylanetworks.aylasdk.change.FieldChange;
import com.aylanetworks.aylasdk.lan.AylaLanConfig;
import com.aylanetworks.aylasdk.util.ObjectUtils;
import com.google.gson.annotations.Expose;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents an AylaDevice that is used by {@link AylaSetup}. This device should only be used
 * during the WiFi setup process and is not considered part of the user's set of devices.
 */
public class AylaSetupDevice extends AylaDevice {
    public static final String LOG_TAG = "AylaSetupDevice";
    @Expose
    protected String api_version;
    @Expose
    protected String build;
    @Expose
    protected String device_service;
    @Expose
    protected String [] features;
    @Expose
    protected long last_connect_mtime;
    @Expose
    protected long mtime;
    @Expose
    protected String version;
    @Expose
    protected String regToken;

    // Constants for known feature strings found in the features[] array
    protected static final String FEATURE_AP_STA = "ap-sta";
    protected static final String FEATURE_REG_TOKEN = "reg-type";
    protected static final String FEATURE_WPS = "wps";
    protected static final String FEATURE_RSA_KE = "rsa-ke";

    public AylaSetupDevice() {
        this.lanEnabled = true;
    }

    public void setFeatures(String[] features) {
        this.features = features;
    }

    public void setRegToken(String regToken) {
        this.regToken = regToken;
    }

    public String getApiVersion() {
        return api_version;
    }

    public String getBuild() {
        return build;
    }

    public String getDeviceService() {
        return device_service;
    }

    public String[] getFeatures() {
        return features;
    }

    public long getLastConnectMtime() {
        return last_connect_mtime;
    }

    public long getMtime() {
        return mtime;
    }

    public String getVersion() {
        return version;
    }

    public void setLanIp(String lanIp) {
        this.lanIp = lanIp;
    }

    public void setLanConfig(AylaLanConfig lanConfig) {
        _lanConfig = lanConfig;
    }

    public void setRegistrationType(RegistrationType registrationType) {
        this.registrationType = registrationType.stringValue();
    }

    public String getRegToken() {
        return regToken;
    }

    public boolean hasFeature(String feature) {
        if ( features == null ) {
            return false;
        }
        for ( String myFeature : features ) {
            if (TextUtils.equals(myFeature, feature) ) {
                return true;
            }
        }
        return false;
    }

    // When our access point joins the main wifi network, normally we would start polling as we
    // have lost our LAN connection. This makes sure we don't really try to poll for the setup
    // device.
    @Override
    public boolean startPolling() {
        return false;
    }

    @Override
    public Change updateFrom(AylaDevice other, DataSource source) {

        if(!(other instanceof AylaSetupDevice) ){
            return super.updateFrom(other,source);
        }
        AylaSetupDevice updatedDevice = (AylaSetupDevice) other;
        AylaLog.d(LOG_TAG, "Updating setup device");
        // See if any fields have changed
        Set<String> changedFields = new HashSet<>();
        if (!ObjectUtils.equals(updatedDevice.dsn, dsn)) {
            dsn = updatedDevice.dsn;
            changedFields.add("dsn");
        }

        if (!ObjectUtils.equals(updatedDevice.model, model)) {
            model = updatedDevice.model;
            changedFields.add("model");
        }

        if (!ObjectUtils.equals(updatedDevice.features, features)) {
            features = updatedDevice.features;
            changedFields.add("features");
        }

        if (!ObjectUtils.equals(updatedDevice.mtime, mtime)) {
            mtime = updatedDevice.mtime;
            changedFields.add("mtime");
        }

        if (!ObjectUtils.equals(updatedDevice.version, version)) {
            version = updatedDevice.version;
            changedFields.add("version");
        }
        if (!ObjectUtils.equals(updatedDevice.build, build)) {
            build = updatedDevice.build;
            changedFields.add("build");
        }

        if (!ObjectUtils.equals(updatedDevice.device_service, device_service)) {
            device_service = updatedDevice.device_service;
            changedFields.add("device_service");
        }

        Change change = null;
        if (changedFields.size() > 0) {
            change = new FieldChange(changedFields);
            notifyDeviceChanged(change, source);
        }

        return change;
    }
}
