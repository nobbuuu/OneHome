package com.aylanetworks.aylasdk;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.core.content.FileProvider;

import com.android.volley.Request;
import com.android.volley.Response;
import com.aylanetworks.aylasdk.error.AylaError;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.aylanetworks.aylasdk.error.PreconditionError;
import com.aylanetworks.aylasdk.util.ServiceUrls;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Scanner;

/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */


public class AylaLog {

    private static final String LOG_TAG = "AYLA_LOG";
    private static int __fileCount;
    private static File __currentFile;
    private static File __logDirectory;
    private static String __logFileName;
    private static final int FILE_MEMORY_LIMIT = 200000;
    private static final int TIMESTAMP_LENGTH = 19;
    private static final int NUM_OF_LOG_FILES = 3; //files will be replaced after max number is reached
    private static final String LOG_COMPONENT_DELIMITER = ",  ";
    private static final String CRASH_LOG_IDENTIFIER = "CRASH_";
    private static final String LOGS_DELIMITER = "\n";
    private static final String DELIMITER_REPLACE_CHAR = "\\n";
    private static final String LOG_DIRECTORY_RELATIVE_PATH = "logs";
    private static final String LOGS_FILE_EXTENSION = ".txt";

    private final static String LOG_URL_PATH="api/v1/app/logs.json";
    private static String __sessionName;

    public enum LogLevel{
        Verbose,
        Debug,
        Info,
        Warning,
        Error,
        None
    }

    //Log levels for console and file logs.
    private static LogLevel __consoleLogLevel = LogLevel.Warning;
    private static LogLevel __fileLogLevel = LogLevel.None;

    private static SimpleDateFormat __dateFormat =
            new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", Locale.US);
    private static String __packageName = null;

    /**
     * Set console log level for Ayla logs. Logs will be displayed on the console only if the log
     * level in the message is higher priority than the saved console log level.
     * @param logLevel Log level
     */
    public static void setConsoleLogLevel(LogLevel logLevel){
        __consoleLogLevel = logLevel;
    }

    /**
     * Set file log level for Ayla logs. Logs will be saved to log files only if the log
     * level in the message is higher priority than the saved file log level.
     * @param logLevel Log level
     * @throws {@link PreconditionError} if AylaLog is not initialized correctly.
     */
    public static void setFileLogLevel(LogLevel logLevel) throws PreconditionError{
        if(__logDirectory == null || __logFileName == null){
            throw new PreconditionError("AylaLog is not initialized.");
        }
        __fileLogLevel = logLevel;
    }

    /**
     * Initialize AylaLog. Sets default log level for console and file loggers, defaults to
     * write logs to app specific private logs directory in the internal storage.
     * use {@link #initAylaLog(String, String, String, LogLevel, LogLevel)} to change the
     * default directory the logs will be written to.
     * @param sessionName the session name when the logger is initialized.
     * @param logFileName the log file name relative to the default log directory.
     * @param consoleLevel the target console log level, log entries that have higher level will
     *                     be written to console.
     * @param fileLevel the target file log level, log entries that have higher level will
     *                  be written to the log file.
     *
     * @throws {@link PreconditionError} if file logger was not initialized.
     */
    public static void initAylaLog(String sessionName,
                                   String logFileName,
                                   LogLevel consoleLevel,
                                   LogLevel fileLevel) throws PreconditionError{
        initAylaLog(sessionName, null, logFileName, consoleLevel, fileLevel);
    }

    /**
     * Initialize AylaLog. Sets default log level for console and file loggers.
     * @param sessionName the session name when the logger is initialized.
     * @param logDirectory the default directory the logs will be written to.
     * @param logFileName the log file name relative to the log directory.
     * @param consoleLevel the target console log level, log entries that have higher level will
     *                     be written to console.
     * @param fileLevel the target file log level, log entries that have higher level will
     *                  be written to the log file.
     * @throws {@link PreconditionError} if file logger was not initialized.
     */
    public static void initAylaLog(String sessionName,
                                   String logDirectory,
                                   String logFileName,
                                   LogLevel consoleLevel,
                                   LogLevel fileLevel) throws PreconditionError{
        __sessionName = sessionName;
        __logFileName = logFileName;
        __packageName = AylaNetworks.sharedInstance().getContext().getPackageName();

        if (logDirectory == null) {
            File filesDir = AylaNetworks.sharedInstance().getContext().getFilesDir();
            __logDirectory = new File(filesDir, LOG_DIRECTORY_RELATIVE_PATH);
        } else {
            __logDirectory = new File(logDirectory);
        }

        setConsoleLogLevel(consoleLevel);
        setFileLogLevel(fileLevel);
        try {
            if (!__logDirectory.exists()) {
                __logDirectory.mkdir();
            }

            if (fileLevel != LogLevel.None) {
                if ((__fileCount = getFileCount()) == 0) {
                    createNewLogFile();
                } else {
                    String currentPath = getCurrentFilePath();
                    if (currentPath != null) {
                        __currentFile = new File(currentPath);
                    }
                }
            }
        } catch (SecurityException | IOException e) {
            Log.e(LOG_TAG,"Cannot create new log file " + e.getMessage());
        }
    }

