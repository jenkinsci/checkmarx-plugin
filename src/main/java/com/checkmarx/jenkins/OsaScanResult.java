package com.checkmarx.jenkins;

import com.checkmarx.jenkins.web.model.GetOpenSourceSummaryResponse;
import com.checkmarx.jenkins.web.model.Library;

import java.util.List;

/**
 * Created by zoharby on 10/01/2017.
 */
public class OsaScanResult {

    //osa results
    private boolean isOsaReturnedResult;
    private GetOpenSourceSummaryResponse getOpenSourceSummaryResponse;
    private int osaHighCount;
    private int osaMediumCount;
    private int osaLowCount;
    private int osaVulnerableAndOutdatedLibs;
    private int osaTotalVulnerabilitiesLibs;
    private int osaNoVulnerabilityLibs;
    private boolean osaEnabled = false;
    private List<Library> osaLibrariesList;
    private String highCvesList;
    private String mediumCvesList;
    private String lowCvesList;

    private String scanId;

    public void addOsaResults(GetOpenSourceSummaryResponse osaResults) {
        setIsOsaReturnedResult(osaResults != null);
        //osa fields
        if (isOsaReturnedResult) {
            this.getOpenSourceSummaryResponse = osaResults;
            this.setOsaEnabled(true);
            this.osaHighCount = osaResults.getHighCount();
            this.osaMediumCount = osaResults.getMediumCount();
            this.osaLowCount = osaResults.getLowCount();
            this.osaTotalVulnerabilitiesLibs = osaResults.getHighCount() + osaResults.getMediumCount() + osaResults.getLowCount();
            this.osaVulnerableAndOutdatedLibs = osaResults.getVulnerableAndOutdated();
            this.osaNoVulnerabilityLibs = osaResults.getNoKnownVulnerabilities();
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

    public List<Library> getOsaLibrariesList() {
        return osaLibrariesList;
    }

    public void setOsaLibrariesList(List<Library> ohsaLibrariesList) {
        this.osaLibrariesList = ohsaLibrariesList;
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

    public GetOpenSourceSummaryResponse getGetOpenSourceSummaryResponse() {
        return getOpenSourceSummaryResponse;
    }

    public void setGetOpenSourceSummaryResponse(GetOpenSourceSummaryResponse getOpenSourceSummaryResponse) {
        this.getOpenSourceSummaryResponse = getOpenSourceSummaryResponse;
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
