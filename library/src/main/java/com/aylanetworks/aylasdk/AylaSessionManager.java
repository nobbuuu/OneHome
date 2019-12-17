package com.aylanetworks.aylasdk;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;
import com.aylanetworks.AylaLinkedAccount;
import com.aylanetworks.aylasdk.AylaAPIRequest.EmptyResponse;
import com.aylanetworks.aylasdk.auth.AylaAuthProvider;
import com.aylanetworks.aylasdk.auth.AylaAuthorization;
import com.aylanetworks.aylasdk.error.AylaError;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.aylanetworks.aylasdk.error.InternalError;
import com.aylanetworks.aylasdk.error.InvalidArgumentError;
import com.aylanetworks.aylasdk.error.JsonError;
import com.aylanetworks.aylasdk.metrics.AylaAppLifecycleMetric;
import com.aylanetworks.aylasdk.metrics.AylaMetric;
import com.aylanetworks.aylasdk.metrics.AylaLoginMetric;
import com.aylanetworks.aylasdk.metrics.AylaMetricsManager;
import com.aylanetworks.aylasdk.util.EmptyListener;
import com.aylanetworks.aylasdk.util.ServiceUrls;
import com.aylanetworks.aylasdk.util.URLHelper;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

/**
 * The AylaSessionManager represents an authenticated session with the Ayla Cloud Service.
 * Successfully signing in via {@link AylaLoginManager#signIn} creates an AylaSessionManager with
 * the sessionName provided to {@link AylaLoginManager#signIn} and may always be obtained by a
 * call to {@link AylaNetworks#getSessionManager(String)}.
 * <p>
 * Applications should register an object to listen for AylaSessionManager events by implementing
 * the {@link SessionManagerListener} interface and
 * registering via {@link #addListener(SessionManagerListener)}. Listeners are notified when the
 * AylaSessionManager refreshes the session's authorization token or if the session is closed.
 * <p>
 * Sessions are closed (signs out the user) by calling {@link #shutDown}. Sessions also may be
 * closed without an explicit call to {@link #shutDown} if the service refuses to authenticate a
 * request to refresh the session token.
 */
public class AylaSessionManager {
    private static final String LOG_TAG = "SessionManager";

    /**
     * Time in seconds before our auth token expires that we will refresh our token. Currently
     * set to 12 hours.
     */
    private static final long REFRESH_GRACE_PERIOD = (60*60*12);
    /**
     * Listeners to receive session notifications
     */
    private final Set<SessionManagerListener> _listeners = new HashSet<>();
    /**
     * Credential obtained from one of the sign-in processes
     */
    private AylaAuthorization _authorization;

    /**
     * Cache of devices and their configurations used for offline access
     */
    private AylaCache _aylaCache;

    /**
     * Auth provider for the session.
     */
    private AylaAuthProvider _authProvider;


    /**
     * Device manager for this session. Store a weak reference, as the SessionManager already has
     * a reference to this object.
     */
    private AylaDeviceManager _deviceManager;
    private AylaDSManager _dsManager;
    private Handler _sessionTimerHandler;
    private String _sessionName;
    private boolean _isCachedSession;
    private AylaRulesService _rulesService;


    public AylaAuthProvider getAuthProvider(){
        return _authProvider;
    }
    public boolean isCachedSession() {
        return _isCachedSession;
    }

    public void setCachedSession(boolean isCachedSession) {
        this._isCachedSession = isCachedSession;
        AylaDeviceManager dm = getDeviceManager();
        if (dm != null && dm.getState() != AylaDeviceManager.DeviceManagerState.Ready) {
            dm.fetchDevices();
        }
    }

    /**
     * Package-level constructor. A SessionManager is created by the CoreManager when the user
     * logs in using this constructor.
     *
     * @param authorization AylaUser representing the user who has just logged in
     */
    AylaSessionManager(AylaAuthorization authorization, AylaAuthProvider authProvider,
                       String sessionName) {
        _sessionName = sessionName;
        _authProvider = authProvider;
        _sessionTimerHandler = new Handler(Looper.getMainLooper());
        _aylaCache = new AylaCache(this);

        authorizationRefreshed(authorization);
        _deviceManager = new AylaDeviceManager(this);
       if(AylaNetworks.sharedInstance().getSystemSettings().allowDSS){
           startDSS();
       }
       _rulesService = new AylaRulesService(this);
    }

    /**
     * Private default constructor. Use {@link AylaSessionManager(AylaAuthorization)} to create
     * a session manager. This is usually done by the AylaNetworks on successful sign-in.
     */
    private AylaSessionManager() {
    }

    /**
     * Adds the specified listener to be notifed of SessionManager events
     *
     * @param listener Listener to be notifed of SessionManager events
     */
    public void addListener(SessionManagerListener listener) {
        synchronized (_listeners) {
            _listeners.add(listener);
        }
    }

    /**
     * Returns the device manager for this session. The {@link AylaDeviceManager} class manages
     * devices for the logged-in user.
     *
     * @return the {@link AylaDeviceManager} created for this session.
     */
    public AylaDeviceManager getDeviceManager() {
        return _deviceManager;
    }

    /**
     * Returns the DSS manager for this session. The {@link AylaDSManager} class manages
     * datastream connection for devices for the logged-in user.
     *
     * @return the {@link AylaDSManager} created for this session.
     */
    public AylaDSManager getDSManager() {
        return _dsManager;
    }

    public AylaCache getCache() {
        return _aylaCache;
    }

    /**
     * Initialize a Datastream manager. DSManager will start when deviceManager initialization is
     * complete.
     */
    public void startDSS(){
        _dsManager = new AylaDSManager(this);
    }

    /**
     * Stop DSManager
     */
    public void stopDSS(){
        _dsManager.onPause();
    }
    /**
     * Removes the specified listener from the list of listeners to be notified of SessionManager
     * events.
     *
     * @param listener Listner to no longer receive SessionManager events
     */
    public void removeListener(SessionManagerListener listener) {
        synchronized (_listeners) {
            _listeners.remove(listener);
        }
    }

