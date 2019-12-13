package com.aylanetworks.aylasdk.auth;

/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import com.android.volley.Response;
import com.aylanetworks.aylasdk.AylaAPIRequest;
import com.aylanetworks.aylasdk.AylaSessionManager;
import com.aylanetworks.aylasdk.AylaUser;
import com.aylanetworks.aylasdk.error.AylaError;
import com.aylanetworks.aylasdk.error.ErrorListener;

/**
 * An AylaAuthProvider performs the steps necessary to authenticate a user. The AylaAuthProvider
 * object is passed into
 * {@link com.aylanetworks.aylasdk.AylaLoginManager#signIn}
 * which will call the {@link #authenticate(AuthProviderListener, String) authenticate} method and
 * listen for the response. The didAuthenticate() response is passed to AylaLoginManager on a
 * successful sign-in
 * operation. The LoginManager at that point will start up the system using the provided
 * authorization credentials.
 */
public interface AylaAuthProvider {
    /**
     * Called by AylaLoginManager, this method should perform the necessary operations to sign in
     * a user. On successful sign-in, the method should call the provided listener via
     * {@link com.aylanetworks.aylasdk.auth.AylaAuthProvider.AuthProviderListener#didAuthenticate
     * didAuthenticate}
     * and provide the AylaAuthorization object with the necessary authenticated credentials.
     *
     * This method should only be called by AylaLoginManager.
     *
     * @param listener The listener to be notified of sign-in success or failure.
     */
    void authenticate(AuthProviderListener listener);

    /**
     * Called by AylaLoginManager, this method should perform the necessary operations to sign in
     * a user. On successful sign-in, the method should call the provided listener via
     * {@link com.aylanetworks.aylasdk.auth.AylaAuthProvider.AuthProviderListener#didAuthenticate
     * didAuthenticate}
     * and provide the AylaAuthorization object with the necessary authenticated credentials.
     *
     * @param listener The listener to be notified of sign-in success or failure.
     * @param sessionName Name of the session for which this authentication is done. This is used
     *                    for offline mode login
     */
    void authenticate(AuthProviderListener listener, String sessionName);

    /**
     * Called by AylaSessionManager to update the user profile on the Ayla service or external
     * identity provider service (for SSO users) to match the fields in the provided AylaUser.
     *
     * @param user            AylaUser containing the updated user information
     * @param successListener Listener to receive the updated user when the operation succeeds
     * @param errorListener   Listener to receive an error if something went wrong
     * @return the {link AylaAPIRequest} for this operation
     */
    AylaAPIRequest updateUserProfile(AylaSessionManager sessionManager, AylaUser user,
                                     Response.Listener<AylaUser> successListener,
                                     ErrorListener errorListener);

    /**
     * Called by AylaSessionManager to delete a user account from Ayla service or the external
     * identity provider (for SSO users).
     * @param successListener Listener called if the operation is successful.
     * @param errorListener Listener called if an error occurred
     * @return the AylaAPIRequest for this operation, which may be canceled
     */

    AylaAPIRequest deleteUser(AylaSessionManager sessionManager,
                              Response.Listener<AylaAPIRequest.EmptyResponse> successListener,
                              ErrorListener errorListener);

    /**
     * Called by AylaSessionManager to sign out the user.
     *
     * @param successListener Listener to receive the results of the network sign-out call.
     * @param errorListener   Listener to receive an error from the network sign-out call.
     * @return the AylaAPIRequest for this command. While the request to sign out from the server
     * may be canceled, the session will be closed regardless.
     */
    AylaAPIRequest signout(AylaSessionManager sessionManager,
                           Response.Listener<AylaAPIRequest.EmptyResponse> successListener,
                           ErrorListener errorListener);

    interface AuthProviderListener {
        /**
         * Called when the AuthProvider has successfully signed in.
         * @param authorization The AylaAuthorization result of the successful sign-in operation
         * @param isOfflineUse True if this authentication is only for offline use.
         */
        void didAuthenticate(AylaAuthorization authorization, boolean isOfflineUse);
        void didFailAuthentication(AylaError error);
    }
}
