package com.checkmarx.jenkins.opensourceanalysis;

import com.checkmarx.jenkins.CxWebService;
import com.checkmarx.jenkins.filesystem.zip.CxZip;
import com.checkmarx.jenkins.filesystem.FolderPattern;
import com.checkmarx.jenkins.web.model.GetOpenSourceSummaryResponse;
import hudson.FilePath;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import java.io.IOException;

/**
 * @author tsahi
 * @since 02/02/16
 */
public class ScanService {

    private static final String OSA_RUN_STARTED="OSA (open source analysis) Run has started";
    private static final String OSA_RUN_ENDED="OSA (open source analysis) Run has finished successfully";
    public static final String NO_LICENSE_ERROR = "Open Source Analysis License is not enabled for this project. Please contact your CxSAST Administrator";
    private DependencyFolder dependencyFolder;
    private transient Logger logger;
    private CxWebService webServiceClient;
    private final CxZip cxZip;
    private final FolderPattern folderPattern;
    private ScanResultsPresenter scanResultsPresenter;

    public ScanService(DependencyFolder dependencyFolder, Logger logger, CxWebService webServiceClient, CxZip cxZip, FolderPattern folderPattern, ScanResultsPresenter scanResultsPresenter) {
        this.dependencyFolder = dependencyFolder;
        this.logger = logger;
        this.webServiceClient = webServiceClient;
        this.cxZip = cxZip;
        this.folderPattern = folderPattern;
        this.scanResultsPresenter = scanResultsPresenter;
    }

    public void scan(ScanSender scanSender) {
        try{
            if (!isOsaConfigured()) {
                return;
            }

            if (!validLicense()){
                logger.error(NO_LICENSE_ERROR);
                return;
            }

            logger.info(OSA_RUN_STARTED);
            FilePath sourceCodeZip = zipOpenSourceCode();
            scanSender.send(sourceCodeZip);
            if (scanSender instanceof SynchronousScanSender ){
                GetOpenSourceSummaryResponse scanResults = ((SynchronousScanSender) scanSender).getScanResults();
                printResultsToOutput(scanResults);
                scanResultsPresenter.printResultsToOutput(scanResults);
            }
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

    private void printResultsToOutput(GetOpenSourceSummaryResponse results) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n").append("open source libraries: ").append(results.getTotal()).append("\n");
        sb.append("vulnerable and outdated: ").append(results.getVulnerableAndOutdated()).append("\n");
        sb.append("vulnerable and updated: ").append(results.getVulnerableAndUpdate()).append("\n");
        sb.append("high vulnerabilities: ").append(results.getHighVulnerabilities()).append("\n");
        sb.append("medium vulnerabilities: ").append(results.getMediumVulnerabilities()).append("\n");
        sb.append("low vulnerabilities: ").append(results.getLowVulnerabilities()).append("\n");
        sb.append("with no known vulnerabilities: ").append(results.getNoKnownVulnerabilities()).append("\n");
        sb.append("vulnerability score: ").append(results.getVulnerabilityScore()).append("\n");
        logger.info(sb.toString());
    }

}
