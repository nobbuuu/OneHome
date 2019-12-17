package com.aylanetworks.aylasdk.error;

/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import com.android.volley.VolleyError;
import com.aylanetworks.aylasdk.AylaLog;

/**
 * All errors returned from the SDK are instances of AylaError. Returned AylaErrors are most often
 * subclasses of AylaError. The specific type of error returned can be found via {@link
 * #getErrorType()}. If additional information is needed about an error, many of the subclasses
 * provide additional methods detailing the error. All AylaError classes support {@link
 * #getMessage()}, which returns a non-localized string with a description of the error.
 */
public class AylaError extends Exception {
    private final static String LOG_TAG = "AylaError";

    /** Enumeration of error types */
    public enum ErrorType {
        AylaError,                          // Base error type, should generally not be used
        AuthError,                          // Authorization error: bad password, not logged in, etc
        NetworkError,                       // Network is not reachable, etc.
        JsonError,                          // Error parsing JSON
        ServerError,                        // Bad status code received from server
        Timeout,                            // Request timed out
        InvalidArgument,                    // The arguments to the method were invalid
        Precondition,                       // A precondition was not met
        AppPermission,                      // Application has not granted permission to perform
        Internal,                           // An SDK internal error occurred
        OperationIncompleteError            // Partial success for some requests
    }

    private ErrorType _errorType;

    /** Constructor for a simple AylaError with a detail message
     *
     * @param errorType ErrorCode for this error
     * @param detailMessage Details of the error, not to be displayed to the end user
     */
    public AylaError(ErrorType errorType, String detailMessage) {
        super(detailMessage);
        _errorType = errorType;
    }

    /** Constructor for an AylaError with a code, detail message and cause
     *
     * @param errorType ErrorCode for this error
     * @param detailMessage Details of the error, not to be displayed to the end user
     * @param cause Throwable representing another exception that was the cause of this error. The
     *              cause can be retrieved by calling getCause().
     */
    public AylaError(ErrorType errorType, String detailMessage, Throwable cause) {
        super(detailMessage, cause);
        _errorType = errorType;
    }

    /**
     * Returns a new AylaError encapsulating the provided VolleyError. All returned AylaError
     * objects will contain the VolleyError as the cause, which can be obtained via a call to
     * {@link #getCause()}.
     *
     * @param volleyError VolleyError to be wrapped into an AylaError object
     * @return the appropriate AylaError for the provided VolleyError
     */
    public static AylaError fromVolleyError(VolleyError volleyError) {
        AylaError aylaError;

        // See if there was a network response we can set as the detail message
        int responseCode = 0;
        String responseString = null;
        if ( volleyError.networkResponse != null ) {
            if ( volleyError.networkResponse.data != null ) {
                responseString = new String(volleyError.networkResponse.data);
            }
            responseCode = volleyError.networkResponse.statusCode;
        }

        // Check each known VolleyError subclass and convert to an AylaError
        if ( volleyError instanceof com.android.volley.AuthFailureError) {
            aylaError = new AuthError(responseString, volleyError);
        } else if ( volleyError instanceof com.android.volley.NoConnectionError ) {
            aylaError = new NetworkError(responseString, volleyError);
        } else if ( volleyError instanceof com.android.volley.NetworkError) {
            aylaError = new NetworkError(null, volleyError);
        } else if ( volleyError instanceof com.android.volley.ParseError ) {
            aylaError = new JsonError(responseString, "Failed to parse response from server", volleyError);
        } else if ( volleyError instanceof com.android.volley.ServerError ) {
            byte[] responseData = null;
            if (volleyError.networkResponse != null) {
                responseData = volleyError.networkResponse.data;
            }
            aylaError = new ServerError(
                    responseCode, responseData, responseString, volleyError);
        } else if ( volleyError instanceof com.android.volley.TimeoutError ) {
            aylaError = new TimeoutError("Request timed out", volleyError);
        } else {
            // Nothing matches
            AylaLog.w(LOG_TAG, "Unknown error: " + volleyError.getClass().toString());
            aylaError = new AylaError(ErrorType.AylaError, responseString, volleyError);
        }

        return aylaError;
    }

    /**
     * Returns the ErrorType of this error. This may be used to know the specific subclass of
     * error so the object may be cast if desired.
     * @return The ErrorType for this error object
     */
    public ErrorType getErrorType() {
        return _errorType;
    }
}
