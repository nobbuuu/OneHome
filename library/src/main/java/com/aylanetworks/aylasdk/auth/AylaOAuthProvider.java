package com.aylanetworks.aylasdk.auth;
/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.android.volley.Request;
import com.android.volley.Response;
import com.aylanetworks.aylasdk.AylaAPIRequest;
import com.aylanetworks.aylasdk.AylaJsonRequest;
import com.aylanetworks.aylasdk.AylaLog;
import com.aylanetworks.aylasdk.AylaLoginManager;
import com.aylanetworks.aylasdk.AylaNetworks;
import com.aylanetworks.aylasdk.AylaSystemSettings;
import com.aylanetworks.aylasdk.error.AylaError;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.aylanetworks.aylasdk.error.JsonError;
import com.aylanetworks.aylasdk.error.PreconditionError;
import com.aylanetworks.aylasdk.util.EmptyListener;
import com.google.gson.annotations.Expose;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;

/**
 * This class is used to authenticate login credentials via a 3rd party OAuth provider
 * It does the following requests
 * 1. Retrieve the Oauth Provider URL used for authentication from Ayla User Service
 * 2. Retrieve Oauth provider authCode via WebView after user enters credentials
 * 3. Pass authCode to Ayla User Service for login account validation
 * 4. The response we receive is converted to AylaAuthorization object
 */
public abstract class AylaOAuthProvider extends BaseAuthProvider {
    private static final String LOG_TAG = "AylaOAuthProvider";
    protected AuthProviderListener _listener;
    private final static String AUTH_CODE_PARSER = "code=";
    protected final static String MOBILE_HOST_URL = "mobile.aylanetworks.com";
    protected final static String LOCAL_HOST = "localhost";

    public abstract String getAuthType();

    public abstract String getAuthURL();

    public abstract WebView getWebView();

    /**
     * Retrieve the Oauth Provider URL used for authentication from Ayla User Service
     * and then set up a web view with that URL. The subclass can override this method like for e.g.
     * AylaWeChatAuthProvider overrides and implements its own method. In case the subclass does
     * not override it the webview is not null
     *
     * @param listener The listener to be notified of sign-in success or failure.
     */
    @Override
    public void authenticate(final AuthProviderListener listener) {
        //Just make sure listener is not null
        if (listener == null) {
            throw new IllegalArgumentException("listener cannot be null");
        }
        //Just make sure webView is not null. This is applicable only if the Subclass does not
        // override authenticate method
        if (getWebView() == null) {
            listener.didFailAuthentication(new PreconditionError("Web View cannot be null"));
            return;
        }

        AylaLoginManager loginManager = AylaNetworks.sharedInstance().getLoginManager();
        String url = loginManager.userServiceUrl("users/sign_in.json");

        // The JSON body needs to be like following
        // { "user": {
        //      "auth_method":"google_provider",
        //      "application":{
        //          "app_id":"my_app_id",
        //          "app_secret":"my_app_secret"
        //      }
        //    }
        // }

        AylaSystemSettings settings = AylaNetworks.sharedInstance().getSystemSettings();
        if (settings == null) {
            listener.didFailAuthentication(new PreconditionError("Library has not been " +
                    "initialized. Call AylaNetworks.initialize() before calling any other " +
                    "methods"));
        }

        // Construct a JSON object to contain the parameters.
        JSONObject user = new JSONObject();
        JSONObject userParam = new JSONObject();
        try {
            userParam.put("auth_method", getAuthType());
            JSONObject application = new JSONObject();
            application.put("app_id", settings.appId);
            application.put("app_secret", settings.appSecret);
            userParam.put("application", application);
            user.put("user", userParam);
        } catch (JSONException e) {
            listener.didFailAuthentication(new JsonError(null, "JSONException trying to create " +
                    "Post body for authorization", e));
            return;
        }

        String postBodyString = user.toString();

        // Create our request object with some overrides to handle the POST body and
        // updating the CoreManager when we succeed
        AylaAPIRequest<AylaAuthorization> request = new AylaJsonRequest<AylaAuthorization>(
                Request.Method.POST,
                url,
                postBodyString,
                null,
                AylaAuthorization.class,
                null, // No session manager exists until we are logged in!
                new EmptyListener<AylaAuthorization>(),
                new ErrorListener() {
                    @Override
                    public void onErrorResponse(AylaError error) {
                        listener.didFailAuthentication(error);
                    }
                }) {

            @Override
            protected void deliverResponse(AylaAuthorization response) {
                _listener = listener;
                setupWebView(response.getUrl(), this);
            }
        };
        loginManager.sendUserServiceRequest(request);
    }

    @Override
    public void authenticate(AuthProviderListener listener, String sessionName) {
        authenticate(listener);
    }


    /**
     * set up a web view client with the passed URL
     *
     * @param webViewURL      Web View URL
     * @param originalRequest AylaAPIRequest
     */
    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView(String webViewURL, AylaAPIRequest originalRequest) {
        // setup a WebView session so user can authenticate with OAUTH provider
        // monitor user/oauth provider exchange in oAuthWebViewClient.shouldOverrideUrlLoading()
        WebView webView = getWebView();
        webView.getSettings().setJavaScriptEnabled(true);
        OAuthWebViewClient oAuthWebViewClient = new OAuthWebViewClient(this, _listener,
                originalRequest);
        webView.setWebViewClient(oAuthWebViewClient);
        webView.setVisibility(View.VISIBLE);
        webView.bringToFront();

        String redirectUrl;
        // In the Google Console we have given our redirect uri to be http://localhost:9000/
        // For FaceBook we registered our redirect uri as http://mobile.aylanetworks.com/ in the
        // Facebook Console
        redirectUrl = getAuthURL();
        clearCookies();
        String toWebViewURL = String.format("%s&redirect_uri=%s", webViewURL, redirectUrl);
        webView.loadUrl(toWebViewURL);    // display web page to user
    }

