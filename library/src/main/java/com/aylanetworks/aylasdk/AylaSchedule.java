package com.aylanetworks.aylasdk;
/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import com.android.volley.Request;
import com.android.volley.Response;
import com.aylanetworks.aylasdk.error.AylaError;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.aylanetworks.aylasdk.error.InvalidArgumentError;
import com.aylanetworks.aylasdk.error.OperationIncompleteError;
import com.aylanetworks.aylasdk.error.PreconditionError;
import com.aylanetworks.aylasdk.util.EmptyListener;
import com.google.gson.annotations.Expose;

import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AylaSchedule objects are used to set schedules on devices. Schedules may be configured in many
 * ways, and can fire {@link AylaScheduleAction}s at the start or end of the schedule cycle.
 * <p>
 * AylaSchedules may not be created or destroyed, but rather are provided to devices via the
 * device template when the device is registered.
 * <p>
 * Schedules for a particular device may be obtained via {@link AylaDevice#fetchSchedules}
 */
public class AylaSchedule implements Cloneable{
    private static final String LOG_TAG = "AylaSchedule";
    @Expose
    private Number key; // key for the schedule
    @Expose
    private String direction; //input:to_device, output:from_device, required
    @Expose
    private String name; // name of the schedule, required
    @Expose
    private String displayName; // user facing name of the schedule, optional
    @Expose
    private boolean active; // true/active by default, optional
    @Expose
    private boolean utc; // true/utc tz by default, optional
    @Expose
    private String startDate; // yyyy-mm-dd inclusive, optional
    @Expose
    private String endDate; // yyyy-mm-dd inclusive, optional
    @Expose
    private String startTimeEachDay; // HH:mm:ss inclusive
    @Expose
    private String endTimeEachDay; // HH:mm:ss inclusive, optional
    @Expose
    private int[] daysOfWeek; // 1-7 inclusive, 1 == Sunday, optional
    @Expose
    private int[] daysOfMonth; // 1-31 inclusive, 28, 29, 30, or 31, optional
    @Expose
    private int[] monthsOfYear; // 1-12 inclusive, 1 == January, optional
    @Expose
    private int[] dayOccurOfMonth; // 1-7 inclusive, optional
    @Expose
    private int duration; // seconds, default == 0, optional
    @Expose
    private int interval; // seconds, default == 0, optional
    @Expose
    private boolean fixedActions;// true if Schedule Actions are fixed
    /**
     * Weak reference to the AylaDevice
     */
    private WeakReference<AylaDevice> _device;

    public String getDirection() { return direction; }

    public String getName() { return name; }

    public String getDisplayName() { return displayName; }

    public boolean isActive() { return active; }

    public boolean isUtc() { return utc; }

    private static final SimpleDateFormat _dateFormat = new SimpleDateFormat
            ("yyyy-MM-dd", Locale.US);

    public Date getStartDate() { return getDate(startDate); }

    public Date getEndDate() { return getDate(endDate); }

    public String getStartTimeEachDay() { return startTimeEachDay; }

    public String getEndTimeEachDay() { return endTimeEachDay; }

    public int[] getDaysOfWeek() { return daysOfWeek; }

    public int[] getDaysOfMonth() { return daysOfMonth; }

    public int[] getMonthsOfYear() { return monthsOfYear; }

    public int[] getDayOccurOfMonth() { return dayOccurOfMonth; }

    public int getDuration() { return duration; }

    public int getInterval() { return interval; }

    public Number getKey() { return key; }

    /**
     * When a Schedule has fixedActions value as true it means the Schedule already has Schedule
     * actions and the user cannot add any new Schedule actions. For fixedActions the user can
     * only edit the existing Schedule Actions
    */
    public boolean hasFixedActions() { return fixedActions; }

    public void setDirection(String direction) { this.direction = direction; }

    public void setName(String name) { this.name = name; }

    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public void setActive(boolean active) { this.active = active; }

    public void setUtc(boolean utc) { this.utc = utc; }

    public void setStartDate(Date startDate) {
        if(startDate != null) {
            this.startDate = _dateFormat.format(startDate);
        } else {
            this.startDate = null;
        }

    }

    public void setEndDate(Date endDate) {
        if(endDate != null) {
            this.endDate = _dateFormat.format(endDate);
        } else {
            this.endDate = null;
        }
    }

    public void setEndTimeEachDay(String endTimeEachDay) { this.endTimeEachDay = endTimeEachDay; }

    public void setDaysOfWeek(int[] daysOfWeek) { this.daysOfWeek = daysOfWeek; }

    public void setDaysOfMonth(int[] daysOfMonth) { this.daysOfMonth = daysOfMonth; }

    public void setMonthsOfYear(int[] monthsOfYear) { this.monthsOfYear = monthsOfYear; }

    public void setDayOccurOfMonth(int[] dayOccurOfMonth) { this.dayOccurOfMonth = dayOccurOfMonth;}

    public void setDuration(int duration) { this.duration = duration; }

    public void setInterval(int interval) { this.interval = interval; }

    public void setStartTimeEachDay(String startTimeEachDay) {
        this.startTimeEachDay = startTimeEachDay;
    }
    @Override
    protected Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    /**
     * Fetches an Array of existing AylaScheduleActions from the cloud for this Schedule
     *
     * @param successListener Listener to receive on successful fetch of AylaScheduleActions
     * @param errorListener   Listener to receive an AylaError should one occur
     * @return the AylaAPIRequest object used to Fetch an array of ScheduleActions
     */
    public AylaAPIRequest fetchActions(final Response.Listener<AylaScheduleAction[]>
                                               successListener,
                                       final ErrorListener errorListener) {
        final AylaDeviceManager deviceManager = getDeviceManager();
        if (deviceManager == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new PreconditionError("No device manager is " +
                        "available"));
            }
            return null;
        }

        AylaSessionManager sessionManager = deviceManager.getSessionManager();
        if (sessionManager == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new PreconditionError("No session is active"));
            }
            return null;
        }
        Number scheduleKey = getKey();
        if (scheduleKey == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InvalidArgumentError("Invalid Schedule Key."
                        + "The Schedule should be first fetched from the service"));
            }
            return null;
        }
        String url = deviceManager.deviceServiceUrl("apiv1/schedules/" + scheduleKey +
                "/schedule_actions.json");

        AylaAPIRequest request = new AylaAPIRequest<>(
                Request.Method.GET,
                url,
                null,
                AylaScheduleAction.Wrapper[].class,
                sessionManager,
                new Response.Listener<AylaScheduleAction.Wrapper[]>() {
                    @Override
                    public void onResponse(AylaScheduleAction.Wrapper[] response) {
                        AylaScheduleAction[] scheduleActions
                                = AylaScheduleAction.Wrapper.unwrap(response);
                        successListener.onResponse(scheduleActions);
                    }
                }, errorListener);

        deviceManager.sendDeviceServiceRequest(request);
        return request;
    }

    /**
     * Updates an array of AylaScheduleActions.
     *
     * @param scheduleActions An array AylaScheduleActions to be created for this schedule
     * @param successListener Listener to receive on successful creation of ScheduleAction
     * @param errorListener   Listener to receive an AylaError should one occur
     * @return the AylaAPIRequest object used to Create ScheduleAction
     */
    public AylaAPIRequest updateActions(
            final AylaScheduleAction[] scheduleActions,
            final Response.Listener<AylaScheduleAction[]> successListener,
            final ErrorListener errorListener) {

        final AylaDeviceManager deviceManager = getDeviceManager();
        if (deviceManager == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new PreconditionError("No device manager is " +
                        "available"));
            }
            return null;
        }

        AylaSessionManager sessionManager = deviceManager.getSessionManager();
        if (sessionManager == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new PreconditionError("No session is active"));
            }
            return null;
        }
        if (scheduleActions == null || scheduleActions.length == 0) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InvalidArgumentError("Invalid Schedule Actions"));
            }
            return null;
        }
        final List<AylaScheduleAction> listScheduleActions = new ArrayList<>
                (Arrays.asList(scheduleActions));
        //Now Iterate through the list and make sure they all are ValidScheduleActions and have
        // valid Keys.
        for(AylaScheduleAction aylaScheduleAction :listScheduleActions) {
            if (isInValidScheduleAction(aylaScheduleAction, errorListener))
                return null;

            Number scheduleActionKey = aylaScheduleAction.getKey();
            if (scheduleActionKey == null) {
                if (errorListener != null) {
                    errorListener.onErrorResponse(new InvalidArgumentError("Invalid Schedule Action " +
                            "Key.The Schedule Action should be first fetched from the service"));
                }
                return null;
            }
        }

        //Check if the first Schedule Action is valid.
        AylaScheduleAction firstScheduleAction = listScheduleActions.remove(0);

        AylaScheduleAction.Wrapper scheduleWrapper = new AylaScheduleAction.Wrapper();
        scheduleWrapper.scheduleAction = firstScheduleAction;
        final String postBodyString = AylaNetworks.sharedInstance().getGson().toJson
                (scheduleWrapper, AylaScheduleAction.Wrapper.class);

        String url = deviceManager.deviceServiceUrl("apiv1/schedule_actions/" +
                firstScheduleAction.getKey() + ".json");

        final Map<String,AylaAPIRequest> requestMap = new HashMap<>(1);
        final List<AylaScheduleAction> updatedActions = new ArrayList<>(scheduleActions.length);
        final ErrorListener updateErrorListener = new ErrorListener() {
            @Override
            public void onErrorResponse(AylaError error) {
                if (errorListener != null) {
                    errorListener.onErrorResponse(new OperationIncompleteError(updatedActions,
                            error.getMessage(),error.getCause()));
                }
            }
        };
        final Response.Listener<AylaScheduleAction> listener = new Response.Listener
                <AylaScheduleAction>() {
            public void onResponse(AylaScheduleAction scheduleAction) {
                updatedActions.add(scheduleAction);
                if (updatedActions.size()== scheduleActions.length) {
                    AylaScheduleAction[] arrayCreated = updatedActions.toArray(
                            new AylaScheduleAction[updatedActions.size()]);
                    successListener.onResponse(arrayCreated);
                }
                AylaAPIRequest origReq= requestMap.get("originalReq");
                while (!listScheduleActions.isEmpty()) {
                    AylaScheduleAction aylaScheduleAction = listScheduleActions.remove(0);
                    updateAction(aylaScheduleAction, this, updateErrorListener,origReq);
                }
            }
        };

        AylaAPIRequest<AylaScheduleAction.Wrapper> request = new AylaJsonRequest
                <AylaScheduleAction.Wrapper>(
                Request.Method.PUT,
                url,
                postBodyString,
                null,
                AylaScheduleAction.Wrapper.class,
                sessionManager,
                new EmptyListener<AylaScheduleAction.Wrapper>(),
                updateErrorListener) {
            @Override
            protected void deliverResponse(AylaScheduleAction.Wrapper response) {
                updatedActions.add(response.scheduleAction);
                if (!listScheduleActions.isEmpty()) {
                    requestMap.put("originalReq",this);
                    AylaScheduleAction scheduleAction = listScheduleActions.remove(0);
                    updateAction(scheduleAction, listener, updateErrorListener, this);
                }
            }
        };

        deviceManager.sendDeviceServiceRequest(request);
        return request;
    }

    /**
     * Creates an AylaScheduleAction in the cloud
     *
     * @param scheduleAction  AylaScheduleAction
     * @param successListener Listener to receive on successful creation of ScheduleAction
     * @param errorListener   Listener to receive an AylaError should one occur
     * @return the AylaAPIRequest object used to Create ScheduleAction
     */
    public AylaAPIRequest createAction(final AylaScheduleAction scheduleAction,
                                       final Response.Listener<AylaScheduleAction> successListener,
                                       final ErrorListener errorListener) {
        final AylaDeviceManager deviceManager = getDeviceManager();
        if (deviceManager == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new PreconditionError("No device manager is " +
                        "available"));
            }
            return null;
        }

        AylaSessionManager sessionManager = deviceManager.getSessionManager();
        if (sessionManager == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new PreconditionError("No session is active"));
            }
            return null;
        }
        //Check if it is a valid ScheduleAction
        if (isInValidScheduleAction(scheduleAction, errorListener)) {
            return null;
        }
        Number scheduleKey = getKey();
        if (scheduleKey == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InvalidArgumentError("Schedule Key is required"));
            }
            return null;
        }
        AylaScheduleAction.Wrapper scheduleWrapper = new AylaScheduleAction.Wrapper();
        scheduleWrapper.scheduleAction = scheduleAction;
        final String postBodyString = AylaNetworks.sharedInstance().getGson().toJson
                (scheduleWrapper, AylaScheduleAction.Wrapper.class);

        String url = deviceManager.deviceServiceUrl("apiv1/schedules/" + scheduleKey +
                "/schedule_actions.json");
        AylaAPIRequest request = new AylaJsonRequest<>(
                Request.Method.POST,
                url,
                postBodyString,
                null,
                AylaScheduleAction.Wrapper.class,
                sessionManager,
                new Response.Listener<AylaScheduleAction.Wrapper>() {
                    @Override
                    public void onResponse(AylaScheduleAction.Wrapper response) {
                        successListener.onResponse(response.scheduleAction);
                    }
                }, errorListener);

        deviceManager.sendDeviceServiceRequest(request);
        return request;
    }

    /**
     * Deletes an existing AylaScheduleAction from the cloud
     *
     * @param scheduleAction  AylaScheduleAction that needs to be deleted. This is the
     *                        existing ScheduleAction that has been fetched from service
     * @param successListener Listener to receive on successful deletion of AylaScheduleAction
     * @param errorListener   Listener to receive an AylaError should one occur
     * @param originalRequest Original API Request
     * @return the AylaAPIRequest object used to delete ScheduleAction
     */
    public AylaAPIRequest deleteAction(final AylaScheduleAction scheduleAction,
                                       final Response.Listener<AylaAPIRequest.EmptyResponse>
                                               successListener,
                                       final ErrorListener errorListener,
                                       final AylaAPIRequest originalRequest) {
        AylaDeviceManager deviceManager = getDeviceManager();
        if (deviceManager == null) {
            errorListener.onErrorResponse(new PreconditionError("No device manager is available"));
            return null;
        }

        AylaSessionManager sessionManager = deviceManager.getSessionManager();
        if (sessionManager == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new PreconditionError("No session is active"));
            }
            return null;
        }

        if (scheduleAction == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InvalidArgumentError("Invalid Schedule Action"));
            }
            return null;
        }
        Number scheduleActionKey = scheduleAction.getKey();
        if (scheduleActionKey == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InvalidArgumentError("Invalid Schedule Action " +
                        "Key.The Schedule Action should be first fetched from the service"));
            }
            return null;
        }

        String url = deviceManager.deviceServiceUrl("apiv1/schedule_actions/" + scheduleActionKey + "" +
                ".json");
        AylaAPIRequest<AylaAPIRequest.EmptyResponse> request = new AylaAPIRequest<>(
                Request.Method.DELETE, url, null, AylaAPIRequest.EmptyResponse.class,
                sessionManager, successListener, errorListener);

        // This is a compound request- we need to keep the chain going so canceling the original
        // request will cancel this new request.
        if (originalRequest != null) {
            if (originalRequest.isCanceled()) {
                request.cancel();
            } else {
                originalRequest.setChainedRequest(request);
                deviceManager.sendDeviceServiceRequest(request);
            }
        } else {
            deviceManager.sendDeviceServiceRequest(request);
        }
        return request;
    }

    /**
     * Deletes all ScheduleActions for this Schedule
     *
     * @param successListener Listener to receive on successful deletion of AylaScheduleAction
     * @param errorListener   Listener to receive an AylaError should one occur
     * @return the AylaAPIRequest object used to delete ScheduleAction
     */
    public AylaAPIRequest deleteAllActions(final Response.Listener<AylaAPIRequest.EmptyResponse>
                                                   successListener,
                                           final ErrorListener errorListener) {
        final AylaDeviceManager deviceManager = getDeviceManager();
        if (deviceManager == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new PreconditionError("No device manager is " +
                        "available"));
            }
            return null;
        }

        AylaSessionManager sessionManager = deviceManager.getSessionManager();
        if (sessionManager == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new PreconditionError("No session is active"));
            }
            return null;
        }
        Number scheduleKey = getKey();
        if (scheduleKey == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InvalidArgumentError("Invalid Schedule Key."
                        + "The Schedule should be first fetched from the service"));
            }
            return null;
        }
        String url = deviceManager.deviceServiceUrl("apiv1/schedules/" + scheduleKey +
                "/schedule_actions.json");

        final Map <String, AylaAPIRequest>requestMap = new HashMap<>(1);
        final List<AylaScheduleAction> deletedActions = new ArrayList<>();
        final Map <String, List<AylaScheduleAction>>deleteActionsMap = new HashMap<>(1);
        final ErrorListener deleteErrorListener = new ErrorListener() {
            @Override
            public void onErrorResponse(AylaError error) {
                if (errorListener != null) {
                    errorListener.onErrorResponse(new OperationIncompleteError(deletedActions,
                            error.getMessage(), error.getCause()));
                }
            }
        };
        final String keyForActionsMap="scheduleActions";
        final Response.Listener<AylaAPIRequest.EmptyResponse> listener = new Response.Listener
                <AylaAPIRequest.EmptyResponse>() {
            @Override
            public void onResponse(AylaAPIRequest.EmptyResponse response) {
                List<AylaScheduleAction> toDeleteList = deleteActionsMap.get(keyForActionsMap);
                //We just deleted the top element. Now add it to the deletedActions list and remove
                //it from the toDeleteList
                AylaScheduleAction scheduleAction = toDeleteList.get(0);
                deletedActions.add(scheduleAction);
                toDeleteList.remove(0);
                if (toDeleteList.isEmpty()) {
                    //We dont have any more requests to delete
                    successListener.onResponse(response);
                } else {
                    deleteActionsMap.put(keyForActionsMap, toDeleteList);
                    scheduleAction = toDeleteList.get(0);
                    AylaAPIRequest origReq = requestMap.get("originalReq");
                    deleteAction(scheduleAction, this, deleteErrorListener, origReq);
                }
            }
        };
        //First use a Get call to get all the requests
        AylaAPIRequest<AylaScheduleAction.Wrapper[]> request = new AylaAPIRequest
                <AylaScheduleAction.Wrapper[]>(
                Request.Method.GET,
                url,
                null,
                AylaScheduleAction.Wrapper[].class,
                sessionManager,
                new EmptyListener<AylaScheduleAction.Wrapper[]>(),
                errorListener) {
            @Override
            protected void deliverResponse(AylaScheduleAction.Wrapper[] response) {
                AylaScheduleAction[] scheduleActions = AylaScheduleAction.Wrapper.unwrap(response);
                if (scheduleActions == null || scheduleActions.length == 0) {
                    //There are no ScheduleActions. This is not an error condition, Just send
                    //an empty success message
                    successListener.onResponse(new EmptyResponse());
                } else {
                    requestMap.put("originalReq", this);
                    List<AylaScheduleAction> toDeleteList = new ArrayList<>(Arrays
                            .asList(scheduleActions));
                    deleteActionsMap.put(keyForActionsMap, toDeleteList);
                    AylaScheduleAction scheduleAction = toDeleteList.get(0);
                    deleteAction(scheduleAction, listener, deleteErrorListener, this);
                }
            }
        };
        deviceManager.sendDeviceServiceRequest(request);
        return request;
    }

    /**
     * Check if the Schedule Action is Valid
     *
     * @param scheduleAction ScheduleAction
     * @param errorListener  Listener to receive an AylaError should one occur
     * @return true or false based on the validity
     */
    private boolean isInValidScheduleAction(AylaScheduleAction scheduleAction, ErrorListener
            errorListener) {
        if (scheduleAction == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InvalidArgumentError("Invalid Schedule Action"));
            }
            return true;
        }
        if (scheduleAction.getName() == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InvalidArgumentError("Schedule Action Name is " +
                        "required"));
            }
            return true;
        }
        if (scheduleAction.getType() == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InvalidArgumentError("Schedule Action Type is " +
                        "required"));
            }
            return true;
        }
        if (scheduleAction.getValue() == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InvalidArgumentError("Schedule Action Value is " +
                        "required"));
            }
            return true;
        }
        if (scheduleAction.getBaseType() == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InvalidArgumentError("Schedule Action Base " +
                        "Type is required"));
            }
            return true;
        }
        return false;
    }

    /**
     * Updates an existing AylaScheduleAction in the cloud
     *
     * @param scheduleAction  AylaScheduleAction that needs to be updated
     * @param successListener Listener to receive on successful update of ScheduleAction
     * @param errorListener   Listener to receive an AylaError should one occur
     * @return return the AylaAPIRequest object used to update ScheduleAction
     */
    private AylaAPIRequest updateAction(final AylaScheduleAction scheduleAction,
                                        final Response.Listener<AylaScheduleAction>
                                                successListener,
                                        final ErrorListener errorListener,
                                        final AylaAPIRequest originalRequest) {
        final AylaDeviceManager deviceManager = getDeviceManager();
        if (deviceManager == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new PreconditionError("No device manager is " +
                        "available"));
            }
            return null;
        }

        AylaSessionManager sessionManager = deviceManager.getSessionManager();
        if (sessionManager == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new PreconditionError("No session is active"));
            }
            return null;
        }
        if (isInValidScheduleAction(scheduleAction, errorListener)) {
            return null;
        }
        Number scheduleActionKey = scheduleAction.getKey();
        if (scheduleActionKey == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InvalidArgumentError("Invalid Schedule Action " +
                        "Key.The Schedule Action should be first fetched from the service"));
            }
            return null;
        }
        AylaScheduleAction.Wrapper scheduleWrapper = new AylaScheduleAction.Wrapper();
        scheduleWrapper.scheduleAction = scheduleAction;
        final String postBodyString = AylaNetworks.sharedInstance().getGson().toJson
                (scheduleWrapper, AylaScheduleAction.Wrapper.class);

        String url = deviceManager.deviceServiceUrl("apiv1/schedule_actions/" + scheduleActionKey +
                ".json");
        AylaAPIRequest request = new AylaJsonRequest<>(
                Request.Method.PUT,
                url,
                postBodyString,
                null,
                AylaScheduleAction.Wrapper.class,
                sessionManager,
                new Response.Listener<AylaScheduleAction.Wrapper>() {
                    @Override
                    public void onResponse(AylaScheduleAction.Wrapper response) {
                        successListener.onResponse(response.scheduleAction);
                    }
                }, errorListener);
        // This is a compound request- we need to keep the chain going so cancelling the original
        // request will cancel this new request.
        if (originalRequest != null) {
            if (originalRequest.isCanceled()) {
                request.cancel();
            } else {
                originalRequest.setChainedRequest(request);
                deviceManager.sendDeviceServiceRequest(request);
            }
        } else {
            deviceManager.sendDeviceServiceRequest(request);
        }
        return request;
    }

    /**
     * Updates an existing AylaScheduleAction in the cloud
     *
     * @param scheduleAction  AylaScheduleAction that needs to be updated
     * @param successListener Listener to receive on successful update of ScheduleAction
     * @param errorListener   Listener to receive an AylaError should one occur
     * @return return the AylaAPIRequest object used to update ScheduleAction
     */
    public AylaAPIRequest updateAction(final AylaScheduleAction scheduleAction,
                                final Response.Listener<AylaScheduleAction>
                                        successListener,
                                final ErrorListener errorListener){
        return updateAction(scheduleAction, successListener, errorListener, null);
    }

    private AylaDeviceManager getDeviceManager() {
        AylaDevice device = _device.get();
        if (device != null) {
            return device.getDeviceManager();
        }
        return null;
    }

    void setDevice(AylaDevice device) {
        _device = new WeakReference<>(device);
    }

    AylaDevice getDevice() {
        if (_device == null) {
            return null;
        }
        return _device.get();
    }

    public static class Wrapper {
        @Expose
        public AylaSchedule schedule;

        public static AylaSchedule[] unwrap(Wrapper[] wrappedSchedules) {
            int size = 0;
            if (wrappedSchedules != null) {
                size = wrappedSchedules.length;
            }

            AylaSchedule[] schedules = new AylaSchedule[size];
            for (int i = 0; i < size; i++) {
                schedules[i] = wrappedSchedules[i].schedule;
            }
            return schedules;
        }
    }
    private Date getDate(String strDate) {
        try {
            return _dateFormat.parse(strDate);
        } catch(ParseException ex) {
            AylaLog.e(LOG_TAG, "ParseException for getDate "+ ex.getMessage());
        }
        return null;
    }
}
