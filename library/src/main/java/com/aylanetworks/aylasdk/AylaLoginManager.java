package com.aylanetworks.aylasdk;

import android.content.Context;
import android.content.SharedPreferences;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.Response.Listener;
import com.aylanetworks.aylasdk.AylaAPIRequest.EmptyResponse;
import com.aylanetworks.aylasdk.auth.AylaAuthProvider;
import com.aylanetworks.aylasdk.auth.AylaAuthorization;
import com.aylanetworks.aylasdk.error.AylaError;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.aylanetworks.aylasdk.error.InvalidArgumentError;
import com.aylanetworks.aylasdk.error.JsonError;
import com.aylanetworks.aylasdk.error.PreconditionError;
import com.aylanetworks.aylasdk.metrics.AylaMetricsManager;
import com.aylanetworks.aylasdk.metrics.AylaMetric;
import com.aylanetworks.aylasdk.metrics.AylaLoginMetric;
import com.aylanetworks.aylasdk.util.ServiceUrls;
import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

/**
 * Class used for all API operations when the user is not signed in. These tasks include:
 * <ul>
 * <li>Sign-in with username / password</li>
 * <li>SSO sign-in</li>
 * <li>Facebook / Google sign-in</li>
 * <li>Password reset</li>
 * <li>Re-send email confirmation</li>
 * </ul>
 */
public class AylaLoginManager {
    private static final String LOG_TAG = "LoginManager";

    /**
     * Package-private constructor. AylaNetworks owns this object.
     */
    AylaLoginManager() {
    }

    /**
     * Signs in the user with the given AylaAuthProvider. When complete, the successResponse
     * listener will be called if successful with the AylaAuthorization object of the logged-in
     * user. Errors will cause the errorListener to be called with an AylaError result indicating
     * the cause of the failure.
     *
     * @param authProvider    Provider to perform the sign-in operation
     * @param sessionName     Name for this session. If a session with this name already exists,
     *                        the existing session will be closed before opening this one
     * @param successListener Listener to receive the AylaUser object on login success
     * @param errorListener   Listener to receive an AylaError in case of failures
     */
    public void signIn(final AylaAuthProvider authProvider,
                       final String sessionName,
                       final Listener<AylaAuthorization> successListener,
                       final ErrorListener errorListener) {
        if (sessionName == null) {
            errorListener.onErrorResponse(new InvalidArgumentError("Session name may not be null"));
            return;
        }
        authProvider.authenticate(new AylaAuthProvider.AuthProviderListener() {
            @Override
            public void didAuthenticate(AylaAuthorization authorization, boolean isOfflineUse) {
                // Let the CoreManager know we have an authenticated sign-in
                AylaNetworks.sharedInstance().signInSuccessful(sessionName, authorization,
                        authProvider, isOfflineUse);
                successListener.onResponse(authorization);
            }

            @Override
            public void didFailAuthentication(AylaError error) {
                errorListener.onErrorResponse(error);
                Context context = AylaNetworks.sharedInstance().getContext();
                Gson gson = AylaNetworks.sharedInstance().getGson();
                if (context != null && gson != null) {
                    AylaLoginMetric loginMetric = new AylaLoginMetric(AylaMetric.LogLevel
                            .ERROR, AylaLoginMetric.MetricType.LOGIN_FAILURE, "signin",
                            authProvider.getClass().getSimpleName(),
                            AylaMetric.Result.FAILURE, error.getMessage());
                    AylaNetworks.sharedInstance().getMetricsManager().addMessageToUploadsQueue(
                            loginMetric);
                }
            }
        }, sessionName);
    }

