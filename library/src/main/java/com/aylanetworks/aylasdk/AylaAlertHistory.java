package com.aylanetworks.aylasdk;

/**
 * AylaSDK
 * <p/>
 * Copyright 2016 Ayla Networks, all rights reserved
 */

import android.text.TextUtils;

import com.aylanetworks.aylasdk.util.ObjectUtils;
import com.google.gson.annotations.Expose;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

public class AylaAlertHistory {
    @Expose
    private String oem;
    @Expose
    private String sentAt;
    @Expose
    private String propertyId;
    @Expose
    private String propertyDescription;
    @Expose
    private String propertyName;
    @Expose
    private String propertyValue;
    @Expose
    private String propertyDataUpdatedAt;
    @Expose
    private String propertyDataUpdatedAtDeviceTz;
    @Expose
    private String triggerId;
    @Expose
    private String triggerDescription;
    @Expose
    private String triggerTriggeredAt;
    @Expose
    private String triggerAppDescription;
    @Expose
    private String alertType;
    @Expose
    private AylaAlertContent alertContent;
    @Expose
    private String contentDescription;

    public String getPropertyName() {
        return propertyName;
    }

    public static class Wrapper{
        @Expose
        public AylaAlertHistory alertHistory;

        public static AylaAlertHistory[] unwrap(AylaAlertHistory.Wrapper[] wrappedHistory){
            AylaAlertHistory[] alerts = new AylaAlertHistory[wrappedHistory.length];
            for(int i=0; i<wrappedHistory.length; i++){
                alerts[i] = wrappedHistory[i].alertHistory;
            }
            return alerts;
        }
    }

    public String getAlertType() {
        return alertType;
    }

    public String getPropertyDescription() {
        return propertyDescription;
    }

    public String getPropertyValue() {
        return propertyValue;
    }

    public String getPropertyDataUpdatedAt() {
        return propertyDataUpdatedAt;
    }

    public String getPropertyDataUpdatedAtDeviceTz() {
        return propertyDataUpdatedAtDeviceTz;
    }

    public String getTriggerId() {
        return triggerId;
    }

    public String getTriggerTriggeredAt() {
        return triggerTriggeredAt;
    }

    public String getTriggerDescription() {
        return triggerDescription;
    }

    public String getTriggerAppDescription() {
        return triggerAppDescription;
    }

    public AylaAlertContent getAlertContent() {
        return alertContent;
    }

    public String getContentDescription() {
        return contentDescription;
    }

    public String getPropertyId() {
        return propertyId;
    }

