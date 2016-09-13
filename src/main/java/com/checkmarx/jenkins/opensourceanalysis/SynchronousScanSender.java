package com.checkmarx.jenkins.opensourceanalysis;

import com.checkmarx.jenkins.web.client.ScanClient;
import com.checkmarx.jenkins.web.model.GetOpenSourceSummaryRequest;
import com.checkmarx.jenkins.web.model.GetOpenSourceSummaryResponse;
import hudson.FilePath;
import org.apache.log4j.Logger;

import java.net.URI;

/**
 * Created by tsahib on 9/12/2016.
 */
public class SynchronousScanSender extends ScanSender {
    private transient Logger logger;
    private GetOpenSourceSummaryResponse scanResults;

    protected SynchronousScanSender(ScanClient scanClient, long projectId, Logger logger) {
        super(scanClient, projectId);
        this.logger = logger;
    }

    @Override
    public void send(FilePath sourceCodeZip) throws Exception {
        URI scanStatusUri = createScan(sourceCodeZip);
        waitForScanToFinish(scanStatusUri);
        scanResults = getOpenSourceSummary();
    }

    private void waitForScanToFinish(URI uri) throws InterruptedException {
        scanClient.waitForScanToFinish(uri);
    }

    private GetOpenSourceSummaryResponse getOpenSourceSummary() throws Exception {
        GetOpenSourceSummaryRequest summaryRequest = new GetOpenSourceSummaryRequest(projectId);
        return scanClient.getOpenSourceSummary(summaryRequest);
    }

    public GetOpenSourceSummaryResponse getScanResults() {
        return scanResults;
    }
}
