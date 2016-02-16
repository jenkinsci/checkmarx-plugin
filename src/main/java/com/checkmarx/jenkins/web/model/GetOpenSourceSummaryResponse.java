package com.checkmarx.jenkins.web.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * @author tsahi
 * @since 02/02/16
 */
@XmlRootElement
public class GetOpenSourceSummaryResponse {
    @XmlElement(name="nonVulnerableLibraries")
    private String noKnownVulnerabilities;
    @XmlElement(name="vulnerableAndUpdated")
    private String vulnerableAndUpdate;
    @XmlElement(name="vulnerableAndOutdated")
    private String vulnerableAndOutdated;
    @XmlElement(name="totalLibraries")
    private String total;
    @XmlElement(name="highVulnerabilityLibraries")
    private String highVulnerabilityLibraries;
    @XmlElement(name="mediumVulnerabilityLibraries")
    private String mediumVulnerabilityLibraries;
    @XmlElement(name="lowVulnerabilityLibraries")
    private String lowVulnerabilityLibraries;
    @XmlElement(name="analyzeTime")
    private String analyzeTime;

    public String getNoKnownVulnerabilities() {
        return noKnownVulnerabilities;
    }

    public void setNoKnownVulnerabilities(String noKnownVulnerabilities) {
        this.noKnownVulnerabilities = noKnownVulnerabilities;
    }

    public String getVulnerableAndUpdate() {
        return vulnerableAndUpdate;
    }

    public void setVulnerableAndUpdate(String vulnerableAndUpdate) {
        this.vulnerableAndUpdate = vulnerableAndUpdate;
    }

    public String getVulnerableAndOutdated() {
        return vulnerableAndOutdated;
    }

    public void setVulnerableAndOutdated(String vulnerableAndOutdated) {
        this.vulnerableAndOutdated = vulnerableAndOutdated;
    }

    public String getTotal() {
        return total;
    }

    public void setTotal(String total) {
        this.total = total;
    }

    public String getHighVulnerabilityLibraries() {
        return highVulnerabilityLibraries;
    }

    public void setHighVulnerabilityLibraries(String highVulnerabilityLibraries) {
        this.highVulnerabilityLibraries = highVulnerabilityLibraries;
    }

    public String getMediumVulnerabilityLibraries() {
        return mediumVulnerabilityLibraries;
    }

    public void setMediumVulnerabilityLibraries(String mediumVulnerabilityLibraries) {
        this.mediumVulnerabilityLibraries = mediumVulnerabilityLibraries;
    }
}
