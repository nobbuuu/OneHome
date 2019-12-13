package com.aylanetworks.aylasdk;
/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import com.google.gson.annotations.Expose;

/**
 * Represents an Email template. Templates are used when a notification is sent to an email.
 */

public class AylaEmailTemplate {
    @Expose
    private String emailTemplateId;
    @Expose
    private String emailSubject;
    @Expose
    private String emailBodyHtml;

    public String getEmailTemplateId() {
        return emailTemplateId;
    }

    public void setEmailTemplateId(String emailTemplateId) {
        this.emailTemplateId = emailTemplateId;
    }

    public String getEmailSubject() {
        return emailSubject;
    }

    public void setEmailSubject(String emailSubject) {
        this.emailSubject = emailSubject;
    }

    public String getEmailBodyHtml() {
        return emailBodyHtml;
    }

    public void setEmailBodyHtml(String emailBodyHtml) {
        this.emailBodyHtml = emailBodyHtml;
    }
}