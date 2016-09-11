package com.checkmarx.jenkins.opensourceanalysis;

import com.checkmarx.jenkins.CxWebService;
import com.checkmarx.jenkins.filesystem.zip.CxZip;
import com.checkmarx.jenkins.filesystem.FolderPattern;
import com.checkmarx.jenkins.web.client.RestClient;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import java.io.IOException;
import java.net.URI;
import java.util.regex.Pattern;

import com.checkmarx.jenkins.web.model.ScanRequest;
import com.checkmarx.jenkins.web.model.GetOpenSourceSummaryRequest;
import com.checkmarx.jenkins.web.model.getOpenSourceSummaryResponse;

/**
 * @author tsahi
 * @since 02/02/16
 */
public class OpenSourceAnalyzerService {

    private static final String OSA_RUN_STARTED="OSA (open source analysis) Run has started";
    private static final String OSA_RUN_ENDED="OSA (open source analysis) Run has finished successfully";
    private DependencyFolder dependencyFolder;
    private AbstractBuild<?, ?> build;
    private RestClient restClient;
    private long projectId;
    private transient Logger logger;
    private static final Pattern PARAM_LIST_SPLIT_PATTERN = Pattern.compile(",|$", Pattern.MULTILINE);
    private CxWebService webServiceClient;
    private final CxZip cxZip;
    private final FolderPattern folderPattern;
    public static final String NO_LICENSE_ERROR = "Open Source Analysis License is not enabled for this project. Please contact your CxSAST Administrator";

    public OpenSourceAnalyzerService(final AbstractBuild<?, ?> build, DependencyFolder dependencyFolder, RestClient restClient, long projectId, Logger logger, CxWebService webServiceClient, CxZip cxZip, FolderPattern folderPattern) {
        this.dependencyFolder = dependencyFolder;
        this.build = build;
        this.restClient = restClient;
        this.projectId = projectId;
        this.logger = logger;
        this.webServiceClient = webServiceClient;
        this.cxZip = cxZip;
        this.folderPattern = folderPattern;
    }

    public void analyze() throws IOException, InterruptedException {
        try{
            if (!isOsaConfigured()) {
                return;
            }

            if (!validLicense()){
                logger.error(NO_LICENSE_ERROR);
                return;
            }

            logger.info(OSA_RUN_STARTED);
            FilePath zipFile = zipOpenSourceCode();
            URI scanStatusUri = createScan(zipFile);
            waitForScanToFinish(scanStatusUri);
            getOpenSourceSummaryResponse summaryResponse = getOpenSourceSummary();
            printResultsToOutput(summaryResponse);
            logger.info(OSA_RUN_ENDED);
        }
        catch (Exception e){
            logger.error("Open Source Analysis failed:", e);
        }
    }

    private boolean isOsaConfigured() {
        return ! StringUtils.isEmpty(dependencyFolder.getInclude());
    }

    private boolean validLicense() {
        return webServiceClient.isOsaLicenseValid();
    }

    private FilePath zipOpenSourceCode() throws IOException, InterruptedException {
        String combinedFilterPattern = folderPattern.generatePattern(dependencyFolder.getInclude(), dependencyFolder.getExclude());
        return cxZip.zipSourceCode(combinedFilterPattern);
    }

    private URI createScan(FilePath zipFile) throws Exception {
        ScanRequest anaReq = new ScanRequest(projectId, zipFile);
        return restClient.createScan(anaReq);
    }

    private void waitForScanToFinish(URI uri) throws InterruptedException {
        restClient.waitForScanToFinish(uri);
    }

    private getOpenSourceSummaryResponse getOpenSourceSummary() throws Exception {
        GetOpenSourceSummaryRequest summaryRequest = new GetOpenSourceSummaryRequest(projectId);
        return restClient.getOpenSourceSummary(summaryRequest);
    }

    private void printResultsToOutput(getOpenSourceSummaryResponse results) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n").append("open source libraries: ").append(results.getTotal()).append("\n");
        sb.append("vulnerable and outdated: ").append(results.getVulnerableAndOutdated()).append("\n");
        sb.append("vulnerable and updated: ").append(results.getVulnerableAndUpdate()).append("\n");
        sb.append("with no known vulnerabilities: ").append(results.getNoKnownVulnerabilities()).append("\n");
        sb.append("vulnerability score: ").append(results.getVulnerabilityScore()).append("\n");
        logger.info(sb.toString());
    }
}
