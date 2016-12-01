package com.checkmarx.jenkins;

import com.checkmarx.components.zipper.Zipper;
import com.checkmarx.jenkins.filesystem.FolderPattern;
import com.checkmarx.jenkins.filesystem.zip.CxZip;
import com.checkmarx.jenkins.opensourceanalysis.DependencyFolder;
import com.checkmarx.jenkins.opensourceanalysis.ScanResultsPresenter;
import com.checkmarx.jenkins.opensourceanalysis.ScanSender;
import com.checkmarx.jenkins.opensourceanalysis.ScanService;
import com.checkmarx.jenkins.web.client.ScanClient;
import com.checkmarx.jenkins.web.contracts.ProjectContract;
import com.checkmarx.jenkins.web.model.AuthenticationRequest;
import com.checkmarx.jenkins.web.model.GetOpenSourceSummaryResponse;
import com.checkmarx.ws.CxJenkinsWebService.*;
import hudson.*;
import hudson.console.HyperlinkNote;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.triggers.SCMTrigger;
import hudson.util.ComboBoxModel;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import net.sf.json.JSONObject;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.xml.ws.WebServiceException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URLDecoder;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The main entry point for Checkmarx plugin. This class implements the Builder
 * build stage that scans the source code.
 *
 * @author Denis Krivitski
 * @since 3/10/13
 */

public class CxScanBuilder extends Builder {


    //////////////////////////////////////////////////////////////////////////////////////
    // Persistent plugin configuration parameters
    //////////////////////////////////////////////////////////////////////////////////////

    private boolean useOwnServerCredentials;

    @Nullable
    private String serverUrl;
    @Nullable
    private String username;
    @Nullable
    private String password;
    @Nullable
    private String projectName;
    @Nullable
    private String groupId;
    @Nullable
    private long projectId;

    @Nullable
    private String preset;
    private boolean presetSpecified;
    @Nullable
    private String excludeFolders;
    @Nullable
    private String filterPattern;

    private boolean incremental;
    private boolean fullScansScheduled;
    private int fullScanCycle;

    private boolean isThisBuildIncremental;

    @Nullable
    private String sourceEncoding;
    @Nullable
    private String comment;

    private boolean skipSCMTriggers;
    private boolean waitForResultsEnabled;

    private boolean vulnerabilityThresholdEnabled;
    @Nullable
    private Integer highThreshold;
    @Nullable
    private Integer mediumThreshold;
    @Nullable
    private Integer lowThreshold;
    private boolean generatePdfReport;

    private boolean osaEnabled;
    @Nullable
    private Integer osaHighThreshold;
    @Nullable
    private Integer osaMediumThreshold;
    @Nullable
    private Integer osaLowThreshold;

    @Nullable
    private String includeOpenSourceFolders;
    @Nullable
    private String excludeOpenSourceFolders;

    //////////////////////////////////////////////////////////////////////////////////////
    // Private variables
    //////////////////////////////////////////////////////////////////////////////////////
    static {
        BasicConfigurator.configure();  // Set the log4j system to log to console
    }

    // Kept for backward compatibility with old serialized plugin configuration.
    private static transient Logger staticLogger;

    private static final transient Logger LOGGER = Logger.getLogger(CxScanBuilder.class);

    private transient Logger instanceLogger = LOGGER; // Instance logger redirects to static logger until
    // it is initialized in perform method
    private transient FileAppender fileAppender;

    private JobStatusOnError jobStatusOnError;

    private String thresholdSettings;

    private Result vulnerabilityThresholdResult;


    private boolean avoidDuplicateProjectScans;

    public static final String PROJECT_STATE_URL_TEMPLATE = "/CxWebClient/portal#/projectState/{0}/Summary";
    public static final String ASYNC_MESSAGE = "CxSAST scan was run in asynchronous mode.\nRefer to the {0} for the scan results\n";

    public static final int MINIMUM_TIMEOUT_IN_MINUTES = 1;
    public static final String REPORTS_FOLDER = "Checkmarx\\Reports";

    private StringBuilder thresholdsError;

    //////////////////////////////////////////////////////////////////////////////////////
    // Constructors
    //////////////////////////////////////////////////////////////////////////////////////

    @DataBoundConstructor
    public CxScanBuilder(
            boolean useOwnServerCredentials, // NOSONAR
            @Nullable String serverUrl,
            @Nullable String username,
            @Nullable String password,
            String projectName,
            long projectId,
            String buildStep,
            @Nullable String groupId,
            @Nullable String preset,
            JobStatusOnError jobStatusOnError,
            boolean presetSpecified,
            @Nullable String excludeFolders,
            @Nullable String filterPattern,
            boolean incremental,
            boolean fullScansScheduled,
            int fullScanCycle,
            @Nullable String sourceEncoding,
            @Nullable String comment,
            boolean skipSCMTriggers,
            boolean waitForResultsEnabled,
            boolean vulnerabilityThresholdEnabled,
            @Nullable Integer highThreshold,
            @Nullable Integer mediumThreshold,
            @Nullable Integer lowThreshold,
            boolean osaEnabled,
            @Nullable Integer osaHighThreshold,
            @Nullable Integer osaMediumThreshold,
            @Nullable Integer osaLowThreshold,
            boolean generatePdfReport,
            String thresholdSettings,
            Result vulnerabilityThresholdResult,
            @Nullable String includeOpenSourceFolders,
            @Nullable String excludeOpenSourceFolders,
            boolean avoidDuplicateProjectScans) {
        this.useOwnServerCredentials = useOwnServerCredentials;
        this.serverUrl = serverUrl;
        this.username = username;
        this.password = Secret.fromString(password).getEncryptedValue();
        // Workaround for compatibility with Conditional BuildStep Plugin
        this.projectName = (projectName == null) ? buildStep : projectName;
        this.projectId = projectId;
        this.groupId = groupId;
        this.preset = preset;
        this.jobStatusOnError = jobStatusOnError;
        this.presetSpecified = presetSpecified;
        this.excludeFolders = excludeFolders;
        this.filterPattern = filterPattern;
        this.incremental = incremental;
        this.fullScansScheduled = fullScansScheduled;
        this.fullScanCycle = fullScanCycle;
        this.sourceEncoding = sourceEncoding;
        this.comment = comment;
        this.skipSCMTriggers = skipSCMTriggers;
        this.waitForResultsEnabled = waitForResultsEnabled;
        this.vulnerabilityThresholdEnabled = vulnerabilityThresholdEnabled;
        this.highThreshold = highThreshold;
        this.mediumThreshold = mediumThreshold;
        this.lowThreshold = lowThreshold;
        this.osaEnabled = osaEnabled;
        this.osaHighThreshold = osaHighThreshold;
        this.osaMediumThreshold = osaMediumThreshold;
        this.osaLowThreshold = osaLowThreshold;
        this.generatePdfReport = generatePdfReport;
        this.includeOpenSourceFolders = includeOpenSourceFolders;
        this.excludeOpenSourceFolders = excludeOpenSourceFolders;
        this.thresholdSettings = thresholdSettings;
        this.vulnerabilityThresholdResult = vulnerabilityThresholdResult;
        this.avoidDuplicateProjectScans = avoidDuplicateProjectScans;
        init();
    }

    private void init() {
        updateJobOnGlobalConfigChange();
    }

