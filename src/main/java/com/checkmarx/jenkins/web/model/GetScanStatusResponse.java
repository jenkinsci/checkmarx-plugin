package com.checkmarx.jenkins.web.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.net.URI;


/**
 * Created by tsahib on 9/27/2016.
 */
@Deprecated
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetScanStatusResponse {
    private int status;
    private URI link;
    private String message;

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public URI getLink() {
        return link;
    }

    public void setLink(URI link) {
        this.link = link;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
