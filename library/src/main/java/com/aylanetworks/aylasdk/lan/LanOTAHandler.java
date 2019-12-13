package com.aylanetworks.aylasdk.lan;
/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import android.text.TextUtils;
import com.aylanetworks.aylasdk.ota.AylaLanOTADevice;
import com.aylanetworks.aylasdk.AylaLog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.router.RouterNanoHTTPD;

/**
 HTTP server handler used to handle LAN OTA Image Update. In the get call we skip the first 256
 bytes and serve rest of the file. This is the ota image file stored on the Android device that has
 been downloaded for that specific Ayla device.Once the entire image has been received by the Module
 it makes the put call to update the Status.
 */
public class LanOTAHandler extends AylaHttpRouteTarget {
    private final static String LOG_TAG = "LanOTAHandler";

    @Override
    public NanoHTTPD.Response put(RouterNanoHTTPD.UriResource uriResource, Map<String, String>
            urlParams, NanoHTTPD.IHTTPSession session) {
        AylaLanOTADevice device = getOTADevice(uriResource, session);
        if (device == null) {
            // Nobody around to service the request
            String clientIP = session.getHeaders().get(AylaHttpServer.HEADER_CLIENT_IP);
            AylaLog.e(LOG_TAG, "No device for Lan OTA from " + clientIP);
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND,
                    AylaHttpServer.MIME_PLAINTEXT, "No device found");
        }
        Map<String, String> parms = session.getParms();
        String status = parms.get("status");
        String error = parms.get("err");
        Integer statusCode;
        try {
            statusCode = Integer.parseInt(status);
            device.setOTAUpdateStatus(statusCode,error);
        } catch (NumberFormatException ex) {
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST,
                    AylaHttpServer.MIME_PLAINTEXT, "NumberFormatException while parsing status " +
                            "code" + ex.getMessage());
        }
        if (statusCode == NanoHTTPD.Response.Status.OK.getRequestStatus()) {
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK,
                    NanoHTTPD.MIME_PLAINTEXT, "LAN OTA Finished successfully");
        } else {
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST,
                    NanoHTTPD.MIME_PLAINTEXT, "LAN OTA failure with status code" + statusCode);
        }
    }

    @Override
    public NanoHTTPD.Response get(RouterNanoHTTPD.UriResource uriResource,
                                  Map<String, String> urlParams,
                                  NanoHTTPD.IHTTPSession session) {
        AylaLanOTADevice device = getOTADevice(uriResource, session);

        if (device == null) {
            // Nobody around to service the request
            String clientIP = session.getHeaders().get(AylaHttpServer.HEADER_CLIENT_IP);
            AylaLog.e(LOG_TAG, "No device for Lan OTA from " + clientIP);
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND,
                    NanoHTTPD.MIME_PLAINTEXT, "No device found");
        }

        AylaLog.d(LOG_TAG, "GET Lan OTA request from " + device.getDsn());

        InputStream is;
        NanoHTTPD.Response response;
        String path = device.getOTAPath();
        try {
            File file = new File(path);
            long start = 0;
            long end = file.length() - AylaLanOTADevice.HEADER_FILE_SIZE;
            NanoHTTPD.Response.IStatus status = NanoHTTPD.Response.Status.OK;
            String contentRange = null;

            String rangeHeader = session.getHeaders().get("range");
            if (!TextUtils.isEmpty(rangeHeader)) {
                String rangeValue = rangeHeader.trim().substring("bytes=".length());
                long fileRemainLength = file.length() - AylaLanOTADevice.HEADER_FILE_SIZE;

                if (rangeValue.startsWith("-")) {
                    start = fileRemainLength - Long.parseLong(rangeValue.substring("-".length()));
                } else {
                    String[] range = rangeValue.split("-");
                    start = Long.parseLong(range[0]);
                    end = range.length > 1 ? Long.parseLong(range[1])
                            : fileRemainLength;
                }
                if (end > fileRemainLength) {
                    end = fileRemainLength;
                }
                status = NanoHTTPD.Response.Status.PARTIAL_CONTENT;
                StringBuilder sb = new StringBuilder();
                sb.append("Content-Range: bytes ");
                sb.append(start);
                sb.append("-");
                sb.append(end);
                sb.append("/");
                sb.append(fileRemainLength);
                contentRange = sb.toString();
            }
            is = new FileInputStream(file);
            //Now skip the HEADER_FILE_SIZE and the start value
            is.skip(AylaLanOTADevice.HEADER_FILE_SIZE + start);
            response = NanoHTTPD.newFixedLengthResponse(status, NanoHTTPD.MIME_PLAINTEXT, is, end);
            if (contentRange != null) {
                response.addHeader("Accept-Ranges", "bytes");
                response.addHeader(" Content-Range", contentRange);
            }
        } catch (FileNotFoundException e) {
            AylaLog.e(LOG_TAG, "FileNotFoundException for Lan OTA " + e.getMessage());
            response = NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND,
                    NanoHTTPD.MIME_PLAINTEXT, "No image file found for device " + device.getDsn());
        } catch (IOException e) {
            AylaLog.e(LOG_TAG, "IOException while reading Lan OTA image " + e.getMessage());
            response = NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR,
                    NanoHTTPD.MIME_PLAINTEXT, "I/O Error while reading LAN OTA for device"
                            + device.getDsn());
        }
        return response;
    }
}