    private void updateJobOnGlobalConfigChange() {
        if (!getDescriptor().isForcingVulnerabilityThresholdEnabled() && shouldUseGlobalThreshold()) {
            vulnerabilityThresholdEnabled = false;
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////
    // Configuration fields getters
    //////////////////////////////////////////////////////////////////////////////////////


    public boolean isUseOwnServerCredentials() {
        return useOwnServerCredentials;
    }

    @Nullable
    public String getServerUrl() {
        return serverUrl;
    }

    @Nullable
    public String getUsername() {
        return username;
    }

    @Nullable
    public String getPassword() {
        return password;
    }

    @Nullable
    public String getPasswordPlainText() {
        return Secret.fromString(password).getPlainText();
    }

    @Nullable
    public String getProjectName() {
        return projectName;
    }

    // Workaround for compatibility with Conditional BuildStep Plugin
    @Nullable
    public String getBuildStep() {
        return projectName;
    }

    @Nullable
    public String getGroupId() {
        return groupId;
    }

    @Nullable
    public String getPreset() {
        return preset;
    }

    public boolean isPresetSpecified() {
        return presetSpecified;
    }

    @Nullable
    public String getExcludeFolders() {
        return excludeFolders;
    }

    @Nullable
    public String getFilterPattern() {
        return filterPattern;
    }

    public boolean isIncremental() {
        return incremental;
    }

    public boolean isFullScansScheduled() {
        return fullScansScheduled;
    }

    public int getFullScanCycle() {
        return fullScanCycle;
    }

    @Nullable
    public String getSourceEncoding() {
        return sourceEncoding;
    }

    @Nullable
    public String getComment() {
        return comment;
    }

    public JobStatusOnError getJobStatusOnError() {
        return (null == jobStatusOnError) ? JobStatusOnError.GLOBAL : jobStatusOnError;
    }

    public boolean isSkipSCMTriggers() {
        return skipSCMTriggers;
    }

    public boolean isWaitForResultsEnabled() {
        return waitForResultsEnabled;
    }

    public boolean isVulnerabilityThresholdEnabled() {
        updateJobOnGlobalConfigChange();
        return vulnerabilityThresholdEnabled;
    }

    public Integer getHighThreshold() {
        return highThreshold;
    }

    public Integer getMediumThreshold() {
        return mediumThreshold;
    }

    public Integer getLowThreshold() {
        return lowThreshold;
    }

    public boolean isOsaEnabled() {
        return osaEnabled;
    }

    public void setOsaEnabled(boolean osaEnabled) {
        this.osaEnabled = osaEnabled;
    }

    @Nullable
    public Integer getOsaHighThreshold() {
        return osaHighThreshold;
    }

    public void setOsaHighThreshold(Integer osaHighThreshold) {
        this.osaHighThreshold = osaHighThreshold;
    }

    @Nullable
    public Integer getOsaMediumThreshold() {
        return osaMediumThreshold;
    }

    public void setOsaMediumThreshold(Integer osaMediumThreshold) {
        this.osaMediumThreshold = osaMediumThreshold;
    }

    @Nullable
    public Integer getOsaLowThreshold() {
        return osaLowThreshold;
    }

    public void setOsaLowThreshold(Integer osaLowThreshold) {
        this.osaLowThreshold = osaLowThreshold;
    }

    @Nullable
    public String getExcludeOpenSourceFolders() {
        return excludeOpenSourceFolders;
    }

    @Nullable
    public String getIncludeOpenSourceFolders() {
        return includeOpenSourceFolders;
    }

    public boolean isGeneratePdfReport() {
        return generatePdfReport;
    }

    public boolean isAvoidDuplicateProjectScans() {
        return avoidDuplicateProjectScans;
    }

    public void setThresholdSettings(String thresholdSettings) {
        this.thresholdSettings = thresholdSettings;
    }

    public String getThresholdSettings() {
        return thresholdSettings;
    }

    public void setVulnerabilityThresholdResult(Result result) {
        this.vulnerabilityThresholdResult = result;
    }

    public Result getVulnerabilityThresholdResult() {
        return vulnerabilityThresholdResult;
    }

    @Override
    public boolean perform(final AbstractBuild<?, ?> build,
                           final Launcher launcher,
                           final BuildListener listener) throws InterruptedException, IOException {

        final DescriptorImpl descriptor = getDescriptor();

        CxWSResponseRunID cxWSResponseRunID = null;
        CxWebService cxWebService = null;
        CxWSCreateReportResponse reportResponse = null;

        try {
            File checkmarxBuildDir = new File(build.getRootDir(), "checkmarx");
            checkmarxBuildDir.mkdir();

            initLogger(checkmarxBuildDir, listener, instanceLoggerSuffix(build));

            instanceLogger.info("Checkmarx Jenkins plugin version: " + CxConfig.version());
            printConfiguration(descriptor);

            if (isSkipScan(build)) {
                instanceLogger.info("Checkmarx scan skipped since the build was triggered by SCM. " +
                        "Visit plugin configuration page to disable this skip.");
                return true;
            }
            final String serverUrlToUse = isUseOwnServerCredentials() ? getServerUrl() : descriptor.getServerUrl();
            final String usernameToUse = isUseOwnServerCredentials() ? getUsername() : descriptor.getUsername();
            final String passwordToUse = isUseOwnServerCredentials() ? getPasswordPlainText() : descriptor.getPasswordPlainText();

            String serverUrlToUseNotNull = serverUrlToUse != null ? serverUrlToUse : "";

            cxWebService = new CxWebService(serverUrlToUseNotNull, instanceLoggerSuffix(build));
            cxWebService.login(usernameToUse, passwordToUse);

            instanceLogger.info("Checkmarx server login successful");

            setProjectId(build, listener, cxWebService);

            if (needToAvoidDuplicateProjectScans(cxWebService)) {
                instanceLogger.info("\nAvoid duplicate project scans in queue\n");
                return true;
            }

            cxWSResponseRunID = submitScan(build, cxWebService, listener);
            setProjectId(build, listener, cxWebService);

            boolean shouldRunAsynchronous = scanShouldRunAsynchronous(descriptor);
            if (shouldRunAsynchronous) {
                logAsyncMessage(serverUrlToUse);
                addScanResultAction(build, serverUrlToUse, shouldRunAsynchronous, null);
                if (osaEnabled) {
                    analyzeOpenSources(build, serverUrlToUseNotNull, usernameToUse, passwordToUse, projectId, cxWebService, listener, shouldRunAsynchronous);
                }
                return true;
            }

            long scanId = cxWebService.trackScanProgress(cxWSResponseRunID, usernameToUse, passwordToUse, descriptor.getScanTimeOutEnabled(), descriptor.getScanTimeoutDuration());

            if (scanId == 0) {
                build.setResult(Result.UNSTABLE);
                return true;
            }

            reportResponse = cxWebService.generateScanReport(scanId, CxWSReportType.XML);
            File xmlReportFile = new File(checkmarxBuildDir, "ScanReport.xml");
            cxWebService.retrieveScanReport(reportResponse.getID(), xmlReportFile, CxWSReportType.XML);

            if (generatePdfReport) {
                reportResponse = cxWebService.generateScanReport(scanId, CxWSReportType.PDF);
                File pdfReportFile = new File(checkmarxBuildDir, CxScanResult.PDF_REPORT_NAME);
                cxWebService.retrieveScanReport(reportResponse.getID(), pdfReportFile, CxWSReportType.PDF);
            }



            CxScanResult cxScanResult = addScanResultAction(build, serverUrlToUse, shouldRunAsynchronous, xmlReportFile);

            // Set scan results to environment
            EnvVarAction envVarAction = new EnvVarAction();
            envVarAction.setCxSastResults(cxScanResult);
            build.addAction(envVarAction);

            //CxSAST Thresholds
            thresholdsError = new StringBuilder();
            ThresholdConfig thresholdConfig = createThresholdConfig();

            boolean isSASTThresholdFailedTheBuild = ((descriptor.isForcingVulnerabilityThresholdEnabled() && descriptor.isLockVulnerabilitySettings()) || isVulnerabilityThresholdEnabled())
                    && isThresholdCrossed(thresholdConfig, cxScanResult.getHighCount(), cxScanResult.getMediumCount(), cxScanResult.getLowCount(), "CxSAST ");
            printScanResult(cxScanResult);

            //OSA scan
            boolean isOSAThresholdFailedTheBuild = false;
            if (osaEnabled) {
                GetOpenSourceSummaryResponse osaResults = analyzeOpenSources(build, serverUrlToUseNotNull, usernameToUse, passwordToUse, projectId, cxWebService, listener, shouldRunAsynchronous);
                ThresholdConfig osaThresholdConfig = createOsaThresholdConfig();
                //retrieve osa scan results pdf + html
                getOSAReports(serverUrlToUseNotNull, usernameToUse, passwordToUse, checkmarxBuildDir);


                //OSA Threshold
                isOSAThresholdFailedTheBuild = osaResults != null && ((descriptor.isForcingVulnerabilityThresholdEnabled() && descriptor.isLockVulnerabilitySettings()) || isVulnerabilityThresholdEnabled())
                        && isThresholdCrossed(osaThresholdConfig, osaResults.getHighCount(), osaResults.getMediumCount(), osaResults.getLowCount(), "OSA ");
            }


//            generateHtmlReport(build, checkmarxBuildDir);
            instanceLogger.info("Copying reports to workspace");
            copyReportsToWorkspace(build, checkmarxBuildDir);

            //If one of the scan's threshold was crossed - fail the build
            if (isSASTThresholdFailedTheBuild || isOSAThresholdFailedTheBuild) {
                build.setResult(thresholdConfig.getBuildStatus());
                instanceLogger.info("*************************");
                instanceLogger.info("The Build Failed due to: ");
                instanceLogger.info("*************************");
                String[] lines = thresholdsError.toString().split("\\n");
                for (String s : lines) {
                    instanceLogger.info(s);
                }
                instanceLogger.info("---------------------------------------------------------------------");
            }

            return true;
        } catch (IOException | WebServiceException e) {
            if (useUnstableOnError(descriptor)) {
                build.setResult(Result.UNSTABLE);
                instanceLogger.error(e.getMessage(), e);
                return true;
            } else {
                throw e;
            }
        } catch (InterruptedException e) {
            if (reportResponse != null) {
                instanceLogger.error("Cancelling report generation on the Checkmarx server...");
                cxWebService.cancelScanReport(reportResponse.getID());
            } else if (cxWSResponseRunID != null) {
                instanceLogger.error("Cancelling scan on the Checkmarx server...");
                cxWebService.cancelScan(cxWSResponseRunID.getRunId());
            }
            throw e;
        } finally {
            closeLogger();
        }
    }

//    private void generateHtmlReport(AbstractBuild<?, ?> build, File checkmarxBuildDir) {
//
//        String linkToResults = Jenkins.getInstance().getRootUrl() +  build.getUrl() + "checkmarx/";
//        String linkToPDF = Jenkins.getInstance().getRootUrl() + build.getUrl() + "checkmarx/pdfReport";
//        String html = null;
//        try {
//            html = IOUtils.toString(this.getClass().getClassLoader().getResourceAsStream("com/checkmarx/jenkins/CxScanResult/summary-section.html"));
//            html = html.replace("#RESULTS_LINK#", linkToResults).replace("#PDF_REPORT_LINK#", linkToPDF);
//            FileUtils.writeStringToFile(new File(checkmarxBuildDir, "report.html"), html);
//        } catch (IOException e) {
//            instanceLogger.warn("fail to generate html report", e);
//
//        }
//
//    }

    private void getOSAReports(String serverUrl, String username, String password, File checkmarxBuildDir) {
        instanceLogger.info("retrieving osa report files");
        AuthenticationRequest authReq = new AuthenticationRequest(username, password);
        ScanClient scanClient = new ScanClient(serverUrl, authReq);
        String osaScanHtmlResults = scanClient.getOSAScanHtmlResults(projectId);
        File osaHtmlReport = new File(checkmarxBuildDir, "OSAReport.html");
        try {
            FileUtils.writeStringToFile(osaHtmlReport, osaScanHtmlResults);
        } catch (IOException e) {
            instanceLogger.warn("fail to write osa html report to ["+osaHtmlReport.getAbsolutePath()+"]");
        }
        instanceLogger.info("osa report file ["+osaHtmlReport.getAbsolutePath()+"] generated successfully");

        byte[] osaScanPdfResults = scanClient.getOSAScanPdfResults(projectId);
        File osaPdfReport = new File(checkmarxBuildDir, "OSAReport.pdf");
        try {
            FileUtils.writeByteArrayToFile(osaPdfReport, osaScanPdfResults);
        } catch (IOException e) {
            instanceLogger.warn("fail to write osa pdf report to ["+osaHtmlReport.getAbsolutePath()+"]");
        }
        instanceLogger.info("osa report file ["+osaHtmlReport.getAbsolutePath()+"] generated successfully");



    }

    private void printConfiguration(DescriptorImpl descriptor) {
        StringBuilder sb = new StringBuilder();
        boolean useGlobalThreshold = shouldUseGlobalThreshold();
        sb.append("----------------------------Configurations:-----------------------------").append("\n");
        sb.append("username: ").append(getUsername()).append("\n");
        sb.append("url: ").append(getServerUrl()).append("\n");
        sb.append("projectName: ").append(getProjectName()).append("\n");
        //sb.append("preset: ").append(getPreset()).append("\n");
        sb.append("isIncrementalScan: ").append(isIncremental()).append("\n");
        sb.append("folderExclusions: ").append(getExcludeFolders()).append("\n");
        sb.append("isSynchronous: ").append(isWaitForResultsEnabled()).append("\n"); //TODO GLOBAL
        sb.append("generatePDFReport: ").append(isGeneratePdfReport()).append("\n");
        if (useGlobalThreshold) {
            sb.append("highSeveritiesThreshold: ").append(descriptor.getHighThresholdEnforcement()).append("\n");
            sb.append("mediumSeveritiesThreshold: ").append(descriptor.getMediumThresholdEnforcement()).append("\n");
            sb.append("lowSeveritiesThreshold: ").append(descriptor.getLowThresholdEnforcement()).append("\n");
        } else if (isSASTThresholdEnabled()) {
            sb.append("highSeveritiesThreshold: ").append(getHighThreshold()).append("\n");
            sb.append("mediumSeveritiesThreshold: ").append(getMediumThreshold()).append("\n");
            sb.append("lowSeveritiesThreshold: ").append(getLowThreshold()).append("\n");
        }
        sb.append("osaEnabled: ").append(isOsaEnabled()).append("\n");
        if (osaEnabled) {
            sb.append("osaExclusions: ").append(getExcludeOpenSourceFolders()).append("\n");
            if (useGlobalThreshold) {
                sb.append("osaHighSeveritiesThreshold: ").append(descriptor.getOsaHighThresholdEnforcement()).append("\n");
                sb.append("osaMediumSeveritiesThreshold: ").append(descriptor.getOsaMediumThresholdEnforcement()).append("\n");
                sb.append("osaLowSeveritiesThreshold: ").append(descriptor.getOsaLowThresholdEnforcement()).append("\n");
            } else if (isOsaThresholdEnabled()) {
                sb.append("osaHighSeveritiesThreshold: ").append(getOsaHighThreshold()).append("\n");
                sb.append("osaMediumSeveritiesThreshold: ").append(getOsaMediumThreshold()).append("\n");
                sb.append("osaLowSeveritiesThreshold: ").append(getOsaLowThreshold()).append("\n");
            }
        }
        sb.append(" ------------------------------------------------------------------------").append("\n");

        instanceLogger.info(sb.toString());
    }

    private boolean isOsaThresholdEnabled() {
        return isVulnerabilityThresholdEnabled() && (getOsaLowThreshold() != null || getOsaMediumThreshold() != null || getOsaHighThreshold() != null);
    }
    private boolean isSASTThresholdEnabled() {
        return isVulnerabilityThresholdEnabled() && (getLowThreshold() != null || getMediumThreshold() != null || getHighThreshold() != null);
    }

    private void printScanResult(CxScanResult scanResult) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n---------------------Checkmarx Scan Results(CxSAST)-------------------------").append("\n");
        sb.append("High Severity Results: ").append(scanResult.getHighCount()).append("\n");
        sb.append("Medium Severity Results: ").append(scanResult.getMediumCount()).append("\n");
        sb.append("Low Severity Results: ").append(scanResult.getLowCount()).append("\n");
        sb.append("Info Severity Results: ").append(scanResult.getInfoCount()).append("\n");
        sb.append("----------------------------------------------------------------------------").append("\n");

        instanceLogger.info(sb.toString());
    }

    private void copyReportsToWorkspace(AbstractBuild<?, ?> build, File checkmarxBuildDir) {

        String remoteDirPath = build.getWorkspace().getRemote() + "\\" + REPORTS_FOLDER;

        Collection<File> files = FileUtils.listFiles(checkmarxBuildDir, null, true);
        FileInputStream fileInputStream = null;

        for (File file : files) {
            try {
                String remoteFilePath = remoteDirPath + "\\" + file.getName();
                instanceLogger.info("Copying file [" + file.getName() + "] to workspace [" + remoteFilePath + "]");
                FilePath remoteFile = new FilePath(build.getWorkspace().getChannel(), remoteFilePath);
                fileInputStream = new FileInputStream(file);
                remoteFile.copyFrom(fileInputStream);

            } catch (Exception e) {
                instanceLogger.warn("fail to copy file [" + file.getName() + "] to workspace", e);

            } finally {
                IOUtils.closeQuietly(fileInputStream);
            }
        }
    }

    @NotNull
    private CxScanResult addScanResultAction(AbstractBuild<?, ?> build, String serverUrlToUse, boolean shouldRunAsynchronous, File xmlReportFile) {
        CxScanResult cxScanResult = new CxScanResult(build, instanceLoggerSuffix(build), serverUrlToUse, projectId, shouldRunAsynchronous);
        if (xmlReportFile != null) {
            cxScanResult.readScanXMLReport(xmlReportFile);
        }
        build.addAction(cxScanResult);
        return cxScanResult;
    }

    private void setProjectId(AbstractBuild<?, ?> build, BuildListener listener, CxWebService cxWebService) throws IOException, InterruptedException {
        if (projectId == 0) {
            EnvVars env = build.getEnvironment(listener);
            ProjectContract projectContract = new ProjectContract(cxWebService);
            if (!projectContract.newProject(env.expand(getProjectName()), getGroupId())) {
                projectId = getProjectId(build, listener, cxWebService);
            }
        }
    }

    private void logAsyncMessage(String serverUrlToUse) {
        String projectStateUrl = serverUrlToUse + PROJECT_STATE_URL_TEMPLATE.replace("{0}", Long.toString(projectId));
        String projectStateLink = HyperlinkNote.encodeTo(projectStateUrl, "CxSAST Web");
        instanceLogger.info(ASYNC_MESSAGE.replace("{0}", projectStateLink));
    }

    private boolean scanShouldRunAsynchronous(DescriptorImpl descriptor) {
        return !isWaitForResultsEnabled() && !(descriptor.isForcingVulnerabilityThresholdEnabled() && descriptor.isLockVulnerabilitySettings());
    }

    private long getProjectId(AbstractBuild<?, ?> build, BuildListener listener, CxWebService cxWebService) throws IOException, InterruptedException {
        EnvVars env = build.getEnvironment(listener);
        final CliScanArgs cliScanArgs = createCliScanArgs(new byte[]{}, env);
        return cxWebService.getProjectId(cliScanArgs.getPrjSettings());
    }

    private GetOpenSourceSummaryResponse analyzeOpenSources(AbstractBuild<?, ?> build, String baseUri, String user, String password, long projectId, CxWebService webServiceClient, BuildListener listener, boolean shouldRunAsynchronous) throws IOException, InterruptedException {
        DependencyFolder folders = new DependencyFolder(includeOpenSourceFolders, excludeOpenSourceFolders);
        AuthenticationRequest authReq = new AuthenticationRequest(user, password);
        try (ScanClient scanClient = new ScanClient(baseUri, authReq)) {
            ScanService scanService = new ScanService(folders,
                    instanceLogger, webServiceClient, new CxZip(instanceLogger, build, listener), new FolderPattern(instanceLogger, build, listener), new ScanResultsPresenter(instanceLogger), new ScanSender(scanClient, projectId));
            return scanService.scan(shouldRunAsynchronous);
        }
    }

    private ThresholdConfig createThresholdConfig() {
        ThresholdConfig config = new ThresholdConfig();

        if (shouldUseGlobalThreshold()) {
            final DescriptorImpl descriptor = getDescriptor();
            config.setHighSeverity(descriptor.getHighThresholdEnforcement());
            config.setMediumSeverity(descriptor.getMediumThresholdEnforcement());
            config.setLowSeverity(descriptor.getLowThresholdEnforcement());
            config.setBuildStatus(Result.fromString(descriptor.getJobGlobalStatusOnThresholdViolation().name()));
        } else {
            config.setHighSeverity(getHighThreshold());
            config.setMediumSeverity(getMediumThreshold());
            config.setLowSeverity(getLowThreshold());
            config.setBuildStatus(getVulnerabilityThresholdResult());
        }

        return config;
    }

    private ThresholdConfig createOsaThresholdConfig() {
        ThresholdConfig config = new ThresholdConfig();
        if (shouldUseGlobalThreshold()) {
            final DescriptorImpl descriptor = getDescriptor();
            config.setHighSeverity(descriptor.getOsaHighThresholdEnforcement());
            config.setMediumSeverity(descriptor.getOsaMediumThresholdEnforcement());
            config.setLowSeverity(descriptor.getOsaLowThresholdEnforcement());
        } else {
        config.setHighSeverity(getOsaHighThreshold());
        config.setMediumSeverity(getOsaMediumThreshold());
        config.setLowSeverity(getOsaLowThreshold());
    }
        return config;
    }

    private boolean shouldUseGlobalThreshold() {
        final DescriptorImpl descriptor = getDescriptor();
        return descriptor.isForcingVulnerabilityThresholdEnabled() && descriptor.isLockVulnerabilitySettings() || "global".equals(getThresholdSettings());
    }


    /**
     * Checks if job should fail with <code>UNSTABLE</code> status instead of <code>FAILED</code>
     *
     * @param descriptor Descriptor of the current build step
     * @return if job should fail with <code>UNSTABLE</code> status instead of <code>FAILED</code>
     */
    private boolean useUnstableOnError(final DescriptorImpl descriptor) {
        return JobStatusOnError.UNSTABLE.equals(getJobStatusOnError())
                || (JobStatusOnError.GLOBAL.equals(getJobStatusOnError()) && JobGlobalStatusOnError.UNSTABLE.equals(descriptor
                .getJobGlobalStatusOnError()));
    }

    private boolean isThresholdCrossed(ThresholdConfig thresholdConfig, int high, int medium, int low, String scanType) {
        boolean ret = isThresholdCrossedByLevel(high, thresholdConfig.getHighSeverity(), scanType + "high");
        ret |= isThresholdCrossedByLevel(medium, thresholdConfig.getMediumSeverity(), scanType + "medium");
        ret |= isThresholdCrossedByLevel(low, thresholdConfig.getLowSeverity(), scanType + "low");
        return ret;
    }

    private boolean isThresholdCrossedByLevel(int result, Integer threshold, String vulnerabilityLevel) {
        boolean ret = false;
        if (threshold != null && result > threshold) {
            thresholdsError.append(vulnerabilityLevel + " Severity Results are Above Threshold. Results: " + result + ". Threshold: " + threshold + '\n');
            ret = true;
        }
        return ret;
    }

    private String instanceLoggerSuffix(final AbstractBuild<?, ?> build) {
        return build.getProject().getDisplayName() + "-" + build.getDisplayName();
    }

    private void initLogger(final File checkmarxBuildDir, final BuildListener listener, final String loggerSuffix) {
        instanceLogger = CxLogUtils.loggerWithSuffix(getClass(), loggerSuffix);
        final WriterAppender writerAppender = new WriterAppender(new PatternLayout("%m%n"), listener.getLogger());
        writerAppender.setThreshold(Level.INFO);
        final Logger parentLogger = CxLogUtils.parentLoggerWithSuffix(loggerSuffix);
        parentLogger.addAppender(writerAppender);
        String logFileName = checkmarxBuildDir.getAbsolutePath() + File.separator + "checkmarx.log";

        try {
            fileAppender = new FileAppender(new PatternLayout("%C: [%d] %-5p: %m%n"), logFileName);
            fileAppender.setThreshold(Level.DEBUG);
            parentLogger.addAppender(fileAppender);
        } catch (IOException e) {
            LOGGER.warn("Could not open log file for writing: " + logFileName);
            LOGGER.debug(e);
        }
    }

    private void closeLogger() {
        instanceLogger.removeAppender(fileAppender);
        fileAppender.close();
        instanceLogger = LOGGER; // Redirect all logs back to static logger
    }

    private CxWSResponseRunID submitScan(final AbstractBuild<?, ?> build, final CxWebService cxWebService, final BuildListener listener) throws IOException {

        try {
            EnvVars env = build.getEnvironment(listener);
            final CliScanArgs cliScanArgs = createCliScanArgs(new byte[]{}, env);
            checkIncrementalScan(build);
            FilePath zipFile = zipWorkspaceFolder(build, listener);
            SastScan sastScan = new SastScan(cxWebService, cliScanArgs, new ProjectContract(cxWebService));
            CxWSResponseRunID cxWSResponseRunId = sastScan.scan(getGroupId(), zipFile, isThisBuildIncremental);
            zipFile.delete();
            instanceLogger.info("Temporary file deleted");
            instanceLogger.info("\nScan job submitted successfully\n");
            return cxWSResponseRunId;
        } catch (Zipper.MaxZipSizeReached e) {
            throw new AbortException("Checkmarx Scan Failed: Reached maximum upload size limit of "
                    + FileUtils.byteCountToDisplaySize(CxConfig.maxZipSize()));
        } catch (Zipper.NoFilesToZip e) {
            throw new AbortException("Checkmarx Scan Failed: No files to scan");
        } catch (InterruptedException e) {
            throw new AbortException("Remote operation failed on slave node: " + e.getMessage());
        }
    }


    private boolean needToAvoidDuplicateProjectScans(CxWebService cxWebService) throws AbortException {
        return avoidDuplicateProjectScans && projectHasQueuedScans(cxWebService);
    }

    private void checkIncrementalScan(AbstractBuild<?, ?> build) {
        isThisBuildIncremental = isThisBuildIncremental(build.getNumber());

        if (isThisBuildIncremental) {
            instanceLogger.info("\nScan job started in incremental scan mode\n");
        } else {
            instanceLogger.info("\nScan job started in full scan mode\n");
        }
    }

    private FilePath zipWorkspaceFolder(AbstractBuild<?, ?> build, BuildListener listener) throws IOException, InterruptedException {
        FolderPattern folderPattern = new FolderPattern(instanceLogger, build, listener);
        DescriptorImpl descriptor = getDescriptor();
        String excludeFolders = StringUtils.isNotEmpty(getExcludeFolders()) ? getExcludeFolders() : descriptor.getExcludeFolders();
        String filterPattern = StringUtils.isNotEmpty(getFilterPattern()) ? getFilterPattern() : descriptor.getFilterPattern();

        String combinedFilterPattern = folderPattern.generatePattern(filterPattern, excludeFolders);

        CxZip cxZip = new CxZip(instanceLogger, build, listener);
        return cxZip.ZipWorkspaceFolder(combinedFilterPattern);
    }

    private boolean projectHasQueuedScans(final CxWebService cxWebService) throws AbortException {
        ProjectContract projectContract = new ProjectContract(cxWebService);
        return projectContract.projectHasQueuedScans(projectId);
    }


    private CliScanArgs createCliScanArgs(byte[] compressedSources, EnvVars env) {
        CliScanArgsFactory cliScanArgsFactory = new CliScanArgsFactory(instanceLogger, getPreset(), getProjectName(), getGroupId(), getSourceEncoding(), getComment(), isThisBuildIncremental, compressedSources, env, projectId);
        return cliScanArgsFactory.create();
    }

    private boolean isThisBuildIncremental(int buildNumber) {

        boolean askedForIncremental = isIncremental();
        if (!askedForIncremental) {
            return false;
        }

        boolean askedForPeriodicFullScans = isFullScansScheduled();
        if (!askedForPeriodicFullScans) {
            return true;
        }

        // if user entered invalid value for full scan cycle - all scans will be incremental
        if (fullScanCycle < DescriptorImpl.FULL_SCAN_CYCLE_MIN || fullScanCycle > DescriptorImpl.FULL_SCAN_CYCLE_MAX) {
            return true;
        }

        // If user asked to perform full scan after every 9 incremental scans -
        // it means that every 10th scan should be full,
        // that is the ordinal numbers of full scans will be "1", "11", "21" and so on...
        boolean shouldBeFullScan = buildNumber % (fullScanCycle + 1) == 1;

        return !shouldBeFullScan;
    }

    // Check what triggered this build, and in case the trigger was SCM
    // and the build is configured to skip those triggers, return true.
    private boolean isSkipScan(final AbstractBuild<?, ?> build) {

        if (!isSkipSCMTriggers()) {
            return false;
        }

        final List<Cause> causes = build.getCauses();
        final List<Cause> allowedCauses = new LinkedList<>();

        for (Cause c : causes) {
            if (!(c instanceof SCMTrigger.SCMTriggerCause)) {
                allowedCauses.add(c);
            }
        }
        return allowedCauses.isEmpty();
    }


    //////////////////////////////////////////////////////////////////////////////////////////////
    //
    //  Descriptor class
    //
    //////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        public static final String DEFAULT_FILTER_PATTERNS = CxConfig.defaultFilterPattern();
        public static final int FULL_SCAN_CYCLE_MIN = 1;
        public static final int FULL_SCAN_CYCLE_MAX = 99;

        private static final Logger logger = Logger.getLogger(DescriptorImpl.class);

        //////////////////////////////////////////////////////////////////////////////////////
        //  Persistent plugin global configuration parameters
        //////////////////////////////////////////////////////////////////////////////////////

        @Nullable
        private String serverUrl;
        @Nullable
        private String username;
        @Nullable
        private String password;
        private boolean hideResults;
        private boolean enableCertificateValidation;
        @Nullable
        private String excludeFolders;
        @Nullable
        private String filterPattern;


        private boolean forcingVulnerabilityThresholdEnabled;
        @Nullable
        private Integer highThresholdEnforcement;
        @Nullable
        private Integer mediumThresholdEnforcement;
        @Nullable
        private Integer lowThresholdEnforcement;
        @Nullable
        private Integer osaHighThresholdEnforcement;
        @Nullable
        private Integer osaMediumThresholdEnforcement;
        @Nullable
        private Integer osaLowThresholdEnforcement;
        private JobGlobalStatusOnError jobGlobalStatusOnError;
        private JobGlobalStatusOnError jobGlobalStatusOnThresholdViolation = JobGlobalStatusOnError.FAILURE;
        private boolean scanTimeOutEnabled;
        private double scanTimeoutDuration; // In Hours.
        private boolean lockVulnerabilitySettings = true;

        private final transient Pattern msGuid = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

        public DescriptorImpl() {
            load();
        }

        @Nullable
        public String getServerUrl() {
            return serverUrl;
        }

        public void setServerUrl(@Nullable String serverUrl) {
            this.serverUrl = serverUrl;
        }

        @Nullable
        public String getUsername() {

            return username;
        }

        public void setUsername(@Nullable String username) {
            this.username = username;
        }

        @Nullable
        public String getPassword() {
            return password;
        }

        @Nullable
        public String getPasswordPlainText() {
            return Secret.fromString(password).getPlainText();
        }

        public void setPassword(@Nullable String password) {
            this.password = Secret.fromString(password).getEncryptedValue();
        }

        @Nullable
        public String getPasswordPlainText(String password) {
            return Secret.fromString(password).getPlainText();
        }

        public boolean isHideResults() {
            return hideResults;
        }

        public void setHideResults(boolean hideResults) {
            this.hideResults = hideResults;
        }

        public boolean isEnableCertificateValidation() {
            return enableCertificateValidation;
        }

        public void setEnableCertificateValidation(final boolean enableCertificateValidation) {

            if (!this.enableCertificateValidation && enableCertificateValidation) {
                /*
                This condition in needed to re-enable immediately the verification of
	            server certificates as the user changes the setting. This alleviates
	            the requirement to restart the Jenkins server for configuration to take
	            effect.
	             */

                CxSSLUtility.enableSSLCertificateVerification();
            }
            this.enableCertificateValidation = enableCertificateValidation;
        }

        @Nullable
        public String getExcludeFolders() {
            return excludeFolders;
        }

        public void setExcludeFolders(@Nullable String excludeFolders) {
            this.excludeFolders = excludeFolders;
        }

        @Nullable
        public String getFilterPattern() {
            return filterPattern;
        }

        public void setFilterPattern(@Nullable String filterPattern) {
            this.filterPattern = filterPattern;
        }

        public boolean isForcingVulnerabilityThresholdEnabled() {
            return forcingVulnerabilityThresholdEnabled;
        }

        public void setForcingVulnerabilityThresholdEnabled(boolean forcingVulnerabilityThresholdEnabled) {
            this.forcingVulnerabilityThresholdEnabled = forcingVulnerabilityThresholdEnabled;
        }

        public Integer getHighThresholdEnforcement() {
            return highThresholdEnforcement;
        }

        public void setHighThresholdEnforcement(Integer highThresholdEnforcement) {
            this.highThresholdEnforcement = highThresholdEnforcement;
        }

        public Integer getMediumThresholdEnforcement() {
            return mediumThresholdEnforcement;
        }

        public void setMediumThresholdEnforcement(Integer mediumThresholdEnforcement) {
            this.mediumThresholdEnforcement = mediumThresholdEnforcement;
        }

        public Integer getLowThresholdEnforcement() {
            return lowThresholdEnforcement;
        }

        public void setLowThresholdEnforcement(Integer lowThresholdEnforcement) {
            this.lowThresholdEnforcement = lowThresholdEnforcement;
        }

        @Nullable
        public Integer getOsaHighThresholdEnforcement() {
            return osaHighThresholdEnforcement;
        }

        public void setOsaHighThresholdEnforcement(@Nullable Integer osaHighThresholdEnforcement) {
            this.osaHighThresholdEnforcement = osaHighThresholdEnforcement;
        }

        @Nullable
        public Integer getOsaMediumThresholdEnforcement() {
            return osaMediumThresholdEnforcement;
        }

        public void setOsaMediumThresholdEnforcement(@Nullable Integer osaMediumThresholdEnforcement) {
            this.osaMediumThresholdEnforcement = osaMediumThresholdEnforcement;
        }

        @Nullable
        public Integer getOsaLowThresholdEnforcement() {
            return osaLowThresholdEnforcement;
        }

        public void setOsaLowThresholdEnforcement(@Nullable Integer osaLowThresholdEnforcement) {
            this.osaLowThresholdEnforcement = osaLowThresholdEnforcement;
        }

        public boolean getScanTimeOutEnabled() {
            return scanTimeOutEnabled;
        }

        public void setScanTimeOutEnabled(boolean scanTimeOutEnabled) {
            this.scanTimeOutEnabled = scanTimeOutEnabled;
        }

        public int getScanTimeoutDuration() {
            if (!timeoutValid(scanTimeoutDuration)) {
                scanTimeoutDuration = 1;
            }

            return (int) Math.round(scanTimeoutDuration * 60);
        }

        public void setScanTimeoutDuration(int scanTimeoutDurationInMinutes) {
            if (timeoutValid(scanTimeoutDurationInMinutes)) {
                this.scanTimeoutDuration = scanTimeoutDurationInMinutes / (double) 60;
            }
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        //////////////////////////////////////////////////////////////////////////////////////
        //  Helper methods for jelly views
        //////////////////////////////////////////////////////////////////////////////////////

        // Provides a description string to be displayed near "Use default server credentials"
        // configuration option
        public String getCredentialsDescription() {
            if (getServerUrl() == null || getServerUrl().isEmpty() ||
                    getUsername() == null || getUsername().isEmpty()) {
                return "not set";
            }

            return "Server URL: " + getServerUrl() + " username: " + getUsername();

        }

        /*
         * Used to fill the value of hidden timestamp textbox, which in turn is used for Internet Explorer cache invalidation
         */
        @NotNull
        public String getCurrentTime() {
            return String.valueOf(System.currentTimeMillis());
        }

        //////////////////////////////////////////////////////////////////////////////////////
        // Field value validators
        //////////////////////////////////////////////////////////////////////////////////////


        /*
         *  Note: This method is called concurrently by multiple threads, refrain from using mutable
	     *  shared state to avoid synchronization issues.
	     */
        public FormValidation doCheckIncludeOpenSourceFolders(@QueryParameter final boolean useOwnServerCredentials, @QueryParameter final String serverUrl, @QueryParameter final String password,
                                                              @QueryParameter final String username, @QueryParameter final String includeOpenSourceFolders, @QueryParameter final String timestamp) {
            // timestamp is not used in code, it is one of the arguments to invalidate Internet Explorer cache
            CxWebService cxWebService = null;

            if (!osaConfigured(includeOpenSourceFolders)) {
                return FormValidation.ok();
            }

            try {
                cxWebService = prepareLoggedInWebservice(useOwnServerCredentials, serverUrl, username, getPasswordPlainText(password));
            } catch (Exception e) {
                logger.debug(e);
                return FormValidation.ok();
            }

            try {
                Boolean isOsaLicenseValid = cxWebService.isOsaLicenseValid();
                if (!isOsaLicenseValid) {
                    return FormValidation.error(ScanService.NO_LICENSE_ERROR);
                }
                return FormValidation.ok();

            } catch (Exception e) {
                return FormValidation.error(e.getMessage());
            }
        }

        private boolean timeoutValid(double timeInput) {
            return timeInput >= MINIMUM_TIMEOUT_IN_MINUTES;
        }

        private boolean osaConfigured(String includeOpenSourceFolders) {
            return !org.apache.commons.lang.StringUtils.isEmpty(includeOpenSourceFolders);
        }


        /*
	     *  Note: This method is called concurrently by multiple threads, refrain from using mutable
	     *  shared state to avoid synchronization issues.
	     */
        public FormValidation doTestConnection(@QueryParameter final String serverUrl, @QueryParameter final String password,
                                               @QueryParameter final String username, @QueryParameter final String timestamp) {
            // timestamp is not used in code, it is one of the arguments to invalidate Internet Explorer cache
            CxWebService cxWebService = null;
            try {
                cxWebService = new CxWebService(serverUrl);
            } catch (Exception e) {
                logger.debug(e);
                return FormValidation.error("Invalid system URL");
            }

            try {
                cxWebService.login(username, getPasswordPlainText(password));
                return FormValidation.ok("Success");

            } catch (Exception e) {
                return FormValidation.error(e.getMessage());
            }
        }

        // Prepares a this.cxWebService object to be connected and logged in
	    /*
	     *  Note: This method is called concurrently by multiple threads, refrain from using mutable
	     *  shared state to avoid synchronization issues.
	     */
        private CxWebService prepareLoggedInWebservice(boolean useOwnServerCredentials,
                                                       String serverUrl,
                                                       String username,
                                                       String password)
                throws AbortException, MalformedURLException {
            String serverUrlToUse = !useOwnServerCredentials ? serverUrl : getServerUrl();
            String usernameToUse = !useOwnServerCredentials ? username : getUsername();
            String passwordToUse = !useOwnServerCredentials ? getPasswordPlainText(password) : getPasswordPlainText();
            logger.debug("prepareLoggedInWebservice: server: " + serverUrlToUse + " user: " + usernameToUse);

            CxWebService cxWebService = new CxWebService(serverUrlToUse);
            logger.debug("prepareLoggedInWebservice: created cxWebService");
            cxWebService.login(usernameToUse, passwordToUse);
            logger.debug("prepareLoggedInWebservice: logged in");
            return cxWebService;
        }

        /*
         *  Note: This method is called concurrently by multiple threads, refrain from using mutable
         *  shared state to avoid synchronization issues.
         */
        public ComboBoxModel doFillProjectNameItems(@QueryParameter final boolean useOwnServerCredentials, @QueryParameter final String serverUrl,
                                                    @QueryParameter final String username, @QueryParameter final String password, @QueryParameter final String timestamp) {
            // timestamp is not used in code, it is one of the arguments to invalidate Internet Explorer cache
            ComboBoxModel projectNames = new ComboBoxModel();

            try {
                final CxWebService cxWebService = prepareLoggedInWebservice(useOwnServerCredentials, serverUrl, username, getPasswordPlainText(password));

                List<ProjectDisplayData> projectsDisplayData = cxWebService.getProjectsDisplayData();
                for (ProjectDisplayData pd : projectsDisplayData) {
                    projectNames.add(pd.getProjectName());
                }

                logger.debug("Projects list: " + projectNames.size());
                return projectNames;

            } catch (Exception e) {
                logger.debug("Projects list: empty");
                return projectNames; // Return empty list of project names
            }
        }

        public boolean hasOSALicense(@QueryParameter final boolean useOwnServerCredentials, @QueryParameter final String serverUrl,
                                     @QueryParameter final String username, @QueryParameter final String password) {

            boolean hasOSALicense = false;

            try {
                final CxWebService cxWebService = prepareLoggedInWebservice(useOwnServerCredentials, serverUrl, username, getPasswordPlainText(password));

                hasOSALicense = cxWebService.isOsaLicenseValid();

            } catch (Exception e) {
                logger.debug("fail to check OSA license", e);
            }

            return hasOSALicense;
        }

	    /*
	     *  Note: This method is called concurrently by multiple threads, refrain from using mutable
	     *  shared state to avoid synchronization issues.
	     */

        public FormValidation doCheckProjectName(@AncestorInPath AbstractProject project, @QueryParameter final String projectName, @QueryParameter final boolean useOwnServerCredentials,
                                                 @QueryParameter final String serverUrl, @QueryParameter final String username, @QueryParameter final String password,
                                                 @QueryParameter final String groupId, @QueryParameter final String timestamp) {
            // timestamp is not used in code, it is one of the arguments to invalidate Internet Explorer cache

            try {
                final CxWebService cxWebService = prepareLoggedInWebservice(useOwnServerCredentials, serverUrl, username, getPasswordPlainText(password));

                if (msGuid.matcher(groupId).matches()) {
                    String resolvedProjectName = projectName;
                    if (project.getSomeBuildWithWorkspace() == null) { //is it the first build of a new project
                        if (projectName.equals("${JOB_NAME}")) {
                            resolvedProjectName = project.getName();
                        }
                    } else {
                        EnvVars ev = new EnvVars(project.getSomeBuildWithWorkspace().getEnvironment(null));
                        resolvedProjectName = ev.expand(projectName);
                    }
                    CxWSBasicRepsonse cxWSBasicRepsonse = cxWebService.validateProjectName(resolvedProjectName, groupId);
                    if (cxWSBasicRepsonse.isIsSuccesfull()) {
                        return FormValidation.ok("Project Name Validated Successfully");
                    } else {
                        if (cxWSBasicRepsonse.getErrorMessage().startsWith("project name validation failed: duplicate name, project name") ||
                                cxWSBasicRepsonse.getErrorMessage().equalsIgnoreCase("Project name already exists")) {
                            return FormValidation.ok("Scan will be added to existing project");
                        } else if (cxWSBasicRepsonse.getErrorMessage().equalsIgnoreCase("project name validation failed: unauthorized user") ||
                                cxWSBasicRepsonse.getErrorMessage().equalsIgnoreCase("Unauthorized user")) {
                            return FormValidation.error("The user is not authorized to create/run Checkmarx projects");
                        } else if (cxWSBasicRepsonse.getErrorMessage().startsWith("Exception occurred at IsValidProjectCreationRequest:")) {
                            logger.warn("Couldn't validate project name with Checkmarx sever:\n" + cxWSBasicRepsonse.getErrorMessage());
                            return FormValidation.warning(cxWSBasicRepsonse.getErrorMessage());
                        } else {
                            return FormValidation.error(cxWSBasicRepsonse.getErrorMessage());
                        }
                    }
                } else {
                    return FormValidation.ok();
                }
            } catch (Exception e) {
                logger.warn("Couldn't validate project name with Checkmarx sever:\n" + e.getLocalizedMessage());
                return FormValidation.warning("Can't reach server to validate project name");
            }
        }


        /**
         * Provides a list of presets from Checkmarx server for dynamic drop-down list in configuration page
         *
         * @param useOwnServerCredentials
         * @param serverUrl
         * @param username
         * @param password
         * @param timestamp
         * @return list of presets
         */
	    /*
	     *  Note: This method is called concurrently by multiple threads, refrain from using mutable
	     *  shared state to avoid synchronization issues.
	     */
        public ListBoxModel doFillPresetItems(@QueryParameter final boolean useOwnServerCredentials, @QueryParameter final String serverUrl,
                                              @QueryParameter final String username, @QueryParameter final String password, @QueryParameter final String timestamp) {
            // timestamp is not used in code, it is one of the arguments to invalidate Internet Explorer cache
            ListBoxModel listBoxModel = new ListBoxModel();
            try {
                final CxWebService cxWebService = prepareLoggedInWebservice(useOwnServerCredentials, serverUrl, username, getPasswordPlainText(password));

                final List<Preset> presets = cxWebService.getPresets();
                for (Preset p : presets) {
                    listBoxModel.add(new ListBoxModel.Option(p.getPresetName(), Long.toString(p.getID())));
                }

                logger.debug("Presets list: " + listBoxModel.size());
                return listBoxModel;

            } catch (Exception e) {
                logger.debug("Presets list: empty");
                String message = "Provide Checkmarx server credentials to see presets list";
                listBoxModel.add(new ListBoxModel.Option(message, message));
                return listBoxModel; // Return empty list of project names
            }
        }

        /**
         * Validates frequency of full scans
         *
         * @param value
         * @return if frequency is valid
         */
	    /*
	     *  Note: This method is called concurrently by multiple threads, refrain from using mutable
	     *  shared state to avoid synchronization issues.
	     */
        public FormValidation doCheckFullScanCycle(@QueryParameter final int value) {
            if (value >= FULL_SCAN_CYCLE_MIN && value <= FULL_SCAN_CYCLE_MAX) {
                return FormValidation.ok();
            } else {
                return FormValidation.error("Number must be in the range " + FULL_SCAN_CYCLE_MIN + "-" + FULL_SCAN_CYCLE_MAX);
            }
        }

        public ListBoxModel doFillSourceEncodingItems(@QueryParameter final boolean useOwnServerCredentials, @QueryParameter final String serverUrl,
                                                      @QueryParameter final String username, @QueryParameter final String password, @QueryParameter final String timestamp) {
            // timestamp is not used in code, it is one of the arguments to invalidate Internet Explorer cache
            ListBoxModel listBoxModel = new ListBoxModel();
            try {
                final CxWebService cxWebService = prepareLoggedInWebservice(useOwnServerCredentials, serverUrl, username, getPasswordPlainText(password));

                final List<ConfigurationSet> sourceEncodings = cxWebService.getSourceEncodings();
                for (ConfigurationSet cs : sourceEncodings) {
                    listBoxModel.add(new ListBoxModel.Option(cs.getConfigSetName(), Long.toString(cs.getID())));
                }

                logger.debug("Source encodings list: " + listBoxModel.size());
            } catch (Exception e) {
                logger.debug("Source encodings list: empty");
                String message = "Provide Checkmarx server credentials to see source encodings list";
                listBoxModel.add(new ListBoxModel.Option(message, message));
            }

            return listBoxModel;
        }


        // Provides a list of source encodings from checkmarx server for dynamic drop-down list in configuration page
	    /*
	     *  Note: This method is called concurrently by multiple threads, refrain from using mutable
	     *  shared state to avoid synchronization issues.
	     */

        public ListBoxModel doFillGroupIdItems(@QueryParameter final boolean useOwnServerCredentials, @QueryParameter final String serverUrl,
                                               @QueryParameter final String username, @QueryParameter final String password, @QueryParameter final String timestamp) {
            // timestamp is not used in code, it is one of the arguments to invalidate Internet Explorer cache
            ListBoxModel listBoxModel = new ListBoxModel();
            try {
                final CxWebService cxWebService = prepareLoggedInWebservice(useOwnServerCredentials, serverUrl, username, getPasswordPlainText(password));
                final List<Group> groups = cxWebService.getAssociatedGroups();
                for (Group group : groups) {
                    listBoxModel.add(new ListBoxModel.Option(group.getGroupName(), group.getID()));
                }

                logger.debug("Associated groups list: " + listBoxModel.size());
                return listBoxModel;

            } catch (Exception e) {
                logger.debug("Associated groups: empty");
                String message = "Provide Checkmarx server credentials to see teams list";
                listBoxModel.add(new ListBoxModel.Option(message, message));
                return listBoxModel; // Return empty list of project names
            }

        }

        public ListBoxModel doFillVulnerabilityThresholdResultItems() {
            ListBoxModel listBoxModel = new ListBoxModel();

            for (JobStatusOnError status : JobStatusOnError.values()) {
                if (status != JobStatusOnError.GLOBAL) {
                    listBoxModel.add(new ListBoxModel.Option(status.getDisplayName(), status.name()));
                }
            }

            return listBoxModel;
        }
		
		

		/*
		 * Note: This method is called concurrently by multiple threads, refrain from using mutable shared state to
		 * avoid synchronization issues.
		 */

        public FormValidation doCheckHighThreshold(@QueryParameter final Integer value) {
            return checkNonNegativeValue(value);
        }

		/*
		 * Note: This method is called concurrently by multiple threads, refrain from using mutable shared state to
		 * avoid synchronization issues.
		 */

        public FormValidation doCheckMediumThreshold(@QueryParameter final Integer value) {
            return checkNonNegativeValue(value);
        }

		/*
		 * Note: This method is called concurrently by multiple threads, refrain from using mutable shared state to
		 * avoid synchronization issues.
		 */

        public FormValidation doCheckLowThreshold(@QueryParameter final Integer value) {
            return checkNonNegativeValue(value);
        }

		/*
		 * Note: This method is called concurrently by multiple threads, refrain from using mutable shared state to
		 * avoid synchronization issues.
		 */

        public FormValidation doCheckHighThresholdEnforcement(@QueryParameter final Integer value) {
            return checkNonNegativeValue(value);
        }

		/*
		 * Note: This method is called concurrently by multiple threads, refrain from using mutable shared state to
		 * avoid synchronization issues.
		 */

        public FormValidation doCheckMediumThresholdEnforcement(@QueryParameter final Integer value) {
            return checkNonNegativeValue(value);
        }

		/*
		 * Note: This method is called concurrently by multiple threads, refrain from using mutable shared state to
		 * avoid synchronization issues.
		 */

        public FormValidation doCheckLowThresholdEnforcement(@QueryParameter final Integer value) {
            return checkNonNegativeValue(value);
        }

        /*
         *  Note: This method is called concurrently by multiple threads, refrain from using mutable
         *  shared state to avoid synchronization issues.
         */


        public FormValidation doCheckOsaHighThreshold(@QueryParameter final Integer value) {
            return checkNonNegativeValue(value);
        }

		/*
		 * Note: This method is called concurrently by multiple threads, refrain from using mutable shared state to
		 * avoid synchronization issues.
		 */

        public FormValidation doCheckOsaMediumThreshold(@QueryParameter final Integer value) {
            return checkNonNegativeValue(value);
        }

		/*
		 * Note: This method is called concurrently by multiple threads, refrain from using mutable shared state to
		 * avoid synchronization issues.
		 */

        public FormValidation doCheckOsaLowThreshold(@QueryParameter final Integer value) {
            return checkNonNegativeValue(value);
        }



        public FormValidation doCheckOsaHighThresholdEnforcement(@QueryParameter final Integer value) {
            return checkNonNegativeValue(value);
        }

		/*
		 * Note: This method is called concurrently by multiple threads, refrain from using mutable shared state to
		 * avoid synchronization issues.
		 */

        public FormValidation doCheckOsaMediumThresholdEnforcement(@QueryParameter final Integer value) {
            return checkNonNegativeValue(value);
        }

		/*
		 * Note: This method is called concurrently by multiple threads, refrain from using mutable shared state to
		 * avoid synchronization issues.
		 */

        public FormValidation doCheckOsaLowThresholdEnforcement(@QueryParameter final Integer value) {
            return checkNonNegativeValue(value);
        }


        private FormValidation checkNonNegativeValue(final Integer value) {
            if (value == null || value >= 0) {
                return FormValidation.ok();
            } else {
                return FormValidation.error("Number must be non-negative");
            }
        }

        public String getDefaultProjectName() {
            // Retrieves the job name from request URL, cleans it from special characters,\
            // and returns as a default project name.

            final String url = getCurrentDescriptorByNameUrl();

            String decodedUrl = null;
            try {
                decodedUrl = URLDecoder.decode(url, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                decodedUrl = url;
            }

            final String[] urlComponents = decodedUrl.split("/");
            if (urlComponents.length > 0) {
                return urlComponents[urlComponents.length - 1];
            }

            // This is a fallback if the above code fails
            return "";
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        @Override
        public String getDisplayName() {
            return "Execute Checkmarx Scan";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            // To persist global configuration information,
            // set that to properties and call save().

            // ^Can also use req.bindJSON(this, formData);
            //  (easier when there are many fields; need set* methods for this, like setUseFrench)

            req.bindJSON(this, formData.getJSONObject("checkmarx"));
            save();
            return super.configure(req, formData);
        }

        public JobGlobalStatusOnError getJobGlobalStatusOnError() {
            return jobGlobalStatusOnError;
        }

        public void setJobGlobalStatusOnError(JobGlobalStatusOnError jobGlobalStatusOnError) {
            this.jobGlobalStatusOnError = (null == jobGlobalStatusOnError) ? JobGlobalStatusOnError.FAILURE : jobGlobalStatusOnError;
        }

        public JobGlobalStatusOnError getJobGlobalStatusOnThresholdViolation() {
            return jobGlobalStatusOnThresholdViolation;
        }

        public void setJobGlobalStatusOnThresholdViolation(JobGlobalStatusOnError jobGlobalStatusOnThresholdViolation) {
            this.jobGlobalStatusOnThresholdViolation = jobGlobalStatusOnThresholdViolation;
        }

        public boolean isLockVulnerabilitySettings() {
            return lockVulnerabilitySettings;
        }

        public void setLockVulnerabilitySettings(boolean lockVulnerabilitySettings) {
            this.lockVulnerabilitySettings = lockVulnerabilitySettings;
        }
    }
}
