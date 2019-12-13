package com.aylanetworks.aylasdk.auth;
/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.Response;
import com.aylanetworks.aylasdk.AylaAPIRequest;
import com.aylanetworks.aylasdk.AylaJsonRequest;
import com.aylanetworks.aylasdk.AylaLog;
import com.aylanetworks.aylasdk.AylaLoginManager;
import com.aylanetworks.aylasdk.AylaNetworks;
import com.aylanetworks.aylasdk.error.AylaError;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.aylanetworks.aylasdk.error.InvalidArgumentError;
import com.aylanetworks.aylasdk.error.JsonError;
import com.aylanetworks.aylasdk.error.ServerError;
import com.aylanetworks.aylasdk.metrics.AylaLoginMetric;
import com.aylanetworks.aylasdk.metrics.AylaMetric;
import com.aylanetworks.aylasdk.metrics.AylaMetricsManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * AylaAuthProvider that uses cached credentials to sign in. If your application is saving the
 * AylaAuthorization to log the user in automatically on subsequent runs of the app, this
 * object can be initialized with the cached credentials and passed to
 * {@link com.aylanetworks.aylasdk.AylaLoginManager#signIn(AylaAuthProvider, String, Response.Listener, ErrorListener)}
 * to sign-in the user.
 * <p>
 * The CachedAuthProvider will refresh the provided credentials as part of the sign-in process if
 * the cloud is available.
 * <p>
 * This class allows for offline sign-in if the cloud service is available. If the cloud service
 * is not available, and the object was initialized with allowOfflineLogin set to true, the
 * provider will allow the sign-in operation to succeed.
 * <p>
 * While the cloud service is unavailable, the system will attempt to return cached data to
 * API requests whenever possible. Not all data is cached, however, so the application should
 * always be prepared to receive error responses to any API request.
 */
public class CachedAuthProvider extends BaseAuthProvider {
    public final static String AYLA_CACHED_CREDENTIAL_KEY = "com.aylanetworks.aylasdk.credential";
    public final static String LOG_TAG = "CACHED_AUTH_PROVIDER";
    protected AylaAuthorization _cachedCredentials;

    public CachedAuthProvider(AylaAuthorization cachedCredentials) {
        _cachedCredentials = cachedCredentials;
    }

    /**
     * Returns a CachedAuthProvider initialized with credentials that have been cached via a call
     * to {@link #cacheAuthorization(Context, AylaAuthorization)}.
     *
     * @param context           Context used to obtain shared preferences to obtain the cached data
     * @return A CachedAuthProvider object initialized with the cached authorization, or null if
     * no cached authorization data was found.
     */
    public static CachedAuthProvider getCachedProvider(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String cachedPrefJSON = prefs.getString(AYLA_CACHED_CREDENTIAL_KEY, null);
        if (cachedPrefJSON != null) {
            AylaAuthorization authorization = AylaNetworks.sharedInstance().getGson()
                    .fromJson(cachedPrefJSON, AylaAuthorization.class);
            if (authorization != null) {
                return new CachedAuthProvider(authorization);
            }
        }
        return null;
    }

    /**
     * Saves the provided authorization object to cache. CachedAuthProvider objects initialized
     * with this cached authorization may be created via a call to
     * {@link #getCachedProvider (Context, boolean)}.
     *
     * @param context       Context used to obtain shared preferences to store the cached data
     * @param authorization Authorization object to be cached
     */
    public static void cacheAuthorization(Context context, AylaAuthorization authorization) {
        SharedPreferences.Editor editor = PreferenceManager
                .getDefaultSharedPreferences(context).edit();
        String authJSON = AylaNetworks.sharedInstance().getGson().toJson(authorization);
        editor.putString(AYLA_CACHED_CREDENTIAL_KEY, authJSON);
        editor.apply();
    }

    /**
     * Clears cached authorization. This method should be called whenever the user signs out if
     * the application uses cached authorization.
     *
     * @param context Context used to obtain shared preferences to clear the cached data
     */
    public static void clearCachedAuthorization(Context context) {
        SharedPreferences.Editor editor = PreferenceManager
                .getDefaultSharedPreferences(context).edit();
        editor.remove(AYLA_CACHED_CREDENTIAL_KEY);
        editor.apply();
    }

    @Override
    public void authenticate(AuthProviderListener listener) {
        authenticate(listener, null);
    }

    @Override
    public void authenticate(final AuthProviderListener listener, final String sessionName) {
        // Try to refresh the auth token. If we cannot reach the server, we will still
        // call didAuthenticate if _allowOfflineLogin is true. Otherwise we will fail.
        if (_cachedCredentials.getRefreshToken() == null ||
                _cachedCredentials.getAccessToken() == null) {
            listener.didFailAuthentication(new InvalidArgumentError("Invalid authorization object"));
            return;
        }

        // Expected JSON body we need to send looks like this:
        // {"user":{"refresh_token":"3ccd999effb335c50775d739ece32ab8"}}
        JSONObject userObject = new JSONObject();
        JSONObject bodyObject = new JSONObject();
        try {
            userObject.put("refresh_token", _cachedCredentials.getRefreshToken());
            bodyObject.put("user", userObject);
        } catch (JSONException e) {
            listener.didFailAuthentication(new JsonError(null, "JSONException when creating body " +
                    "JSON for refreshAuthorization", e));
            return;
        }
        final String bodyJSON = bodyObject.toString();

        AylaLoginManager lm = AylaNetworks.sharedInstance().getLoginManager();

        String url = lm.userServiceUrl("users/refresh_token.json");

        // We may not be signed-in, so will need to provide the access token in the HTTP headers
        Map<String, String> additionalHeaders = new HashMap<>();
        additionalHeaders.put("Authorization", "auth_token " + _cachedCredentials.getAccessToken());

        final AylaAPIRequest<AylaAuthorization> request = new AylaJsonRequest<AylaAuthorization>(
                Request.Method.POST,
                url,
                bodyJSON,
                additionalHeaders,
                AylaAuthorization.class,
                null,
                new Response.Listener<AylaAuthorization>() {
                    @Override
                    public void onResponse(AylaAuthorization response) {
                        listener.didAuthenticate(response, false);
                    }
                },
                new ErrorListener() {
                    @Override
                    public void onErrorResponse(AylaError error) {
                        //Attempt LAN login
                        if(error.getErrorType() == AylaError.ErrorType.NetworkError ||
                                error.getErrorType() == AylaError.ErrorType.Timeout){
                            if(AylaNetworks.sharedInstance().getSystemSettings().allowOfflineUse &&
                                    sessionName != null){
                                AylaLog.d(LOG_TAG, "Starting LAN login");
                                listener.didAuthenticate(_cachedCredentials, true);
                            } else{
                                listener.didFailAuthentication(error);
                                AylaLoginMetric loginMetric = new AylaLoginMetric(
                                        AylaMetric.LogLevel.ERROR,
                                        AylaLoginMetric.MetricType.LOGIN_FAILURE, "authenticate",
                                        "CachedAuthProvider", AylaMetric.Result.FAILURE,
                                        error.getMessage());
                                sendToMetricsManager(loginMetric);
                            }
                        } else{
                            listener.didFailAuthentication(error);
                            AylaLoginMetric loginMetric = new AylaLoginMetric(
                                    AylaMetric.LogLevel.ERROR,
                                    AylaLoginMetric.MetricType.LOGIN_FAILURE, "authenticate",
                                    "CachedAuthProvider",
                                    AylaMetric.Result.FAILURE, error.getMessage());
                            sendToMetricsManager(loginMetric);
                        }
                    }
                }) {

            @Override
            protected void deliverResponse(AylaAuthorization response) {
                // https://aylanetworks.atlassian.net/browse/SVC-2332
                // Cloud service is returning 200 even if there is an error refreshing
                // the access token. The response in this case looks like this:
                // {"error":"Your access token is invalid"}
                //
                // In this case, we will not have valid fields set in the response object.
                if (response.getAccessToken() == null) {
                    AylaLog.e("CachedAuth", "SVC-2332: 200 returned without auth token");
                    listener.didFailAuthentication(new ServerError(
                            NanoHTTPD.Response.Status.UNAUTHORIZED.getRequestStatus(),
                            null,
                            "Access token not refreshed", null));
                } else {
                    // Update our existing credentials with the response
                    _cachedCredentials.updateFrom(response);
                    AylaLoginMetric loginMetric = new AylaLoginMetric
                            (AylaMetric.LogLevel.INFO, AylaLoginMetric.MetricType.LOGIN_SUCCESS,
                                    "authenticate", "CachedAuthProvider",
                                    AylaMetric.Result.SUCCESS, null);
                    loginMetric.setRequestTotalTime(this.getNetworkTimeMs());
                    sendToMetricsManager(loginMetric);
                    super.deliverResponse(_cachedCredentials);
                }
            }
        };

        request.logResponse();
        lm.sendUserServiceRequest(request);
    }

    private void sendToMetricsManager(AylaMetric metrics){
        AylaMetricsManager metricsManager = AylaNetworks.sharedInstance().getMetricsManager();
        if(metricsManager != null){
            metricsManager.addMessageToUploadsQueue(metrics);
        }
    }
}
