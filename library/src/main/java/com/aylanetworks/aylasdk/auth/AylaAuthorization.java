package com.aylanetworks.aylasdk.auth;

import com.android.volley.Response;
import com.aylanetworks.aylasdk.AylaLog;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.google.gson.annotations.Expose;

import java.util.Arrays;
import java.util.Date;

/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

/**
 * Class containing session authorization information. Objects of this type are returned from
 * {@link AylaAuthProvider} objects when used to sign-in.
 *
 * Authorizations may be cached and used to initialize a {@link CachedAuthProvider} object, which
 * can be passed to {@link com.aylanetworks.aylasdk.AylaLoginManager#signIn}
 * to refresh the previous authorization.
 */
public class AylaAuthorization {
    private static final String LOG_TAG = "AylaAuth";

    @Expose
    private String accessToken;
    @Expose
    private String refreshToken;
    @Expose
    private String expiresIn;
    @Expose
    private String role;
    @Expose
    private RoleTag roleTags[];

    @Expose
    String url;	// OAUTH provider URL

    // Remember when this object is created so we know how long until we expire
    @Expose
    private Date _createdAt;

    public AylaAuthorization() {
        _createdAt = new Date();
    }

    /**
     * Returns the custom URL for this authorization, if present. This field is used for SSO or
     * O-Auth authorizations.
     *
     * @return The custom URL for this authorization, or null if one is not present
     */
    public String getUrl() { return url; }

    /**
     * Returns the time the authorization object was created
     * @return the time the authorization object was created
     */
    public Date getCreatedAt() {
        return _createdAt;
    }

    /**
     * Returns the access token for this authorization. This is required to make API calls to the
     * Ayla servicef and is generally not used outside of the SDK.
     *
     * @return the access token for this authorization
     */
    public String getAccessToken() {
        return accessToken;
    }

    /**
     * Returns the refresh token for this authorization. This token is used to obtain a new
     * authorization when the current one is about to expire.
     *
     * @return the refresh token for this authorization
     */
    public String getRefreshToken() {
        return refreshToken;
    }

    /**
     * Returns the number of seconds after creation that this authorization expires. Call
     * {@link #getSecondsToExpiry()} to get the number of seconds from the current time that this
     * object will expire.
     *
     * @return The number of seconds since the time of fetch that this object will expire in
     */
    public long getExpiresIn() {
        try {
            return Long.parseLong(expiresIn);
        } catch (NumberFormatException e) {
            AylaLog.e(LOG_TAG, "Nubmer format exception trying to parse expiration: " +
                    e.getMessage());
            return 0L;
        }
    }

    /**
     * Returns the array of role tags, if present. Otherwise returns null.
     * @return the array of role tags, if present. Otherwise returns null.
     */
    public RoleTag[] getRoleTags() {
        return roleTags;
    }

    /**
     * Returns the number of seconds from the current time that this authorization expires, or 0
     * if the authoriztion has already expired.
     *
     * @return The number of seconds from the current time that this authorization expires in, or
     * 0 if the authorization has already expired.
     */
    public long getSecondsToExpiry() {
        if (_createdAt == null) {
            return 0L;
        }

        return (long) Math.max(0L, getExpiresIn() - ((new Date().getTime() - _createdAt.getTime())*0.001));
    }

    /**
     * Returns the role for this authorization. Roles are OEM-specific.
     *
     * @return the role for this authorization
     */
    public String getRole() {
        return role;
    }

    /**
     * Used to update this authorization object from another. Used internally by the SDK.
     *
     * @param other AylaAuthorization used to update this object.
     */
    public void updateFrom(AylaAuthorization other) {
        if ( other.accessToken != null ) {
            accessToken = other.accessToken;
        }
        if ( other.refreshToken != null ) {
            refreshToken = other.refreshToken;
        }
        if ( other.expiresIn != null ) {
            expiresIn = other.expiresIn;
        }
        if ( other.role != null ) {
            role = other.role;
        }
        if ( other._createdAt != null ) {
            this._createdAt = other._createdAt;
        } else {
            this._createdAt = new Date();
        }
    }

    public static class RoleTag {
        @Expose
        public String key;
        @Expose
        public String value;
    }
}
