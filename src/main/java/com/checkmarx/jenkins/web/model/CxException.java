package com.checkmarx.jenkins.web.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * @author tsahi
 * @since 21/02/16
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CxException {
    private String messageCode;
    private String messageDetails;

    public String getMessageCode() {
        return messageCode;
    }

    public void setMessageCode(String message) {
        this.messageCode = message;
    }

    public String getMessageDetails() {
        return messageDetails;
    }

    public void setMessageDetails(String messageDetails) {
        this.messageDetails = messageDetails;
    }
}
