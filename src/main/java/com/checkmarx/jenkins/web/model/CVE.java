package com.checkmarx.jenkins.web.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Created by zoharby on 09/01/2017.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CVE {

    @JsonProperty("id")
    String id;
    @JsonProperty("cveName")
    String cveName;
    @JsonProperty("score")
    Float score;
    @JsonProperty("severity")
    Severity severity;
    @JsonProperty("publishDate")
    String publishDate;//:"2016-11-07T10:16:06.1206743Z",
    @JsonProperty("url")
    String url;//:"http://cv1",
    @JsonProperty("description")
    String description;//:null,
    @JsonProperty("recommendations")
    String recommendations;//:"recommendation 1",
    @JsonProperty("sourceFileName")
    String sourceFileName;//:"SourceFileName 1",
    @JsonProperty("libraryId")
    String libraryId;//:"36b32b00-9ee6-4e2f-85c9-3f03f26519a9"

    //Added after request
    @JsonProperty("libraryName")
    String libraryName;

    public String getLibraryId() {
        return libraryId;
    }

    public void setLibraryId(String libraryId) {
        this.libraryId = libraryId;
    }

    public String getSourceFileName() {
        return sourceFileName;
    }

    public void setSourceFileName(String sourceFileName) {
        this.sourceFileName = sourceFileName;
    }

    public String getRecommendations() {
        return recommendations;
    }

    public void setRecommendations(String recommendations) {
        this.recommendations = recommendations;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getPublishDate() {
        return publishDate;
    }

    public void setPublishDate(String publishDate) {
        this.publishDate = publishDate;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public Float getScore() {
        return score;
    }

    public void setScore(Float score) {
        this.score = score;
    }

    public String getCveName() {
        return cveName;
    }

    public void setCveName(String cveName) {
        this.cveName = cveName;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLibraryName() {
        return libraryName;
    }

    public void setLibraryName(String libraryName) {
        this.libraryName = libraryName;
    }
}