    /**
     * @return Date this alert was sent in the format "yyyy-MM-dd'T'HH:mm:ss'Z'". Returns null if
     * date is not available or is not in the supported format.
     */
    public Date getSentAtDate() {
        if(sentAt == null){
            return null;
        }
        SimpleDateFormat dateFormat = new SimpleDateFormat
                ("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        Date date = null;
        try {
            date = dateFormat.parse(sentAt);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return date;
    }

    /**
     * Returns the date this alert was sent on as a String.
     */
    public String getSentAt(){
        return sentAt;
    }

    public String getOem() {
        return oem;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        final String NEW_LINE = System.getProperty("line.separator");
        result.append(this.getClass().getName()).append(" {").append(NEW_LINE)
                .append(" oem: " + oem).append(NEW_LINE)
                .append(" sentAt: " + sentAt).append(NEW_LINE)

                .append(" propertyId: " + propertyId).append(NEW_LINE)
                .append(" propertyDescription: " + propertyDescription).append(NEW_LINE)
                .append(" propertyName: " + propertyName).append(NEW_LINE)
                .append(" propertyValue: " + propertyValue).append(NEW_LINE)
                .append(" propertyDataUpdatedAt: " + propertyDataUpdatedAt).append(NEW_LINE)

                .append(" triggerId: " + triggerId).append(NEW_LINE)
                .append(" triggerDescription: " + triggerDescription).append(NEW_LINE)
                .append(" triggerTriggeredAt: " + triggerTriggeredAt).append(NEW_LINE)
                .append(" triggerAppDescription: " + triggerAppDescription).append(NEW_LINE)

                .append(" alertType: " + alertType).append(NEW_LINE)
                .append(" alertContent: " + alertContent).append(NEW_LINE)
                .append(" contentDescription: " + contentDescription).append(NEW_LINE)
                .append("}");
        return result.toString();
    }

    @Override
    public boolean equals(Object other) {
        if (other==null)
            return false;
        if (!(other instanceof AylaAlertHistory)) {
            return false;
        }

        AylaAlertHistory that = (AylaAlertHistory) other;

        // Custom equality check here.
        return TextUtils.equals(this.oem, that.oem) &&
                TextUtils.equals(this.sentAt, that.sentAt) &&
                TextUtils.equals(this.propertyId, that.propertyId) &&
                TextUtils.equals(this.propertyDescription, that.propertyDescription) &&
                TextUtils.equals(this.propertyName, that.propertyName) &&
                TextUtils.equals(this.propertyValue, that.propertyValue) &&
                TextUtils.equals(this.propertyDataUpdatedAt, that.propertyDataUpdatedAt) &&
                TextUtils.equals(this.propertyDataUpdatedAtDeviceTz, that.propertyDataUpdatedAtDeviceTz) &&
                TextUtils.equals(this.triggerId, that.triggerId) &&
                TextUtils.equals(this.triggerDescription, that.triggerDescription) &&
                TextUtils.equals(this.triggerTriggeredAt, that.triggerTriggeredAt) &&
                TextUtils.equals(this.triggerAppDescription, that.triggerAppDescription) &&
                TextUtils.equals(this.triggerTriggeredAt, that.triggerTriggeredAt) &&
                TextUtils.equals(this.alertType, that.alertType) &&
                this.alertContent.equals(that.alertContent) &&
                TextUtils.equals(this.contentDescription, that.contentDescription);
    }

    public static class AylaAlertContent {
        @Expose
        private String message;
        @Expose
        private String emailTo;
        @Expose
        private String emailSubject;
        @Expose
        private String smsTo;
        @Expose
        private String smsCountryCode;

        public String getMessage() {
            return message;
        }

        public String getEmailTo() {
            return emailTo;
        }

        public String getEmailSubject() {
            return emailSubject;
        }

        public String getSmsTo() {
            return smsTo;
        }

        public String getSmsCountryCode() {
            return smsCountryCode;
        }

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder();
            final String NEW_LINE = System.getProperty("line.separator");
            result.append(this.getClass().getName()).append(" {").append(NEW_LINE)
                    .append(" message: " + message).append(NEW_LINE)
                    .append(" emailTo: " + emailTo).append(NEW_LINE)
                    .append(" emailSubject: " + emailSubject).append(NEW_LINE)
                    .append(" smsTo: " + smsTo).append(NEW_LINE)
                    .append(" smsCountryCode: " + smsCountryCode).append(NEW_LINE)
                    .append("}");
            return result.toString();
        }

        @Override
        public boolean equals(Object other) {
            if (other == null) {
                return false;
            }
            if (!(other instanceof AylaAlertContent)) {
                return false;
            }

            AylaAlertContent that = (AylaAlertContent) other;

            // Custom equality check here.
            return TextUtils.equals(this.message, that.message) &&
                    TextUtils.equals(this.emailTo, that.emailTo) &&
                    TextUtils.equals(this.emailSubject, that.emailSubject) &&
                    TextUtils.equals(this.smsTo, that.smsTo) &&
                    TextUtils.equals(this.smsCountryCode, that.smsCountryCode);
        }
    }

    public static class AlertFilter{
        private HashMap<String, String> filterMap;

        public AlertFilter(){
            filterMap = new HashMap<String, String>();
        }

        public HashMap<String, String> build(){
            return filterMap;
        }

        /**
         * Create a filter for alert history requests. If this method is called with the same
         * filter and operator combination, the previous entry will be replaced.
         * @param filter Field to filter. {@link AlertHistoryFilters}
         * @param operator Valid operator for the filter {@link FilterOperators}.
         * @param operand A single value or a set of comma separated values to be passed as a single
         *                string. eg: "Blue_LED,Blue_button"
         *
         * For example,
         *                To exclude all alerts with property name Blue_LED, use
         *                add(PropertyName, Not,"Blue_LED")
         *
         *                To exclude results with property names Blue_LED and Blue_button, use
         *                add(PropertyName, Notin, "Blue_LED,Blue_button");
         *
         */
        public void add(AlertHistoryFilters filter, FilterOperators operator,
                        String operand){
            if(filterMap == null){
                filterMap = new HashMap<String, String>();
            }
            filterMap.put(filter.stringValue() + operator.stringValue(), operand);
        }

    }

    /**
     * Valid filters for alert history
     */
    public enum AlertHistoryFilters{
        PropertyName("property_name"),
        PropertyDescription("property_description"),
        PropertyValue("property_value"),
        TriggerTriggeredAt("trigger_triggered_at"),
        TriggerDescription("trigger_description"),
        TriggerAppDescription("trigger_app_description"),
        AlertType("alert_type"),
        AlertContent("alert_content"),
        ContentDescription("content_description");


        private String _stringValue;

        AlertHistoryFilters(String value){
            _stringValue = value;
        }

        public String stringValue(){
            return _stringValue;
        }
    }

    /**
     * Filter operators for alert history
     */
    public enum FilterOperators{
        Not("_not"), // Exact match exclusion
        Like("_like"), // Partial match
        NotLike("_notlike"), //Partial match exclusion
        GreaterThan("_gt"), //Greater than
        GreaterThanOrEqualTo("_gte"), // Greater than or equals
        LessThan("_lt"), // Less than
        LessThanOrEqualTo("_lte"), //Less than or equals
        In("_in"), // Values are in a comma separated list
        NotIn("_notin"), // Values are in a comma separated list
        EqualTo("_eq"); // Exact match

        private String _stringValue;

        FilterOperators(String value){
            _stringValue = value;
        }

        public String stringValue(){
            return _stringValue;
        }
    }
}