    /**
     * Returns the auth value required in the HTTP headers for all network requests to the service.
     *
     * @return The auth value for the Authorization: header in HTTPS requests to the cloud
     */
    public String getAuthHeaderValue() {
        if (_authorization == null) {
            return null;
        }

        return "auth_token " + _authorization.getAccessToken();
    }

    /**
     * Returns the access token.
     *
     * @return Returns the access token from the Authorization if Authorization is not null
     */
    public String getAccessToken() {
        if (_authorization == null) {
            return null;
        }

        return _authorization.getAccessToken();
    }

    public String getSessionName() {
        return _sessionName;
    }

    /**
     * Shuts down the current session and logs out the user. If an error occurred when sending
     * the sign-out message to the cloud service, the session will still be closed and the user
     * will need to sign in again.
     *
     * @param successListener Listener to receive the results of the network sign-out call.
     * @param errorListener   Listener to receive an error from the network sign-out call.
     * @return the AylaAPIRequest for this command. While the request to sign out from the server
     * may be canceled, the session will be closed regardless.
     */
    public AylaAPIRequest shutDown(final Response.Listener<EmptyResponse> successListener,
                                   final ErrorListener errorListener) {
        onPause();
        notifySessionClosed(null);
        _listeners.clear();

        String currentAuthToken = getAuthHeaderValue();
        _deviceManager.shutDown();
        _rulesService.shutDown();
        AylaAPIRequest request = _authProvider.signout(this, new Response.Listener<EmptyResponse> () {
            @Override
            public void onResponse(EmptyResponse response) {
                if (!AylaNetworks.isShutDown()) {
                    successListener.onResponse(response);
                    Context context = AylaNetworks.sharedInstance().getContext();
                    Gson gson = AylaNetworks.sharedInstance().getGson();
                    if( context != null && gson != null){
                        AylaLog.d(LOG_TAG, "saving logout success logs for next session");
                        AylaLoginMetric loginMetric = new AylaLoginMetric(
                                AylaMetric.LogLevel.INFO, AylaLoginMetric.MetricType.LOGOUT_SUCCESS,
                                "signout", _authProvider.getClass().getSimpleName(),
                                AylaMetric.Result.SUCCESS, null);
                       AylaNetworks.sharedInstance().getMetricsManager().addMessageToUploadsQueue
                               (loginMetric);
                       AylaNetworks.sharedInstance().getMetricsManager().onPause();

                    }
                }
            }
        }, new ErrorListener() {
            @Override
            public void onErrorResponse(AylaError error) {
                if (!AylaNetworks.isShutDown()) {
                    errorListener.onErrorResponse(error);
                    Context context = AylaNetworks.sharedInstance().getContext();
                    Gson gson = AylaNetworks.sharedInstance().getGson();
                    if( context != null && gson != null){
                        AylaLog.d(LOG_TAG, "saving logout failure logs for next session");
                        AylaLoginMetric loginMetric = new AylaLoginMetric(AylaMetric.LogLevel.INFO,
                                AylaLoginMetric.MetricType.LOGOUT_FAILURE, "signout",
                                _authProvider.getClass().getSimpleName(), AylaMetric.Result.FAILURE,
                                error.getMessage());
                        AylaNetworks.sharedInstance().getMetricsManager().addMessageToUploadsQueue(
                                loginMetric);
                        AylaNetworks.sharedInstance().getMetricsManager().onPause();

                    }
                }
            }
        });

        _authorization = null;
        return request;
    }

    /**
     * Refreshes an authorization with the Ayla cloud service. Authorizations have expirations,
     * and need to be refreshed from time to time. The library generally will take care of
     * updating authorization for you. When authorization is refreshed, if a Session is active,
     * the SessionManager will notify listeners of the refresh.
     *
     * @param successListener Listener to receive the results of a successful call
     * @param errorListener   Listener to receive errors in case of failure
     * @return the AylaAPIRequest to refresh the authorization, or null if an error occurred
     * before sending
     */
    public AylaAPIRequest
    refreshAuthorization(final Response.Listener<AylaAuthorization> successListener,
                         final ErrorListener errorListener) {
        if (_authorization == null ||
                _authorization.getRefreshToken() == null ||
                _authorization.getAccessToken() == null) {
            errorListener.onErrorResponse(new InvalidArgumentError("Invalid authorization object"));
            return null;
        }

        // Expected JSON body we need to send looks like this:
        // {"user":{"refresh_token":"3ccd999effb335c50775d739ece32ab8"}}
        JSONObject userObject = new JSONObject();
        JSONObject bodyObject = new JSONObject();
        try {
            userObject.put("refresh_token", _authorization.getRefreshToken());
            bodyObject.put("user", userObject);
        } catch (JSONException e) {
            errorListener.onErrorResponse(new JsonError(null, "JSONException when creating body " +
                    "JSON for refreshAuthorization", e));
            return null;
        }
        final String bodyJSON = bodyObject.toString();

        String url = userServiceUrl("users/refresh_token.json");

        // We may not be signed-in, so will need to provide the access token in the HTTP headers
        Map<String, String> additionalHeaders = new HashMap<>();
        additionalHeaders.put("Authorization", "auth_token " + _authorization.getAccessToken());
        AylaAPIRequest<AylaAuthorization> request = new AylaJsonRequest<AylaAuthorization>(
                Request.Method.POST,
                url,
                bodyJSON,
                additionalHeaders,
                AylaAuthorization.class,
                this,
                successListener,
                errorListener) {
            @Override
            protected void deliverResponse(AylaAuthorization response) {
                if (_authorization != null) {
                    _authorization.updateFrom(response);
                    // Let the SessionManager, if present, know that we have updated our authorization
                    authorizationRefreshed(_authorization);
                }
                super.deliverResponse(response);
            }
        };

        sendUserServiceRequest(request);

        return request;
    }

