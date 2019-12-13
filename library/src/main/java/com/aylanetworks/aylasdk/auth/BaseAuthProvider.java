package com.aylanetworks.aylasdk.auth;

import com.android.volley.Request;
import com.android.volley.Response;
import com.aylanetworks.aylasdk.AylaAPIRequest;
import com.aylanetworks.aylasdk.AylaJsonRequest;
import com.aylanetworks.aylasdk.AylaNetworks;
import com.aylanetworks.aylasdk.AylaSessionManager;
import com.aylanetworks.aylasdk.AylaSystemSettings;
import com.aylanetworks.aylasdk.AylaUser;
import com.aylanetworks.aylasdk.error.AylaError;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.aylanetworks.aylasdk.error.InvalidArgumentError;
import com.aylanetworks.aylasdk.error.JsonError;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Ayla SDK
 * <p>
 * Copyright 2016 Ayla Networks, all rights reserved
 */

public abstract class BaseAuthProvider implements AylaAuthProvider {

    /**
     * Updates the user profile on the Ayla service to match the fields in the provided AylaUser.
     *
     * @param user            AylaUser containing the updated user information
     * @param successListener Listener to receive the updated user when the operation succeeds
     * @param errorListener   Listener to receive an error if something went wrong
     * @return the {link AylaAPIRequest} for this operation
     */
    @Override
    public AylaAPIRequest updateUserProfile(AylaSessionManager sessionmanager, final AylaUser user,
                                            final Response.Listener<AylaUser> successListener,
                                            ErrorListener errorListener) {
        String url = sessionmanager.userServiceUrl("users.json");

        // First check the user to make sure everything required is set
        if (user == null) {
            errorListener.onErrorResponse(new InvalidArgumentError("User may not be null"));
            return null;
        }

        // Make sure the SDK has been been configured
        AylaSystemSettings settings = AylaNetworks.sharedInstance().getSystemSettings();
        if (settings == null) {
            errorListener.onErrorResponse(new AylaError(AylaError.ErrorType.AylaError,
                    "Library has not been initialized. Call AylaNetworks.initialize() before " +
                            "calling any other methods"));
            return null;
        }

        JSONObject userJson = new JSONObject();
        AylaError error = user.prepareUpdateArguments(userJson);
        if (error != null) {
            errorListener.onErrorResponse(error);
            return null;
        }

        String bodyJson;
        try {
            // Format the update user JSON body
            bodyJson = new JSONObject().put("user", userJson).toString();
        } catch (JSONException e) {
            errorListener.onErrorResponse(new JsonError(null, "Exception constructing " +
                    "updateUserProfile JSON", e));
            return null;
        }

        Response.Listener<AylaAPIRequest.EmptyResponse> completeListener = new Response.Listener<AylaAPIRequest.EmptyResponse>() {
            @Override
            public void onResponse(AylaAPIRequest.EmptyResponse response) {
                if (successListener != null) {
                    successListener.onResponse(user);
                }
            }
        };
        AylaAPIRequest<AylaAPIRequest.EmptyResponse> request = new AylaJsonRequest<>(Request.Method.PUT,
                url, bodyJson, null, AylaAPIRequest.EmptyResponse.class, sessionmanager, completeListener,
                errorListener);

        sessionmanager.sendUserServiceRequest(request);
        return request;
    }

    @Override
    public AylaAPIRequest deleteUser(AylaSessionManager sessionManager,
                                     Response.Listener<AylaAPIRequest.EmptyResponse> successListener,
                                     ErrorListener errorListener) {
        String url = sessionManager.userServiceUrl("users.json");
        AylaAPIRequest<AylaAPIRequest.EmptyResponse> request = new AylaAPIRequest<>(
                Request.Method.DELETE, url, null, AylaAPIRequest.EmptyResponse.class,
                sessionManager, successListener, errorListener);
        sessionManager.sendUserServiceRequest(request);
        return request;
    }

    @Override
    public AylaAPIRequest signout(AylaSessionManager sessionManager, Response.Listener<AylaAPIRequest.EmptyResponse> successListener, ErrorListener errorListener) {
        String url = sessionManager.userServiceUrl("users/sign_out.json");
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", sessionManager.getAuthHeaderValue());
        JSONObject userObject = new JSONObject();
        JSONObject bodyObject = new JSONObject();
        try {
            userObject.put("access_token", sessionManager.getAccessToken());
            bodyObject.put("user", userObject);
        } catch (JSONException e) {
            errorListener.onErrorResponse(new JsonError(null, "JSONException when creating body " +
                    "JSON for shutDown", e));
            return null;
        }
        final String bodyJSON = bodyObject.toString();
        AylaAPIRequest<AylaAPIRequest.EmptyResponse> request = new AylaJsonRequest<>(
                Request.Method.POST, url, bodyJSON, headers, AylaAPIRequest.EmptyResponse.class, null,
                successListener, errorListener);
        sessionManager.sendUserServiceRequest(request);
        return request;
    }
}
