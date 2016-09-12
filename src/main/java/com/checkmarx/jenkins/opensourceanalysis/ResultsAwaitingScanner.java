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
public class ResultsAwaitingScanner extends Scanner {
    private transient Logger logger;

    protected ResultsAwaitingScanner(ScanClient scanClient, long projectId, Logger logger) {
        super(scanClient, projectId);
        this.logger = logger;
    }

    public void scan(FilePath sourceCodeZip) throws Exception {
        URI scanStatusUri = createScan(sourceCodeZip);
        waitForScanToFinish(scanStatusUri);
        GetOpenSourceSummaryResponse summaryResponse = getOpenSourceSummary();
        printResultsToOutput(summaryResponse);
    }

    private void waitForScanToFinish(URI uri) throws InterruptedException {
        scanClient.waitForScanToFinish(uri);
    }

    private GetOpenSourceSummaryResponse getOpenSourceSummary() throws Exception {
        GetOpenSourceSummaryRequest summaryRequest = new GetOpenSourceSummaryRequest(projectId);
        return scanClient.getOpenSourceSummary(summaryRequest);
    }

    private void printResultsToOutput(GetOpenSourceSummaryResponse results) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n").append("open source libraries: ").append(results.getTotal()).append("\n");
        sb.append("vulnerable and outdated: ").append(results.getVulnerableAndOutdated()).append("\n");
        sb.append("vulnerable and updated: ").append(results.getVulnerableAndUpdate()).append("\n");
        sb.append("with no known vulnerabilities: ").append(results.getNoKnownVulnerabilities()).append("\n");
        sb.append("vulnerability score: ").append(results.getVulnerabilityScore()).append("\n");
        logger.info(sb.toString());
    }
}
