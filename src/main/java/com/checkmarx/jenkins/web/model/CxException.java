package com.checkmarx.jenkins.web.model;

/**
 * @author tsahi
 * @since 21/02/16
 */
public class CxException {
    private String message;
    private String messageDetails;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getMessageDetails() {
        return messageDetails;
    }

    public void setMessageDetails(String messageDetails) {
        this.messageDetails = messageDetails;
    }
}
