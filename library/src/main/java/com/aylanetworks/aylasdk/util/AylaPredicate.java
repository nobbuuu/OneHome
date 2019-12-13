/*
 * AylaSDK
 *
 * Copyright 2019 Ayla Networks, all rights reserved
 */

package com.aylanetworks.aylasdk.util;

/**
 *
 * A Predicate can determine a true or false value for any input of its
 * parameterized type. For example, a {@code RegexPredicate} might implement
 * {@code Predicate<String>}, and return true for any String that matches its
 * given regular expression. <p/>
 *
 * This is an replacement of the com.android.internal.util.Predicate interface which was
 * deprecated in API level 26, and based on the Java version java.util.function.Predicate. <p/>
 *
 * @see <a href="https://developer.android.com/reference/com/android/internal/util/Predicate">Predicate</a>
 * @see <a href="https://developer.android.com/reference/kotlin/java/util/function/Predicate?hl=en">Predicate</a>
 */
public interface AylaPredicate<T> {

    /**
     * Evaluates this predicate on the given argument.
     *
     * @param t the input argument
     * @return {@code true} if the input argument matches the predicate,
     * otherwise {@code false}
     */
    boolean test(T t);

}
