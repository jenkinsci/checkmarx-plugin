package com.checkmarx.jenkins.opensourceanalysis;

import com.checkmarx.jenkins.CxWebService;
import com.checkmarx.jenkins.OsaScanResult;
import com.checkmarx.jenkins.filesystem.FolderPattern;
import com.checkmarx.jenkins.logger.CxPluginLogger;
import hudson.FilePath;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

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
    private LibrariesAndCVEsExtractor librariesAndCVEsExtractor;
    private boolean runInstallBeforeScan;
    private ScanServiceTools scanServiceTools;

    public ScanService(ScanServiceTools scanServiceTools) {
        this.scanServiceTools = scanServiceTools;
        this.dependencyFolder = scanServiceTools.getDependencyFolder();
        this.webServiceClient = scanServiceTools.getWebServiceClient();
        this.workspace = scanServiceTools.getWorkspace();
        this.folderPattern = new FolderPattern(scanServiceTools.getRun(), scanServiceTools.getListener());
        this.scanResultsPresenter = new ScanResultsPresenter(scanServiceTools.getListener());
        this.scanSender = new ScanSender(scanServiceTools.getOsaScanClient(), scanServiceTools.getProjectId());
        this.librariesAndCVEsExtractor = new LibrariesAndCVEsExtractor(scanServiceTools.getOsaScanClient());
        this.logger = new CxPluginLogger(scanServiceTools.getListener());
        this.runInstallBeforeScan = scanServiceTools.isRunInstallBeforeScan();
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
            Properties scannerProperties = generateOSAScanConfiguration(combinedFilterPattern, dependencyFolder.getArchiveIncludePatterns(), runInstallBeforeScan);
            OsaScannerCallable scannerCallable = new OsaScannerCallable(scannerProperties, scanServiceTools.getListener());
            logger.info("Scanning for OSA compatible files");
            String osaDependenciesJson = workspace.act(scannerCallable);

            if (asynchronousScan) {
                logger.info(OSA_RUN_SUBMITTED);
                return scanSender.sendAsync(osaDependenciesJson, librariesAndCVEsExtractor);

            } else {
                logger.info(OSA_RUN_STARTED);
                osaScanResult = scanSender.sendOsaScanAndGetResults(osaDependenciesJson);
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

    private Properties generateOSAScanConfiguration(String filterPatterns, String archiveIncludes, boolean runInstallBeforeScan) {
        Properties ret = new Properties();
        List<String> inclusions = new ArrayList<String>();
        List<String> exclusions = new ArrayList<String>();
        String[] filters = filterPatterns.split("\\s*,\\s*"); //split by comma and trim (spaces + newline)
        for (String filter : filters) {
            if(StringUtils.isNotEmpty(filter)) {
                if (!filter.startsWith("!") ) {
                    inclusions.add(filter);
                } else if(filter.length() > 1){
                    filter = filter.substring(1); // Trim the "!"
                    exclusions.add(filter);
                }
            }
        }

        String includesString = String.join(",", inclusions);
        String excludesString = String.join(",", exclusions);

        if(StringUtils.isNotEmpty(includesString)) {
            ret.put("includes",includesString);
        }

        if(StringUtils.isNotEmpty(excludesString)) {
            ret.put("excludes",excludesString);
        }

        if(StringUtils.isNotEmpty(archiveIncludes)) {
            String[] archivePatterns = archiveIncludes.split("\\s*,\\s*"); //split by comma and trim (spaces + newline)
            for (int i = 0; i < archivePatterns.length; i++) {
                if(StringUtils.isNotEmpty(archivePatterns[i]) && archivePatterns[i].startsWith("*.")) {
                    archivePatterns[i] = "**/" + archivePatterns[i];
                }
            }
            archiveIncludes = String.join(",", archivePatterns);
            ret.put("archiveIncludes", archiveIncludes);
        } else {
            ret.put("archiveIncludes", "**/.*jar,**/*.war,**/*.ear,**/*.sca,**/*.gem,**/*.whl,**/*.egg,**/*.tar,**/*.tar.gz,**/*.tgz,**/*.zip,**/*.rar");
        }

        ret.put("archiveExtractionDepth", "4");

        if(runInstallBeforeScan) {
            ret.put("npm.runPreStep", "true");
            ret.put("bower.runPreStep", "true");
        }

        return ret;
    }
}