    /**
     * Creates an account on the Ayla service using the supplied AylaUser. The AylaUser object
     * should contain all of the fields that should be set in the new account. Required fields are:
     * email
     * password
     * firstname
     * lastname
     * country
     *
     * @param userInfo        Object containing the user information for the new account
     * @param template        An optional template object to configure the welcome email sent to the new
     *                        user
     * @param successListener Listener to receive the new AylaUser after creation
     * @param errorListener   Listener to receive an error should one occur
     * @return the AylaAPIRequest object for this request
     */
    public AylaAPIRequest signUp(AylaUser userInfo,
                                 AylaEmailTemplate template,
                                 Listener<AylaUser> successListener,
                                 ErrorListener errorListener) {
        String url = userServiceUrl("users.json");
        // Put together our JSON body. It should look like this:
        // {
        //   user: {
        //            application: {app_id: <app_id> app_secret: <app_secret>}
        //            ... user info
        //         }
        //   email_template_id: <template id>
        //   email_subject: <email subject>
        //   email_body_html: <html body>
        // }

        JSONObject bodyObject = new JSONObject();
        String userJsonString = AylaNetworks.sharedInstance().getGson().toJson(userInfo,
                AylaUser.class);
        JSONObject userObject = null;
        try {
            userObject = new JSONObject(userJsonString);
        } catch (JSONException e) {
            AylaLog.e(LOG_TAG, "Failed parsing user data");
            errorListener.onErrorResponse(new JsonError(userJsonString, "Failed parsing user " +
                    "data", e));
            return null;
        }

        String templateId = null;
        String emailSubject = null;
        String emailBodyHtml = null;
        if (template != null) {
            templateId = template.getEmailTemplateId();
            emailSubject = template.getEmailSubject();
            emailBodyHtml = template.getEmailBodyHtml();
        }

        JSONObject applicationObject = new JSONObject();
        AylaSystemSettings settings = AylaNetworks.sharedInstance().getSystemSettings();
        // Put together the body
        try {
            applicationObject.put("app_id", settings.appId);
            applicationObject.put("app_secret", settings.appSecret);

            userObject.put("application", applicationObject);

            bodyObject.put("user", userObject);

            if (templateId != null) {
                bodyObject.put("email_template_id", templateId);
            }
            if (emailSubject != null) {
                bodyObject.put("email_subject", emailSubject);
            }
            if (emailBodyHtml != null) {
                bodyObject.put("email_body_html", emailBodyHtml);
            }
        } catch (JSONException e) {
            AylaLog.e(LOG_TAG, "Failed to create body JSON for signUp()");
            errorListener.onErrorResponse(new JsonError(null, "Failed to create body JSON for " +
                    "signUp()", e));
            return null;
        }

        String bodyJsonString = bodyObject.toString();
        AylaAPIRequest request = new AylaJsonRequest<AylaUser>(
                Request.Method.POST,
                url, bodyJsonString, null, AylaUser.class, null, successListener, errorListener);

        sendUserServiceRequest(request);
        return request;
    }