    /**
     * Updates the user profile on the Ayla service to match the fields in the provided AylaUser.
     *
     * @param user            AylaUser containing the updated user information
     * @param successListener Listener to receive the updated user when the operation succeeds
     * @param errorListener   Listener to receive an error if something went wrong
     * @return the {link AylaAPIRequest} for this operation
     */
    public AylaAPIRequest updateUserProfile(AylaUser user,
                                            Response.Listener<AylaUser> successListener,
                                            ErrorListener errorListener) {
        AylaAPIRequest<EmptyResponse> request = _authProvider.updateUserProfile(this, user,
                successListener, errorListener);
        return request;
    }

    /**
     * Use this method to modify the user's email address.
     * The user must be authenticated/logged-in before calling this method.
     *
     * @param email           New email address
     * @param successListener Listener to receive result on success
     * @param errorListener   Listener to receive an error if something went wrong
     * @return the {link AylaAPIRequest} for this operation
     */
    public AylaAPIRequest updateUserEmailAddress(String email,
                                                 Response.Listener<EmptyResponse> successListener,
                                                 ErrorListener errorListener) {

        if (TextUtils.isEmpty(email) || !email.contains("@")) { // simple check
            errorListener.onErrorResponse(new InvalidArgumentError("Invalid Email address"));
            return null;
        }

        String url = userServiceUrl("users/update_email.json");

        String bodyJson;
        try {
            bodyJson = new JSONObject().put("email", email).toString();
        } catch (JSONException e) {
            errorListener.onErrorResponse(new JsonError(null, "Exception constructing " +
                    "updateUserEmailAddress JSON", e));
            return null;
        }

        AylaAPIRequest<EmptyResponse> request = new AylaJsonRequest<EmptyResponse>(Request.Method.PUT,
                url,
                bodyJson,
                null,
                EmptyResponse.class,
                this,
                successListener,
                errorListener);

        sendUserServiceRequest(request);
        return request;
    }

    /**
     * Updates the user's password with a new password. The user must be logged in to the Ayla
     * Cloud service and supply the correct current password and a different new password.
     *
     * @param currentPassword User's current password
     * @param newPassword New password to set
     * @param successListener Listener called on success
     * @param errorListener Listener called with an error should one occur
     *
     * @return the AylaAPIRequest for this method, which may be canceled
     */
    public AylaAPIRequest updatePassword(String currentPassword, String newPassword,
                                         Response.Listener<EmptyResponse> successListener,
                                         ErrorListener errorListener) {
        if (TextUtils.isEmpty(currentPassword) || TextUtils.isEmpty(newPassword)) {
            errorListener.onErrorResponse(new InvalidArgumentError("Current and new password " +
                    "must not be empty or null"));
            return null;
        }

        if (TextUtils.equals(currentPassword, newPassword)) {
            errorListener.onErrorResponse(new InvalidArgumentError("Current and new password are " +
                    "identical"));
            return null;
        }

        String url = userServiceUrl("users.json");

        JSONObject root = new JSONObject();
        JSONObject user = new JSONObject();

        try {
            user.put("current_password", currentPassword);
            user.put("password", newPassword);
            root.put("user", user);
        } catch (JSONException e) {
            AylaLog.e(LOG_TAG, "Failed to create updatePassword JSON: " + e);
            errorListener.onErrorResponse(new JsonError(null, "Exception creating updatePassword " +
                    "user JSON", e));
            return null;
        }

        AylaJsonRequest<EmptyResponse> request = new AylaJsonRequest<>(
                Request.Method.PUT,
                url,
                root.toString(),
                null,
                EmptyResponse.class,
                this,
                successListener,
                errorListener);

        sendUserServiceRequest(request);
        return request;
    }

    /**
     * Fetches the current user profile from the cloud service. The returned user will contain all
     * fields filled out from the service.
     * <p>
     * The user must be logged in to retrieve this information.
     * <p>
     * Possible errors are:
     * <ul>
     * <li>{@link com.aylanetworks.aylasdk.error.AuthError} - The user is not logged in</li>
     * <li>{@link com.aylanetworks.aylasdk.error.NetworkError} - A network error occurred</li>
     * <li>{@link }</li>
     * </ul>
     *
     * @param successListener Listener to receive the AylaUser when the operation succeeds
     * @param errorListener   Listener to receive an error if something went wrong
     * @return the {link AylaAPIRequest} for this operation
     */
    public AylaAPIRequest fetchUserProfile(Response.Listener<AylaUser> successListener,
                                           final ErrorListener errorListener) {
        String url = userServiceUrl("users/get_user_profile.json");

        AylaAPIRequest<AylaUser> request = new AylaAPIRequest<>(Request.Method.GET,
                url,
                null,
                AylaUser.class,
                this,
                successListener,
                errorListener);

        // Queue up the request
        AylaNetworks.sharedInstance().getUserServiceRequestQueue().add(request);
        return request;
    }

    public void onPause() {
        AylaAppLifecycleMetric appLifecycleMetric = new AylaAppLifecycleMetric
                (AylaMetric.LogLevel.INFO, AylaAppLifecycleMetric.MetricType.APP_BACKGROUND,
                        "onPause");
        AylaNetworks.sharedInstance().getMetricsManager().addMessageToUploadsQueue(appLifecycleMetric);
        if(_sessionTimerHandler != null){
            _sessionTimerHandler.removeCallbacksAndMessages(null);
        }
        _deviceManager.onPause();
        if(_dsManager != null){
            _dsManager.onPause();
        }
    }

