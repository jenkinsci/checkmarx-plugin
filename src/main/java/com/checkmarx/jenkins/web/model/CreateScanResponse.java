package com.checkmarx.jenkins.web.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.net.URI;

/**
 * Created by tsahib on 9/26/2016.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateScanResponse {
    private String scanId;
    private URI link;

    public CreateScanResponse() {
    }

    public CreateScanResponse(String scanId, URI link) {
        this.scanId = scanId;
        this.link = link;
    }

    public String getScanId() {
        return scanId;
    }

    public void setScanId(String scanId) {
        this.scanId = scanId;
    }

    public URI getLink() {
        return link;
    }

    public void setLink(URI link) {
        this.link = link;
    }
}
