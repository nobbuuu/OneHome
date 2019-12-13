package com.aylanetworks.aylasdk.auth;
/**
 * AylaSDK
 * <p>
 * Copyright 2017 Ayla Networks, all rights reserved
 */
import android.text.TextUtils;

import com.android.volley.Request;
import com.android.volley.Response;
import com.aylanetworks.aylasdk.AylaAPIRequest;
import com.aylanetworks.aylasdk.AylaJsonRequest;
import com.aylanetworks.aylasdk.AylaNetworks;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.aylanetworks.aylasdk.error.JsonError;
import com.google.gson.annotations.Expose;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.Date;


public class AylaPartnerAuthorization {
    private final static String LOG_TAG = "AylaPartnerAuthorization";

    /** Partner Auth Token */
    @Expose
    private String partnerAccessToken;

    /** Partner Refresh Token */
    @Expose
    private String refreshToken;

    /** User role for current partner access */
    @Expose
    private String role;

    /** Duration in seconds of how long after its creation the current partner auth token is valid */
    @Expose
    private long expiresIn;

    /** Date at which the current partner auth token was created */
    @Expose
    private Date createdAt;

    private final String _partnerId;
    private final String _partnerName;
    private final String _partnerAuthUrl;
    private final String _partnerAppId;
    private final String _partnerAppSecret;
    private final String _partnerRefreshAuthTokenUrl;
    private final long AUTH_TOKEN_REFRESH_INTERVAL = 900; // 15 mins

    /**
     * Constructor for AylaPartnerAuthorization
     * @param partnerId Partner Id
     * @param partnerName Partner Name
     * @param partnerAuthUrl Partner cloud auth URL endpoint
     * @param partnerAppId Partner Application Id
     * @param partnerAppSecret Partner Application Secret
     * @param partnerRefreshAuthTokenUrl Partner Refresh auth URL endpoint
     */
    public AylaPartnerAuthorization(String partnerId,
                                    String partnerName,
                                    String partnerAuthUrl,
                                    String partnerAppId,
                                    String partnerAppSecret,
                                    String partnerRefreshAuthTokenUrl) {
        _partnerId = partnerId;
        _partnerName = partnerName;
        _partnerAuthUrl = partnerAuthUrl;
        _partnerAppId = partnerAppId;
        _partnerAppSecret = partnerAppSecret;
        _partnerRefreshAuthTokenUrl = partnerRefreshAuthTokenUrl;
    }

    /**
     * Log-in to partner cloud using short-lived partner token retrieved from Ayla IDP
     *
     * @param partnerToken Short-lived partner token to login to partner cloud
     * @param partnerTokenKeyName Partner Token Key Name
     * param appendAppIdAndSecret if true append appId and appSecret to request params
     * @param partnerAuthUrl Partner cloud auth URL endpoint
     * @param successListener Listener after successful auth to partner cloud. Passes in the created AylaPartnerAuthorization object
     * @param errorListener Listener if auth fails to partner cloud
     * @return the {link AylaAPIRequest} for this operation
     */
    public AylaAPIRequest loginToPartnerCloud(String partnerToken,
                                                     String partnerTokenKeyName,
                                                     boolean appendAppIdAndSecret,
                                                     String partnerAuthUrl,
                                                     Response.Listener<AylaPartnerAuthorization> successListener,
                                                     ErrorListener errorListener) {
        JSONObject params = new JSONObject();
        try {
            params.put(partnerTokenKeyName, partnerToken);
            if(appendAppIdAndSecret) {
                params.put("app_id", _partnerAppId);
                params.put("app_secret", _partnerAppSecret);
            }
        } catch (JSONException e) {
            errorListener.onErrorResponse(new JsonError(null, "JSONException while trying to generate loginToPartnerCloud parameters.", e));
            return null;
        }
        AylaJsonRequest<AylaPartnerAuthorization> request = new AylaJsonRequest<>(
                Request.Method.POST,
                partnerAuthUrl,
                params.toString(),
                null,
                AylaPartnerAuthorization.class,
                null,
                successListener,
                errorListener);
        AylaNetworks.sharedInstance().getUserServiceRequestQueue().add(request);
        return  request;
    }

    /**
     * Update Partner Authorization with Refresh Token
     * @param refreshToken Partner Refresh Token
     * @param authToken Partner Auth Token
     * @param expiresIn Expiration time for token
     */
    public void updatePartnerAuthorizationWithRefreshToken(String refreshToken,
                                                           String authToken,
                                                           long expiresIn) {
        if (!TextUtils.isEmpty(refreshToken)) {
            this.refreshToken = refreshToken;
        }
        if (!TextUtils.isEmpty(authToken)) {
            partnerAccessToken = authToken;
        }
        this.expiresIn = expiresIn;
    }

    /**
     * Refresh Auth token
     * @param partnerRefreshToken Partner Refresh token
     * @param successListener Listener after successful refresh of Auth Token to partner cloud
     * @param errorListener ErrorListener in case of failure
     * @return the {link AylaAPIRequest} for this operation
     */
    public AylaAPIRequest refreshAuthToken(String partnerRefreshToken,
                                            final Response.Listener<AylaPartnerAuthorization> successListener,
                                            final ErrorListener errorListener) {
        JSONObject params = new JSONObject();
        try {
            params.put("refresh_token", partnerRefreshToken);
        } catch (JSONException e) {
            errorListener.onErrorResponse(new JsonError(null, "JSONException while trying to generate loginToPartnerCloud parameters.", e));
            return null;
        }
        AylaJsonRequest<AylaPartnerAuthorization> request = new AylaJsonRequest<>(
                Request.Method.POST,
                _partnerRefreshAuthTokenUrl,
                params.toString(),
                null,
                AylaPartnerAuthorization.class,
                null,
                successListener,
                errorListener);
        AylaNetworks.sharedInstance().getUserServiceRequestQueue().add(request);
        return request;
    }


    public String getAuthToken() {
        return partnerAccessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public String getRole() {
        return role;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public Date getCreatedAt() {
        return createdAt;
    }
}