    public void onResume() {
        // Refresh our auth token if it's getting close to the expiration, to make sure we're still
        // authorized
        if (_authorization != null && _authorization.getSecondsToExpiry() < REFRESH_GRACE_PERIOD) {
            refreshAuthorization(
                    new Response.Listener<AylaAuthorization>() {
                        @Override
                        public void onResponse(AylaAuthorization response) {
                            AylaLog.i(LOG_TAG, "Authorization refreshed in onResume");
                            _deviceManager.onResume();
                            if (_dsManager != null) {
                                _dsManager.onResume();
                            }
                            authorizationRefreshed(response);
                            AylaAppLifecycleMetric appLifecycleMetric = new AylaAppLifecycleMetric
                                    (AylaMetric.LogLevel.INFO,
                                            AylaAppLifecycleMetric.MetricType.APP_LAUNCH, "onResume");
                            AylaNetworks.sharedInstance().getMetricsManager().addMessageToUploadsQueue
                                    (appLifecycleMetric);
                        }
                    },
                    new ErrorListener() {
                        @Override
                        public void onErrorResponse(AylaError error) {
                            AylaLog.e(LOG_TAG, "Failed to refresh session token in onResume()");
                            if (!isCachedSession()) {
                                notifySessionClosed(error);
                                shutDown(new EmptyListener<EmptyResponse>(),
                                        new EmptyListener<>());
                            }
                        }
                    });
        } else{
            _deviceManager.onResume();
            if (_dsManager != null && !_deviceManager.isCachedDeviceList()) {
                _dsManager.onResume();
            }
            AylaAppLifecycleMetric appLifecycleMetric = new AylaAppLifecycleMetric
                    (AylaMetric.LogLevel.INFO, AylaAppLifecycleMetric.MetricType.APP_LAUNCH,
                            "onResume");
            AylaNetworks.sharedInstance().getMetricsManager().addMessageToUploadsQueue
                    (appLifecycleMetric);
        }
    }

