package com.checkmarx.jenkins.opensourceanalysis;
import com.checkmarx.jenkins.web.client.ScanClient;
import com.checkmarx.jenkins.web.model.GetOpenSourceSummaryRequest;
import com.checkmarx.jenkins.web.model.GetOpenSourceSummaryResponse;
import com.checkmarx.jenkins.web.model.ScanRequest;
import hudson.FilePath;

import java.net.URI;


/**
 * Created by tsahib on 9/12/2016.
 */
public class ScanSender {
    private ScanClient scanClient;
    private long projectId;

    public ScanSender(ScanClient scanClient, long projectId) {
        this.scanClient = scanClient;
        this.projectId = projectId;
    }

    public void sendAsync(FilePath sourceCodeZip) throws Exception {
        createScan(sourceCodeZip);
    }

    public GetOpenSourceSummaryResponse send(FilePath sourceCodeZip) throws Exception {
        URI scanStatusUri = createScan(sourceCodeZip);
        waitForScanToFinish(scanStatusUri);
        GetOpenSourceSummaryResponse summary = getOpenSourceSummary();
        return summary;
    }

    private URI createScan(FilePath zipFile) throws Exception {
        ScanRequest anaReq = new ScanRequest(projectId, zipFile);
        return scanClient.createScan(anaReq);
    }

    private void waitForScanToFinish(URI uri) throws InterruptedException {
        scanClient.waitForScanToFinish(uri);
    }

    private GetOpenSourceSummaryResponse getOpenSourceSummary() throws Exception {
        GetOpenSourceSummaryRequest summaryRequest = new GetOpenSourceSummaryRequest(projectId);
        return scanClient.getOpenSourceSummary(summaryRequest);
    }
}
