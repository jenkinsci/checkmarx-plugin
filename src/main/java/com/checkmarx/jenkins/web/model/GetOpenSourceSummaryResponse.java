package com.checkmarx.jenkins.web.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * @author tsahi
 * @since 02/02/16
 */
@JsonIgnoreProperties(ignoreUnknown = true)
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
    @XmlElement(name="vulnerabilityScore")
    private String vulnerabilityScore;
    @XmlElement(name="highVulnerabilities")
    private String highVulnerabilities;
    @XmlElement(name="mediumVulnerabilities")
    private String mediumVulnerabilities;
    @XmlElement(name="lowVulnerabilities")
    private String lowVulnerabilities;


    public int getHighCount() {
        return Integer.parseInt(highVulnerabilities);
    }
    public int getMediumCount() {
        return Integer.parseInt(mediumVulnerabilities);
    }
    public int getLowCount() {
        return Integer.parseInt(lowVulnerabilities);
    }

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

    public String getVulnerabilityScore()
    {
        return vulnerabilityScore;
    }

    public String getHighVulnerabilities()
    {
        return highVulnerabilities;
    }

    public void setHighVulnerabilities(String highVulnerabilities) {
        this.highVulnerabilities = highVulnerabilities;
    }

    public String getMediumVulnerabilities()
    {
        return mediumVulnerabilities;
    }

    public void setMediumVulnerabilities(String mediumVulnerabilities) {
        this.mediumVulnerabilities = mediumVulnerabilities;
    }

    public String getLowVulnerabilities()
    {
        return lowVulnerabilities;
    }

    public void setLowVulnerabilities(String lowVulnerabilities) {
        this.lowVulnerabilities = lowVulnerabilities;
    }

}
