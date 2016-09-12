package com.checkmarx.jenkins.opensourceanalysis;

import com.checkmarx.jenkins.CxWebService;
import com.checkmarx.jenkins.filesystem.FolderPattern;
import com.checkmarx.jenkins.filesystem.zip.CxZip;
import com.checkmarx.jenkins.web.client.ScanClient;
import com.checkmarx.jenkins.web.model.ScanRequest;
import hudson.FilePath;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.net.URI;

/**
 * Created by tsahib on 9/12/2016.
 */
public abstract class Scanner {
    protected long projectId;
    protected ScanClient scanClient;

    protected Scanner(ScanClient scanClient, long projectId) {
        this.scanClient = scanClient;
        this.projectId = projectId;
    }

    abstract void scan(FilePath sourceCodeZip) throws Exception;

    protected URI createScan(FilePath zipFile) throws Exception {
        ScanRequest anaReq = new ScanRequest(projectId, zipFile);
        return scanClient.createScan(anaReq);
    }
}
