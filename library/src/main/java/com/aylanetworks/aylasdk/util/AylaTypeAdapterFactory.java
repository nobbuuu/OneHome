package com.aylanetworks.aylasdk.util;

import android.text.TextUtils;

import com.aylanetworks.aylasdk.AylaDataStream;
import com.aylanetworks.aylasdk.AylaDevice;
import com.aylanetworks.aylasdk.AylaDeviceGateway;
import com.aylanetworks.aylasdk.AylaDeviceNode;
import com.aylanetworks.aylasdk.AylaLog;
import com.aylanetworks.aylasdk.AylaNetworks;
import com.aylanetworks.aylasdk.AylaProperty;
import com.aylanetworks.aylasdk.localdevice.AylaLocalDevice;
import com.aylanetworks.aylasdk.plugin.DeviceClassPlugin;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.internal.Streams;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.ParseException;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/*
 * AylaSDK
 *
 * Copyright 2017 Ayla Networks, all rights reserved
 */

/**
 * Adapts for the following classes.
 *  - Any device classes inherited from AylaDevice. Current version supports AylaDevice,
 * AylaDeviceGateway AylaDeviceNode, AylaDeviceZigbeeGateway(when having Zigbee support) and
 * AylaDeviceZigbeeNode (when having Zigbee support).
 *  - Any object of type AylaProperty according to its base_type.
 *  - Any object of AylaDataStream according to event_type of the datastream event and base_type of
 *  the property for datapoint and datapointack events.
 *
 */
public class AylaTypeAdapterFactory implements TypeAdapterFactory
{
    private static final String LOG_TAG = "TypeAdapterFactory";

    private static final String DEVICE_TYPE = "device_type";
    private static final String GATEWAY_TYPE = "gateway_type";
    private static final String NODE_TYPE = "node_type";

    private static final String NODE_TYPE_LOCAL = "Local";

    private static final String DEVICE_TYPE_WIFI = "Wifi";
    private static final String DEVICE_TYPE_GATEWAY = "Gateway";
    private static final String DEVICE_TYPE_NODE = "Node";

    private static final String PROPERTY_BASE_TYPE = "base_type";
    private static final String DATASTREAM_EVENT_TYPE = "event_type";
    private static final String METADATA = "metadata";
    private static final String CONNECTIVITY = "connectivity";
    private static final String DATAPOINT = "datapoint";
    private static final String DATAPOINT_ACK = "datapointack";

    private final Map<Class<?>, TypeAdapter<?>> classToDelegate = new LinkedHashMap<>();