    /**
     * Enqueues the provided request to the Ayla Cloud User Service.
     *
     * @param request the request to send
     * @return the AylaAPIRequest for this operation
     */
    public AylaAPIRequest sendUserServiceRequest(AylaAPIRequest<?> request) {
        request.setShouldCache(false);
        request.logResponse();
        AylaNetworks.sharedInstance().getUserServiceRequestQueue().add(request);
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
     * Notifies all listeners that the session was closed.
     *
     * @param error Error causing the closure, or null if the user signed out normally
     */
    private void notifySessionClosed(AylaError error) {
        // Notify the core manager first
        AylaNetworks.sharedInstance().sessionClosed(getSessionName());
        synchronized (_listeners) {
            for (SessionManagerListener l : _listeners) {
                l.sessionClosed(getSessionName(), error);
            }
        }
    }

    /**
     * Notifies listeners that our authorization has been refreshed
     *
     * @param authorization the refreshed authorization
     */
    private void notifyAuthorizationRefreshed(AylaAuthorization authorization) {
        synchronized (_listeners) {
            for (SessionManagerListener l : _listeners) {
                l.authorizationRefreshed(getSessionName(), authorization);
            }
        }
    }

    /**
     * Called when the authorization object used for network authentication has been refreshed.
     *
     * @param authorization the new authorization
     */
    public void authorizationRefreshed(AylaAuthorization authorization) {
        _authorization = authorization;
        // Notify listeners
        synchronized (_listeners) {
            for (SessionManagerListener l : _listeners) {
                l.authorizationRefreshed(getSessionName(), authorization);
            }
        }

        // Set up a timer to refresh authorization
        long expireTime = _authorization.getSecondsToExpiry();
        long refreshTime = expireTime - REFRESH_GRACE_PERIOD;
        refreshTime = Math.max(30, refreshTime);
        _sessionTimerHandler.removeCallbacksAndMessages(null);
        _sessionTimerHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                refreshAuthorization(
                        new Response.Listener<AylaAuthorization>() {
                            @Override
                            public void onResponse(AylaAuthorization response) {
                                notifyAuthorizationRefreshed(response);
                            }
                        },
                        new ErrorListener() {
                            @Override
                            public void onErrorResponse(AylaError error) {
                                // Could not refresh the authorization. Session is closed.
                                AylaLog.e(LOG_TAG, "Failed to refresh session token");
                                if(!isCachedSession()){
                                    notifySessionClosed(error);
                                    shutDown(new EmptyListener<EmptyResponse>(),
                                            new EmptyListener<>());
                                }
                            }
                        }
                );
            }
        }, refreshTime * 1000);  // Set the timer REFRESH_GRACE_PERIOD
        // seconds
                                                // before the expiration
        AylaLog.i(LOG_TAG, "Set refresh token timer for " + refreshTime +
                " seconds");
    }

    public interface SessionManagerListener {
        /**
         * Notifies that the session has been closed. If the session was closed due to an error,
         * the error field will contain an AylaError with the reason for closure. If the session
         * was closed because the user signed out, error will be null.
         *
         * @param sessionName name of the session that was closed
         * @param error null for user sign-out, or the error that caused the closure of the session
         */
        void sessionClosed(String sessionName, AylaError error);

        /**
         * Notifies that the authorization has been refreshed. Applications that cache
         * authorization data should update their cache with the new value.
         *
         * @param sessionName name of the session that had the authorization refreshed
         * @param authorization The new authorization object
         */
        void authorizationRefreshed(String sessionName, AylaAuthorization authorization);
    }

    /**
     * Creates a datum for this user on the Ayla service.
     *
     * @param key             key of the datum to be stored in Ayla service.
     * @param value           value of datum object to be stored in Ayla service.
     * @param successListener Listener to receive the newly created datum object on success.
     * @param errorListener   Listener to receive error information.
     * @return The {@link AylaAPIRequest} object queued to send for this request
     */
    public AylaAPIRequest createDatum(String key, String value,
                                      final Response.Listener<AylaDatum> successListener,
                                      ErrorListener errorListener) {
        String url = userServiceUrl("api/v1/users/data.json");
        JSONObject datumObject = new JSONObject();
        JSONObject bodyObject = new JSONObject();
        try {
            bodyObject.put("key", key);
            bodyObject.put("value", value);
            datumObject.put("datum", bodyObject);
        } catch (JSONException e) {
            e.printStackTrace();
            errorListener.onErrorResponse(new JsonError(null, "JSONException trying to create " +
                    "message", e));
            return null;
        }
        String bodyText = datumObject.toString();
        AylaAPIRequest<AylaDatum.Wrapper> request = new AylaJsonRequest<AylaDatum.Wrapper>(
                Request.Method.POST, url, bodyText, null, AylaDatum.Wrapper.class, this,
                new Response.Listener<AylaDatum.Wrapper>() {
                    @Override
                    public void onResponse(AylaDatum.Wrapper response) {
                        successListener.onResponse(response.datum);
                    }
                }, errorListener) {
        };
        sendUserServiceRequest(request);
        return request;
    }

    /**
     * Fetches a single datum owned by this user from Ayla service.
     *
     * @param key             Key of the datum to be fetched
     * @param successListener Listener to receive the fetched datum object on success.
     * @param errorListener   Listener to receive error information
     * @return The AylaAPIRequest object queued to send for this request
     */
    public AylaAPIRequest fetchAylaDatum(String key,
                                         final Response.Listener<AylaDatum> successListener,
                                         ErrorListener errorListener) {
        if (key == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InvalidArgumentError("Datum key is required"));
            }
            return null;
        }
        String url = userServiceUrl("api/v1/users/data/" + key + ".json");
        AylaAPIRequest<AylaDatum.Wrapper> request = new AylaAPIRequest<>(
                Request.Method.GET, url, null, AylaDatum.Wrapper.class, this,
                new Response.Listener<AylaDatum.Wrapper>() {
                    @Override
                    public void onResponse(AylaDatum.Wrapper response) {
                        successListener.onResponse(response.datum);
                    }
                }, errorListener);
        sendUserServiceRequest(request);
        return request;
    }

    /**
     * Fetches a list of user data from Ayla service.
     *
     * @param keys            A list of one or more keys for which datum needs to be fetched.
     *                        All datum objects for this user will be fetched if this parameter
     *                        is null.
     *
     * @param successListener Listener to receive the datum object list on success.
     * @param errorListener   Listener to receive error information.
     * @return The AylaAPIRequest object queued to send for this request.
     */
    public AylaAPIRequest fetchAylaDatums(String[] keys,
                                          final Response.Listener<AylaDatum[]> successListener,
                                          ErrorListener errorListener) {
        String url = userServiceUrl("api/v1/users/data.json");
        if (keys != null) {
            url += URLHelper.parameterizeArray("keys", keys);
        }
        AylaAPIRequest<AylaDatum.Wrapper[]> request = new AylaAPIRequest<>(
                Request.Method.GET, url, null, AylaDatum.Wrapper[].class, this,
                new Response.Listener<AylaDatum.Wrapper[]>() {
                    @Override
                    public void onResponse(AylaDatum.Wrapper[] response) {
                        successListener.onResponse(AylaDatum.Wrapper.unwrap(response));
                    }
                }, errorListener);
        sendUserServiceRequest(request);
        return request;

    }

    /**
     * Fetches a list of user data with keys that match the pattern passed to this
     * method.
     *
     * @param wildcardedString A pattern string. "%" sign is used to define wildcards in the pattern.
     *                         eg. Use "%s" to select all data with keys ending in letter s, and use
     *                         "%input%" to select all data with keys containing the string "input".
     * @param successListener  Listener to receive the datum objects on success.
     * @param errorListener    Listener to receive error information.
     * @return The AylaAPIRequest object queued to send for this request.
     */
    public AylaAPIRequest fetchAylaDatums(String wildcardedString,
                                          final Response.Listener<AylaDatum[]> successListener,
                                          ErrorListener errorListener) {
        String url = userServiceUrl("api/v1/users/data.json");
        Map<String, String> params = new HashMap<>();
        params.put("keys", wildcardedString);
        url = URLHelper.appendParameters(url, params);
        AylaAPIRequest<AylaDatum.Wrapper[]> request = new AylaAPIRequest<>(
                Request.Method.GET, url, null, AylaDatum.Wrapper[].class, this,
                new Response.Listener<AylaDatum.Wrapper[]>() {
                    @Override
                    public void onResponse(AylaDatum.Wrapper[] response) {
                        successListener.onResponse(AylaDatum.Wrapper.unwrap(response));
                    }
                }, errorListener);
        sendUserServiceRequest(request);
        return request;

    }

    /**
     * Fetches list of all datum objects for this user. To fetch a filtered datum list use
     * {@link AylaSessionManager#fetchAylaDatums(String[], Response.Listener, ErrorListener)}
     *
     * @param successListener Listener to receive datum object list on success.
     * @param errorListener   Listener to receive error information.
     * @return The AylaAPIRequest object queued to send for this request.
     */
    public AylaAPIRequest fetchAylaDatums(final Response.Listener<AylaDatum[]> successListener,
                                          ErrorListener errorListener) {
        return fetchAylaDatums((String[]) null, successListener, errorListener);
    }

    /**
     * Updates value of a user datum in Ayla service.
     *
     * @param key             Key of the datum to be updated.
     * @param value           Changed value for the datum.
     * @param successListener Listener to receive the updated datum object
     *                        on success.
     * @param errorListener   Listener to receive error information.
     * @return The AylaAPIRequest object queued to send for this request.
     */
    public AylaAPIRequest updateDatum(String key, String value,
                                      final Response.Listener<AylaDatum> successListener,
                                      ErrorListener errorListener) {
        if (key == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InvalidArgumentError("Datum key is required"));
            }
            return null;
        }
        String url = userServiceUrl("api/v1/users/data/" + key + ".json");
        JSONObject datumObject = new JSONObject();
        JSONObject bodyObject = new JSONObject();
        try {
            bodyObject.put("key", key);
            bodyObject.put("value", value);
            datumObject.put("datum", bodyObject);
        } catch (JSONException e) {
            e.printStackTrace();
            errorListener.onErrorResponse(new JsonError(null, "JSONException trying to create " +
                    "message", e));
            return null;
        }
        String bodyText = datumObject.toString();
        final AylaAPIRequest<AylaDatum.Wrapper> request = new AylaJsonRequest<AylaDatum.Wrapper>(
                Request.Method.PUT, url, bodyText, null, AylaDatum.Wrapper.class, this,
                new Response.Listener<AylaDatum.Wrapper>() {
                    @Override
                    public void onResponse(AylaDatum.Wrapper response) {
                        successListener.onResponse(response.datum);
                    }
                }, errorListener);

        sendUserServiceRequest(request);
        return request;
    }

    /**
     * Deletes a user datum from Ayla service.
     *
     * @param key             Key of the datum to be deleted.
     * @param successListener Listener to receive result on success
     * @param errorListener   Listener to receive error information.
     * @return The AylaAPIRequest object queued to send for this request.
     */
    public AylaAPIRequest deleteDatum(String key,
                                      Response.Listener<EmptyResponse> successListener,
                                      ErrorListener errorListener) {
        if (key == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InvalidArgumentError("Datum key is required"));
            }
            return null;
        }

        String url = userServiceUrl("api/v1/users/data/" + key + ".json");
        AylaAPIRequest<EmptyResponse> request = new AylaAPIRequest<EmptyResponse>(
                Request.Method.DELETE, url, null, null, this, successListener, errorListener) {
            @Override
            protected Response<EmptyResponse> parseNetworkResponse(NetworkResponse response) {
                return Response.success(new EmptyResponse(),
                        HttpHeaderParser.parseCacheHeaders(response));
            }
        };
        sendUserServiceRequest(request);
        return request;
    }

    /**
     * Creates a Contact on the Ayla service.
     *
     * @param contact         AylaContact to be stored in Ayla service.
     * @param successListener Listener to receive the newly created contact object on success.
     * @param errorListener   Listener to receive error information.
     * @return The AylaAPIRequest object queued to send for this request
     */
    public AylaAPIRequest createContact(final AylaContact contact,
                                        final Response.Listener<AylaContact> successListener,
                                        final ErrorListener errorListener) {
        String url = userServiceUrl("api/v1/users/contacts.json");

        AylaContact.Wrapper contactWrapper = new AylaContact.Wrapper();
        contactWrapper.contact = contact;
        final String postBodyString = AylaNetworks.sharedInstance().getGson().toJson
                (contactWrapper, AylaContact.Wrapper.class);

        AylaAPIRequest<AylaContact.Wrapper> request = new AylaJsonRequest(
                Request.Method.POST, url, postBodyString, null, AylaContact.Wrapper.class, this,
                new Response.Listener<AylaContact.Wrapper>() {
                    @Override
                    public void onResponse(AylaContact.Wrapper response) {
                        successListener.onResponse(response.contact);
                    }
                }, errorListener);

        sendUserServiceRequest(request);
        return request;
    }

    /**
     * Updates a Contact on the Ayla service.
     *
     * @param contact         AylaContact to be updated in Ayla service.
     * @param successListener Listener to receive the updated contact object on success.
     * @param errorListener   Listener to receive error information.
     * @return The AylaAPIRequest object queued to send for this request
     */
    public AylaAPIRequest updateContact(final AylaContact contact,
                                        final Response.Listener<AylaContact> successListener,
                                        final ErrorListener errorListener) {
        Integer contactKey = contact.getId();
        if (contactKey == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InvalidArgumentError("Invalid contact"));
            }
            return null;
        }
        String url = userServiceUrl("api/v1/users/contacts/" + contactKey + ".json");

        AylaContact.Wrapper contactWrapper = new AylaContact.Wrapper();
        contactWrapper.contact = contact;
        final String postBodyString = AylaNetworks.sharedInstance().getGson().toJson
                (contactWrapper, AylaContact.Wrapper.class);
        final byte[] requestBody = postBodyString.getBytes();

        AylaAPIRequest<AylaContact.Wrapper> request = new AylaJsonRequest<AylaContact.Wrapper>(
                Request.Method.PUT, url, postBodyString, null, AylaContact.Wrapper.class, this,
                new Response.Listener<AylaContact.Wrapper>() {
                    @Override
                    public void onResponse(AylaContact.Wrapper response) {
                        successListener.onResponse(response.contact);
                    }
                }, errorListener);
        sendUserServiceRequest(request);
        return request;
    }

    /**
     * Fetches a list of Contacts for this user from Ayla service.
     *
     * @param successListener Listener to receive on successful fetch of Contacts.
     * @param errorListener   Listener to receive error information.
     * @return The AylaAPIRequest object queued to send for this request.
     */
    public AylaAPIRequest fetchContacts(final Response.Listener<AylaContact[]> successListener,
                                        final ErrorListener errorListener) {
        String url = userServiceUrl("api/v1/users/contacts.json");

        AylaAPIRequest<AylaContact.Wrapper[]> request = new AylaAPIRequest<>(
                Request.Method.GET, url, null, AylaContact.Wrapper[].class, this,
                new Response.Listener<AylaContact.Wrapper[]>() {
                    @Override
                    public void onResponse(AylaContact.Wrapper[] response) {
                        successListener.onResponse(AylaContact.Wrapper.unwrap(response));
                    }
                }, errorListener);
        sendUserServiceRequest(request);
        return request;
    }

    /**
     * Deletes a Contact from Ayla service.
     *
     * @param contact         AylaContact to be deleted in Ayla service.
     * @param successListener Listener to receive result on success
     * @param errorListener   Listener to receive error information.
     * @return The AylaAPIRequest object queued to send for this request.
     */
    public AylaAPIRequest deleteContact(final AylaContact contact,
                                        final Response.Listener<EmptyResponse> successListener,
                                        final ErrorListener errorListener) {
        Integer contactKey = contact.getId();
        if (contactKey == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InvalidArgumentError("Invalid contact"));
            }
            return null;
        }
        String url = userServiceUrl("api/v1/users/contacts/" + contactKey + ".json");

        AylaAPIRequest<EmptyResponse> request = new AylaAPIRequest<EmptyResponse>(
                Request.Method.DELETE, url, null, null, this, successListener, errorListener) {
            @Override
            protected Response<EmptyResponse> parseNetworkResponse(NetworkResponse response) {
                return Response.success(new EmptyResponse(),
                        HttpHeaderParser.parseCacheHeaders(response));
            }
        };
        sendUserServiceRequest(request);
        return request;
    }

    /**
     * Fetch shares owned by this user.
     *
     * @param successListener Listener to receive list of owned shares on success.
     * @param errorListener   Listener to receive error information
     * @return The AylaAPIRequest object queued to send for this request.
     */
    public AylaAPIRequest fetchOwnedShares(final Response.Listener<AylaShare[]> successListener,
                                           ErrorListener errorListener) {
        String url = userServiceUrl("api/v1/users/shares.json");
        AylaAPIRequest<AylaShare.Wrapper[]> request = new AylaAPIRequest<>
                (Request.Method.GET, url, null, AylaShare.Wrapper[].class, this,
                        new Response.Listener<AylaShare.Wrapper[]>() {
                            @Override
                            public void onResponse(AylaShare.Wrapper[] response) {
                                successListener.onResponse(AylaShare.Wrapper.unwrap(response));
                            }
                        }, errorListener);
        sendUserServiceRequest(request);
        return request;
    }

    /**
     * Fetch shares received by this user.
     *
     * @param successListener Listener to receive list of received shares on success.
     * @param errorListener   Listener to receive error information
     * @return The AylaAPIRequest object queued to send for this request.
     */
    public AylaAPIRequest fetchReceivedShares(final Response.Listener<AylaShare[]> successListener,
                                              ErrorListener errorListener) {
        String url = userServiceUrl("api/v1/users/shares/received.json");
        AylaAPIRequest<AylaShare.Wrapper[]> request = new AylaAPIRequest<>
                (Request.Method.GET, url, null, AylaShare.Wrapper[].class, this,
                        new Response.Listener<AylaShare.Wrapper[]>() {
                            @Override
                            public void onResponse(AylaShare.Wrapper[] response) {
                                successListener.onResponse(AylaShare.Wrapper.unwrap(response));
                            }
                        }, errorListener);
        sendUserServiceRequest(request);
        return request;
    }

    /**
     * Fetch a single share from Ayla service.
     *
     * @param shareId         Id of the share to be fetched.
     * @param successListener Listener to receive the share on success.
     * @param errorListener   Listener to receive error information
     * @return The AylaAPIRequest object queued to send for this request.
     */
    public AylaAPIRequest fetchShare(String shareId,
                                     final Response.Listener<AylaShare> successListener,
                                     ErrorListener errorListener) {
        if (shareId == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InvalidArgumentError("ShareId is required"));
            }
            return null;
        }
        String url = userServiceUrl("api/v1/users/shares/" + shareId + ".json");
        AylaAPIRequest<AylaShare.Wrapper> request = new AylaAPIRequest<>
                (Request.Method.GET, url, null, AylaShare.Wrapper.class, this,
                        new Response.Listener<AylaShare.Wrapper>() {
                            @Override
                            public void onResponse(AylaShare.Wrapper response) {
                                AylaLog.d(LOG_TAG, "Fetched share: "+response.share.toString());
                                successListener.onResponse(response.share);
                            }
                        }, errorListener);
        sendUserServiceRequest(request);
        return request;
    }

    /**
     * Create a share owned by this user
     *
     * @param share           Share object to be created in the service for this user.
     * @param emailTemplateId optional email template id for the share email.
     * @param successListener Listener to receive the created share on success.
     * @param errorListener   Listener to receive error information.
     * @return The AylaAPIRequest object queued to send for this request.
     */
    public AylaAPIRequest createShare(AylaShare share, String emailTemplateId,
                                      final Response.Listener<AylaShare> successListener,
                                      ErrorListener errorListener) {
        String url = userServiceUrl("api/v1/users/shares.json");
        if (emailTemplateId != null) {
            Map<String, String> params = new HashMap<>();
            params.put("email_template_id", emailTemplateId);
            url = URLHelper.appendParameters(url, params);
        }
        final AylaShare.Wrapper shareWrapper = new AylaShare.Wrapper();
        shareWrapper.share = share;
        GsonBuilder builder = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .excludeFieldsWithoutExposeAnnotation()
                .serializeNulls();
        Gson gson = builder.create();
        String shareJson = gson.toJson(shareWrapper);
        AylaAPIRequest<AylaShare.Wrapper> request = new AylaJsonRequest<AylaShare.Wrapper>(
                Request.Method.POST, url,shareJson, null, AylaShare.Wrapper.class, this,
                new Response.Listener<AylaShare.Wrapper>() {
                    @Override
                    public void onResponse(AylaShare.Wrapper response) {
                        AylaLog.d(LOG_TAG, "Share created: "+response.share.toString());
                        successListener.onResponse(response.share);
                    }
                }, errorListener);

        sendUserServiceRequest(request);
        return request;
    }

    /**
     * Create shares owned by this user.
     *
     * @param shares          sahres to be created for this user.
     * @param emailTemplateId email template id for shares(optional)
     * @param successListener Listener to receive created shares on success.
     * @param errorListener   Listener to receive error information.
     * @return AylaAPIRequest queued up for this request.
     */
    public AylaAPIRequest createShares(AylaShare[] shares, String emailTemplateId,
                                       final Response.Listener<AylaShare[]> successListener,
                                       ErrorListener errorListener) {
        String url = userServiceUrl("api/v1/users/shares.json");
        if (emailTemplateId != null) {
            Map<String, String> params = new HashMap<>();
            params.put("email_template_id", emailTemplateId);
            url = URLHelper.appendParameters(url, params);
        }
        JsonElement shareJsonElement =
                AylaNetworks.sharedInstance().getGson().toJsonTree(shares);
        final JsonArray jsonArray = new JsonArray();
        jsonArray.add(shareJsonElement);
        final JsonObject sharesJson = new JsonObject();
        sharesJson.add("shares", jsonArray);
        AylaAPIRequest request = new AylaJsonRequest<AylaShare.Wrapper[]>(Request.Method.POST, url,
                sharesJson.toString(), null, AylaShare.Wrapper[].class, this,
                new Response.Listener<AylaShare.Wrapper[]>() {
                    @Override
                    public void onResponse(AylaShare.Wrapper[] response) {
                        successListener.onResponse(AylaShare.Wrapper.unwrap(response));
                    }
                }, errorListener);
        sendUserServiceRequest(request);
        return request;
    }

    /**
     * Update a share owned by this user.
     *
     * @param share           Share containing the new values to be updated.
     * @param emailTemplateId optional email template id for the share email.
     * @param successListener Listener to receive updated share on success.
     * @param errorListener   Listener to receive error information.
     * @return The AylaAPIRequest object queued to send for this request.
     */
    public AylaAPIRequest updateShare(AylaShare share, String emailTemplateId,
                                      final Response.Listener<AylaShare> successListener,
                                      ErrorListener errorListener) {
        if (share.getId() == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InvalidArgumentError("Only shares fetched from " +
                        "Ayla service can be updated"));
            }
            return null;
        }
        String url = userServiceUrl("api/v1/users/shares/" + share.getId() + ".json");
        final AylaShare.Wrapper shareWrapper = new AylaShare.Wrapper();
        shareWrapper.share = share;
        GsonBuilder builder = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .excludeFieldsWithoutExposeAnnotation()
                .serializeNulls();
        Gson gson = builder.create();
        String shareJson = gson.toJson(shareWrapper);
        AylaAPIRequest<AylaShare.Wrapper> request = new AylaJsonRequest<AylaShare.Wrapper>(
                Request.Method.PUT, url, shareJson.toString(),
                null, AylaShare.Wrapper.class, this, new Response.Listener<AylaShare.Wrapper>() {
            @Override
            public void onResponse(AylaShare.Wrapper response) {
                AylaLog.d(LOG_TAG, "Updated share: "+response.share.toString());
                successListener.onResponse(response.share);
            }
        }, errorListener);

        sendUserServiceRequest(request);
        return request;
    }

    /**
     * Deletes a share owned or received by this user.
     *
     * @param shareId         Id of the share to be deleted
     * @param successListener Listener to receive result on success.
     * @param errorListener   Listener to receive error information.
     * @return the {@link AylaAPIRequest} for this operation
     */
    public AylaAPIRequest deleteShare(String shareId,
                                      Response.Listener<EmptyResponse> successListener,
                                      ErrorListener errorListener) {
        if (shareId == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InvalidArgumentError("ShareId is required"));
            }
            return null;
        }
        String url = userServiceUrl("api/v1/users/shares/" + shareId + ".json");
        AylaAPIRequest<EmptyResponse> request = new AylaAPIRequest<EmptyResponse>(
                Request.Method.DELETE, url, null, null, this, successListener, errorListener) {
            @Override
            protected Response<EmptyResponse> parseNetworkResponse(NetworkResponse response) {
                return Response.success(new EmptyResponse(),
                        HttpHeaderParser.parseCacheHeaders(response));
            }
        };
        sendUserServiceRequest(request);
        return request;
    }

    /**
     * Deletes a user account from Ayla service.
     * @param successListener Listener called if the operation is successful.
     * @param errorListener Listener called if an error occurred
     * @return the AylaAPIRequest for this operation, which may be canceled
     */
    public AylaAPIRequest<EmptyResponse> deleteAccount(
            Response.Listener<EmptyResponse> successListener, ErrorListener errorListener){
        return _authProvider.deleteUser(this, successListener, errorListener);

    }

    /**
     * Returns the AylaRulesService for this session.
     *
     * @return the AylaRulesService created for this session.
     */
    public AylaRulesService getRulesService() {
        return _rulesService;
    }

    ///////////////////////////////////////////////
    // Super App Methods
    ///////////////////////////////////////////////

    /**
     * Links this user's account with another account
     * @param account AylaLinkedAccount object representing the account to link with
     * @param password Password for the linked account
     * @param successListener Listener called upon a successful link
     * @param errorListener Listener called in case of an error
     * @return the AylaAPIRequest, which may be used to cancel the operation
     */
    public AylaAPIRequest<EmptyResponse> linkAccount(AylaLinkedAccount account, String password,
                                                     Response.Listener<EmptyResponse> successListener,
                                                     ErrorListener errorListener) {
        String url = userServiceUrl("api/v1/users/link.json");
        AylaAPIRequest<EmptyResponse> request = new AylaAPIRequest<>(Request.Method.POST, url,
                null, null, this, successListener, errorListener);
        sendUserServiceRequest(request);
        return request;
    }

    /**
     * Fetches the array of linked accounts from the service
     * @param successListener Listener called with the array of fetched accounts
     * @param errorListener Listener called in case of an error
     * @return the AylaAPIRequest, which may be canceled
     */
    public AylaAPIRequest<AylaLinkedAccount[]> fetchLinkedAccounts(
            Response.Listener<AylaLinkedAccount[]> successListener,
            ErrorListener errorListener) {
        String url = userServiceUrl("api/v1/users/link.json");
        AylaAPIRequest<AylaLinkedAccount[]> request = new AylaAPIRequest<>(Request.Method.GET, url,
                null, AylaLinkedAccount[].class, this, successListener, errorListener);
        sendUserServiceRequest(request);
        return request;
    }

    /**
     * Deletes (unlinks) the specified account that was previously linked to this account
     * @param account Account to delete from the list of linked accounts (unlink)
     * @param successListener Listener called upon success
     * @param errorListener Listener called with an error should one occur
     * @return the AylaAPIRequest, which may be used to cancel the operation
     */
    public AylaAPIRequest<EmptyResponse> deleteLinkedAccount(
            AylaLinkedAccount account,
            Response.Listener<EmptyResponse> successListener,
            ErrorListener errorListener) {
        String url = userServiceUrl("api/v1/users/link.json");
        String json = AylaNetworks.sharedInstance().getGson().toJson(account);

        // HACK: The cloud expects user_email instead of email here
        // TODO: Remove this when SVC-10451 is fixed
        try {
            JSONObject jo = new JSONObject(json);
            if (jo.has("email")) {
                jo.put("user_email", account.email);
                json = jo.toString();
            }
        } catch (JSONException e) {
            AylaLog.e(LOG_TAG, "Failed to parse JSON: " + json);
            errorListener.onErrorResponse(new InternalError("Failed to parse JSON", e));
            return  null;
        }

        AylaJsonRequest<EmptyResponse> request = new AylaJsonRequest<>(Request.Method.DELETE, url,
                json, null, null, this, successListener, errorListener);
        sendUserServiceRequest(request);
        return request;
    }
}

