package com.aylanetworks.aylasdk.util;
/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class DateUtils {
    private static DateFormat __iso8601DateFormat;

    static {
        // Server returns with a literal 'Z' at the end
        // "data_updated_at": "2015-12-28T19:35:50Z",
        __iso8601DateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        __iso8601DateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    /**
     * Returns a DateFormat object initialized to deal with date strings returned from the Ayla
     * cloud service
     *
     * @return A DateFormat object used to translate strings to dates and back. The DateFormat
     * object is configured for UTC time and should not be modified.
     */
    public static DateFormat getISO8601DateFormat() {
        return __iso8601DateFormat;
    }

    public static Date fromJsonString(String jsonDateString) {
        try {
            return __iso8601DateFormat.parse(jsonDateString);
        } catch (ParseException e) {
            return null;
        } catch (NullPointerException e) {
            return null;
        }
    }
}