    private void clearCookies() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            AylaLog.d(LOG_TAG, "Using clearCookies code for API >=" + String.valueOf(Build
                    .VERSION_CODES.LOLLIPOP_MR1));
            CookieManager.getInstance().removeAllCookies(null);
            CookieManager.getInstance().flush();
        } else {
            Context context = AylaNetworks.sharedInstance().getContext();
            AylaLog.d(LOG_TAG, "Using clearCookies code for API <" + String.valueOf(Build
                    .VERSION_CODES.LOLLIPOP_MR1));
            CookieSyncManager cookieSyncMngr = CookieSyncManager.createInstance(context);
            cookieSyncMngr.startSync();
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.removeAllCookie();
            cookieManager.removeSessionCookie();
            cookieSyncMngr.stopSync();
            cookieSyncMngr.sync();
        }
    }

    /**
     * Retrieve Oauth provider authCode via WebView after user enters credentials
     * Monitor WebView exchange between user and 3rd Party OAUTH provider
     * On success, pass authentication code to authenticateToService()
     */
    private static class OAuthWebViewClient extends WebViewClient {

        private final WeakReference<AylaOAuthProvider> _aylaOAuthRef;
        private String _authCode = null;//authorization credentials from 3rd party OAUTH provider
        private final AuthProviderListener _authListener;
        private final AylaAPIRequest _originalRequest;

        public OAuthWebViewClient(AylaOAuthProvider aylaOAuth, AuthProviderListener listener,
                                  AylaAPIRequest originalRequest) {
            super();
            _aylaOAuthRef = new WeakReference<>(aylaOAuth);
            _authListener = listener;
            _originalRequest = originalRequest;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            String host = Uri.parse(url).getHost();
            if (!(host.equals(LOCAL_HOST) || host.equals(MOBILE_HOST_URL))) {
                AylaLog.i(LOG_TAG, "display auth provider sign-in page");
                return false;
            }

            int codeParserBeginIndex = url.indexOf(AUTH_CODE_PARSER) + AUTH_CODE_PARSER.length();
            if (codeParserBeginIndex != -1) {
                if (host.equals(LOCAL_HOST)) {
                    _authCode = url.substring(codeParserBeginIndex);
                } else {
                    int codeParserEndIndex = url.indexOf('&', codeParserBeginIndex);
                    _authCode = url.substring(codeParserBeginIndex, codeParserEndIndex);
                }
            }

            AylaOAuthProvider aylaOAuth = _aylaOAuthRef.get();
            if (aylaOAuth != null) {
                // Continue oauth login process
                aylaOAuth.authenticateToService(_authCode, _originalRequest);
            }
            return true;
        }
    }

    /**
     * Pass authCode to Ayla User Service for login account validation
     *
     * @param authCode    3rd Party oAuthCode
     * @param originalReq Original API Request
     */
    public void authenticateToService(final String authCode, final AylaAPIRequest originalReq) {
        AylaLoginManager loginManager = AylaNetworks.sharedInstance().getLoginManager();
        String url = loginManager.userServiceUrl("users/provider_auth.json");

        AylaSystemSettings settings = AylaNetworks.sharedInstance().getSystemSettings();
        // Construct a JSON object to contain the parameters.
        JSONObject userParam = new JSONObject();
        try {
            userParam.put("code", authCode);
            userParam.put("app_id", settings.appId);

            userParam.put("provider", getAuthType());
            userParam.put("redirect_url", getAuthURL());

        } catch (JSONException e) {
            _listener.didFailAuthentication(new JsonError(null, "JSONException trying to create " +
                    "Post body for authentication to Service", e));
            return;
        }

        String postBodyString = userParam.toString();
        AylaAPIRequest<AylaAuthorization> request = new
                AylaJsonRequest<>(
                Request.Method.POST,
                url,
                postBodyString,
                null,
                AylaAuthorization.class,
                null, // No session manager exists until we are logged in!
                new Response.Listener<AylaAuthorization>() {
                    @Override
                    public void onResponse(AylaAuthorization response) {
                        _listener.didAuthenticate(response, false);
                    }
                },
                new ErrorListener() {
                    @Override
                    public void onErrorResponse(AylaError error) {
                        _listener.didFailAuthentication(error);
                    }
                });

        // This is a compound request- we need to keep the chain going so canceling the original
        // request will cancel this new request.
        if (originalReq != null) {
            if (originalReq.isCanceled()) {
                request.cancel();
            } else {
                originalReq.setChainedRequest(request);
                loginManager.sendUserServiceRequest(request);
            }
        } else {
            loginManager.sendUserServiceRequest(request);
        }
    }

    /**
     * Set the listener
     *
     * @param listener The listener to be notified of sign-in success or failure.
     */
    protected void setListener(AuthProviderListener listener) {
        _listener = listener;
    }

    public static class AylaUserRole {
        @Expose
        private boolean canAddRoleUser;
        @Expose
        private int group;
        @Expose
        private int id;
        @Expose
        private String name;
        @Expose
        private int oemId;

        public String getName() {
            return name;
        }
    }
}
