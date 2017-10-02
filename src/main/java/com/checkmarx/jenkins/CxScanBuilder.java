package com.checkmarx.jenkins;

import com.checkmarx.jenkins.filesystem.FolderPattern;
import com.checkmarx.jenkins.filesystem.zip.CxZip;
import com.checkmarx.jenkins.filesystem.zip.Zipper;
import com.checkmarx.jenkins.logger.CxPluginLogger;
import com.checkmarx.jenkins.opensourceanalysis.DependencyFolder;
import com.checkmarx.jenkins.opensourceanalysis.ScanService;
import com.checkmarx.jenkins.opensourceanalysis.ScanServiceTools;
import com.checkmarx.jenkins.web.client.OsaScanClient;
import com.checkmarx.jenkins.web.contracts.ProjectContract;
import com.checkmarx.jenkins.web.model.AuthenticationRequest;
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
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.jelly.JellyContext;
import org.apache.commons.jelly.XMLOutput;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kohsuke.stapler.*;
import org.xml.sax.InputSource;

import javax.annotation.Nonnull;
import javax.xml.ws.WebServiceException;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URLDecoder;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The main entry point for Checkmarx plugin. This class implements the Builder
 * build stage that scans the source code.
 *
 * @author Denis Krivitski
 * @since 3/10/13
 */

public class CxScanBuilder extends Builder implements SimpleBuildStep {


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

    //used by pipeline
    @Nullable
    private String teamPath;

    @Nullable
    private String preset;
    private boolean presetSpecified;

    private boolean globalExclusions = true;

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

    // Kept for backward compatibility with old serialized plugin configuration.
    private static transient Logger staticLogger;

    // STATIC_LOGGER is initialized here due to the pre-perform methods, will NOT print to job console
    private static CxPluginLogger STATIC_LOGGER = new CxPluginLogger();
    //Print to job console, initialized within perform
    private volatile transient CxPluginLogger jobConsoleLogger;

    // it is initialized in perform method
    private JobStatusOnError jobStatusOnError;

    private String exclusionsSetting;
    private String thresholdSettings;

    private Result vulnerabilityThresholdResult;


    private boolean avoidDuplicateProjectScans;

    public static final String PROJECT_STATE_URL_TEMPLATE = "/CxWebClient/portal#/projectState/{0}/Summary";
    public static final String ASYNC_MESSAGE = "CxSAST scan was run in asynchronous mode.\nRefer to the {0} for the scan results\n";