    /**
     * Method to send logs to console and file if the log level is within the set level.
     * @param tag Tag for the log message.
     * @param msg Message to be logged.
     */
    public static void d(String tag, String msg) {
        if(__consoleLogLevel.ordinal() <= LogLevel.Debug.ordinal()){
            Log.d(tag, msg);
        }
        if(__fileLogLevel.ordinal() <= LogLevel.Debug.ordinal()){
            try{
                logToFile("D", tag, msg);
            } catch (FileNotFoundException e){
                Log.d(LOG_TAG, "Log file not found");
            }
        }
    }

    public static void e(String tag, String msg) {
        if(__consoleLogLevel.ordinal() <= LogLevel.Error.ordinal()){
            Log.e(tag, msg);
        }
        if(__fileLogLevel.ordinal() <= LogLevel.Error.ordinal()){
            try{
                logToFile("E", tag, msg);
            } catch(FileNotFoundException e){
                Log.d(LOG_TAG, "Log file not found");
            }
        }
    }

    public static void w(String tag, String msg) {
        if(__consoleLogLevel.ordinal() <= LogLevel.Warning.ordinal()){
            Log.w(tag, msg);
        }
        if(__fileLogLevel.ordinal() <= LogLevel.Warning.ordinal()){
            try{
                logToFile("W", tag, msg);
            } catch (FileNotFoundException e){
                Log.d(LOG_TAG, "Log file not found");
            }
        }
    }

    public static void i(String tag, String msg) {
        if(__consoleLogLevel.ordinal() <= LogLevel.Info.ordinal()){
            Log.i(tag, msg);
        }
        if(__fileLogLevel.ordinal() <= LogLevel.Info.ordinal()){
            try{
                logToFile("I", tag, msg);
            } catch (FileNotFoundException e){
                Log.d(LOG_TAG, "Log file not found");
            }
        }
    }

    public static void v(String tag, String msg) {
        if(__consoleLogLevel.ordinal() <= LogLevel.Verbose.ordinal()){
            Log.v(tag, msg);
        }
        if(__fileLogLevel.ordinal() <= LogLevel.Verbose.ordinal()){
            try{
                logToFile("V", tag, msg);
            } catch (FileNotFoundException e){
                Log.d(LOG_TAG, "Log file not found");
            }
        }
    }

    /**
     * Writes a debug log with this tag and message to the console, regardless of the log levels
     * set in the app.
     * @param tag Log tag
     * @param msg Log message
     */
    public static void consoleLogDebug(String tag, String msg){
        Log.d(tag, msg);
    }

    /**
     * Writes an error log with this tag and message to the console, regardless of the log levels
     * set in the app.
     * @param tag Log tag
     * @param msg Log message
     */
    public static void consoleLogError(String tag, String msg){
        Log.e(tag, msg);
    }

    /**
     * Saves logs to file if log level is above the fileLogLevel setting.
     * @param tag Tag for the log message.
     * @param message Message to be saved in the file.
     */
    public static void logToFile(String level, String tag, String message)
            throws FileNotFoundException{
        saveToFile(level, tag, message);
    }

    /**
     * Get path of the most recently written log file.
     * @return Absolute file path
     */
    private static String getCurrentFilePath(){
        File logDir = getLogDirectory();
        if(logDir == null){
            return null;
        }
        String[]  fileNameList = logDir.list();
        if(fileNameList == null || fileNameList.length == 0){
            return null;
        }
        Timestamp lastTimeStamp = null;
        for(int i = 0; i <fileNameList.length; i++){
            if(fileNameList[i].contains(CRASH_LOG_IDENTIFIER)){
                continue;
            }
            Timestamp timestamp = getTimestampFromFileName(fileNameList[i]);
            if(lastTimeStamp == null){
                lastTimeStamp = timestamp;
            } else if(lastTimeStamp.before(timestamp)){
                lastTimeStamp = timestamp;
            }
        }
        if(lastTimeStamp != null){
            return logDir.getAbsolutePath() + "/" + __logFileName + lastTimeStamp;
        }
        return null;
    }

