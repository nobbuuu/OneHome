package com.aylanetworks.aylasdk;

import com.aylanetworks.aylasdk.util.ObjectUtils;
import com.google.gson.annotations.Expose;

/*
 * AylaSDK
 *
 * Copyright 2017 Ayla Networks, all rights reserved
 */
/**
 * Class used to represent Action in Ayla cloud. An AylaAction object needs to be created for using
 * Rules. A Rule has one or more Actions. Currently there are 2 types of Actions viz. DATAPOINT and
 * EMAIL. For Datapoint action required parameter key is 'datapoint'. For Email action required
 * parameters are email_to, email_subject  and email_body. For Creating a DATAPOINT action a
 * device must exist.
 */
public class AylaAction {
    @Expose
    private String action_uuid; //This is created by cloud when a new Action is created
    @Expose
    private String name; //User given name to this action
    @Expose
    private String type; //User given action type (DATAPOINT,EMAIL)
    @Expose
    private AylaActionParameters parameters; //Contains key/value pairs with information relevant
                                            // to be performed
    @Expose
    private String createdAt; //Time set by service when the action was created
    @Expose
    private String updatedAt; //Time set by service when the action was updated

    public String getName() { return name; }

    public ActionType getType() { return ActionType.fromStringValue(type); }

    public String getUUID() { return action_uuid; }

    public String getCreatedAt() { return createdAt; }

    public String getUpdatedAt() { return updatedAt; }

    public AylaActionParameters getParameters() { return parameters; }

    public void setName(String name) {
        this.name = name;
    }

    public void setType(ActionType type) {
       this.type = type.stringValue();
    }

    public void setParameters(AylaActionParameters parameters) {
        this.parameters = parameters;
    }

    public void updateFrom(AylaAction other) {
        action_uuid = other.action_uuid;
        name = other.name;
        type = other.type;
        parameters = other.parameters;
        createdAt = other.createdAt;
        updatedAt = other.updatedAt;
    }

    public static AylaAction datapointAction(String name,
                                             AylaProperty property,
                                             String operator,
                                             Object value) {
        String condition = formatDatapointExpression(property, operator, value);

        AylaAction action = new AylaAction();
        action.setType(ActionType.Datapoint);
        if (name == null) {
            action.setName(condition);
        } else {
            action.setName(name);
        }
        AylaActionParameters params = new AylaActionParameters();
        params.datapoint = condition;
        action.setParameters(params);

        return action;
    }

    public static String formatDatapointExpression(AylaProperty property, String operator, Object value) {
        String dsn = property.getOwner().getDsn();
        String valueText;
        switch(property.getBaseType()) {
            default:
            case "string":
                valueText = ObjectUtils.quote(value.toString());
                break;

            case "integer":
                //even though the basetype is integer the value being passed is double.
                if(value instanceof  Double){
                    valueText = String.valueOf(((Double) value).intValue());
                } else {
                    valueText = String.valueOf((int) value);
                }
                break;

            case "boolean":
                if(value instanceof  Double){
                    valueText = ((Double)value) == 0.0 ? "false" : "true";
                } else {
                    valueText = ((Integer)value) == 0 ? "false" : "true";
                }
                break;

            case "decimal":
                valueText = String.valueOf((double)value);
                break;
        }

        return "DATAPOINT(" + dsn + "," + property.getName() + ")" + operator + valueText;
    }

    /**
     * Wrapper object used by AylaRulesService
     */
    public static class ActionsWrapper {
        @Expose
        AylaAction[] actions;
    }

    /**
     * Wrapper object used by AylaRulesService
     */
    public static class ActionWrapper {
        @Expose
        public AylaAction action;
    }

    /**
     * ActionType is  an enumerator that has Email/Datapoint
     */
    public enum ActionType {
        EMail("EMAIL"),
        Datapoint("DATAPOINT");

        ActionType(String value) {
            _stringValue = value;
        }

        public final String stringValue() {
            return _stringValue;
        }

        public static ActionType fromStringValue(String value) {
            for (ActionType val : values()) {
                if (val.stringValue().equals(value)) {
                    return val;
                }
            }
            return null;
        }

        private final String _stringValue;
    }
    /**
     Contains key / value pairs with information relevant to the action to be performed.
     For Datapoint action required parameter key is ‘datapoint’.
     For e.g.
     "parameters": {
     "datapoint" :
     "DATAPOINT(dsn1,prop1) = 70"
     }

     For Email action required parameter keys are
     email_to, email_subject &
     Email_body.

     For e.g.
     "parameters": {
     "email_body": "Hi there!!",
     "email_to": [   "abc@xyz.com"
     ],
     "email_subject": "Device Updated Notification"
     }
     */

    public static class AylaActionParameters {
        @Expose
        public String email_body;
        @Expose
        public String[] email_to;
        @Expose
        public String email_subject;
        @Expose
        public String datapoint;
    }
}
