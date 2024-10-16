package com.checkmarx.jenkins.legacy8_7;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author tsahi
 * @since 02/02/16
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetOpenSourceSummaryResponse {
    @JsonProperty("nonVulnerableLibraries")
    private Integer noKnownVulnerabilities;
    @JsonProperty("vulnerableAndUpdated")
    private Integer vulnerableAndUpdate;
    @JsonProperty("vulnerableAndOutdated")
    private Integer vulnerableAndOutdated;
    @JsonProperty("totalLibraries")
    private Integer total;
    @JsonProperty("criticalVulnerabilityLibraries")
    private Integer criticalVulnerabilityLibraries;
    @JsonProperty("highVulnerabilityLibraries")
    private Integer highVulnerabilityLibraries;
    @JsonProperty("mediumVulnerabilityLibraries")
    private Integer mediumVulnerabilityLibraries;
    @JsonProperty("lowVulnerabilityLibraries")
    private Integer lowVulnerabilityLibraries;
    @JsonProperty("vulnerabilityScore")
    private String vulnerabilityScore;
    @JsonProperty("totalCriticalVulnerabilities")
    private Integer criticalVulnerabilities;
    @JsonProperty("totalHighVulnerabilities")
    private Integer highVulnerabilities;
    @JsonProperty("totalMediumVulnerabilities")
    private Integer mediumVulnerabilities;
    @JsonProperty("totalLowVulnerabilities")
    private Integer lowVulnerabilities;


    public int getCriticalCount() {
        return criticalVulnerabilities;
    }
    
    public int getHighCount() {
        return highVulnerabilities;
    }
    public int getMediumCount() {
        return mediumVulnerabilities;
    }

    public int getLowCount() {
        return lowVulnerabilities;
    }

    public Integer getNoKnownVulnerabilities() {
        return noKnownVulnerabilities;
    }

    public void setNoKnownVulnerabilities(Integer noKnownVulnerabilities) {
        this.noKnownVulnerabilities = noKnownVulnerabilities;
    }

    public Integer getVulnerableAndUpdate() {
        return vulnerableAndUpdate;
    }

    public void setVulnerableAndUpdate(Integer vulnerableAndUpdate) {
        this.vulnerableAndUpdate = vulnerableAndUpdate;
    }

    public Integer getVulnerableAndOutdated() {
        return vulnerableAndOutdated;
    }

    public void setVulnerableAndOutdated(Integer vulnerableAndOutdated) {
        this.vulnerableAndOutdated = vulnerableAndOutdated;
    }

    public Integer getTotal() {
        return total;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }
    
    public Integer getCriticalVulnerabilityLibraries() {
        return criticalVulnerabilityLibraries;
    }

    public void setCriticalVulnerabilityLibraries(Integer criticalVulnerabilityLibraries) {
        this.criticalVulnerabilityLibraries = criticalVulnerabilityLibraries;
    }

    public Integer getHighVulnerabilityLibraries() {
        return highVulnerabilityLibraries;
    }

    public void setHighVulnerabilityLibraries(Integer highVulnerabilityLibraries) {
        this.highVulnerabilityLibraries = highVulnerabilityLibraries;
    }

    public Integer getMediumVulnerabilityLibraries() {
        return mediumVulnerabilityLibraries;
    }

    public void setMediumVulnerabilityLibraries(Integer mediumVulnerabilityLibraries) {
        this.mediumVulnerabilityLibraries = mediumVulnerabilityLibraries;
    }

    public String getVulnerabilityScore()
    {
        return vulnerabilityScore;
    }

    public Integer getCriticalVulnerabilities()
    {
        return criticalVulnerabilities;
    }

    public void setCriticalVulnerabilities(Integer criticalVulnerabilities) {
        this.criticalVulnerabilities = criticalVulnerabilities;
    }
    
    public Integer getHighVulnerabilities()
    {
        return highVulnerabilities;
    }

    public void setHighVulnerabilities(Integer highVulnerabilities) {
        this.highVulnerabilities = highVulnerabilities;
    }

    public Integer getMediumVulnerabilities()
    {
        return mediumVulnerabilities;
    }

    public void setMediumVulnerabilities(Integer mediumVulnerabilities) {
        this.mediumVulnerabilities = mediumVulnerabilities;
    }

    public Integer getLowVulnerabilities()
    {
        return lowVulnerabilities;
    }

    public void setLowVulnerabilities(Integer lowVulnerabilities) {
        this.lowVulnerabilities = lowVulnerabilities;
    }

    public Integer getLowVulnerabilityLibraries() {
        return lowVulnerabilityLibraries;
    }

    public void setLowVulnerabilityLibraries(Integer lowVulnerabilityLibraries) {
        this.lowVulnerabilityLibraries = lowVulnerabilityLibraries;
    }

    public void setVulnerabilityScore(String vulnerabilityScore) {
        this.vulnerabilityScore = vulnerabilityScore;
    }
}
