package com.aylanetworks.aylasdk;

import android.text.TextUtils;

import com.android.volley.Response;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.aylanetworks.aylasdk.error.PreconditionError;
import com.google.gson.annotations.Expose;

import java.util.ArrayList;
import java.util.List;

/*
 * AylaSDK
 *
 * Copyright 2017 Ayla Networks, all rights reserved
 */
/**
 * Class used to represent Rule in Ayla cloud. A rule consists of expression and Action(s). When
 * an expression is reached the actions are fired. In general, rule expression is a logical
 * statement conveyed in terms of logical relationships between the rule subjects.
 * Rule subjects can enter a rule expression as stand-alone terms or as function arguments.
 * For example, the above statement expresses relationships between the following six rule
 * (event) subjects:
    DATAPOINT(dsn_1,prop_name_1)
    DATAPOINT(dsn_2,prop_name_2)
    DATAPOINT(dsn_3,prop_name_3)
    DATAPOINT(dsn_4,prop_name_4)
    LOCATION(uuid_1)
    LOCATION(dsn_1)

 Currently supported rule subjects:

 Rule Subject	                Derived from event	                Type
 DATAPOINT(dsn,prop_name)	    datapoint event	                    :: datapoint_value
 DATAPOINT_ACK(dsn,prop_name)	datapoint event	                    :: datapoint_value
 CONNECTIVITY(dsn)	            connectivity event	                :: status → dsn online or offline
 REGISTRATION(dsn) 	            device registration event	        :: boolean → dsn registered or
 LOCATION(dsn)	                device location change event	    :: (lat,long) →pair of decimals
 LOCATION(uuid)	                user’s phone location change event	:: (lat,long) →pair of decimals
 */
public class AylaRule {
    @Expose
    private String rule_uuid; //This is created by cloud when a new Action is created
    @Expose
    private String name; //User given name to this rule
    @Expose
    private boolean isEnabled;//Boolean value to indicate if Rule is enabled or not
    @Expose
    private String description;//Optional.User given description of this rule
    @Expose
    private String expression;//A logical statement conveyed in terms of logical relationships
                             //between the rule subjects
    @Expose
    private String[] actionIds;// An array of Action UUIDs
    @Expose
    private String createdAt;//Time set by service when the Rule  was created
    @Expose
    private String updatedAt;//Time set by service when the Rule was updated

    public String getName() { return name; }

    public boolean getEnabled() { return isEnabled; }

    public String getDescription() { return description; }

    public String getExpression() { return expression; }

    public String[] getActionIds() { return actionIds; }

    public String getCreatedAt() { return createdAt; }

    public String getUpdatedAt() { return updatedAt; }

    public String getUUID() { return rule_uuid; }

    public void setName(String name) { this.name = name; }

    public void setEnabled(Boolean enabled) { isEnabled = enabled; }

    public void setDescription(String description) { this.description = description; }

    public void setExpression(String expression) { this.expression = expression; }

    public void setActionIds(String[] actionIds) { this.actionIds = actionIds; }

    /**
     * Wrapper object used by AylaRulesService
     */
    public static class RulesWrapper {
        @Expose
        public AylaRule[] rules;
    }

    /**
     * Wrapper object used by AylaRulesService
     */
    public static class RuleWrapper {
        @Expose
        public AylaRule rule;
    }

    /**
     * RuleType is  an enumerator that has device/user
     */
    public enum RuleType {
        Device("device"),
        User("user");

        RuleType(String value) {
            _stringValue = value;
        }

        public final String stringValue() {
            return _stringValue;
        }

        public static RuleType fromStringValue(String value) {
            for (RuleType val : values()) {
                if (val.stringValue().equals(value)) {
                    return val;
                }
            }
            return null;
        }

        private final String _stringValue;
    }

    /**
     * Helper class for creating a rule and associated actions. To use, create a Builder object and
     * call methods on it to set the name, description, property to evaluate, condition and actions.
     *
     * When the Builder has been configured, call {@link #create} to create the rule and any
     * required actions on the service.
     *
     * Any actions that are added to the Builder will be created if they do not already exist (e.g.
     * if they do not have an action ID already associated, they will be created).
     *
     */
    public static class Builder {
        private final static String LOG_TAG = "AylaRule.Builder";
        private AylaProperty _property;
        private String _name;
        private String _description;
        private boolean _enabled = true;
        private String _condition;
        private Object _value;
        private List<AylaAction> _actionList;
        private AylaRulesService _rulesService;

