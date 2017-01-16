package com.checkmarx.jenkins;

import com.checkmarx.jenkins.web.model.CVE;
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
    private int osaNoVulnerabilityLibs;
    private boolean osaEnabled = false;
    private List<Library> osaLibrariesList;
    private List<CVE> osaCveList;
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

    public List<CVE> getOsaCveList() {
        return osaCveList;
    }

    public void setOsaCveList(List<CVE> osaCveList) {
        this.osaCveList = osaCveList;
    }

    public boolean isOsaEnabled() {
        return osaEnabled;
    }

    public void setOsaEnabled(boolean osaEnabled) {
        this.osaEnabled = osaEnabled;
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
}