    /**
     * Get timestamp from log file name.
     */
    private static Timestamp getTimestampFromFileName(String fileName){
        int length = fileName.length();
        int beginIndex = length - TIMESTAMP_LENGTH - LOGS_FILE_EXTENSION.length();
        int endIndex = length - LOGS_FILE_EXTENSION.length();
        String timeStampString = length > TIMESTAMP_LENGTH ?
                fileName.substring(beginIndex, endIndex) : null;
        Log.d(LOG_TAG, "Reading timestamp "+timeStampString);
        try {
            return Timestamp.valueOf(timeStampString);
        } catch (IllegalArgumentException e) {
            Log.d(LOG_TAG, "fileName " + fileName + " doesn't have timestamp.");
            return null;
        }
    }

    /**
     * Returns path to oldest log file.
     * @return absolute path to oldest log file.
     */
    private static String getOldestLogFile(){
        File logDir = getLogDirectory();
        if(logDir == null){
            return null;
        }
        String[]  fileNameList = logDir.list();
        Timestamp firstTimeStamp = null;
        for(int i = 0; i <fileNameList.length; i++){
            if(fileNameList[i].contains(CRASH_LOG_IDENTIFIER)){
                continue;
            }
            Timestamp timestamp = getTimestampFromFileName(fileNameList[i]);
            if(firstTimeStamp == null){
                firstTimeStamp = timestamp;
            } else if(firstTimeStamp.after(timestamp)){
                firstTimeStamp = timestamp;
            }
        }
        if(firstTimeStamp != null){
            return logDir.getAbsolutePath() + "/" + __logFileName + firstTimeStamp;
        }
        return null;
    }

    /**
     * Deletes oldest log file if there are no crash logs saved in the file.
     */
    private static void deleteOldestLogFile(){
        Log.d(LOG_TAG, "deleteOldestLogFile");
        String fileNameToDelete = getOldestLogFile();
        if(fileNameToDelete != null){
            File fileToDelete = new File(fileNameToDelete);
            if(fileToDelete.exists()){
                fileToDelete.delete();
                __fileCount--;
            }
        }
    }

    /**
     * Deletes log files with crash logs. To be called after uploading crash logs to service.
     */
    private static void deleteCrashLogFile(){
        Log.d(LOG_TAG, "deleteCrashLogFile");
        File logDir = getLogDirectory();
        File[]  fileList = logDir.listFiles();
        for(File file: fileList){
            if(file.getAbsolutePath().contains(CRASH_LOG_IDENTIFIER)){
                if(file.exists()){
                    boolean deleteSuccess = file.delete();
                    Log.d(LOG_TAG, "deleted file "+deleteSuccess + " "+file
                            .getAbsolutePath());
                }
            }
        }
    }

    private static File getLogDirectory() {
        return __logDirectory;
    }

    private static String formatLogMessage(String level, String tag, String msg){
        StringBuilder strBuilder = new StringBuilder(tag.length() + msg.length() + 48);
        String date = null;
        //add date
        Date currentDate = Calendar.getInstance().getTime();
        date = __dateFormat.format(currentDate);
        strBuilder.append(date);
        strBuilder.append(LOG_COMPONENT_DELIMITER);
        strBuilder.append(level);
        strBuilder.append(LOG_COMPONENT_DELIMITER);
        strBuilder.append(__packageName);
        strBuilder.append(LOG_COMPONENT_DELIMITER);
        strBuilder.append(tag);
        strBuilder.append(LOG_COMPONENT_DELIMITER);
        strBuilder.append(msg);
        strBuilder.append(LOGS_DELIMITER);
        return strBuilder.toString();
    }

    private static synchronized void saveToFile(String level,
                                                String tag,
                                                String message) throws FileNotFoundException {

        try {
            if (__currentFile == null || !__currentFile.exists()) {
                createNewLogFile();
            } else if (__currentFile.length() >= FILE_MEMORY_LIMIT) {
                if (__fileCount < NUM_OF_LOG_FILES) {
                    createNewLogFile();
                } else if (__fileCount == NUM_OF_LOG_FILES) {
                    deleteOldestLogFile();
                    createNewLogFile();
                }
            }
        } catch (IOException e) {
            Log.e(LOG_TAG, "save to file error:" + e);
            throw new FileNotFoundException();
        }

        //If the log message already has LOGS_DELIMITER in text, replace it so that parsing is not
        //affected later.
        message = message.replace(LOGS_DELIMITER, DELIMITER_REPLACE_CHAR);
        String formattedLog = formatLogMessage(level, tag, message);

        writeMessageToFile(formattedLog, false);

    }