    public static final int MINIMUM_TIMEOUT_IN_MINUTES = 1;
    public static final String REPORTS_FOLDER = "Checkmarx/Reports";

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
            @Nullable String teamPath, //used by pipeline
            @Nullable String preset,
            JobStatusOnError jobStatusOnError,
            boolean presetSpecified,
            String exclusionsSetting,
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
            String vulnerabilityThresholdResult,
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
        this.teamPath = teamPath;
        this.preset = preset;
        this.jobStatusOnError = jobStatusOnError;
        this.presetSpecified = presetSpecified;
        this.exclusionsSetting = exclusionsSetting;
        this.globalExclusions = "global".equals(exclusionsSetting);
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
        if(vulnerabilityThresholdResult != null) {
            this.vulnerabilityThresholdResult = Result.fromString(vulnerabilityThresholdResult);
        }
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
        return null;
    }

    @Nullable
    public String getGroupId() {
        return groupId;
    }

    @Nullable
    public String getTeamPath() {
        return teamPath;
    }

    @Nullable
    public String getPreset() {
        return preset;
    }

    public boolean isPresetSpecified() {
        return presetSpecified;
    }

    public boolean isGlobalExclusions() {
        return globalExclusions;
    }


    public void setGlobalExclusions(boolean globalExclusions) {
        this.globalExclusions = globalExclusions;
    }

    public String getExclusionsSetting() {
        return exclusionsSetting;
    }

    public void setExclusionsSetting(String exclusionsSetting) {
        this.exclusionsSetting = exclusionsSetting;
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

    @DataBoundSetter
    public void setOsaEnabled(boolean osaEnabled) {
        this.osaEnabled = osaEnabled;
    }

    @Nullable
    public Integer getOsaHighThreshold() {
        return osaHighThreshold;
    }

    @DataBoundSetter
    public void setOsaHighThreshold(Integer osaHighThreshold) {
        this.osaHighThreshold = osaHighThreshold;
    }

    @Nullable
    public Integer getOsaMediumThreshold() {
        return osaMediumThreshold;
    }

    @DataBoundSetter
    public void setOsaMediumThreshold(Integer osaMediumThreshold) {
        this.osaMediumThreshold = osaMediumThreshold;
    }

    @Nullable
    public Integer getOsaLowThreshold() {
        return osaLowThreshold;
    }

    @DataBoundSetter
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

    @DataBoundSetter
    public void setThresholdSettings(String thresholdSettings) {
        this.thresholdSettings = thresholdSettings;
    }

    public String getThresholdSettings() {
        return thresholdSettings;
    }

    @DataBoundSetter
    public void setVulnerabilityThresholdResult(String result) {
        if(result != null) {
            this.vulnerabilityThresholdResult = Result.fromString(result);
        }
    }

    public String getVulnerabilityThresholdResult() {
        if(vulnerabilityThresholdResult != null) {
            return vulnerabilityThresholdResult.toString();
        }
        return null;
    }

    @DataBoundSetter
    public void setUseOwnServerCredentials(boolean useOwnServerCredentials) {
        this.useOwnServerCredentials = useOwnServerCredentials;
    }

    @DataBoundSetter
    public void setServerUrl(@Nullable String serverUrl) {
        this.serverUrl = serverUrl;
    }

    @DataBoundSetter
    public void setUsername(@Nullable String username) {
        this.username = username;
    }

    @DataBoundSetter
    public void setPassword(@Nullable String password) {
        this.password = password;
    }

    @DataBoundSetter
    public void setProjectName(@Nullable String projectName) {
        this.projectName = projectName;
    }

    @DataBoundSetter
    public void setPreset(@Nullable String preset) {
        this.preset = preset;
    }

    @DataBoundSetter
    public void setPresetSpecified(boolean presetSpecified) {
        this.presetSpecified = presetSpecified;
    }

    @DataBoundSetter
    public void setExcludeFolders(@Nullable String excludeFolders) {
        this.excludeFolders = excludeFolders;
    }

    @DataBoundSetter
    public void setFilterPattern(@Nullable String filterPattern) {
        this.filterPattern = filterPattern;
    }

    @DataBoundSetter
    public void setIncremental(boolean incremental) {
        this.incremental = incremental;
    }

    @DataBoundSetter
    public void setFullScansScheduled(boolean fullScansScheduled) {
        this.fullScansScheduled = fullScansScheduled;
    }

    @DataBoundSetter
    public void setFullScanCycle(int fullScanCycle) {
        this.fullScanCycle = fullScanCycle;
    }

    @DataBoundSetter
    public void setThisBuildIncremental(boolean thisBuildIncremental) {
        isThisBuildIncremental = thisBuildIncremental;
    }

    @DataBoundSetter
    public void setSourceEncoding(@Nullable String sourceEncoding) {
        this.sourceEncoding = sourceEncoding;
    }

    @DataBoundSetter
    public void setComment(@Nullable String comment) {
        this.comment = comment;
    }

    @DataBoundSetter
    public void setSkipSCMTriggers(boolean skipSCMTriggers) {
        this.skipSCMTriggers = skipSCMTriggers;
    }

    @DataBoundSetter
    public void setWaitForResultsEnabled(boolean waitForResultsEnabled) {
        this.waitForResultsEnabled = waitForResultsEnabled;
    }

    @DataBoundSetter
    public void setVulnerabilityThresholdEnabled(boolean vulnerabilityThresholdEnabled) {
        this.vulnerabilityThresholdEnabled = vulnerabilityThresholdEnabled;
    }

    @DataBoundSetter
    public void setHighThreshold(@Nullable Integer highThreshold) {
        this.highThreshold = highThreshold;
    }

    @DataBoundSetter
    public void setMediumThreshold(@Nullable Integer mediumThreshold) {
        this.mediumThreshold = mediumThreshold;
    }

    @DataBoundSetter
    public void setLowThreshold(@Nullable Integer lowThreshold) {
        this.lowThreshold = lowThreshold;
    }

    @DataBoundSetter
    public void setGeneratePdfReport(boolean generatePdfReport) {
        this.generatePdfReport = generatePdfReport;
    }

    @DataBoundSetter
    public void setIncludeOpenSourceFolders(@Nullable String includeOpenSourceFolders) {
        this.includeOpenSourceFolders = includeOpenSourceFolders;
    }

    @DataBoundSetter
    public void setExcludeOpenSourceFolders(@Nullable String excludeOpenSourceFolders) {
        this.excludeOpenSourceFolders = excludeOpenSourceFolders;
    }

    @DataBoundSetter
    public void setJobStatusOnError(JobStatusOnError jobStatusOnError) {
        this.jobStatusOnError = jobStatusOnError;
    }

    @DataBoundSetter
    public void setAvoidDuplicateProjectScans(boolean avoidDuplicateProjectScans) {
        this.avoidDuplicateProjectScans = avoidDuplicateProjectScans;
    }

    @DataBoundSetter
    public void setProjectId(long projectId) {
        this.projectId = projectId;
    }

    public long getProjectId() {
        return projectId;
    }

    public boolean isThisBuildIncremental() {
        return isThisBuildIncremental;
    }

    @DataBoundSetter
    public void setGroupId(@Nullable String groupId) {
        this.groupId = groupId;
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws InterruptedException, IOException {


        //set to the logger to print into the job console
        jobConsoleLogger = new CxPluginLogger(listener);

        final DescriptorImpl descriptor = getDescriptor();

        CxWSResponseRunID cxWSResponseRunID = null;
        CxWebService cxWebService = null;
        CxWSCreateReportResponse reportResponse = null;

        try {
            File checkmarxBuildDir = new File(run.getRootDir(), "checkmarx");
            checkmarxBuildDir.mkdir();


            jobConsoleLogger.info("Checkmarx Jenkins plugin version: " + CxConfig.version());
            printConfiguration(descriptor);

            if (isSkipScan(run)) {
                jobConsoleLogger.info("Checkmarx scan skipped since the build was triggered by SCM. " +
                        "Visit plugin configuration page to disable this skip.");
                return;
            }
            final String serverUrlToUse = isUseOwnServerCredentials() ? getServerUrl() : descriptor.getServerUrl();
            final String usernameToUse = isUseOwnServerCredentials() ? getUsername() : descriptor.getUsername();
            final String passwordToUse = isUseOwnServerCredentials() ? getPasswordPlainText() : descriptor.getPasswordPlainText();

            String serverUrlToUseNotNull = serverUrlToUse != null ? serverUrlToUse : "";

            cxWebService = new CxWebService(serverUrlToUseNotNull, jobConsoleLogger);
            cxWebService.login(usernameToUse, passwordToUse);

            jobConsoleLogger.info("Checkmarx server login successful");

            if (!StringUtils.isEmpty(teamPath)) {
                jobConsoleLogger.info("Resolving teamPath [" + teamPath + "] to groupId");
                groupId = cxWebService.resolveGroupId(teamPath);
            }

            projectId = cxWebService.resolveProjectId(run.getEnvironment(listener).expand(projectName), groupId);
            if (needToAvoidDuplicateProjectScans(cxWebService)) {
                jobConsoleLogger.info("\nAvoid duplicate project scans in queue\n");
                return;
            }

            if (descriptor.isProhibitProjectCreation() && projectId == 0) {
                jobConsoleLogger.info("\nCreation of the new project " + projectName + " is not authorized. Please use an existing project.");
                jobConsoleLogger.info("You can enable the creation of new projects by disabling the \"Deny new Checkmarx projects creation\" checkbox in the Jenkins plugin global settings.\n");
                run.setResult(Result.FAILURE);
                return;
            }

            jobConsoleLogger.info("\n----------------------------Executing CxSAST scan:----------------------------\n");
            //If there no project under the project name a new project will be created
            cxWSResponseRunID = submitScan(run, workspace, cxWebService, listener);
            projectId = cxWSResponseRunID.getProjectID();

            boolean shouldRunAsynchronous = scanShouldRunAsynchronous(descriptor);
            if (shouldRunAsynchronous) {
                logAsyncMessage(serverUrlToUse);
                addScanResultAction(run, serverUrlToUse, shouldRunAsynchronous, null);
                if (osaEnabled) {
                    try {
                        analyzeOpenSources(run, workspace, serverUrlToUseNotNull, usernameToUse, passwordToUse, cxWebService, listener, shouldRunAsynchronous);
                    } catch (Exception ignored) {
                        //todo catch reason for failure
                    }
                }
                return;
            }

            long scanId = 0;
            try {
                scanId = cxWebService.trackScanProgress(cxWSResponseRunID, usernameToUse, passwordToUse, descriptor.getScanTimeOutEnabled(), descriptor.getScanTimeoutDuration());
            }catch (Exception e){
                jobConsoleLogger.error("Error while retrieving SAST scan: " + e.getMessage());
            }

            CxScanResult cxScanResult;
            boolean isSASTThresholdFailedTheBuild = false;
            ThresholdConfig thresholdConfig = createThresholdConfig();
            thresholdsError = new StringBuilder();

            if (scanId == 0) {
                printSastFailedScan();
                run.setResult(Result.UNSTABLE);
                if(projectId == 0) {
                    return;
                }
                cxScanResult = new CxScanResult(run, serverUrlToUse, projectId , shouldRunAsynchronous);
            }else {
                //create report file for putting in workspace to enable sending it by email through Jenkins
                reportResponse = cxWebService.generateScanReport(scanId, CxWSReportType.XML);
                File xmlReportFile = new File(checkmarxBuildDir, "ScanReport.xml");
                cxWebService.retrieveScanReport(reportResponse.getID(), xmlReportFile, CxWSReportType.XML);

                if (generatePdfReport) {
                    reportResponse = cxWebService.generateScanReport(scanId, CxWSReportType.PDF);
                    File pdfReportFile = new File(checkmarxBuildDir, CxScanResult.PDF_REPORT_NAME);
                    cxWebService.retrieveScanReport(reportResponse.getID(), pdfReportFile, CxWSReportType.PDF);
                }


                cxScanResult = addScanResultAction(run, serverUrlToUse, shouldRunAsynchronous, xmlReportFile);
                cxScanResult.setScanId(scanId);
                // Set scan results to environment
                EnvVarAction envVarAction = new EnvVarAction();
                envVarAction.setCxSastResults(cxScanResult);
                run.addAction(envVarAction);

                //CxSAST Thresholds
                thresholdsError = new StringBuilder();
                ThresholdConfig thresholdConfig = createThresholdConfig();

                // Set scan thresholds for the summery.jelly
                if (isSASThresholdEffectivelyEnabled()) {
                    cxScanResult.setThresholds(thresholdConfig);
                }


                isSASTThresholdFailedTheBuild = ((descriptor.isForcingVulnerabilityThresholdEnabled() && descriptor.isLockVulnerabilitySettings()) || isVulnerabilityThresholdEnabled())
                        && isThresholdCrossed(thresholdConfig, cxScanResult.getHighCount(), cxScanResult.getMediumCount(), cxScanResult.getLowCount(), "CxSAST ");
                printScanResult(cxScanResult);
            }
            //OSA scan
            boolean isOSAThresholdFailedTheBuild = false;

            if (osaEnabled) {
                cxScanResult.setOsaEnabled(true);
                OsaScanResult osaScanResult = analyzeOpenSources(run, workspace, serverUrlToUseNotNull, usernameToUse, passwordToUse, cxWebService, listener, shouldRunAsynchronous);

                if(osaScanResult != null) {
                    //todo: when CxResult + ui report legacy code will be removed, stop using this as a flag for osa scan execution success
                    //(meaning - move the line below up [under the line "if (osaEnabled)"] and start using the existence of osaScanResult object to decide weather to present osa results)
                    cxScanResult.setOsaScanResult(osaScanResult);

                    if (osaScanResult.isOsaLicense()) {

                        cxScanResult.setOsaSuccessful(true);

                        ThresholdConfig osaThresholdConfig = createOsaThresholdConfig();
                        // Set scan thresholds for the summery.jelly
                        if (isOsaThresholdEffectivelyEnabled()) {
                            cxScanResult.setOsaThresholds(osaThresholdConfig);
                        }

                    createOsaJsonReports(osaScanResult, checkmarxBuildDir);

                    //retrieve osa scan results pdf + html
                    getOSAReports(cxScanResult.getOsaScanResult().getScanId(), serverUrlToUseNotNull, usernameToUse, passwordToUse, checkmarxBuildDir);


                    //OSA Threshold
                    isOSAThresholdFailedTheBuild = cxScanResult.getOsaScanResult() != null && ((descriptor.isForcingVulnerabilityThresholdEnabled() && descriptor.isLockVulnerabilitySettings()) || isVulnerabilityThresholdEnabled())
                            && isThresholdCrossed(osaThresholdConfig, cxScanResult.getOsaScanResult().getOsaHighCount(), cxScanResult.getOsaScanResult().getOsaMediumCount(), cxScanResult.getOsaScanResult().getOsaLowCount(), "OSA ");
                }
            } else {
                    printOsaFailedScan();
                    cxScanResult.setOsaSuccessful(false);
            }
         }

            // Set scan results to environment
            EnvVarAction envVarAction = new EnvVarAction();
            envVarAction.setCxSastResults(cxScanResult);
            run.addAction(envVarAction);

            generateHtmlReport(checkmarxBuildDir, cxScanResult);
            jobConsoleLogger.info("Copying reports to workspace");
            copyReportsToWorkspace(workspace, checkmarxBuildDir);

            //If one of the scan's threshold was crossed - fail the build
            if (isSASTThresholdFailedTheBuild || isOSAThresholdFailedTheBuild) {
                run.setResult(thresholdConfig.getBuildStatus());
                jobConsoleLogger.info("*************************");
                jobConsoleLogger.info("The Build Failed due to: ");
                jobConsoleLogger.info("*************************");
                String[] lines = thresholdsError.toString().split("\\n");
                for (String s : lines) {
                    jobConsoleLogger.info(s);
                }
                jobConsoleLogger.info("---------------------------------------------------------------------");
            }

            return;
        } catch (IOException | WebServiceException e) {

            addScanResultAction(run, serverUrlToUse, shouldRunAsynchronous, xmlReportFile);

            if (useUnstableOnError(descriptor)) {
                run.setResult(Result.UNSTABLE);
                jobConsoleLogger.error(e.getMessage(), e);
                return;
            } else {
                throw e;
            }
        } catch (InterruptedException e) {
            if (reportResponse != null) {
                jobConsoleLogger.error("Cancelling report generation on the Checkmarx server...");
                cxWebService.cancelScanReport(reportResponse.getID());
            } else if (cxWSResponseRunID != null) {
                jobConsoleLogger.error("Cancelling scan on the Checkmarx server...");
                cxWebService.cancelScan(cxWSResponseRunID.getRunId());
            }
            throw e;
        }
    }


    private void generateHtmlReport( File checkmarxBuildDir, CxScanResult cxScanResult) {

        jobConsoleLogger.info("Generating HTML report");
        try {

            //create output file report.html
            File reportFile = new File(checkmarxBuildDir, "report.html");

            OutputStream output = new FileOutputStream(reportFile);
            XMLOutput xmlOutput = XMLOutput.createXMLOutput(output);

            //jelly template file to set vars in
            InputSource jellyTemplate = new InputSource(this.getClass().getClassLoader().getResourceAsStream("com/checkmarx/jenkins/CxScanResult/summary.jelly"));

            //create jelly context for setting variables
            JellyContext context = new JellyContext();
            context.setVariable("it", cxScanResult);
            context.setVariable("app", Jenkins.getInstance());

            //run script
            context.runScript(jellyTemplate, xmlOutput);
            xmlOutput.flush();
            jobConsoleLogger.info("HTML report created successfully");
        } catch (Exception e) {
            jobConsoleLogger.error("Failed to generate HTML report", e);
        }
    }

    private void createOsaJsonReports(OsaScanResult osaScanResult, File checkmarxBuildDir) {
        jobConsoleLogger.info("retrieving osa json report files");
        File osaSummeryJsonReport = new File(checkmarxBuildDir, "OSASummery.json");
        writeStringToWorkspaceFile("osa summery json report", osaSummeryJsonReport, osaScanResult.getOpenSourceSummaryJson());
        File osaLibrariesJsonReport = new File(checkmarxBuildDir, "OSALibraries.json");
        writeStringToWorkspaceFile("osa libraries json report", osaLibrariesJsonReport, osaScanResult.getOsaFullLibraryList());
        File osaCvesJsonReport = new File(checkmarxBuildDir, "OSAVulnerabilities.json");
        writeStringToWorkspaceFile("osa Vulnerabilities json report", osaCvesJsonReport, osaScanResult.getOsaFullCVEsList());
    }


    private void getOSAReports(String scanId, String serverUrl, String username, String password, File checkmarxBuildDir) {
        jobConsoleLogger.info("retrieving osa report files");
        AuthenticationRequest authReq = new AuthenticationRequest(username, password);
        OsaScanClient scanClient = new OsaScanClient(serverUrl, authReq);
        String osaScanHtmlResults = scanClient.getOSAScanHtmlResults(scanId);
        File osaHtmlReport = new File(checkmarxBuildDir, "OSAReport.html");
        writeStringToWorkspaceFile("osa html report", osaHtmlReport, osaScanHtmlResults);
        byte[] osaScanPdfResults = scanClient.getOSAScanPdfResults(scanId);
        File osaPdfReport = new File(checkmarxBuildDir, "OSAReport.pdf");
        try {
            FileUtils.writeByteArrayToFile(osaPdfReport, osaScanPdfResults);
        } catch (IOException e) {
            jobConsoleLogger.error("fail to write osa pdf report to [" + osaPdfReport.getAbsolutePath() + "]");
        }
        jobConsoleLogger.info("osa report file [" + osaPdfReport.getAbsolutePath() + "] generated successfully");
    }

    private void writeStringToWorkspaceFile(String dataDescription, File workspaceFile, String dataString) {
        try {
            FileUtils.writeStringToFile(workspaceFile, dataString);
        } catch (IOException e) {
            jobConsoleLogger.error("fail to write " + dataDescription + " to [" + workspaceFile.getAbsolutePath() + "]");
        }
        jobConsoleLogger.info(dataDescription + " file [" + workspaceFile.getAbsolutePath() + "] generated successfully");
    }

    private void printConfiguration(DescriptorImpl descriptor) {
        StringBuilder sb = new StringBuilder();
        boolean useGlobalThreshold = shouldUseGlobalThreshold();
        sb.append("----------------------------Configurations:-----------------------------").append("\n");
        if(isUseOwnServerCredentials()) {
            sb.append("username: ").append(getUsername()).append("\n");
            sb.append("url: ").append(getServerUrl()).append("\n");
        } else {
            sb.append("username: ").append(descriptor.getUsername()).append("\n");
            sb.append("url: ").append(descriptor.getServerUrl()).append("\n");
        }
        sb.append("projectName: ").append(getProjectName()).append("\n");
        //sb.append("preset: ").append(getPreset()).append("\n");
        sb.append("isIncrementalScan: ").append(isIncremental()).append("\n");
        if (isGlobalExclusions()) {
            sb.append("folderExclusions: ").append(descriptor.getExcludeFolders()).append("\n");
        } else {
            sb.append("folderExclusions: ").append(getExcludeFolders()).append("\n");

        }
        sb.append("isSynchronous: ").append(isWaitForResultsEnabled()).append("\n"); //TODO GLOBAL
        sb.append("generatePDFReport: ").append(isGeneratePdfReport()).append("\n");
        if (useGlobalThreshold) {
            sb.append("highSeveritiesThreshold: ").append(descriptor.getHighThresholdEnforcement()).append("\n");
            sb.append("mediumSeveritiesThreshold: ").append(descriptor.getMediumThresholdEnforcement()).append("\n");
            sb.append("lowSeveritiesThreshold: ").append(descriptor.getLowThresholdEnforcement()).append("\n");
        } else if (isSASThresholdEffectivelyEnabled()) {
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
            } else if (isOsaThresholdEffectivelyEnabled()) {
                sb.append("osaHighSeveritiesThreshold: ").append(getOsaHighThreshold()).append("\n");
                sb.append("osaMediumSeveritiesThreshold: ").append(getOsaMediumThreshold()).append("\n");
                sb.append("osaLowSeveritiesThreshold: ").append(getOsaLowThreshold()).append("\n");
            }
        }
        sb.append(" ------------------------------------------------------------------------").append("\n");

        jobConsoleLogger.info(sb.toString());
    }

    private boolean isSASThresholdEffectivelyEnabled() {
        DescriptorImpl descriptor = getDescriptor();
        if (shouldUseGlobalThreshold() && (descriptor.getHighThresholdEnforcement() != null || descriptor.getMediumThresholdEnforcement() != null || descriptor.getLowThresholdEnforcement() != null)) {
            return true;
        } else if (this.isVulnerabilityThresholdEnabled() && (this.getHighThreshold() != null && this.getMediumThreshold() != null && this.getLowThreshold() != null)) {
            return true;
        }
        return false;
    }

    private boolean isOsaThresholdEffectivelyEnabled() {
        DescriptorImpl descriptor = getDescriptor();
        if (shouldUseGlobalThreshold() && (descriptor.getOsaHighThresholdEnforcement() != null || descriptor.getOsaMediumThresholdEnforcement() != null || descriptor.getOsaLowThresholdEnforcement() != null)) {
            return true;
        } else if (this.isVulnerabilityThresholdEnabled() && (this.getOsaHighThreshold() != null && this.getOsaMediumThreshold() != null && this.getOsaLowThreshold() != null)) {
            return true;
        }
        return false;
    }

    private void printScanResult(CxScanResult scanResult) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n---------------------Checkmarx Scan Results(CxSAST)-------------------------").append("\n");
        sb.append("High Severity Results: ").append(scanResult.getHighCount()).append("\n");
        sb.append("Medium Severity Results: ").append(scanResult.getMediumCount()).append("\n");
        sb.append("Low Severity Results: ").append(scanResult.getLowCount()).append("\n");
        sb.append("Info Severity Results: ").append(scanResult.getInfoCount()).append("\n");
        sb.append("----------------------------------------------------------------------------").append("\n");

        jobConsoleLogger.info(sb.toString());
    }

    private void printSastFailedScan(){
        printFailedScan("CxSAST scan");
    }

    private void printOsaFailedScan(){
        printFailedScan("CxOSA analysis");
    }

    private void printFailedScan(String failedComponentName){
        jobConsoleLogger.error("---------------------------------------------------------------------");
        jobConsoleLogger.error("----------------------" + failedComponentName + " has failed.---------------------------");
        jobConsoleLogger.error("---------------------------------------------------------------------\n");
    }

    private void copyReportsToWorkspace(FilePath workspace, File checkmarxBuildDir) {

        String remoteDirPath = workspace.getRemote() + "/" + REPORTS_FOLDER;

        Collection<File> files = FileUtils.listFiles(checkmarxBuildDir, null, true);
        FileInputStream fileInputStream = null;

        for (File file : files) {
            try {
                String remoteFilePath = remoteDirPath + "/" + file.getName();
                jobConsoleLogger.info("Copying file [" + file.getName() + "] to workspace [" + remoteFilePath + "]");
                FilePath remoteFile = new FilePath(workspace.getChannel(), remoteFilePath);
                fileInputStream = new FileInputStream(file);
                remoteFile.copyFrom(fileInputStream);

            } catch (Exception e) {
                jobConsoleLogger.error("fail to copy file [" + file.getName() + "] to workspace", e);

            } finally {
                IOUtils.closeQuietly(fileInputStream);
            }
        }
    }

    @NotNull
    private CxScanResult addScanResultAction(Run<?, ?> run, String serverUrlToUse, boolean shouldRunAsynchronous, File xmlReportFile) {
        CxScanResult cxScanResult = new CxScanResult(run, serverUrlToUse, projectId, shouldRunAsynchronous);
        if (xmlReportFile != null) {
            SastResultParser sastResultParser = new SastResultParser(jobConsoleLogger, serverUrlToUse);
            SastScanResult sastScanResult = sastResultParser.readScanXMLReport(xmlReportFile);
            sastScanResult.setResultIsValid(true);
            cxScanResult.setSastScanResult(sastScanResult);
        }
        run.addAction(cxScanResult);
        return cxScanResult;
    }

    private void logAsyncMessage(String serverUrlToUse) {
        String projectStateUrl = serverUrlToUse + PROJECT_STATE_URL_TEMPLATE.replace("{0}", Long.toString(projectId));
        String projectStateLink = HyperlinkNote.encodeTo(projectStateUrl, "CxSAST Web");
        jobConsoleLogger.info(ASYNC_MESSAGE.replace("{0}", projectStateLink));
    }

    private boolean scanShouldRunAsynchronous(DescriptorImpl descriptor) {
        return !isWaitForResultsEnabled() && !(descriptor.isForcingVulnerabilityThresholdEnabled() && descriptor.isLockVulnerabilitySettings());
    }

    private OsaScanResult analyzeOpenSources(Run<?, ?> run, FilePath workspace, String baseUri, String user, String password, CxWebService webServiceClient, TaskListener listener, boolean shouldRunAsynchronous) {
        AuthenticationRequest authReq = new AuthenticationRequest(user, password);
        OsaScanClient scanClient = new OsaScanClient(baseUri, authReq);
        ScanServiceTools scanServiceTools = initScanServiceTools(scanClient, run, workspace, webServiceClient, listener);
        ScanService scanService = new ScanService(scanServiceTools);
        return scanService.scan(shouldRunAsynchronous);
    }

    private ScanServiceTools initScanServiceTools(OsaScanClient scanClient, Run<?, ?> run, FilePath workspace, CxWebService webServiceClient, TaskListener listener) {
        ScanServiceTools scanServiceTools = new ScanServiceTools();
        scanServiceTools.setOsaScanClient(scanClient);
        DependencyFolder folders = new DependencyFolder(includeOpenSourceFolders, excludeOpenSourceFolders);
        scanServiceTools.setDependencyFolder(folders);
        scanServiceTools.setRun(run);
        scanServiceTools.setListener(listener);
        scanServiceTools.setProjectId(projectId);
        scanServiceTools.setWebServiceClient(webServiceClient);
        scanServiceTools.setWorkspace(workspace);
        return scanServiceTools;
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
            config.setBuildStatus(vulnerabilityThresholdResult);
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


    private CxWSResponseRunID submitScan(final Run<?, ?> run, FilePath workspace, final CxWebService cxWebService, final TaskListener listener) throws IOException {

        FilePath zipFile = null;

        try {
            EnvVars env = run.getEnvironment(listener);
            final CliScanArgs cliScanArgs = createCliScanArgs(new byte[]{}, env);
            checkIncrementalScan(run);
            zipFile = zipWorkspaceFolder(run, workspace, listener);
            SastScan sastScan = new SastScan(cxWebService, cliScanArgs, new ProjectContract(cxWebService));
            CxWSResponseRunID cxWSResponseRunId = sastScan.scan(getGroupId(), zipFile, isThisBuildIncremental);
            zipFile.delete();
            jobConsoleLogger.info("Temporary file deleted");
            jobConsoleLogger.info("Scan job submitted successfully\n");
            return cxWSResponseRunId;

        } catch (Zipper.MaxZipSizeReached e) {
            throw new AbortException("Checkmarx Scan Failed: When zipping file " + e.getCurrentZippedFileName() + ", reached maximum upload size limit of "
                    + FileUtils.byteCountToDisplaySize(CxConfig.maxZipSize()) + "\n");
        } catch (Zipper.NoFilesToZip e) {
            exposeZippingLogToJobConsole(e);
            throw new AbortException("Checkmarx Scan Failed: No files to scan");

        } catch (Zipper.ZipperException e) {
            exposeZippingLogToJobConsole(e);
            throw new AbortException("Checkmarx Scan Failed: " + e.getMessage());

        } catch (InterruptedException e) {
            throw new AbortException("Remote operation failed on slave node: " + e.getMessage());

        } finally {
            if (zipFile != null) {
                try {
                    if (zipFile.exists()) {
                        if (zipFile.delete()) {
                            jobConsoleLogger.info("Temporary file deleted");
                        } else {
                            jobConsoleLogger.info("Fail to delete temporary file");
                        }
                    }
                } catch (Exception e) {
                    jobConsoleLogger.error("Fail to delete temporary file", e);
                }
            }
        }
    }

    private void exposeZippingLogToJobConsole(Zipper.ZipperException zipperException) {
        jobConsoleLogger.info(zipperException.getZippingDetails().getZippingLog());
    }

    private boolean needToAvoidDuplicateProjectScans(CxWebService cxWebService) throws AbortException {
        return avoidDuplicateProjectScans && projectHasQueuedScans(cxWebService);
    }

    private void checkIncrementalScan(Run<?, ?> run) {
        isThisBuildIncremental = isThisBuildIncremental(run.getNumber());

        if (isThisBuildIncremental) {
            jobConsoleLogger.info("Scan job started in incremental scan mode");
        } else {
            jobConsoleLogger.info("Scan job started in full scan mode");
        }
    }

    private FilePath zipWorkspaceFolder(Run<?, ?> run, FilePath workspace, TaskListener listener) throws IOException, InterruptedException {
        FolderPattern folderPattern = new FolderPattern(run, listener);
        DescriptorImpl descriptor = getDescriptor();
        String excludeFolders = isGlobalExclusions() ? descriptor.getExcludeFolders() : getExcludeFolders();
        String filterPattern = isGlobalExclusions() ? descriptor.getFilterPattern() : getFilterPattern();

        String combinedFilterPattern = folderPattern.generatePattern(filterPattern, excludeFolders);

        CxZip cxZip = new CxZip(workspace, listener);
        return cxZip.ZipWorkspaceFolder(combinedFilterPattern);
    }

    private boolean projectHasQueuedScans(final CxWebService cxWebService) throws AbortException {
        ProjectContract projectContract = new ProjectContract(cxWebService);
        return projectContract.projectHasQueuedScans(projectId);
    }


    private CliScanArgs createCliScanArgs(byte[] compressedSources, EnvVars env) {
        CliScanArgsFactory cliScanArgsFactory = new CliScanArgsFactory(getPreset(), getProjectName(), getGroupId(), getSourceEncoding(), getComment(), isThisBuildIncremental, compressedSources, env, projectId, jobConsoleLogger);
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
    private boolean isSkipScan(final Run<?, ?> run) {

        if (!isSkipSCMTriggers()) {
            return false;
        }

        final List<Cause> causes = run.getCauses();
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


        //////////////////////////////////////////////////////////////////////////////////////
        //  Persistent plugin global configuration parameters
        //////////////////////////////////////////////////////////////////////////////////////

        @Nullable
        private String serverUrl;
        @Nullable
        private String username;
        @Nullable
        private String password;

        private boolean prohibitProjectCreation;
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

        public boolean isProhibitProjectCreation() {
            return prohibitProjectCreation;
        }

        public void setProhibitProjectCreation(boolean prohibitProjectCreation) {
            this.prohibitProjectCreation = prohibitProjectCreation;
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

                CxSSLUtility.enableSSLCertificateVerification(STATIC_LOGGER);
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
        public FormValidation doCheckOsaEnabled(@QueryParameter final boolean useOwnServerCredentials, @QueryParameter final String serverUrl, @QueryParameter final String password,
                                                @QueryParameter final String username, @QueryParameter final boolean osaEnabled, @QueryParameter final String timestamp) {
            // timestamp is not used in code, it is one of the arguments to invalidate Internet Explorer cache
            CxWebService cxWebService = null;

            if (!osaEnabled) {
                return FormValidation.ok();
            }

            try {
                cxWebService = prepareLoggedInWebservice(useOwnServerCredentials, serverUrl, username, getPasswordPlainText(password));
            } catch (Exception e) {
                STATIC_LOGGER.error(e.getMessage(), e);
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
                cxWebService = new CxWebService(serverUrl, STATIC_LOGGER);
            } catch (Exception e) {
                STATIC_LOGGER.error(e.getMessage(), e);
                return FormValidation.error(e.getMessage());
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

            STATIC_LOGGER.info("prepareLoggedInWebservice: server: " + serverUrlToUse + " user: " + usernameToUse);

            CxWebService cxWebService = new CxWebService(serverUrlToUse, STATIC_LOGGER);
            cxWebService.login(usernameToUse, passwordToUse);
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

                STATIC_LOGGER.info("Projects list: " + projectNames.size());
                return projectNames;

            } catch (Exception e) {
                STATIC_LOGGER.info("Projects list: empty");
                return projectNames; // Return empty list of project names
            }
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
                            STATIC_LOGGER.error("Couldn't validate project name with Checkmarx sever:\n" + cxWSBasicRepsonse.getErrorMessage());
                            return FormValidation.warning(cxWSBasicRepsonse.getErrorMessage());
                        } else {
                            return FormValidation.error(cxWSBasicRepsonse.getErrorMessage());
                        }
                    }
                } else {
                    return FormValidation.ok();
                }
            } catch (Exception e) {
                STATIC_LOGGER.error("Couldn't validate project name with Checkmarx sever:\n" + e.getLocalizedMessage());
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

                STATIC_LOGGER.info("Presets list: " + listBoxModel.size());
                return listBoxModel;

            } catch (Exception e) {
                STATIC_LOGGER.info("Presets list: empty");
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

                STATIC_LOGGER.info("Source encodings list: " + listBoxModel.size());
            } catch (Exception e) {
                STATIC_LOGGER.info("Source encodings list: empty");
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

                STATIC_LOGGER.info("Associated groups list: " + listBoxModel.size());
                return listBoxModel;

            } catch (Exception e) {
                STATIC_LOGGER.info("Associated groups: empty");
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

            String regex = "job/(.*?)(/|$)";
            String ret = "";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(decodedUrl);
            if (matcher.find()) {
                ret = matcher.group(1);
            }

            return ret;
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
