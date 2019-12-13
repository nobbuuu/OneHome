package com.aylanetworks.aylasdk;
/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import android.text.TextUtils;

import com.google.gson.annotations.Expose;

/**
 * AylaPropertyTriggerApp are application triggers on the Cloud Service.This object is used by
 * createApp,updateApp,fetchApps and deleteApp methods in AylaPropertyTrigger object
 */
@Deprecated
public class AylaPropertyTriggerApp extends AylaServiceApp {
    @Expose
    private String name;
    @Expose
    private String param1;
    @Expose
    private String param2;
    @Expose
    private String param3;
    @Expose
    private Number key;
    @Expose
    private String email_template_id;
    @Expose
    private String email_subject;
    @Expose
    private String email_body_html;

    Number getKey() {
        return key;
    }

    @Override
    public String toJSON() {
        name = _notificationType.stringValue();
        switch (_notificationType) {
            case SMS:
                if (TextUtils.isEmpty(countryCode)) {
                    param1 = "1";// country code is required by service, By default US.
                } else {
                    param1 = countryCode.replaceFirst("^0*", "");
                }
                param2 = phoneNumber;
                param3 = message;
                break;
            case EMail:
                param1 = emailAddress;
                param3 = message;
                if (emailTemplate != null) {
                    this.email_template_id = emailTemplate.getEmailTemplateId();
                    this.email_subject = emailTemplate.getEmailSubject();
                    this.email_body_html = emailTemplate.getEmailBodyHtml();
                }
                break;
            case GooglePush:
            case FCMPush:
                param1 = registrationId;
                param2 = applicationId;
                param3 = message;
                break;
            case BaiduPush:
                param1 = AylaNetworks.sharedInstance().getSystemSettings().appId;
                param2 = channelId;
                AylaBaiduMessage baiduMessage = new AylaBaiduMessage();
                baiduMessage.msg = this.getMessage();
                baiduMessage.sound = this.getPushSound();
                baiduMessage.data = this.getPushMdata();
                baiduMessage.msgType = 0;//for normal message

                param3 = AylaNetworks.sharedInstance().getGson().toJson
                        (baiduMessage, AylaBaiduMessage.class);
                break;
            default:
                return null;
        }

        Wrapper triggerAppWrapper = new Wrapper();
        triggerAppWrapper.trigger_app = this;
        return AylaNetworks.sharedInstance().getGson().toJson(triggerAppWrapper, Wrapper.class);
    }

    @Override
    public void populateFields() {
        if (name == null)
            return;

        _notificationType = NotificationType.fromString(name);
        switch (_notificationType) {
            case SMS:
                countryCode = param1;
                phoneNumber = param2;
                message = param3;
                break;
            case EMail:
                emailAddress = param1;
                message = param3;
                if (email_template_id != null) {
                    emailTemplate = new AylaEmailTemplate();
                    emailTemplate.setEmailTemplateId(email_template_id);
                    emailTemplate.setEmailSubject(email_subject);
                    emailTemplate.setEmailBodyHtml(email_body_html);
                }
                break;
            case FCMPush:
                applicationId = param2;
                // pass-through
            case GooglePush:
                registrationId = param1;
                message = param3;
                break;
            case BaiduPush:
                applicationId = param1;
                channelId = param2;
                message = param3;
            default:
                break;
        }
    }

    public static class Wrapper {
        @Expose
        public AylaPropertyTriggerApp trigger_app;

        public static AylaPropertyTriggerApp[] unwrap(Wrapper[] wrappedTriggers) {
            int size = 0;
            if (wrappedTriggers != null) {
                size = wrappedTriggers.length;
            }

            AylaPropertyTriggerApp[] triggers = new AylaPropertyTriggerApp[size];
            for (int i = 0; i < size; i++) {
                triggers[i] = wrappedTriggers[i].trigger_app;
                triggers[i].populateFields();
            }
            return triggers;
        }
    }

    public class AylaBaiduMessage {
        /**
         * User facing plain text
         * */
        @Expose
        private String msg;

        /**
         * Audio file name, same as that in push_android/sound
         * */
        @Expose
        private String sound;

        /**
         * Meta data in json, for future scalability
         * */
        @Expose
        private String data;

        /**
         * Same as messageType in GCM, for future scalability.
         * Ranging from 0 to 99, 0 for message, others for some
         * other commands.
         * */
        @Expose
        private int msgType;

        public int getMsgType() {
            return msgType;
        }

        public String getSound() {
            return sound;
        }

        public String getMsg() {
            return msg;
        }
    }
}
