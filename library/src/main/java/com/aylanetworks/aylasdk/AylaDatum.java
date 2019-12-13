package com.aylanetworks.aylasdk;

/**
 * Android_AylaSDK
 * <p>
 * Copyright 2016 Ayla Networks, all rights reserved
 */

import com.android.volley.Request;
import com.android.volley.Response;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.aylanetworks.aylasdk.error.JsonError;
import com.aylanetworks.aylasdk.util.ServiceUrls;
import com.google.gson.annotations.Expose;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Class used to represent metadata associated with a user or device in Ayla cloud. Each datum is
 * stored as a key-value pair. Metadata associated with a device is deleted on unregistering the
 * device. Metadata associated with a user is deleted on deleting the user account.
 *
 */
public class AylaDatum {
    private static final String LOG_TAG = "AylaDatum";

    @Expose
    private String key;			// Key for the datum. Maximum length is 255 characters
    @Expose
    private String value;		// Value of the datum. Maximum size is 2 MB.
    @Expose
    private String createdAt;	// Time this object was created. Returned with create &
    // update operations
    @Expose
    private String updatedAt;	// Time this object was last updated. Returned with create & update
    // operations

    /**
     * Create a new AylaDatum object
     * @param key key of the datum object
     * @param value value of the datum object
     */
    public AylaDatum(String key, String value){
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public static class Wrapper{
        @Expose
        public AylaDatum datum;

        public static AylaDatum[] unwrap(Wrapper[] container){
            AylaDatum[] aylaData = new AylaDatum[container.length];
            for( int i = 0; i< container.length; i++){
                aylaData[i] = container[i].datum;
            }
            return aylaData;
        }
    }

}
