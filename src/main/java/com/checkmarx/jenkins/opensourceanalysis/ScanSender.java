package com.checkmarx.jenkins.opensourceanalysis;
import com.checkmarx.jenkins.OsaScanResult;
import com.checkmarx.jenkins.web.client.OsaScanClient;
import com.checkmarx.jenkins.web.model.CreateScanRequest;
import com.checkmarx.jenkins.web.model.CreateScanResponse;
import com.checkmarx.jenkins.web.model.GetOpenSourceSummaryResponse;
import hudson.FilePath;


/**
 * Created by tsahib on 9/12/2016.
 */
public class ScanSender {

    //todo: make wait handler part of scan sender and move waiting logic out of client
    private OsaScanClient osaScanClient;
    private long projectId;

    public ScanSender(OsaScanClient scanClient, long projectId) {
        this.osaScanClient = scanClient;
        this.projectId = projectId;
    }

    public void sendAsync(FilePath sourceCodeZip) throws Exception {
        createScan(sourceCodeZip);
    }

    public void sendScanAndSetResults(FilePath sourceCodeZip, OsaScanResult osaScanResult) throws Exception {
        CreateScanResponse createScanResponse = createScan(sourceCodeZip);
        osaScanResult.setScanId(createScanResponse.getScanId());
        waitForScanToFinish(createScanResponse.getScanId());
        GetOpenSourceSummaryResponse getOpenSourceSummaryResponse = getOpenSourceSummary(createScanResponse.getScanId());
        osaScanResult.addOsaResults(getOpenSourceSummaryResponse);
    }

    private CreateScanResponse createScan(FilePath zipFile) throws Exception {
        CreateScanRequest anaReq = new CreateScanRequest(projectId, zipFile);
        return osaScanClient.createScan(anaReq);
    }

    private void waitForScanToFinish(String scanId) throws InterruptedException {
        osaScanClient.waitForScanToFinish(scanId);
    }

    private GetOpenSourceSummaryResponse getOpenSourceSummary(String scanId) throws Exception {
        return osaScanClient.getOpenSourceSummary(scanId);
    }

}
