package com.aylanetworks.aylasdk;

import com.google.gson.annotations.Expose;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/*
 * Android_AylaSDK
 *
 * Copyright 2016 Ayla Networks, all rights reserved
 */

/**
 * Represents a time zone
 */
public class AylaTimeZone {

    @Expose
    public String utcOffset;            // utc offset. Format must be +HH:MM
    // or -HH:MM
    @Expose
    public Boolean dst;	                // true if the location follows DST
    @Expose
    public Boolean dstActive;	        // true if DST is currently active
    @Expose
    public String dstNextChangeDate;	// Next DST state change date. Forma:yyyy-mm-dd
    @Expose
    public String dstNextChangeTime;    // Next DST state change time
    @Expose
    public String tzId;				    // String identifier for the timezone.
    @Expose
    Number key;

    public static class Wrapper{
        @Expose
        AylaTimeZone timeZone;

        public static AylaTimeZone[] unwrap(Wrapper[] container){
            AylaTimeZone[] timeZones = new AylaTimeZone[container.length];
            for(int i=0; i< container.length; i++){
                timeZones[i] = container[i].timeZone;
            }
            return timeZones;
        }
    }

    /**
     *
     * @return Next DST change date in the format "yyyy-MM-dd HH:mm Z"
     */
    public String getDstNextChangeDate() {

        if(dst){
            String nextChangeDateString = dstNextChangeDate + " " + dstNextChangeTime + " " +
                    utcOffset;
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm Z", Locale.US);
            dateFormat.setTimeZone(TimeZone.getTimeZone(tzId));
            dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date;
            Calendar calendar = Calendar.getInstance();
            try {
                date = dateFormat.parse(nextChangeDateString);
            } catch (ParseException e) {
                AylaLog.w("AylaTimeZone", "Error parsing date");
                return null;
            }
            calendar.setTime(date);
            if(dstActive) {
                calendar.set(Calendar.HOUR_OF_DAY, calendar.get(Calendar.HOUR_OF_DAY) - 1);
            }
            return dateFormat.format(calendar.getTime());
        }
        return null;
    }
}
