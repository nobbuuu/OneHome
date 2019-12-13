package com.aylanetworks.aylasdk.util;
/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Miscellaneous static helper methods to help construct URLs with query strings.
 */
public class URLHelper {

    /**
     * Returns a string containing a URL argument list formatted for an array. This string may be
     * appended to a base URL to provide array arguments.
     * <p>
     * Example:
     * <pre>{@code
     *     // itemList is a string array containing "One", "Two" and "Three"
     *     parameterizeArray("my_array", itemList)
     *     // returns: ?my_array[]=One&my_array[]=Two&my_array[]=Three
     * }</pre>
     * @param arrayName Name of the array
     * @param items     Array of strings to be contained in the array
     * @return A string with the array elements formatted as a query string
     */
    public static String parameterizeArray(String arrayName, List<String> items) {
        StringBuilder parameters = new StringBuilder(items.size() * 16 + arrayName.length());
        Boolean first = true;

        String firstFormat = String.format("?%s[]=", arrayName);
        String nextFormat = String.format("&%s[]=", arrayName);

        for (String element : items) {
            parameters.append(first ? firstFormat : nextFormat);
            try {
                parameters.append(URLEncoder.encode(element, "utf-8"));
            } catch (UnsupportedEncodingException e) {
                return null;
            }
            first = false;
        }

        return parameters.toString();
    }

    /**
     * Returns a string containing a URL argument list formatted for an array. This string may be
     * appended to a base URL to provide array arguments.
     * <p>
     * Example:
     * <pre>{@code
     *     // itemList is a string array containing "One", "Two" and "Three"
     *     parameterizeArray("my_array", itemList)
     *     // returns: ?my_array[]=One&my_array[]=Two&my_array[]=Three
     * }</pre>
     * @param arrayName Name of the array
     * @param items     Array of strings to be contained in the array
     * @return A string with the array elements formatted as a query string
     */
    public static String parameterizeArray(String arrayName, String[] items) {
        return parameterizeArray(arrayName, Arrays.asList(items));
    }

    /**
     * Appends a Map of name : value pairs to the end of a URL as a query string.
     * <p>
     * Example:
     * <pre>{@code
     *     // paramMap contains the following key / value pairs:
     *     // key1 : value1
     *     // key2 : value2
     *     // key3 : value3
     *     String result = appendParameters("http://my.site.com/getStuff", paramMap);
     *
     *     // Result is:
     *     // http://my.site.com/getStuff?key1=value1&key2=value2&key3=value3
     * }</pre>
     * @param urlBase URL to append the parameters to
     * @param params Map of name : value Strings to append as parameters
     *
     * @return the URL with the name / value pairs appended as a query string
     */
    public static String appendParameters(String urlBase, Map<String, String> params) {
        StringBuilder sb = new StringBuilder(urlBase.length() + params.size() * 30);
        sb.append(urlBase);
        String separator = "?";
        for (String key : params.keySet()) {
            String value = params.get(key);
            sb.append(separator);
            separator = "&";

            sb.append(key).append("=");
            try {
                sb.append(URLEncoder.encode(value, "utf-8"));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                return null;
            }
        }

        return sb.toString();
    }
}
