package com.checkmarx.jenkins.opensourceanalysis;

import com.checkmarx.jenkins.CxWebService;
import com.checkmarx.jenkins.OsaScanResult;
import com.checkmarx.jenkins.filesystem.FolderPattern;
import com.checkmarx.jenkins.filesystem.zip.CxZip;
import hudson.FilePath;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * @author tsahi
 * @since 02/02/16
 */
public class ScanService {

    private static final Logger LOGGER = LogManager.getLogManager().getLogger("hudson.WebAppMain");

    private static final String OSA_RUN_STARTED = "OSA (open source analysis) Run has started";
    private static final String OSA_RUN_ENDED = "OSA (open source analysis) Run has finished successfully";
    private static final String OSA_RUN_SUBMITTED = "OSA (open source analysis) submitted successfully";
    public static final String NO_LICENSE_ERROR = "Open Source Analysis License is not enabled for this project. Please contact your CxSAST Administrator";
    private DependencyFolder dependencyFolder;
    private CxWebService webServiceClient;
    private final CxZip cxZip;
    private final FolderPattern folderPattern;
    private ScanResultsPresenter scanResultsPresenter;
    private ScanSender scanSender;
    private LibrariesAndCVEsExtractor librariesAndCVEsExtractor;


    public ScanService(ScanServiceTools scanServiceTools) {
        this.dependencyFolder = scanServiceTools.getDependencyFolder();
        this.webServiceClient = scanServiceTools.getWebServiceClient();
        this.cxZip = new CxZip(scanServiceTools.getBuild(), scanServiceTools.getListener());
        this.folderPattern = new FolderPattern(scanServiceTools.getBuild(), scanServiceTools.getListener());
        this.scanResultsPresenter = new ScanResultsPresenter();
        this.scanSender = new ScanSender(scanServiceTools.getOsaScanClient(), scanServiceTools.getProjectId());
        this.librariesAndCVEsExtractor = new LibrariesAndCVEsExtractor(scanServiceTools.getOsaScanClient());
    }

    public OsaScanResult scan(boolean asynchronousScan) {
        OsaScanResult osaScanResult = new OsaScanResult();
        try {
            if (!validLicense()) {
                LOGGER.severe(NO_LICENSE_ERROR);
                osaScanResult.setIsOsaReturnedResult(false);
                return osaScanResult;
            }

            FilePath sourceCodeZip = zipOpenSourceCode();
            if (asynchronousScan) {
                LOGGER.info(OSA_RUN_SUBMITTED);
                scanSender.sendAsync(sourceCodeZip);
                return null;
            } else {
                LOGGER.info(OSA_RUN_STARTED);
                scanSender.sendScanAndSetResults(sourceCodeZip, osaScanResult);
                LOGGER.info(OSA_RUN_ENDED);
                scanResultsPresenter.printResultsToOutput(osaScanResult.getGetOpenSourceSummaryResponse());
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Open Source Analysis failed:", e);
        }
        librariesAndCVEsExtractor.getAndSetLibrariesAndCVEs(osaScanResult);
        return osaScanResult;
    }

    private boolean validLicense() {
        return webServiceClient.isOsaLicenseValid();
    }

    private FilePath zipOpenSourceCode() throws IOException, InterruptedException {
        String combinedFilterPattern = folderPattern.generatePattern(dependencyFolder.getInclude(), dependencyFolder.getExclude());
        return cxZip.zipSourceCode(combinedFilterPattern);
    }
}
