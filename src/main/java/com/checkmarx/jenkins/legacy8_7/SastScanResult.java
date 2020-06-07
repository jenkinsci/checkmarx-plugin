package com.checkmarx.jenkins.legacy8_7;

import com.checkmarx.jenkins.legacy8_7.QueryResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;

/**
 * Created by zoharby on 22/01/2017.
 */
public class SastScanResult {

    private Integer highCount;
    private Integer mediumCount;
    private Integer lowCount;
    private Integer infoCount;

    private Integer newHighCount;
    private Integer newMediumCount;
    private Integer newLowCount;

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
    private String scanEnd;
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
        this.resultDeepLink = "";
    }

    public Integer getHighCount() {
        return highCount;
    }

    public void setHighCount(int highCount) {
        this.highCount = highCount;
    }

    public Integer getMediumCount() {
        return mediumCount;
    }

    public void setMediumCount(int mediumCount) {
        this.mediumCount = mediumCount;
    }

    public Integer getLowCount() {
        return lowCount;
    }

    public void setLowCount(int lowCount) {
        this.lowCount = lowCount;
    }

    public Integer getInfoCount() {
        return infoCount;
    }

    public void setInfoCount(int infoCount) {
        this.infoCount = infoCount;
    }

    public Integer getNewHighCount() { return newHighCount; }

    public void setNewHighCount(Integer newHighCount) { this.newHighCount = newHighCount; }

    public Integer getNewMediumCount() { return newMediumCount; }

    public void setNewMediumCount(Integer newMediumCount) { this.newMediumCount = newMediumCount; }

    public Integer getNewLowCount() { return newLowCount; }

    public void setNewLowCount(Integer newLowCount) { this.newLowCount = newLowCount; }

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
        this.scanStart = scanStart;
    }

    @Nullable
    public String getScanEnd() {
        return scanEnd;
    }

    public void setScanEnd(@Nullable String scanEnd) {
        this.scanEnd = scanEnd;
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

}
