package com.checkmarx.jenkins.web.model;

/**
 * Created by tsahib on 15/02/2016.
 */
@Deprecated
public class GetOpenSourceSummaryRequest {
    private long projectId;

    public GetOpenSourceSummaryRequest(long projectId) {
        this.projectId = projectId;
    }

    public long getProjectId() {
        return projectId;
    }

    public void setProjectId(long projectId) {
        this.projectId = projectId;
    }
}