    private static void writeMessageToFile(String message, boolean isCrashLog){
        if(__currentFile != null && __currentFile.exists()){
            try {
                __currentFile.setWritable(true);
                FileOutputStream fileOutputStream = new FileOutputStream(__currentFile, true);
                fileOutputStream.write(message.getBytes());
                fileOutputStream.flush();
                fileOutputStream.close();
                if(isCrashLog){
                    String newFileName = getNewFileName(true);
                    boolean renameSuccess = __currentFile.renameTo(new File(newFileName));
                    Log.d(LOG_TAG, " __currentFile renamed "+renameSuccess);
                }
            } catch (IOException e) {
                Log.e(LOG_TAG, "IOException in writeMessageToFile ");
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
                    Context context = AylaNetworks.sharedInstance().getContext();
                    if(context.checkSelfPermission("android.permission.READ_EXTERNAL_STORAGE") ==
                            PackageManager.PERMISSION_DENIED){
                        Log.d(LOG_TAG, "External storage permission denied. Disable logs ");
                        try {
                            setFileLogLevel(LogLevel.None);
                        } catch (PreconditionError preconditionError) {
                            preconditionError.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    public static void saveCrashLogs(String message){
        String formattedLog = formatLogMessage("E", "CRASH", message);
        writeMessageToFile(formattedLog, true);
    }

    /**
     * Get total number of log files present. Called at app start.
     * @return number of log files stored in the phone by this app.
     */
    private static int getFileCount(){
        int count = 0;
        File directory = getLogDirectory();
        if(directory != null && directory.isDirectory()){
            Log.d(LOG_TAG, "directory "+directory.getAbsolutePath());
            String[] fileNameList = directory.list();
            if(fileNameList != null && fileNameList.length != 0){
                for(String fileName: fileNameList){
                    if(fileName != null && fileName.contains(__logFileName)){
                        Log.d(LOG_TAG, "incrementing fileCount ");
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static String getNewFileName(boolean isCrashLog){
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        String currentPath;
        if(isCrashLog){
            currentPath = getLogDirectory().getAbsolutePath() + "/"
                    + CRASH_LOG_IDENTIFIER + timestamp.toString().substring(0, TIMESTAMP_LENGTH)
                    + LOGS_FILE_EXTENSION;
        } else{
            currentPath = getLogDirectory().getAbsolutePath() + "/" + __logFileName +
                    timestamp.toString().substring(0, TIMESTAMP_LENGTH) + LOGS_FILE_EXTENSION;
        }
        return currentPath;
    }

    private static void createNewLogFile() throws IOException {
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        String currentPath = getNewFileName(false);
        File file = new File(currentPath);
        file.createNewFile();
        if(file.isFile()){
            __fileCount++;
            __currentFile = file;
            Log.d(LOG_TAG, "new log file created "+file.getAbsolutePath());
            Log.d(LOG_TAG, "current fileCount "+__fileCount);
        }
    }
    /**
     * Get an intent object that can be used to send an email to Ayla Support.
     * Call startActivity(intent) to send the email using default email application on phone.
     * @return Intent object that can be used to send an email to Ayla Support
     */
    public static Intent getEmailIntent(Context context,String[] supportEmail, String
            emailSubject, String emailMessage){
        Intent emailIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        emailIntent.setType("plain/text");
        emailIntent.putExtra(Intent.EXTRA_EMAIL, supportEmail);
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, emailSubject);
        emailIntent.putExtra(Intent.EXTRA_TEXT, emailMessage);
        emailIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        ArrayList<Uri> attachmentUriList = new ArrayList<>(2);
        File[] files = getLogDirectory().listFiles();
        if(files == null || files.length == 0){
            return null;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            String authority = __packageName;

            for(File file: files){
                Uri contentUri1 = FileProvider.getUriForFile(context, authority, file);
                attachmentUriList.add(contentUri1);
            }
        } else {
            for(File file: files){
                if(file != null){
                    attachmentUriList.add(Uri.parse("file://" + file.getAbsolutePath()));
                }
            }
        }
        emailIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        emailIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM,attachmentUriList);
        return emailIntent;
    }




    /**
     * Uploads logs generated from this app to Ayla Log service.
     * @param successListener Listener to be called on successful logs upload.
     * @param errorListener Listener to be called if an error occurs.
     * @return cancelable AylaAPIRequest.
     */
    public static AylaAPIRequest uploadCrashLogsToLogService(
            final Response.Listener<AylaAPIRequest.EmptyResponse> successListener,
            final ErrorListener errorListener){
        // Read contents of file, convert each AylaLog to format supported by log service, and
        // upload to log service
        Log.d(LOG_TAG, "uploadCrashLogsToLogService");
        AylaSessionManager sessionManager = AylaNetworks.sharedInstance().
                getSessionManager(__sessionName);
        if(sessionManager == null){
            errorListener.onErrorResponse(new PreconditionError("No session manager"));
            return null;
        }
        String logsToUpload = getLogsArray();
        if(logsToUpload == null){
            errorListener.onErrorResponse(new PreconditionError("No crash logs to upload"));
            return null;
        }
        Log.d(LOG_TAG, "Uploading logsArray "+logsToUpload);
        String url = AylaNetworks.sharedInstance().getServiceUrl(
                ServiceUrls.CloudService.Log, LOG_URL_PATH);
        AylaJsonRequest request = new AylaJsonRequest<>(Request.Method.POST, url, logsToUpload,
                null, AylaAPIRequest.EmptyResponse.class, sessionManager,
                new Response.Listener<AylaAPIRequest.EmptyResponse>() {
                    @Override
                    public void onResponse(AylaAPIRequest.EmptyResponse response) {
                        Log.d(LOG_TAG, "Upload logs success");
                        successListener.onResponse(new AylaAPIRequest.EmptyResponse());
                        deleteCrashLogFile();
                    }
                }, new ErrorListener() {
            @Override
            public void onErrorResponse(AylaError error) {
                Log.e(LOG_TAG, "Upload logs failed "+error);
                errorListener.onErrorResponse(error);
            }
        });
        sessionManager.sendUserServiceRequest(request);
        return request;
    }

    private static String getLogsArray(){
        File logDirectory = getLogDirectory();
        if(logDirectory == null){
            return null;
        }
        JSONArray logsJsonArray = new JSONArray();
        File[] files = logDirectory.listFiles();
        if (files == null || files.length == 0) {
            return  null;
        }

        Log.d(LOG_TAG, "No. of log files "+files.length);
        for(File file: files){
            if(file.getPath().contains(CRASH_LOG_IDENTIFIER) && file.exists()){
                Log.d(LOG_TAG, "reading crash file "+file.getAbsolutePath());
                readLogsFromFile(file, logsJsonArray);
            }
        }
       // addCrashLogs(logsJsonArray);
        if(logsJsonArray == null || logsJsonArray.length() == 0){
            return null;
        }
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("logs", logsJsonArray);
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
        return jsonObject.toString();
    }

    private static void readLogsFromFile(File file, JSONArray logsArray) {
        try {
            Scanner scanner = new Scanner(file);
            scanner.useDelimiter(LOGS_DELIMITER);
            while (scanner.hasNext()) {
                String log = scanner.next();
                JSONObject logJSON = getFormattedLogJSON(log);
                logsArray.put(logJSON);
            }
            scanner.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

    }

    private static JSONObject getFormattedLogJSON(String logMessage){
        JSONObject logJson = new JSONObject();
        Scanner scanner = new Scanner(logMessage);
        scanner.useDelimiter(LOG_COMPONENT_DELIMITER);
        try {
            String date = scanner.next();
            long timeInMs = getTimeInMs(date);
            String level = scanner.next();
            String packageName = scanner.next();
            String tag = scanner.next();
            String text = scanner.next();
            text = text.replace(DELIMITER_REPLACE_CHAR, LOGS_DELIMITER);
            logJson.put("platform", "Android");
            logJson.put("time", timeInMs);
            logJson.put("text", text);
            logJson.put("level", level);
            logJson.put("tag", tag);
            logJson.put("log_type", "Log");
            logJson.put("sender_id", packageName);
        } catch (JSONException e) {
            e.printStackTrace();
        } catch(NoSuchElementException e){
            e.printStackTrace();
        }
        return logJson;
    }

    private static long getTimeInMs(String date){
        Date parsedDate = __dateFormat.parse(date, new ParsePosition(0));
        if(parsedDate != null){
            return parsedDate.getTime();
        }
        return -1;
    }
}
