package com.aylanetworks.aylasdk.auth;
/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import android.content.Context;
import android.content.SharedPreferences;
import com.android.volley.Request;
import com.android.volley.Response;
import com.aylanetworks.aylasdk.AylaAPIRequest;
import com.aylanetworks.aylasdk.AylaJsonRequest;
import com.aylanetworks.aylasdk.AylaLog;
import com.aylanetworks.aylasdk.AylaLoginManager;
import com.aylanetworks.aylasdk.AylaNetworks;
import com.aylanetworks.aylasdk.AylaSystemSettings;
import com.aylanetworks.aylasdk.error.AuthError;
import com.aylanetworks.aylasdk.error.AylaError;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.aylanetworks.aylasdk.metrics.AylaLoginMetric;
import com.aylanetworks.aylasdk.metrics.AylaMetric;
import com.aylanetworks.aylasdk.metrics.AylaMetricsManager;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * AylaAuthProvider that provides authorization to the Ayla network using a
 * username and password.
 */
public class UsernameAuthProvider extends BaseAuthProvider {
    private static final String LOG_TAG = "USERNAME_AUTH";
    private String _username;
    private String _password;

    /**
     * Constructor
     *
     * @param username Username to sign-in, generally an email address
     * @param password Password to sign-in
     */
    public UsernameAuthProvider(String username, String password) {
        _username = username;
        _password = password;
    }

    @Override
    public void authenticate(AuthProviderListener listener, String sessionName) {
        authenticate(listener);

    }

    @Override
    public void authenticate(final AuthProviderListener listener) {
        AylaLoginManager loginManager = AylaNetworks.sharedInstance().getLoginManager();
        String url = loginManager.userServiceUrl("users/sign_in.json");

        // Login expects JSON data in this format:
        //
        // { "user": {
        //      "email":"user@aylanetworks.com",
        //      "password":"password",
        //      "application":{
        //          "app_id":"my_app_id",
        //          "app_secret":"my_app_secret"
        //      }
        //    }
        // }

        AylaSystemSettings settings = AylaNetworks.sharedInstance().getSystemSettings();
        // Construct a JSON object to contain the parameters.
        JSONObject user = new JSONObject();
        JSONObject userParam = new JSONObject();
        try {
            userParam.put("email", _username);
            userParam.put("password", _password);
            JSONObject application = new JSONObject();
            application.put("app_id", settings.appId);
            application.put("app_secret", settings.appSecret);
            userParam.put("application", application);
            user.put("user", userParam);
        } catch (JSONException e) {
            listener.didFailAuthentication(new AuthError("JSONException in signIn()", e));
            return;
        }

        String bodyText = user.toString();

        // Create our request object with some overrides to handle the POST body and
        // updating the CoreManager when we succeed
        AylaAPIRequest<AylaAuthorization> request = new AylaJsonRequest<AylaAuthorization>(
                Request.Method.POST,
                url,
                bodyText,
                null,
                AylaAuthorization.class,
                null, // No session manager exists until we are logged in!
                new Response.Listener<AylaAuthorization>() {
                    @Override
                    public void onResponse(AylaAuthorization response) {
                        listener.didAuthenticate(response, false);
                    }
                },
                new ErrorListener() {
                    @Override
                    public void onErrorResponse(AylaError error) {
                        listener.didFailAuthentication(error);
                        AylaLoginMetric loginMetric = new AylaLoginMetric
                                (AylaMetric.LogLevel.ERROR, AylaLoginMetric.MetricType.LOGIN_FAILURE,
                                        "authenticate", "UsernameAuthProvider",
                                        AylaMetric.Result.FAILURE, error.getMessage());
                        sendToMetricsManager(loginMetric);
                    }
                }){
            @Override
            protected void deliverResponse(AylaAuthorization response) {
                super.deliverResponse(response);
                AylaLoginMetric loginMetric = new AylaLoginMetric(
                        AylaMetric.LogLevel.INFO, AylaLoginMetric.MetricType.LOGIN_SUCCESS,
                        "authenticate", "UsernameAuthProvider",
                        AylaMetric.Result.SUCCESS, null);
                loginMetric.setRequestTotalTime(this.getNetworkTimeMs());
                sendToMetricsManager(loginMetric);
            }
        };

        loginManager.sendUserServiceRequest(request);
    }

    private void sendToMetricsManager(AylaMetric metrics){
        AylaMetricsManager metricsManager = AylaNetworks.sharedInstance().getMetricsManager();
        if(metricsManager != null){
            metricsManager.addMessageToUploadsQueue(metrics);
        }
    }


}

