package com.checkmarx.jenkins;

import com.checkmarx.jenkins.web.model.GetOpenSourceSummaryResponse;
import com.checkmarx.jenkins.web.model.ScanDetails;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Created by zoharby on 10/01/2017.
 */
public class OsaScanResult {

    //Osa scan times
    private String osaScanStartTime;
    private String osaScanEndTime;

    //osa results
    private GetOpenSourceSummaryResponse openSourceSummaryResponse;
    private String openSourceSummaryJson;
    private Integer osaHighCount;
    private Integer osaMediumCount;
    private Integer osaLowCount;
    private Integer osaVulnerableAndOutdatedLibs;
    private Integer osaTotalVulnerabilitiesLibs;
    private Integer osaNoVulnerabilityLibs;

    private Integer osaScanTotalLibraries;

    private String scanId;

    private String osaFullLibraryList;
    private String osaFullCVEsList;
    private String highCvesList;
    private String mediumCvesList;
    private String lowCvesList;
    public boolean isOsaLicense;

    public boolean isOsaLicense() {
        return isOsaLicense;
    }

    public void setOsaLicense(boolean osaLicense) {
        isOsaLicense = osaLicense;
    }

    public void setOsaScanStartAndEndTimes(ScanDetails scanDetails){
        this.osaScanStartTime = formatTime(scanDetails.getStartAnalyzeTime());
        this.osaScanEndTime = formatTime(scanDetails.getEndAnalyzeTime());
    }

    //Format from "2016-12-19T10:16:06.1196743Z" to "19/12/16 16:06"
    private String formatTime(String time){
        String[] timeParts = time.split("T");
        String[] partsOfTimePart = timeParts[0].split("-");
        String metricDate = partsOfTimePart[2]+"/"+partsOfTimePart[1]+"/"+partsOfTimePart[0].substring(2);
        String armyTime = timeParts[1].substring(0,5);
        return metricDate+" "+armyTime;
    }

    public void setOsaResults(GetOpenSourceSummaryResponse osaResults) {
       if(osaResults != null) {
           this.openSourceSummaryResponse = osaResults;
           this.osaHighCount = osaResults.getHighCount();
           this.osaMediumCount = osaResults.getMediumCount();
           this.osaLowCount = osaResults.getLowCount();
           this.osaTotalVulnerabilitiesLibs = osaResults.getLowVulnerabilityLibraries() + osaResults.getMediumVulnerabilityLibraries() + osaResults.getHighVulnerabilityLibraries();
           this.osaVulnerableAndOutdatedLibs = osaResults.getVulnerableAndOutdated();
           this.osaNoVulnerabilityLibs = osaResults.getNoKnownVulnerabilities();
           this.osaScanTotalLibraries = getOsaTotalVulnerabilitiesLibs() + getOsaNoVulnerabilityLibs();

           ObjectMapper mapper = new ObjectMapper();
           try {
               this.openSourceSummaryJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(osaResults);
           } catch (JsonProcessingException e) {
               e.printStackTrace();
           }
       }
    }

    public String getOsaScanStartTime() {
        return osaScanStartTime;
    }

    public String getOsaScanEndTime() {
        return osaScanEndTime;
    }

    public int getOsaHighCount() {
        return osaHighCount;
    }

    public void setOsaHighCount(int osaHighCount) {
        this.osaHighCount = osaHighCount;
    }

    public int getOsaMediumCount() {
        return osaMediumCount;
    }

    public void setOsaMediumCount(int osaMediumCount) {
        this.osaMediumCount = osaMediumCount;
    }

    public int getOsaLowCount() {
        return osaLowCount;
    }

    public void setOsaLowCount(int osaLowCount) {
        this.osaLowCount = osaLowCount;
    }

    public int getOsaVulnerableAndOutdatedLibs() {
        return osaVulnerableAndOutdatedLibs;
    }

    public void setOsaVulnerableAndOutdatedLibs(int osaVulnerableAndOutdatedLibs) {
        this.osaVulnerableAndOutdatedLibs = osaVulnerableAndOutdatedLibs;
    }

    public int getOsaNoVulnerabilityLibs() {
        return osaNoVulnerabilityLibs;
    }

    public void setOsaNoVulnerabilityLibs(int osaNoVulnerabilityLibs) {
        this.osaNoVulnerabilityLibs = osaNoVulnerabilityLibs;
    }

    public int getOsaTotalVulnerabilitiesLibs() {
        return osaTotalVulnerabilitiesLibs;
    }

    public String getScanId() {
        return scanId;
    }

    public void setScanId(String scanId) {
        this.scanId = scanId;
    }

    public GetOpenSourceSummaryResponse getOpenSourceSummaryResponse() {
        return openSourceSummaryResponse;
    }

    public void setOpenSourceSummaryResponse(GetOpenSourceSummaryResponse openSourceSummaryResponse) {
        this.openSourceSummaryResponse = openSourceSummaryResponse;
    }

    public String getOpenSourceSummaryJson() {
        return openSourceSummaryJson;
    }

    public void setOpenSourceSummaryJson(String openSourceSummaryJson) {
        this.openSourceSummaryJson = openSourceSummaryJson;
    }

    public String getOsaFullLibraryList() {
        return osaFullLibraryList;
    }

    public void setOsaFullLibraryList(String osaFullLibraryList) {
        this.osaFullLibraryList = osaFullLibraryList;
    }

    public String getOsaFullCVEsList() {
        return osaFullCVEsList;
    }

    public void setOsaFullCVEsList(String osaFullCVEsList) {
        this.osaFullCVEsList = osaFullCVEsList;
    }

    public String getHighCvesList() {
        return highCvesList;
    }

    public void setHighCvesList(String highCvesList) {
        this.highCvesList = highCvesList;
    }

    public String getMediumCvesList() {
        return mediumCvesList;
    }

    public void setMediumCvesList(String mediumCvesList) {
        this.mediumCvesList = mediumCvesList;
    }

    public String getLowCvesList() {
        return lowCvesList;
    }

    public void setLowCvesList(String lowCvesList) {
        this.lowCvesList = lowCvesList;
    }

    public Integer getOsaScanTotalLibraries() {
        return osaScanTotalLibraries;
    }
}
