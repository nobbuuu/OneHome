package com.aylanetworks.aylasdk.metrics;

/**
 * Android_Aura
 * <p>
 * Copyright 2018 Ayla Networks, all rights reserved
 */

public class AylaUserDataGrant {
    /**
     * Used to indicate that anonymous user data uploads are not allowed.
     */
    public static final int AYLA_USER_DATA_GRANT_NONE = 0;

    /**
     * Used to indicate that anonymous user data uploads are allowed
     */
    public static final int AYLA_USER_DATA_GRANT_METRICS_SERVICE = 1 << 0;

    /**
     * Used to indicate that uploading crash logs is allowed
     */
    public static final int AYLA_USER_DATA_GRANT_UNCAUGHTEXCEPTION = 1 << 1;

    //Enable metrics by default in the SDK. Apps should disable/enable it based on user settings.
    private int _userDataGrant = AYLA_USER_DATA_GRANT_METRICS_SERVICE | AYLA_USER_DATA_GRANT_UNCAUGHTEXCEPTION;

    /**
     *
     * @param userDataGrant
     */
    public void setUserDataGrant(int userDataGrant) {
        this._userDataGrant = userDataGrant;
    }

    /**
     * Returns true if userDataGrant is enabled.
     * @param userDataGrant One of {@value #AYLA_USER_DATA_GRANT_NONE,
     *                      @value #AYLA_USER_DATA_GRANT_METRICS_SERVICE or
     *                      @value AYLA_USER_DATA_GRANT_UNCAUGHTEXCEPTION}
     * @return true if userDataGrant is enabled.
     */
    public boolean isEnabled(int userDataGrant){
        if(userDataGrant == 0){
            return _userDataGrant == userDataGrant;
        } else{
            return ((_userDataGrant & userDataGrant) == userDataGrant);
        }

    }
}
