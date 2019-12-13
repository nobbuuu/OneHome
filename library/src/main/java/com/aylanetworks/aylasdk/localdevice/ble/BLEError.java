package com.aylanetworks.aylasdk.localdevice.ble;


import android.text.TextUtils;

import com.aylanetworks.aylasdk.error.AylaError;

/*
 * Ayla SDK
 *
 * Copyright 2017 Ayla Networks, all rights reserved
 */

public class BLEError extends AylaError {
    private int _bleErrorCode;

    public BLEError(int bleErrorCode, String detailMessage) {
        super(ErrorType.ServerError, detailMessage);
        _bleErrorCode = bleErrorCode;
    }

    public int getBLEErrorCode() {
        return _bleErrorCode;
    }

    @Override
    public String toString() {
        if (TextUtils.isEmpty(super.toString())) {
            return "error code:" + getBLEErrorCode();
        } else {
            return "error message:" + super.toString() + ", error code:" + getBLEErrorCode();
        }
    }
}
