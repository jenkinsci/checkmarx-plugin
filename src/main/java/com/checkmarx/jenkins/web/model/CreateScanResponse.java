package com.checkmarx.jenkins.web.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.net.URI;

/**
 * Created by tsahib on 9/26/2016.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateScanResponse {
    private String scanId;

    public String getScanId() {
        return scanId;
    }

    public void setScanId(String scanId) {
        this.scanId = scanId;
    }
}
