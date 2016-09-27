package com.checkmarx.jenkins.web.model;

import hudson.FilePath;

import javax.xml.bind.annotation.XmlElement;
import java.util.List;


/**
 */
public class CreateScanRequest {

    private long projectId;
    private FilePath zipFile;
    public static final int JENKINS_ORIGIN = 1;

    public CreateScanRequest(long projectId, FilePath zipFile){
        this.projectId = projectId;
        this.zipFile = zipFile;
    }

    public long getProjectId(){
        return this.projectId;
    }

    public FilePath getZipFile(){
        return this.zipFile;
    }
}