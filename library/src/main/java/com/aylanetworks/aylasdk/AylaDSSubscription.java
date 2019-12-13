package com.aylanetworks.aylasdk;

import com.google.gson.annotations.Expose;

/*
 * Android_AylaSDK
 * <p>
 * Copyright 2016 Ayla Networks, all rights reserved
 */
public class AylaDSSubscription {
    @Expose
    private String id;
    @Expose
    private String oem;
    @Expose
    private String dsn;
    @Expose
    private String name;
    @Expose
    private String description;
    @Expose
    private String propertyName;
    @Expose
    private boolean isSuspended;
    @Expose
    private String createdAt;
    @Expose
    private String updatedAt;
    @Expose
    private String dateSuspended;
    @Expose
    private String oemModel;
    @Expose
    private String streamKey;
    @Expose
    private String clientType;
    @Expose
    private String subscriptionType;

    public static class Wrapper{
        @Expose
        public AylaDSSubscription subscription;

        public static AylaDSSubscription[] unwrap(Wrapper[] subscriptions){
            AylaDSSubscription[] subscriptionList = new AylaDSSubscription[subscriptions.length];
            for(int i=0; i< subscriptions.length; i++){
                subscriptionList[i] = subscriptions[i].subscription;
            }
            return subscriptionList;
        }
    }

    public String getSubscriptionType() {
        return subscriptionType;
    }

    public void setSubscriptionType(String subscriptionType) {
        this.subscriptionType = subscriptionType;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getStreamKey() {
        return streamKey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDsn() {
        return dsn;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setDsn(String dsn) {
        this.dsn = dsn;

    }

    public String getOemModel() {
        return oemModel;
    }
}
