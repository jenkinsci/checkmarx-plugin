package com.checkmarx.jenkins.opensourceanalysis;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;


public class OSAFile implements Serializable{

    private static final long serialVersionUID = 1L;


    @JsonProperty("filename")
    private String fileName;
    @JsonProperty("sha1")
    private String sha1;

    public OSAFile(String fileName, String sha1) {
        this.fileName = fileName;
        this.sha1 = sha1;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getSha1() {
        return sha1;
    }

    public void setSha1(String sha1) {
        this.sha1 = sha1;
    }
}
