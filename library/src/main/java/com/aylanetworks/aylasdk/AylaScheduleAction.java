package com.aylanetworks.aylasdk;
/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import com.google.gson.annotations.Expose;

public class AylaScheduleAction{
    //Properties for Schedule Actions
    @Expose
    private String name; // associated property name, required
    @Expose
    private String type; // "SchedulePropertyAction", required
    @Expose
    private boolean inRange; // true == fire action if time is in the range specified,

    //  false == fire on start/end date/time only, optional
    @Expose
    private boolean atStart; // true == fire action if time is at the start of the range

    // specified by the schedule, optional
    @Expose
    private boolean atEnd; // true == fire action if time is at the end of the range specified
    // by the schedule, optional
    @Expose
    private boolean active; // true if this action is used in determining a firing action
    // required
    @Expose
    private String baseType; // string, integer, boolean, decimal, required
    @Expose
    private String value;    // value to set when fired, required
    @Expose
    private Number key;      // required, except for create

    public String getName() { return name; }

    public String getType() { return type; }

    public boolean isInRange() { return inRange; }

    public boolean isAtStart() { return atStart; }

    public boolean isAtEnd() { return atEnd; }

    public boolean isActive() { return active; }

    public String getBaseType() { return baseType; }

    public String getValue() { return value; }

    public Number getKey() { return key; }

    public void setName(String name) { this.name = name; }

    public void setType(String type) { this.type = type; }

    public void setActive(boolean active) { this.active = active; }

    public void setBaseType(String baseType) { this.baseType = baseType; }

    public void setValue(String value) { this.value = value; }
    // In Schedule Action only one of the following properties atStart, atEnd, inRange can be true.
    // To make sure that User can set only one of them to true we have the enum
    // AylaScheduleActionFirePoint with 3 Values.
    public enum AylaScheduleActionFirePoint {
        AtStart,
        AtEnd,
        InRange;
    }

    public void setScheduleActionFirePoint(AylaScheduleActionFirePoint scheduleActionFirePoint) {
        switch (scheduleActionFirePoint) {
            case AtStart:
                atStart = true;
                atEnd = false;
                inRange = false;
                break;
            case AtEnd:
                atStart = false;
                atEnd = true;
                inRange = false;
                break;
            case InRange:
                atStart = false;
                atEnd = false;
                inRange = true;
                break;
            default:
                atStart = false;
                atEnd = false;
                inRange = false;
                break;
        }
    }

    public static class Wrapper {
        @Expose
        public AylaScheduleAction scheduleAction;

        public static AylaScheduleAction[] unwrap(Wrapper[] wrappedScheduleActions) {
            int size = 0;
            if (wrappedScheduleActions != null) {
                size = wrappedScheduleActions.length;
            }

            AylaScheduleAction[] scheduleActions = new AylaScheduleAction[size];
            for (int i = 0; i < size; i++) {
                scheduleActions[i] = wrappedScheduleActions[i].scheduleAction;
            }
            return scheduleActions;
        }
    }
}