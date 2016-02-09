package com.checkmarx.jenkins.web.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * @author tsahi
 * @since 02/02/16
 */
@XmlRootElement
public class AnalyzeResponse {
    @XmlElement(name="no_Known_Vulnerabilites")
    private int noKnownVulnerabilities;
    @XmlElement(name="vulnerable_And_Update")
    private int vulnerableAndUpdate;
    @XmlElement(name="vulnerable_And_Outdated")
    private int vulnerableAndOutdated;
    @XmlElement(name="total")
    private int total;
    @XmlElement(name="date")
    private String date;

    public int getNoKnownVulnerabilities() {
        return noKnownVulnerabilities;
    }

    public void setNoKnownVulnerabilities(int noKnownVulnerabilities) {
        this.noKnownVulnerabilities = noKnownVulnerabilities;
    }

    public int getVulnerableAndUpdate() {
        return vulnerableAndUpdate;
    }

    public void setVulnerableAndUpdate(int vulnerableAndUpdate) {
        this.vulnerableAndUpdate = vulnerableAndUpdate;
    }

    public int getVulnerableAndOutdated() {
        return vulnerableAndOutdated;
    }

    public void setVulnerableAndOutdated(int vulnerableAndOutdated) {
        this.vulnerableAndOutdated = vulnerableAndOutdated;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }
}
