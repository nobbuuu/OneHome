package com.aylanetworks.aylasdk.error;

import android.text.TextUtils;

/**
 * A specific TimeoutError generated when polling a ACK enabled property timed out,
 * especially to-device property, which means the device the property belongs to
 * was most probably down.
 */
public class PropertyAckTimeoutError extends TimeoutError {

    private String _propertyName;

    public PropertyAckTimeoutError(String propertyName) {
        this(propertyName, null);
    }

    public PropertyAckTimeoutError(String propertyName, String detailMessage) {
        this(propertyName, detailMessage, null);
    }

    public PropertyAckTimeoutError(String propertyName, String detailMessage, Throwable cause) {
        super(detailMessage, cause);
        _propertyName = propertyName;
    }

    public String getProperty() {
        return _propertyName;
    }

    @Override
    public String getMessage() {
        String message = super.getMessage();
        if (TextUtils.isEmpty(message)) {
            return "Ack property " + _propertyName + ", timed out";
        } else {
            return message;
        }
    }
}
