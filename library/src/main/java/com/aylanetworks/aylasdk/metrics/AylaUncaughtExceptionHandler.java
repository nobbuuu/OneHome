package com.aylanetworks.aylasdk.metrics;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.aylanetworks.aylasdk.AylaLog;
import com.aylanetworks.aylasdk.AylaNetworks;
import com.aylanetworks.aylasdk.setup.AylaSetup;

import java.lang.Thread.UncaughtExceptionHandler;
import static com.aylanetworks.aylasdk.metrics.AylaMetricsManager.*;

/**
 * AylaSDK
 * <p>
 * Copyright 2017 Ayla Networks, all rights reserved
 */

/**
 * Implements UncaughtExceptionHandler to capture uncaught exceptions, and store crash logs and
 * crash counts in shared preferences.
 */
public class AylaUncaughtExceptionHandler implements UncaughtExceptionHandler{

    private static final String LOG_TAG = "AylaExceptionHandler";
    private UncaughtExceptionHandler _currentExceptionHandler;
    private Context _context;

    /**
     * Constructor
     * @param context context from the application.
     * @param currentUncaughtExceptionHandler Uncaught exception handler for the app's main thread.
     *                                        If the app overrides the default
     *                                        UncaughtExceptionHandler of the thread, then this
     *                                        handler is passed as parameter to this method.
     */
    public AylaUncaughtExceptionHandler(Context context,
                                        UncaughtExceptionHandler currentUncaughtExceptionHandler){
        _context = context;
        _currentExceptionHandler = currentUncaughtExceptionHandler;
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        AylaLog.e(LOG_TAG, "uncaughtException " + throwable + ", from thread: " + thread.getName());
        AylaSetup.deleteConfiguredNetwork(_context);

        StackTraceElement[] stacktrace = throwable.getStackTrace();
        StringBuilder strBuilder = new StringBuilder();
        strBuilder.append("Crash!!");
        strBuilder.append(throwable.toString());
        for(StackTraceElement trace: stacktrace){
            strBuilder.append(" at "+trace.toString());
        }
        String crashLogs = strBuilder.toString();
        Log.e(LOG_TAG, crashLogs);
        handleCrashLogs(crashLogs);
        _currentExceptionHandler.uncaughtException(thread, throwable);
    }

    /**
     * Save crash logs to log file, and update current crash count saved in
     * preferences.
     * @param crashLogs Crash logs to be saved.
     */
    // We need to make sure this is run immediately
    @SuppressLint("ApplySharedPref")
    private void handleCrashLogs(String crashLogs){
        if(_context != null){
            AylaLog.d(LOG_TAG, "saving crash logs for next session");
            SharedPreferences preferences = _context.getSharedPreferences(
                    AYLA_METRICS_PREFERENCES_KEY, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            int crashCount = preferences.getInt(AYLA_TOTAL_CRASH_COUNT, 1);
            editor.putInt(AYLA_TOTAL_CRASH_COUNT, ++crashCount);
            editor.commit();

            AylaLog.saveCrashLogs(crashLogs);

            AylaAppLifecycleMetric appLifecycleMetric = new AylaAppLifecycleMetric(AylaMetric
                    .LogLevel.INFO, AylaAppLifecycleMetric.MetricType.APP_CRASHED, "");
            appLifecycleMetric.setCrashCount(crashCount);
            AylaMetricsManager metricsManager = AylaNetworks.sharedInstance().getMetricsManager();
            metricsManager.addMessageToUploadsQueue(appLifecycleMetric);
            metricsManager.onPause();

        }
    }

}