    /**
     * Notifies the service to re-send the confirmation email that was sent when the account was
     * first created. If successful, the service will send an email to the specified email
     * address using the template provided, or the default template if none was specified.
     *
     * @param emailAddress The email address of the account to re-send the confirmation for
     * @param template Optional email template information, may be null
     * @param successListener Listener called with an EmptyResponse on success
     * @param errorListener Listener called with an AyalError on failure
     * @return the AylaAPIRequest for this call, which may be canceled.
     */
    public AylaAPIRequest resendConfirmationEmail(String emailAddress,
                                                  AylaEmailTemplate template,
                                                  Listener<EmptyResponse> successListener,
                                                  ErrorListener errorListener) {
        // {\"user\":{\"email\":\"email@yahoo.com\",
        //   "application": {"app_id":"device_service_id", "app_secret":"device_service_secret" }}}
        if (emailAddress == null) {
            errorListener.onErrorResponse(new InvalidArgumentError("emailAddress may not be null"));
            return null;
        }

        // Get the app configuration from Core
        AylaSystemSettings settings = AylaNetworks.sharedInstance().getSystemSettings();
        if (settings == null || settings.appId == null || settings.appSecret == null) {
            errorListener.onErrorResponse(new PreconditionError("Library has not been " +
                    "initialized, or the appId or appSecret were not set in the system settings"));
            return null;
        }

        String emailTemplateId = null;
        String emailSubject = null;
        String emailBody = null;
        if (template != null) {
            emailTemplateId = template.getEmailTemplateId();
            emailSubject = template.getEmailSubject();
            emailBody = template.getEmailBodyHtml();
        }

        JSONObject topObject = new JSONObject();
        JSONObject userObject = new JSONObject();
        JSONObject applicationObject = new JSONObject();
        try {
            applicationObject.put("app_id", settings.appId);
            applicationObject.put("app_secret", settings.appSecret);
            userObject.put("application", applicationObject);
            userObject.put("email", emailAddress);
            topObject.put("user", userObject);
            if (emailTemplateId != null) {
                topObject.put("email_template_id", emailTemplateId);
            }
            if (emailSubject != null) {
                topObject.put("email_subject", emailSubject);
            }
            if (emailBody != null) {
                topObject.put("email_body_html", emailBody);
            }
        } catch (JSONException e) {
            AylaLog.e(LOG_TAG, "Could not create resend confirmation JSON");
            errorListener.onErrorResponse(new JsonError(null, "Could not create resend " +
                    "confirmation JSON", e));
            return null;
        }

        // Create the body bytes
        String jsonString = topObject.toString();
        AylaLog.v(LOG_TAG, "resendConfirmationEmail: " + jsonString);

        String url = userServiceUrl("users/confirmation.json");
        AylaAPIRequest<EmptyResponse> request = new AylaJsonRequest<EmptyResponse>(
                Request.Method.POST, url, jsonString, null, EmptyResponse.class, null,
                successListener,
                errorListener);
        // BUG: SVC-6611
        // Cloud API takes over 6 seconds to return for some reason
        request.setRetryPolicy(new DefaultRetryPolicy(10000, 0, 1));
        sendUserServiceRequest(request);
        return request;
    }

    /**
     * Requests a password reset on the service for the specified email address. The service will
     * send the specified email address an email with a link to reset the password. The details
     * of the email may be specified via the template argument. If this argument is null, the
     * default email template will be used.
     *
     * @param email Email address of the account to have the password reset
     * @param template Email template, optional, may be null
     * @param successListener Listener called if the request was successful
     * @param errorListener Listener called if an error occurred
     * @return an AylaAPIRequest object which may be used to cancel the request
     */
    public AylaAPIRequest requestPasswordReset(String email, AylaEmailTemplate template,
                                               Listener<EmptyResponse> successListener,
                                               ErrorListener errorListener) {
        if (email == null) {
            errorListener.onErrorResponse(new InvalidArgumentError("email may not be null"));
            return null;
        }

        // Get the app configuration from Core
        AylaSystemSettings settings = AylaNetworks.sharedInstance().getSystemSettings();
        if (settings == null || settings.appId == null || settings.appSecret == null) {
            errorListener.onErrorResponse(new PreconditionError("Library has not been " +
                    "initialized, or the appId or appSecret were not set in the system settings"));
            return null;
        }

        String emailTemplateId = null;
        String emailSubject = null;
        String emailBody = null;
        if (template != null) {
            emailTemplateId = template.getEmailTemplateId();
            emailSubject = template.getEmailSubject();
            emailBody = template.getEmailBodyHtml();
        }

        JSONObject topObject = new JSONObject();
        JSONObject userObject = new JSONObject();
        JSONObject applicationObject = new JSONObject();
        try {
            applicationObject.put("app_id", settings.appId);
            applicationObject.put("app_secret", settings.appSecret);
            userObject.put("application", applicationObject);
            userObject.put("email", email);
            topObject.put("user", userObject);
            if (emailTemplateId != null) {
                topObject.put("email_template_id", emailTemplateId);
            }
            if (emailSubject != null) {
                topObject.put("email_subject", emailSubject);
            }
            if (emailBody != null) {
                topObject.put("email_body_html", emailBody);
            }
        } catch (JSONException e) {
            AylaLog.e(LOG_TAG, "Could not create reset password JSON");
            errorListener.onErrorResponse(new JsonError(null, "Could not create resend " +
                    "confirmation JSON", e));
            return null;
        }

        String json = topObject.toString();
        final byte[] bodyData = json.getBytes();

        String url = userServiceUrl("users/password.json");
        AylaAPIRequest request = new AylaJsonRequest<EmptyResponse>(Request.Method.POST,
                url, json, null, EmptyResponse.class, null, successListener, errorListener);

        sendUserServiceRequest(request);
        return request;
    }

