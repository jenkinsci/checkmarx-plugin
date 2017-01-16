package com.checkmarx.jenkins.web.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Created by zoharby on 09/01/2017.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Library {

    @JsonProperty("id")
    String id;//:"36b32b00-9ee6-4e2f-85c9-3f03f26519a9",
    @JsonProperty("name")
    String  name;//:"lib-name",
    @JsonProperty("version")
    String version;//:"lib-version",
    @JsonProperty("highVulnerabilityCount")
    Integer highVulnerabilityCount;//:1,
    @JsonProperty("mediumVulnerabilityCount")
    Integer mediumVulnerabilityCount;//:1,
    @JsonProperty("lowVulnerabilityCount")
    Integer lowVulnerabilityCount;//:1,
    @JsonProperty("newestVersion")
    String newestVersion;//:"1.0.0",
    @JsonProperty("newestVersionReleaseDate")
    String newestVersionReleaseDate;//:"2016-12-19T10:16:19.1206743Z",
    @JsonProperty("numberOfVersionsSinceLastUpdate")
    Integer numberOfVersionsSinceLastUpdate;//":10,
    @JsonProperty("confidenceLevel")
    Integer confidenceLevel;//":100


    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Integer getHighVulnerabilityCount() {
        return highVulnerabilityCount;
    }

    public void setHighVulnerabilityCount(Integer highVulnerabilityCount) {
        this.highVulnerabilityCount = highVulnerabilityCount;
    }

    public Integer getMediumVulnerabilityCount() {
        return mediumVulnerabilityCount;
    }

    public void setMediumVulnerabilityCount(Integer mediumVulnerabilityCount) {
        this.mediumVulnerabilityCount = mediumVulnerabilityCount;
    }

    public Integer getLowVulnerabilityCount() {
        return lowVulnerabilityCount;
    }

    public void setLowVulnerabilityCount(Integer lowVulnerabilityCount) {
        this.lowVulnerabilityCount = lowVulnerabilityCount;
    }

    public String getNewestVersion() {
        return newestVersion;
    }

    public void setNewestVersion(String newestVersion) {
        this.newestVersion = newestVersion;
    }

    public String getNewestVersionReleaseDate() {
        return newestVersionReleaseDate;
    }

    public void setNewestVersionReleaseDate(String newestVersionReleaseDate) {
        this.newestVersionReleaseDate = newestVersionReleaseDate;
    }

    public Integer getNumberOfVersionsSinceLastUpdate() {
        return numberOfVersionsSinceLastUpdate;
    }

    public void setNumberOfVersionsSinceLastUpdate(Integer numberOfVersionsSinceLastUpdate) {
        this.numberOfVersionsSinceLastUpdate = numberOfVersionsSinceLastUpdate;
    }

    public Integer getConfidenceLevel() {
        return confidenceLevel;
    }

    public void setConfidenceLevel(Integer confidenceLevel) {
        this.confidenceLevel = confidenceLevel;
    }

}
