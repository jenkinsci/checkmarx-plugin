package com.checkmarx.jenkins.web.model;

import com.checkmarx.jenkins.opensourceanalysis.OSAFile;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;


public class CreateOSAScanRequest {

    @JsonProperty("ProjectId")
    private long projectId;

    @JsonProperty("Origin")
    private String origin;

    @JsonProperty("HashedFilesList")
    private List<OSAFile> hashedFilesList;

    public CreateOSAScanRequest(long projectId, String origin, List<OSAFile> hashedFilesList) {
        this.projectId = projectId;
        this.hashedFilesList = hashedFilesList;
        this.origin = origin;
    }

    public long getProjectId() {
        return projectId;
    }

    public void setProjectId(long projectId) {
        this.projectId = projectId;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public List<OSAFile> getHashedFilesList() {
        return hashedFilesList;
    }

    public void setHashedFilesList(List<OSAFile> hashedFilesList) {
        this.hashedFilesList = hashedFilesList;
    }
}
