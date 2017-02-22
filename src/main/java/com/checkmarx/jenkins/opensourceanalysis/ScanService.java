package com.checkmarx.jenkins.opensourceanalysis;

import com.checkmarx.jenkins.CxConfig;
import com.checkmarx.jenkins.CxWebService;
import com.checkmarx.jenkins.filesystem.FolderPattern;
import com.checkmarx.jenkins.filesystem.zip.CxZip;
import com.checkmarx.jenkins.filesystem.zip.Zipper;
import com.checkmarx.jenkins.logger.CxPluginLogger;
import com.checkmarx.jenkins.web.model.GetOpenSourceSummaryResponse;
import hudson.FilePath;
import org.apache.commons.io.FileUtils;

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
    private final CxZip cxZip;
    private final FolderPattern folderPattern;
    private ScanResultsPresenter scanResultsPresenter;
    private ScanSender scanSender;

    public ScanService(DependencyFolder dependencyFolder, CxPluginLogger cxPluginLogger, CxWebService webServiceClient, CxZip cxZip, FolderPattern folderPattern, ScanResultsPresenter scanResultsPresenter, ScanSender scanSender) {
        this.dependencyFolder = dependencyFolder;
        this.webServiceClient = webServiceClient;
        this.cxZip = cxZip;
        this.folderPattern = folderPattern;
        this.scanResultsPresenter = scanResultsPresenter;
        this.scanSender = scanSender;
        this.logger = cxPluginLogger;
    }

    public GetOpenSourceSummaryResponse scan(boolean asynchronousScan){
        GetOpenSourceSummaryResponse scanResults = null;

        try {
            if (!validLicense()) {
                logger.error(NO_LICENSE_ERROR);
                return scanResults;
            }
            FilePath sourceCodeZip = zipOpenSourceCode();
            if (asynchronousScan) {
                logger.info(OSA_RUN_SUBMITTED);
                scanSender.sendAsync(sourceCodeZip);
            } else {
                logger.info(OSA_RUN_STARTED);
                scanResults = scanSender.send(sourceCodeZip);
                logger.info(OSA_RUN_ENDED);
                scanResultsPresenter.printResultsToOutput(scanResults);
            }
        } catch (Zipper.MaxZipSizeReached zipSizeReached) {
            exposeZippingLogToJobConsole(zipSizeReached);
            logger.error("Open Source Analysis failed: When zipping file " + zipSizeReached.getCurrentZippedFileName() + ", reached maximum upload size limit of "
                    + FileUtils.byteCountToDisplaySize(CxConfig.maxOSAZipSize()) + "\n");
        } catch (Zipper.NoFilesToZip noFilesToZip) {
            exposeZippingLogToJobConsole(noFilesToZip);
            logger.error("Open Source Analysis failed: No files to scan");
        } catch (Zipper.ZipperException zipException) {
            exposeZippingLogToJobConsole(zipException);
            logger.error("Open Source Analysis failed: " + zipException.getMessage(), zipException);
        } catch (Exception e) {
            logger.error("Open Source Analysis failed: "+e.getMessage(), e);
        }
        return scanResults;
    }

    private boolean validLicense() {
        return webServiceClient.isOsaLicenseValid();
    }

    private FilePath zipOpenSourceCode() throws Exception {
        String combinedFilterPattern = folderPattern.generatePattern(dependencyFolder.getInclude(), dependencyFolder.getExclude());
        return cxZip.zipSourceCode(combinedFilterPattern);
    }

    private void exposeZippingLogToJobConsole(Zipper.ZipperException zipperException){
        logger.info(zipperException.getZippingDetails().getZippingLog());
    }
}
