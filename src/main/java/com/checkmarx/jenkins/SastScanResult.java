package com.checkmarx.jenkins;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;

/**
 * Created by zoharby on 22/01/2017.
 */
public class SastScanResult {

    private int highCount;
    private int mediumCount;
    private int lowCount;
    private int infoCount;

    private LinkedList<QueryResult> highQueryResultList;
    private LinkedList<QueryResult> mediumQueryResultList;
    private LinkedList<QueryResult> lowQueryResultList;
    private LinkedList<QueryResult> infoQueryResultList;

    private String highQueryResultsJson;
    private String mediumQueryResultsJson;
    private String lowQueryResultsJson;
    private String infoQueryResultsJson;

    @NotNull
    private String resultDeepLink;

    @Nullable
    private String scanStart;
    @Nullable
    private String scanTime;
    @Nullable
    private String linesOfCodeScanned;
    @Nullable
    private String filesScanned;
    @Nullable
    private String scanType;

    private boolean resultIsValid;
    private String errorMessage;

    public SastScanResult() {
        this.highQueryResultList = new LinkedList<>();
        this.mediumQueryResultList = new LinkedList<>();
        this.lowQueryResultList = new LinkedList<>();
        this.infoQueryResultList = new LinkedList<>();
    }

    public int getHighCount() {
        return highCount;
    }

    public void setHighCount(int highCount) {
        this.highCount = highCount;
    }

    public int getMediumCount() {
        return mediumCount;
    }

    public void setMediumCount(int mediumCount) {
        this.mediumCount = mediumCount;
    }

    public int getLowCount() {
        return lowCount;
    }

    public void setLowCount(int lowCount) {
        this.lowCount = lowCount;
    }

    public int getInfoCount() {
        return infoCount;
    }

    public void setInfoCount(int infoCount) {
        this.infoCount = infoCount;
    }

    public LinkedList<QueryResult> getHighQueryResultList() {
        return highQueryResultList;
    }

    public LinkedList<QueryResult> getMediumQueryResultList() {
        return mediumQueryResultList;
    }

    public LinkedList<QueryResult> getLowQueryResultList() {
        return lowQueryResultList;
    }

    public LinkedList<QueryResult> getInfoQueryResultList() {
        return infoQueryResultList;
    }

    public String getHighQueryResultsJson() {
        return highQueryResultsJson;
    }

    public void setHighQueryResultsJson(String highQueryResultsJson) {
        this.highQueryResultsJson = highQueryResultsJson;
    }

    public String getMediumQueryResultsJson() {
        return mediumQueryResultsJson;
    }

    public void setMediumQueryResultsJson(String mediumQueryResultsJson) {
        this.mediumQueryResultsJson = mediumQueryResultsJson;
    }

    public String getLowQueryResultsJson() {
        return lowQueryResultsJson;
    }

    public void setLowQueryResultsJson(String lowQueryResultsJson) {
        this.lowQueryResultsJson = lowQueryResultsJson;
    }

    public String getInfoQueryResultsJson() {
        return infoQueryResultsJson;
    }

    public void setInfoQueryResultsJson(String infoQueryResultsJson) {
        this.infoQueryResultsJson = infoQueryResultsJson;
    }

    public boolean isResultIsValid() {
        return resultIsValid;
    }

    public void setResultIsValid(boolean resultIsValid) {
        this.resultIsValid = resultIsValid;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @NotNull
    public String getResultDeepLink() {
        return resultDeepLink;
    }

    public void setResultDeepLink(@NotNull String resultDeepLink) {
        this.resultDeepLink = resultDeepLink;
    }

    @Nullable
    public String getScanStart() {
        return scanStart;
    }

    public void setScanStart(@Nullable String scanStart) {
        this.scanStart = formatTime(scanStart);
    }

    @Nullable
    public String getScanTime() {
        return scanTime;
    }

    public void setScanTime(@Nullable String scanTime) {
        this.scanTime = formatTime(scanTime);
    }

    @Nullable
    public String getLinesOfCodeScanned() {
        return linesOfCodeScanned;
    }

    public void setLinesOfCodeScanned(@Nullable String linesOfCodeScanned) {
        this.linesOfCodeScanned = linesOfCodeScanned;
    }

    @Nullable
    public String getFilesScanned() {
        return filesScanned;
    }

    public void setFilesScanned(@Nullable String filesScanned) {
        this.filesScanned = filesScanned;
    }

    @Nullable
    public String getScanType() {
        return scanType;
    }

    public void setScanType(@Nullable String scanType) {
        this.scanType = scanType;
    }

    //"Sunday, February 26, 2017 12:17:09 PM" Date to "26/2/17 12:17"
    private String formatTime(String time) {

        String oldPattern = "EEEE, MMMM dd, yyyy hh:mm:ss a";
        String newPattern = "dd/MM/yy HH:mm";
        Locale locale = Locale.ENGLISH;

        DateFormat oldDateFormat = new SimpleDateFormat(oldPattern, locale);

        Date date = new Date();

        try {
            date = oldDateFormat.parse(time);
        } catch (ParseException e) {
            e.printStackTrace();
        }

        String newDate = new SimpleDateFormat(newPattern, locale).format(date);


        return newDate;
    }

}
