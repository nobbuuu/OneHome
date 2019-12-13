package com.aylanetworks.aylasdk.util;

/**
 * Android_Aura
 * <p/>
 * Copyright 2016 Ayla Networks, all rights reserved
 */

/**
 * Class used for data type conversions.
 */
public class TypeUtils {

    /**
     * Converts type of datapoint value according to the property's base_type
     * @param baseType Property base type
     * @param value Value of the datapoint
     * @return Java Object type representing a datapoint value parsed to the right type. null
     * value will be returned if the value passed to this method cannot be parsed to the basetype.
     */
    public static Object getTypeConvertedValue(String baseType, String value){

        switch(baseType){
            case "boolean":
            case "integer":
                try{
                    return Integer.valueOf(value);
                } catch(NumberFormatException exception){
                    return null;
                }

            case "decimal":
                try{
                    return Float.valueOf(value);
                } catch(NumberFormatException exception){
                    return null;
                }
            default:
                return value;
        }

    }
}
