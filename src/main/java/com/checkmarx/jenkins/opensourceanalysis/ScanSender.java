package com.checkmarx.jenkins.opensourceanalysis;
import com.checkmarx.jenkins.web.client.ScanClient;
import com.checkmarx.jenkins.web.model.ScanRequest;
import hudson.FilePath;

import java.net.URI;


/**
 * Created by tsahib on 9/12/2016.
 */
public class ScanSender {
    protected ScanClient scanClient;
    protected long projectId;

    public ScanSender(ScanClient scanClient, long projectId) {
        this.scanClient = scanClient;
        this.projectId = projectId;
    }

    public void send(FilePath sourceCodeZip) throws Exception {
        createScan(sourceCodeZip);
    }

    protected URI createScan(FilePath zipFile) throws Exception {
        ScanRequest anaReq = new ScanRequest(projectId, zipFile);
        return scanClient.createScan(anaReq);
    }
}