        public Builder(AylaRulesService rulesService) {
            _rulesService = rulesService;
            _actionList = new ArrayList<>();
        }

        public Builder setName(String name) {
            _name = name;
            return this;
        }

        public Builder setDescription(String description) {
            _description = description;
            return this;
        }

        public Builder setProperty(AylaProperty property) {
            _property = property;
            return this;
        }

        public Builder setEnabled(boolean enabled) {
            _enabled = enabled;
            return this;
        }

        public Builder addAction(AylaAction action) {
            _actionList.add(action);
            return this;
        }

        public Builder setCondition(String condition, Object value) {
            _condition = condition;
            _value = value;
            return this;
        }

        /**
         * Creates the rule as defined by the caller.
         *
         * @param successListener Listener to be called when the rule creation succeeds
         * @return An AylaAPIRequest, or null if an error occurred. If an error occurred, this
         * method will return null and the error listener will be called. The returned AylaAPIRequest
         * may be canceled.
         */
        public AylaAPIRequest create(Response.Listener<AylaRule> successListener,
                                     ErrorListener errorListener) {
            // Make sure we have everything we need to create the rule
            if (_property == null) {
                errorListener.onErrorResponse(new PreconditionError("Property is required"));
                return null;
            }
            if (_name == null) {
                errorListener.onErrorResponse(new PreconditionError("Name is required"));
                return null;
            }
            if (_condition == null) {
                errorListener.onErrorResponse(new PreconditionError("Condition is required"));
                return null;
            }

            // Create the actions if necessary
            List<AylaAction> actionsToCreate = new ArrayList<>();
            for (AylaAction action : _actionList) {
                if (action.getUUID() == null) {
                    actionsToCreate.add(action);
                }
            }

            AylaAPIRequest dummyRequest = AylaAPIRequest.dummyRequest(AylaRule.class,
                    successListener, errorListener);

            createActions(dummyRequest, actionsToCreate);
            return dummyRequest;
        }

        private void createActions(final AylaAPIRequest<AylaRule> request, final List<AylaAction> actions) {
            if (request.isCanceled()) {
                return;
            }
            if (actions.isEmpty()) {
                createRule(request);
                return;
            }

            final AylaAction nextAction = actions.remove(0);
            if (_rulesService == null) {
                request._errorListener.onErrorResponse(new PreconditionError("Rules service is not available"));
                return;
            }

            _rulesService.createAction(nextAction, response -> {
                nextAction.updateFrom(response);
                createActions(request, actions);
            }, error -> {
                AylaLog.e(LOG_TAG, "Error trying to create action " + nextAction + " " +
                        error.getMessage());
                request._errorListener.onErrorResponse(error);
            });
        }

        private void createRule(AylaAPIRequest<AylaRule> request) {
            AylaLog.d(LOG_TAG, "All actions created");
            // Now we create the rule

            if (request.isCanceled()) {
                return;
            }

            if (_rulesService == null) {
                request._errorListener.onErrorResponse(new PreconditionError("Rules service is not available"));
                return;
            }

            AylaRule newRule = new AylaRule();
            newRule.actionIds = new String[_actionList.size()];
            for (int i = 0; i < _actionList.size(); i++) {
                newRule.actionIds[i] = _actionList.get(i).getUUID();
            }
            newRule.name = _name;
            newRule.description = _description;
            newRule.setEnabled(_enabled);

            AylaDevice device = (AylaDevice)_property._owningDevice.get();

            String quotes = "";
            if (TextUtils.equals(_property.getBaseType(), "string")) {
                quotes = "\"";
            }
            newRule.expression = "DATAPOINT(" + device.getDsn() + "," + _property.getName() + ") " +
                    _condition + " " + quotes + _value + quotes;
            AylaLog.d(LOG_TAG, "Generated expression: " + newRule.expression);

            _rulesService.createRule(newRule, request._successListener, request._errorListener);
        }
    }
}
