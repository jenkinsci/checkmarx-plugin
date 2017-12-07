package com.checkmarx.jenkins.opensourceanalysis;

import com.checkmarx.jenkins.CxConfig;
import com.checkmarx.jenkins.CxWebService;
import com.checkmarx.jenkins.OsaScanResult;
import com.checkmarx.jenkins.filesystem.FolderPattern;
import com.checkmarx.jenkins.logger.CxPluginLogger;
import hudson.FilePath;
import hudson.model.TaskListener;

import java.util.List;

/**
 * @author tsahi
 * @since 02/02/16
 */
public class ScanService {

    private transient CxPluginLogger logger;

    private static final String OSA_RUN_STARTED = "OSA (open source analysis) Run has started";
    private static final String OSA_RUN_ENDED = "OSA (open source analysis) Run has finished successfully";
    private static final String OSA_RUN_SUBMITTED = "OSA (open source analysis) submitted successfully";
    public static final String NO_LICENSE_ERROR = "Open Source Analysis License is not enabled for this project. Please contact your CxSAST Administrator";
    private DependencyFolder dependencyFolder;
    private CxWebService webServiceClient;
    private final FilePath workspace;
    private final FolderPattern folderPattern;
    private ScanResultsPresenter scanResultsPresenter;
    private ScanSender scanSender;
    private TaskListener taskListener;
    private LibrariesAndCVEsExtractor librariesAndCVEsExtractor;

    public ScanService(ScanServiceTools scanServiceTools) {
        this.dependencyFolder = scanServiceTools.getDependencyFolder();
        this.webServiceClient = scanServiceTools.getWebServiceClient();
        this.workspace = scanServiceTools.getWorkspace();
        this.folderPattern = new FolderPattern(scanServiceTools.getRun(), scanServiceTools.getListener());
        this.scanResultsPresenter = new ScanResultsPresenter(scanServiceTools.getListener());
        this.scanSender = new ScanSender(scanServiceTools.getOsaScanClient(), scanServiceTools.getProjectId());
        this.librariesAndCVEsExtractor = new LibrariesAndCVEsExtractor(scanServiceTools.getOsaScanClient());
        this.logger = new CxPluginLogger(scanServiceTools.getListener());
        this.taskListener = scanServiceTools.getListener();
    }

    public OsaScanResult scan(boolean asynchronousScan) {
        OsaScanResult osaScanResult;

        try {
            osaScanResult = new OsaScanResult();

            if (!validLicense()) {
                logger.error(NO_LICENSE_ERROR);
                osaScanResult.setOsaLicense(false);
                return osaScanResult;
            }

            String combinedFilterPattern = folderPattern.generatePattern(dependencyFolder.getInclude(), dependencyFolder.getExclude());
            OSAScanner osaScanner = new OSAScanner(CxConfig.getOsaSupportedExtensions(), CxConfig.getOsaExtractableExtensions(), combinedFilterPattern, taskListener);
            OsaScannerCallable scannerCallable = new OsaScannerCallable(osaScanner, taskListener);
            logger.info("Scanning for OSA compatible files");
            List<OSAFile> osaFileList = workspace.act(scannerCallable);// calls to osaScanner.scanFiles()
            logger.info("Found "+osaFileList.size()+" Compatible Files for OSA Scan");

            if (asynchronousScan) {
                logger.info(OSA_RUN_SUBMITTED);
                return scanSender.sendAsync(osaFileList, librariesAndCVEsExtractor);

            } else {
                logger.info(OSA_RUN_STARTED);
                osaScanResult = scanSender.sendOsaScanAndGetResults(osaFileList);
                osaScanResult.setOsaLicense(true);
                logger.info(OSA_RUN_ENDED);
                scanResultsPresenter.printResultsToOutput(osaScanResult.getOpenSourceSummaryResponse());
            }

        } catch (Exception e) {
            logger.error("Open Source Analysis failed: " + e.getMessage(), e);
            return null;
        }
        librariesAndCVEsExtractor.getAndSetLibrariesAndCVEsToScanResult(osaScanResult);
        return osaScanResult;
    }

    private boolean validLicense() {
        return webServiceClient.isOsaLicenseValid();
    }


}