    /**
     * Performs a password reset for the user after the user has received the reset token from
     * the Ayla Cloud Service.
     *
     * @param newPassword New password for the account
     * @param token Token received in email after calling {@link #requestPasswordReset}
     * @param successListener Listener called if the operation is successful
     * @param errorListener Error listener called if an error was encountered
     * @return the AylaAPIRequest for this request, which may be canceled
     */
    public AylaAPIRequest resetPassword(String newPassword, String token,
                                        Listener<EmptyResponse> successListener,
                                        ErrorListener errorListener) {
        String url = userServiceUrl("users/password.json");
        JSONObject rootObject = new JSONObject();
        JSONObject userObject = new JSONObject();
        try {
            userObject.put("reset_password_token", token);
            userObject.put("password", newPassword);
            userObject.put("password_confirmation", newPassword);
            rootObject.put("user", userObject);
        } catch (JSONException e) {
            errorListener.onErrorResponse(new JsonError(null, "Could not create request JSON",
                    e));
            return null;
        }

        AylaJsonRequest<EmptyResponse> request = new AylaJsonRequest<>(Request.Method.PUT,
                url, rootObject.toString(), null, EmptyResponse.class, null, successListener,
                errorListener);
        sendUserServiceRequest(request);
        return request;
    }

    /**
     * Confirms the users email address by validating the token received in email in response to
     * a sign-up request initiated by {@link #signUp}. Once this API returns success, the user
     * account has been confirmed and the user may sign in.
     *
     * @param token Token received in the user's email after signing up
     * @param successListener Listener called if the operation is successful
     * @param errorListener Listener called if an error occurred
     * @return the AylaAPIRequest for this operation, which may be canceled
     */
    public AylaAPIRequest confirmSignUp(String token, Listener<EmptyResponse> successListener,
                                        ErrorListener errorListener) {
        JSONObject rootObject = new JSONObject();
        try {
            rootObject.put("confirmation_token", token);
        } catch (JSONException e) {
            errorListener.onErrorResponse(new JsonError(null, "Failed to create JSON", e));
        }

        String url = userServiceUrl("users/confirmation.json");
        AylaJsonRequest<EmptyResponse> request = new AylaJsonRequest<>(Request.Method.PUT,
                url, rootObject.toString(), null, EmptyResponse.class, null, successListener,
                errorListener);

        sendUserServiceRequest(request);
        return request;
    }


    /**
     * Returns a URL for the User service pointing to the specified path.
     * This URL will vary depending on the AylaSystemSettings provided to the CoreManager
     * during initialization.
     *
     * @param path Path of the URL to be returned, e.g. "users/sign_in.json"
     * @return Full URL, e.g. "https://ads-field.aylanetworks.com/apiv1/users/sign_in.json"
     */
    public String userServiceUrl(String path) {
        return AylaNetworks.sharedInstance().getServiceUrl(ServiceUrls.CloudService.User, path);
    }

    /**
     * Enqueues the provided request to the Ayla Cloud User Service.
     *
     * @param request the request to send
     * @return the request, which can be used for cancellation.
     */
    public AylaAPIRequest sendUserServiceRequest(AylaAPIRequest request) {
        return AylaNetworks.sharedInstance().sendUserServiceRequest(request);
    }
    
}
