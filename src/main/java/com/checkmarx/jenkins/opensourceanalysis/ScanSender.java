package com.checkmarx.jenkins.opensourceanalysis;
import com.checkmarx.jenkins.OsaScanResult;
import com.checkmarx.jenkins.web.client.OsaScanClient;
import com.checkmarx.jenkins.web.model.CreateScanRequest;
import com.checkmarx.jenkins.web.model.CreateScanResponse;
import com.checkmarx.jenkins.web.model.GetOpenSourceSummaryResponse;
import com.checkmarx.jenkins.web.model.ScanDetails;
import hudson.FilePath;


/**
 * Created by tsahib on 9/12/2016.
 */
public class ScanSender {

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
        ScanDetails scanDetails = waitForScanToFinish(createScanResponse.getScanId());
        GetOpenSourceSummaryResponse getOpenSourceSummaryResponse = getOpenSourceSummary(createScanResponse.getScanId());
        osaScanResult.setOsaScanStartAndEndTimes(scanDetails);
        osaScanResult.setOsaResults(getOpenSourceSummaryResponse);
    }

    private CreateScanResponse createScan(FilePath zipFile) throws Exception {
        CreateScanRequest scanRequest = new CreateScanRequest(projectId, zipFile);
        return osaScanClient.createScanLargeFileWorkaround(scanRequest);
    }

    private ScanDetails waitForScanToFinish(String scanId) throws InterruptedException {
        return osaScanClient.waitForScanToFinish(scanId);
    }

    private GetOpenSourceSummaryResponse getOpenSourceSummary(String scanId) throws Exception {
        return osaScanClient.getOpenSourceSummary(scanId);
    }

}
