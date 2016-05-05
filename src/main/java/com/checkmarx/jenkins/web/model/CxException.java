package com.checkmarx.jenkins.web.model;

/**
 * @author tsahi
 * @since 21/02/16
 */
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
