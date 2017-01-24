package com.checkmarx.jenkins;

import com.checkmarx.jenkins.web.model.GetOpenSourceSummaryResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Created by zoharby on 10/01/2017.
 */
public class OsaScanResult {

    //osa results
    private boolean isOsaReturnedResult;
    private GetOpenSourceSummaryResponse openSourceSummaryResponse;
    private String openSourceSummaryJson;
    private int osaHighCount;
    private int osaMediumCount;
    private int osaLowCount;
    private int osaVulnerableAndOutdatedLibs;
    private int osaTotalVulnerabilitiesLibs;
    private int osaNoVulnerabilityLibs;
    private boolean osaEnabled = false;

    private String scanId;

    private String osaFullLibraryList;
    private String osaFullCVEsList;
    private String highCvesList;
    private String mediumCvesList;
    private String lowCvesList;

    public void addOsaResults(GetOpenSourceSummaryResponse osaResults) {
        setIsOsaReturnedResult(osaResults != null);
        //osa fields
        if (isOsaReturnedResult) {
            this.openSourceSummaryResponse = osaResults;
            this.setOsaEnabled(true);
            this.osaHighCount = osaResults.getHighCount();
            this.osaMediumCount = osaResults.getMediumCount();
            this.osaLowCount = osaResults.getLowCount();
            this.osaTotalVulnerabilitiesLibs = osaResults.getHighCount() + osaResults.getMediumCount() + osaResults.getLowCount();
            this.osaVulnerableAndOutdatedLibs = osaResults.getVulnerableAndOutdated();
            this.osaNoVulnerabilityLibs = osaResults.getNoKnownVulnerabilities();

            ObjectMapper mapper = new ObjectMapper();
            try {
                this.openSourceSummaryJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(osaResults);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        }
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

    public boolean isOsaReturnedResult() {
        return isOsaReturnedResult;
    }

    public void setIsOsaReturnedResult(boolean osaReturnedResult) {
        isOsaReturnedResult = osaReturnedResult;
    }

    public boolean isOsaEnabled() {
        return osaEnabled;
    }

    public void setOsaEnabled(boolean osaEnabled) {
        this.osaEnabled = osaEnabled;
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
}