    @Override
    @SuppressWarnings("unchecked") // registration requires that subtype extends T
    public <R> TypeAdapter<R> create(final Gson gson, final TypeToken<R> typeToken) {

        if (typeToken.getRawType() == AylaDevice.class) {
            Class localDeviceClass = null;
            try {
                localDeviceClass = Class.forName("com.aylanetworks.aylasdk.localdevice.AylaLocalDevice");
            } catch (ClassNotFoundException e) {
                // Do nothing, not a problem, we just wanted to know.
            }

            List<Class<?>> classList = new ArrayList<>();
            classList.add(AylaDevice.class);
            classList.add(AylaDeviceGateway.class);
            classList.add(AylaDeviceNode.class);
            if (localDeviceClass != null) {
                classList.add(localDeviceClass);

            }
            classList.add(Date.class);

            for (Class<?> classType : classList) {
                this.classToDelegate.put(classType, gson.getDelegateAdapter(this, TypeToken.get(classType)));
            }

            return new TypeAdapter<R>() {
                @Override
                public void write(JsonWriter jsonWriter, R device) throws IOException {
                    Class<?> srcType = device.getClass();
                            TypeAdapter <R> delegate = (TypeAdapter <R>) classToDelegate.get(srcType);

                    if (delegate == null) {
                        // Get it from gson
                        delegate = (TypeAdapter <R>) gson.getDelegateAdapter(AylaTypeAdapterFactory.this,
                                TypeToken.get(srcType));
                        AylaLog.d(LOG_TAG, "Asked gson for delegate for " + srcType + ", got " +
                                delegate);
                    }

                    if(delegate == null) {
                        throw new JsonParseException("cannot serialize " + srcType.getName()
                                + "; did you forget to register a subtype?");
                    }

                    JsonObject jsonObject;
                    try {
                        jsonObject = delegate.toJsonTree(device).getAsJsonObject();
                    }
                    catch (Exception e) {
                        AylaLog.e(LOG_TAG, "toJsonTree failed: " + e.getMessage());
                        throw new JsonParseException("cannot serialize" + srcType.getName() + "to Json Tree Exception:"
                                + e.toString());
                    }

                    if (jsonObject.has(DEVICE_TYPE)
                            || jsonObject.has(GATEWAY_TYPE)
                            || jsonObject.has(NODE_TYPE)) {
                        throw new JsonParseException("cannot serialize " + srcType.getName()
                                + " because it already defines a field named "
                                + jsonObject.has(DEVICE_TYPE)
                                + jsonObject.has(GATEWAY_TYPE)
                                + jsonObject.has(NODE_TYPE));
                    }

                    JsonObject clone = new JsonObject();
                    for (Map.Entry<String, JsonElement> e : jsonObject.entrySet()) {
                        clone.add(e.getKey(), e.getValue());
                    }

                    if (device instanceof AylaDeviceNode) {
                        clone.add(DEVICE_TYPE, new JsonPrimitive(DEVICE_TYPE_NODE));
                    } else if(device instanceof AylaDeviceGateway){
                        clone.add(DEVICE_TYPE, new JsonPrimitive(DEVICE_TYPE_GATEWAY));
                    } else if (device instanceof AylaLocalDevice) {
                        clone.add(DEVICE_TYPE, new JsonPrimitive(DEVICE_TYPE_NODE));
                        clone.add(NODE_TYPE, new JsonPrimitive(NODE_TYPE_LOCAL));
                    } else {
                        clone.add(DEVICE_TYPE, new JsonPrimitive(DEVICE_TYPE_WIFI));
                    }

                    Streams.write(clone, jsonWriter);
                }

                @Override
                public R read(JsonReader jsonReader) throws IOException {
                    JsonElement jsonElement = Streams.parse(jsonReader);
                    JsonElement labelJsonElement = jsonElement.getAsJsonObject().remove(DEVICE_TYPE);

                    if (labelJsonElement == null) {
                        throw new JsonParseException("cannot deserialize " + " because it does not define a field named "
                                + DEVICE_TYPE);
                    }

                    JsonObject jsonObject = jsonElement.getAsJsonObject();
                    JsonElement elem;

                    String nodeType = null;
                    elem = jsonObject.get(NODE_TYPE);
                    if (elem != null && !(elem instanceof JsonNull)) {
                        nodeType = elem.getAsString();
                    }

                    Class classType = null;

                    // Find the device class plugin, if present

                    DeviceClassPlugin dcp = (DeviceClassPlugin) AylaNetworks.sharedInstance()
                            .getPlugin(AylaNetworks.PLUGIN_ID_DEVICE_CLASS);
                    if (dcp != null) {
                        try {
                            JSONObject jo = new JSONObject(jsonObject.toString());
                            classType = dcp.getDeviceClass(jo);
                        } catch (JSONException e) {
                            AylaLog.e(LOG_TAG, "Unable to convert device JSON: " + jsonObject);
                            classType = null;
                        }
                    }

                    if (classType == null) {
                        if (labelJsonElement.getAsString().equals(DEVICE_TYPE_GATEWAY)) {
                            classType = AylaDeviceGateway.class;
                        } else if (labelJsonElement.getAsString().equals(DEVICE_TYPE_NODE)) {
                            // See if this is a local device or not
                            if (TextUtils.equals(nodeType, NODE_TYPE_LOCAL)) {
                                classType = AylaLocalDevice.class;
                            } else {
                                // Normal node
                                classType = AylaDeviceNode.class;
                            }
                        } else {
                            classType = AylaDevice.class;
                        }
                    }

                    @SuppressWarnings("unchecked") // registration requires that subtype extends T
                            TypeAdapter<R> delegate = (TypeAdapter<R>)classToDelegate.get(classType);

                    if (delegate == null) {
                        // Get it from gson
                        delegate = (TypeAdapter <R>) gson.getDelegateAdapter(AylaTypeAdapterFactory.this,
                                TypeToken.get(classType));
                        AylaLog.d(LOG_TAG, "Asked gson for delegate for " + classType + ", got " +
                                delegate);
                    }

                    if(delegate == null) {
                        throw new JsonParseException("cannot serialize " + classType.getName()
                                + "; did you forget to register a subtype?");
                    } else {
                        classToDelegate.put(classType, delegate);
                    }

                    return delegate.fromJsonTree(jsonElement);
                }
            };
        } else if(typeToken.getRawType() == AylaProperty.class){
            return new TypeAdapter<R>() {
                @Override
                public void write(JsonWriter out, R value) throws IOException {
                    TypeToken typeToken = new TypeToken<AylaProperty<?>>(){};
                    TypeAdapter<R> adapter = gson.getDelegateAdapter(
                            AylaTypeAdapterFactory.this, typeToken);
                    JsonElement jsonElement = adapter.toJsonTree(value);
                    Streams.write(jsonElement, out);
                }

                @Override
                public R read(JsonReader in) throws IOException {
                    JsonElement jsonElement = Streams.parse(in);
                    JsonElement baseTypeJsonElement = jsonElement.getAsJsonObject().get
                            (PROPERTY_BASE_TYPE);

                    if(baseTypeJsonElement == null){
                        AylaLog.e(LOG_TAG, "Missing base_type in property.");
                        throw new JsonParseException("Missing base_type in property. ");
                    }
                    String baseType = baseTypeJsonElement.getAsString();
                    TypeToken typeToken;
                    switch (baseType){
                        case "boolean":
                        case "integer":
                            typeToken = new TypeToken<AylaProperty<Integer>>(){};
                            break;
                        case "decimal":
                            typeToken = new TypeToken<AylaProperty<Float>>(){};
                            break;
                        default:
                            typeToken = new TypeToken<AylaProperty<String>>(){};

                    }
                    TypeAdapter<R> adapter = gson.getDelegateAdapter(
                            AylaTypeAdapterFactory.this, typeToken);
                    return adapter.fromJsonTree(jsonElement);
                }
            };

        } else if(typeToken.getRawType() == AylaDataStream.class){
            return new TypeAdapter<R>() {
                @Override
                public void write(JsonWriter out, R value) throws IOException {
                    TypeToken typeToken = new TypeToken<AylaDataStream<R>>(){};
                    TypeAdapter<R> adapter = gson.getDelegateAdapter(
                            AylaTypeAdapterFactory.this, typeToken);
                    JsonElement jsonElement = adapter.toJsonTree(value);
                    Streams.write(jsonElement, out);
                }

                @Override
                public R read(JsonReader in) throws IOException {
                    JsonElement jsonElement = Streams.parse(in);
                    JsonElement metadataJsonElement = jsonElement.getAsJsonObject().get
                            (METADATA);

                    if(metadataJsonElement == null){
                        AylaLog.e(LOG_TAG, "Missing base_type in property.");
                        throw new JsonParseException("Missing base_type in property. ");
                    }

                    String eventType = metadataJsonElement.getAsJsonObject().get
                            (DATASTREAM_EVENT_TYPE).getAsString();
                    TypeToken type;
                    switch (eventType){
                        case DATAPOINT:
                        case DATAPOINT_ACK:
                            String baseType = metadataJsonElement.getAsJsonObject().get
                                    (PROPERTY_BASE_TYPE).getAsString();
                            switch (baseType){
                                case "boolean":
                                case "integer":
                                    type = new TypeToken<AylaDataStream<Integer>>(){};
                                    break;
                                case "decimal":
                                    type = new TypeToken<AylaDataStream<Float>>(){};
                                    break;
                                default:
                                    type = new TypeToken<AylaDataStream<String>>(){};

                            }
                            break;
                        default:
                            type = new TypeToken<AylaDataStream<String>>(){};
                            break;
                    }

                    TypeAdapter<R> adapter = gson.getDelegateAdapter(
                            AylaTypeAdapterFactory.this, type);
                    return adapter.fromJsonTree(jsonElement);
                }
            };
        } else if (typeToken.getRawType() == Date.class) {
            /**
             * The Date Type Adapter is needed to fix deserialization bug of date in the gson
             * parser after switching my device time preferences from 24 hour to 12 hour,
             * Refer to known issue https://github.com/google/gson/issues/935
             */
            return new TypeAdapter<R>() {
                @Override
                public void write(JsonWriter out, R date) throws IOException {
                    if (date == null) {
                        out.nullValue();
                    } else {
                        String value = UtcDateTypeAdapterUtil.format((Date) date, true,
                                UtcDateTypeAdapterUtil.UTC_TIME_ZONE);
                        out.value(value);
                    }
                }

                @Override
                public R read(JsonReader in) throws IOException {

                    try {
                        switch (in.peek()) {
                            case NULL:
                                in.nextNull();
                                return null;
                            default:
                                String date = in.nextString();
                                return (R) UtcDateTypeAdapterUtil.parse(date, new ParsePosition
                                        (0));
                        }
                    } catch (ParseException e) {
                        throw new JsonParseException(e);
                    }
                }
            };
        } else{
            return null;
        }

    }
}// end of AylaTypeAdapterFactory class
