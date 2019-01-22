package com.checkmarx.jenkins;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cx.restclient.CxShragaClient;
import com.cx.restclient.common.ShragaUtils;
import com.cx.restclient.common.summary.SummaryUtils;
import com.cx.restclient.configuration.CxScanConfig;
import com.cx.restclient.dto.ScanResults;
import com.cx.restclient.dto.Team;
import com.cx.restclient.exception.CxClientException;
import com.cx.restclient.exception.CxTokenExpiredException;
import com.cx.restclient.osa.dto.OSAResults;
import com.cx.restclient.sast.dto.CxNameObj;
import com.cx.restclient.sast.dto.Project;
import com.cx.restclient.sast.dto.SASTResults;
import com.fasterxml.jackson.databind.ObjectMapper;
import freemarker.template.TemplateException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
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
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kohsuke.stapler.*;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
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


    public static final String SCAN_REPORT_XML = "ScanReport.xml";
    public static final String OSA_SUMMERY_JSON = "OSASummery.json";
    public static final String OSA_LIBRARIES_JSON = "OSALibraries.json";
    public static final String OSA_VULNERABILITIES_JSON = "OSAVulnerabilities.json";
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
    private String credentialsId;
    @Nullable
    private String projectName;
    @Nullable
    private String groupId;
    @Nullable
    private long projectId;
    //used by pipeline
    @Nullable
    private String teamPath;
    private Boolean sastEnabled = true;
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
    private boolean failBuildOnNewResults;
    private String failBuildOnNewSeverity;
    private boolean generatePdfReport;
    private boolean enableProjectPolicyEnforcement;
    private boolean osaEnabled;
    @Nullable
    private Integer osaHighThreshold;
    @Nullable
    private Integer osaMediumThreshold;
    @Nullable
    private Integer osaLowThreshold;
    @Nullable
    private String includeOpenSourceFolders;//OSA include/exclude wildcard patterns
    @Nullable
    private String excludeOpenSourceFolders;//OSA exclude folders
    @Nullable
    private String osaArchiveIncludePatterns;
    private boolean osaInstallBeforeScan;

    //////////////////////////////////////////////////////////////////////////////////////
    // Private variables
    //////////////////////////////////////////////////////////////////////////////////////

    //server log, will NOT print to job console
    private static final JenkinsServerLogger serverLog = new JenkinsServerLogger();
    //Print to job console, initialized within perform
    CxLoggerAdapter log;
    private JobStatusOnError jobStatusOnError;
    private String exclusionsSetting;
    private String thresholdSettings;
    private Result vulnerabilityThresholdResult;
    private Result resolvedVulnerabilityThresholdResult;
    private boolean avoidDuplicateProjectScans;
    public static final int MINIMUM_TIMEOUT_IN_MINUTES = 1;
    public static final String REPORTS_FOLDER = "Checkmarx/Reports";
    public static final String CX_ORIGIN = "Jenkins";

    @DataBoundConstructor
    public CxScanBuilder(
            boolean useOwnServerCredentials,
            @Nullable String serverUrl,
            @Nullable String username,
            @Nullable String password,
            String credentialsId,
            String projectName,
            long projectId,
            String buildStep,
            @Nullable String groupId,
            @Nullable String teamPath, //used by pipeline
            Boolean sastEnabled,
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
            boolean failBuildOnNewResults,
            String failBuildOnNewSeverity,
            boolean osaEnabled,
            @Nullable Integer osaHighThreshold,
            @Nullable Integer osaMediumThreshold,
            @Nullable Integer osaLowThreshold,
            boolean generatePdfReport,
            boolean enableProjectPolicyEnforcement,
            String thresholdSettings,
            String vulnerabilityThresholdResult,
            @Nullable String includeOpenSourceFolders,
            @Nullable String excludeOpenSourceFolders,
            @Nullable String osaArchiveIncludePatterns,
            boolean osaInstallBeforeScan,
            boolean avoidDuplicateProjectScans) {
        this.useOwnServerCredentials = useOwnServerCredentials;
        this.serverUrl = serverUrl;
        this.username = username;
        this.password = Secret.fromString(password).getEncryptedValue();
        this.credentialsId = credentialsId;
        // Workaround for compatibility with Conditional BuildStep Plugin
        this.projectName = (projectName == null) ? buildStep : projectName;
        this.projectId = projectId;
        this.groupId = (groupId!= null && !groupId.startsWith("Provide Checkmarx"))? groupId: null;
        this.teamPath = teamPath;
        this.sastEnabled = sastEnabled;
        this.preset = (preset!= null && !preset.startsWith("Provide Checkmarx"))? preset: null;
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
        this.failBuildOnNewResults = failBuildOnNewResults;
        this.failBuildOnNewSeverity = failBuildOnNewSeverity;
        this.osaEnabled = osaEnabled;
        this.osaHighThreshold = osaHighThreshold;
        this.osaMediumThreshold = osaMediumThreshold;
        this.osaLowThreshold = osaLowThreshold;
        this.generatePdfReport = generatePdfReport;
        this.enableProjectPolicyEnforcement = enableProjectPolicyEnforcement;
        this.includeOpenSourceFolders = includeOpenSourceFolders;
        this.excludeOpenSourceFolders = excludeOpenSourceFolders;
        this.osaArchiveIncludePatterns = osaArchiveIncludePatterns;
        this.osaInstallBeforeScan = osaInstallBeforeScan;
        this.thresholdSettings = thresholdSettings;
        if (vulnerabilityThresholdResult != null) {
            this.vulnerabilityThresholdResult = Result.fromString(vulnerabilityThresholdResult);
        }
        this.avoidDuplicateProjectScans = avoidDuplicateProjectScans;
    }

    // Configuration fields getters
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

    public String getCredentialsId() {
        return credentialsId;
    }

    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
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

    public Boolean getSastEnabled() {
        return sastEnabled;
    }

    public void setSastEnabled(Boolean sastEnabled) {
        this.sastEnabled = sastEnabled;
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

    public String getFailBuildOnNewSeverity() {
        return failBuildOnNewSeverity;
    }

    public void setFailBuildOnNewSeverity(String failBuildOnNewSeverity) {
        this.failBuildOnNewSeverity = failBuildOnNewSeverity;
    }

    public boolean isFailBuildOnNewResults() {
        return failBuildOnNewResults;
    }

    public void setFailBuildOnNewResults(boolean failBuildOnNewResults) {
        this.failBuildOnNewResults = failBuildOnNewResults;
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

    @Nullable
    public String getOsaArchiveIncludePatterns() {
        return osaArchiveIncludePatterns;
    }

    public void setOsaArchiveIncludePatterns(@Nullable String osaArchiveIncludePatterns) {
        this.osaArchiveIncludePatterns = osaArchiveIncludePatterns;
    }

    @Nullable
    public boolean isOsaInstallBeforeScan() {
        return osaInstallBeforeScan;
    }

    public void setOsaInstallBeforeScan(@Nullable boolean osaInstallBeforeScan) {
        this.osaInstallBeforeScan = osaInstallBeforeScan;
    }

    public boolean isGeneratePdfReport() {
        return generatePdfReport;
    }

    public boolean isEnableProjectPolicyEnforcement() {
        return enableProjectPolicyEnforcement;
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
        if (result != null) {
            this.vulnerabilityThresholdResult = Result.fromString(result);
        }
    }

    public String getVulnerabilityThresholdResult() {
        if (vulnerabilityThresholdResult != null) {
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
    public void setEnableProjectPolicyEnforcement(boolean enableProjectPolicyEnforcement) {
        this.enableProjectPolicyEnforcement = enableProjectPolicyEnforcement;
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

        log = new CxLoggerAdapter(listener.getLogger());

        if ((sastEnabled == null || sastEnabled) && isSkipScan(run)) {
            log.info("Checkmarx scan skipped since the build was triggered by SCM. " +
                    "Visit plugin configuration page to disable this skip.");
            return;
        }

        //resolve configuration
        final DescriptorImpl descriptor = getDescriptor();
        EnvVars env = run.getEnvironment(listener);
        CxScanConfig config = resolveConfiguration(run, descriptor, env, log);

        //print configuration
        printConfiguration(config, log);

        //validate at least one scan type is enabled
        if (!config.getSastEnabled() && !config.getOsaEnabled()) {
            log.error("Both SAST and OSA are disabled. exiting");
            run.setResult(Result.FAILURE);
            return;
        }

        //create scans and retrieve results (in jenkins agent)
        CxScanCallable a = new CxScanCallable(config, listener);
        ScanResults scanResults = workspace.act(a);
        CxScanResult cxScanResult = new CxScanResult(run, config);

        //write reports to build dir
        File checkmarxBuildDir = new File(run.getRootDir(), "checkmarx");
        checkmarxBuildDir.mkdir();

        //in case of async mode, do not create reports (only the report of the latest scan)
        //and don't assert threshold vulnerabilities
        if (config.getSynchronous()) {
            failTheBuild(run, config, scanResults);

            //generate html report
            String reportName = generateHTMLReport(workspace, checkmarxBuildDir, config, scanResults);
            cxScanResult.setHtmlReportName(reportName);
            run.addAction(cxScanResult);


            //create sast reports
            SASTResults sastResults = scanResults.getSastResults();
            if (sastResults.isSastResultsReady()) {
                createSastReports(sastResults, checkmarxBuildDir, workspace);
                addEnvVarAction(run, sastResults);
                cxScanResult.setSastResults(sastResults);
            }

            //create osa reports
            OSAResults osaResults = scanResults.getOsaResults();
            if (osaResults.isOsaResultsReady()) {
                createOsaReports(scanResults.getOsaResults(), checkmarxBuildDir);
            }
            return;
        }
        //Asynchronous scan - add note message and previous build reports
        String reportName = generateHTMLReport(workspace, checkmarxBuildDir, config, scanResults);
        cxScanResult.setHtmlReportName(reportName);
        run.addAction(cxScanResult);

    }

    private CxScanConfig resolveConfiguration(Run<?, ?> run, DescriptorImpl descriptor, EnvVars env, CxLoggerAdapter log) throws IOException, InterruptedException {

        CxScanConfig ret = new CxScanConfig();

        //general
        ret.setCxOrigin("Jenkins");
        ret.setDisableCertificateValidation(!descriptor.isEnableCertificateValidation());

        //cx server
        CxCred cxCredentials = CxCred.resolveCred(this, descriptor, run);
        ret.setUrl(cxCredentials.getServerUrl().trim());
        ret.setUsername(cxCredentials.getUsername());
        ret.setPassword(cxCredentials.getPssd());

        //project
        ret.setProjectName(env.expand(projectName.trim()));
        ret.setTeamPath(teamPath);
        ret.setTeamId(groupId);
        ret.setJenkinsJob(run.getNumber());
        //scan control
        boolean isaAsync = !isWaitForResultsEnabled() && !(descriptor.isForcingVulnerabilityThresholdEnabled() && descriptor.isLockVulnerabilitySettings());
        ret.setSynchronous(!isaAsync);
        ret.setDenyProject(descriptor.isProhibitProjectCreation());

        //sast
        ret.setSastEnabled(this.sastEnabled == null || sastEnabled); //for backward compatibility, assuming if sastEnabled is not set, then sast is enabled

        if (ret.getSastEnabled()) {
            int presetId = parseInt(preset, log, "Invalid presetId: [%s]. Using default preset.", 0);
            ret.setPresetId(presetId);

            String excludeFolders = isGlobalExclusions() ? descriptor.getExcludeFolders() : getExcludeFolders();
            String filterPattern = isGlobalExclusions() ? descriptor.getFilterPattern() : getFilterPattern();
            ret.setSastFolderExclusions(env.expand(excludeFolders));
            ret.setSastFilterPattern(env.expand(filterPattern));

            if (descriptor.getScanTimeOutEnabled() && descriptor.getScanTimeoutDuration() != null && descriptor.getScanTimeoutDuration() > 0) {
                ret.setSastScanTimeoutInMinutes(descriptor.getScanTimeoutDuration());
            }

            ret.setScanComment(env.expand(comment));
            ret.setIncremental(isThisBuildIncremental(run.getNumber()));
            ret.setGeneratePDFReport(generatePdfReport);

            int configurationId = parseInt(sourceEncoding, log, "Invalid source encoding (configuration) value: [%s]. Using default configuration.", 1);
            ret.setEngineConfigurationId(configurationId);

            boolean useGlobalThreshold = shouldUseGlobalThreshold();
            boolean useJobThreshold = shouldUseJobThreshold();
            ret.setSastThresholdsEnabled(useGlobalThreshold || useJobThreshold);

            if (useGlobalThreshold) {
                ret.setSastHighThreshold(descriptor.getHighThresholdEnforcement());
                ret.setSastMediumThreshold(descriptor.getMediumThresholdEnforcement());
                ret.setSastLowThreshold(descriptor.getLowThresholdEnforcement());
                resolvedVulnerabilityThresholdResult = Result.fromString(descriptor.getJobGlobalStatusOnThresholdViolation().name());
            } else if (useJobThreshold) {
                ret.setSastHighThreshold(getHighThreshold());
                ret.setSastMediumThreshold(getMediumThreshold());
                ret.setSastLowThreshold(getLowThreshold());
                ret.setSastNewResultsThresholdEnabled(failBuildOnNewResults);
                ret.setSastNewResultsThresholdSeverity(failBuildOnNewSeverity);
                resolvedVulnerabilityThresholdResult = vulnerabilityThresholdResult;
            }
        }

        //osa
        ret.setOsaEnabled(osaEnabled);
        if (osaEnabled) {
            ret.setOsaFolderExclusions(env.expand(excludeOpenSourceFolders));
            ret.setOsaFilterPattern(env.expand(includeOpenSourceFolders));
            ret.setOsaArchiveIncludePatterns(osaArchiveIncludePatterns);
            ret.setOsaRunInstall(osaInstallBeforeScan);

            boolean useGlobalThreshold = shouldUseGlobalThreshold();
            boolean useJobThreshold = shouldUseJobThreshold();
            ret.setOsaThresholdsEnabled(useGlobalThreshold || useJobThreshold);

            if (useGlobalThreshold) {
                ret.setOsaHighThreshold(descriptor.getOsaHighThresholdEnforcement());
                ret.setOsaMediumThreshold(descriptor.getOsaMediumThresholdEnforcement());
                ret.setOsaLowThreshold(descriptor.getOsaLowThresholdEnforcement());
            } else if (useJobThreshold) {
                ret.setOsaHighThreshold(getOsaHighThreshold());
                ret.setOsaMediumThreshold(getOsaMediumThreshold());
                ret.setOsaLowThreshold(getOsaLowThreshold());
            }
        }

        if (!ret.getSynchronous()){
            enableProjectPolicyEnforcement = false;
        }
        ret.setEnablePolicyViolations(enableProjectPolicyEnforcement);
        return ret;
    }

    private void printConfiguration(CxScanConfig config, CxLoggerAdapter log) {
        log.info("---------------------------------------Configurations:------------------------------------");
        log.info("plugin version: " + CxConfig.version());
        log.info("server url: " + config.getUrl());
        log.info("username: " + config.getUsername());
        log.info("project name: " + config.getProjectName());
        log.info("team id: " + config.getTeamId());
        log.info("is synchronous mode: " + config.getSynchronous());
        log.info("deny project creation: " + config.getDenyProject());
        log.info("SAST scan enabled: " + config.getSastEnabled());
        log.info("Enable Project Policy Enforcement: " + config.getEnablePolicyViolations());
        if (config.getSastEnabled()) {
            log.info("preset id: " + config.getPresetId());
            log.info("SAST folder exclusions: " + config.getSastFolderExclusions());
            log.info("SAST filter pattern: " + config.getSastFilterPattern());
            log.info("SAST timeout: " + config.getSastScanTimeoutInMinutes());
            log.info("SAST scan comment: " + config.getScanComment());
            log.info("is incremental scan: " + config.getIncremental());
            log.info("is generate pfd report: " + config.getGeneratePDFReport());
            log.info("source code encoding id: " + config.getEngineConfigurationId());
            log.info("SAST thresholds enabled: " + config.getSastThresholdsEnabled());
            if (config.getSastThresholdsEnabled()) {
                log.info("SAST high threshold: " + config.getSastHighThreshold());
                log.info("SAST medium threshold: " + config.getSastMediumThreshold());
                log.info("SAST low threshold: " + config.getSastLowThreshold());
            }
        }

        log.info("OSA scan enabled: " + config.getOsaEnabled());
        if (config.getOsaEnabled()) {
            log.info("OSA folder exclusions: " + config.getOsaFolderExclusions());
            log.info("OSA filter patterns: " + config.getOsaFilterPattern());
            log.info("OSA archive includes: " + config.getOsaArchiveIncludePatterns());
            log.info("OSA run Execute dependency managers install packages command before Scan: " + config.getOsaRunInstall());
            log.info("OSA thresholds enabled: " + config.getOsaThresholdsEnabled());
            if (config.getOsaThresholdsEnabled()) {
                log.info("OSA high threshold: " + config.getOsaHighThreshold());
                log.info("OSA medium threshold: " + config.getOsaMediumThreshold());
                log.info("OSA low threshold: " + config.getOsaLowThreshold());
            }
        }
        log.info("------------------------------------------------------------------------------------------");
    }

    private void createSastReports(SASTResults sastResults, File checkmarxBuildDir, @Nonnull FilePath workspace) {
        File xmlReportFile = new File(checkmarxBuildDir, SCAN_REPORT_XML);
        try {
            FileUtils.writeByteArrayToFile(xmlReportFile, sastResults.getRawXMLReport());
            writeFileToWorkspaceReports(workspace, xmlReportFile);
        } catch (IOException e) {
            log.warn("Failed to write SAST XML report to workspace: " + e.getMessage());
        }

        if (sastResults.getPDFReport() != null) {
            File pdfReportFile = new File(checkmarxBuildDir, CxScanResult.PDF_REPORT_NAME);
            try {
                FileUtils.writeByteArrayToFile(pdfReportFile, sastResults.getPDFReport());
            } catch (IOException e) {
                log.warn("Failed to write SAST PDF report to workspace: " + e.getMessage());
            }
        }
    }

    private void createOsaReports(OSAResults osaResults, File checkmarxBuildDir) {
        writeJsonObjectToFile(osaResults.getResults(), new File(checkmarxBuildDir, OSA_SUMMERY_JSON), "OSA summery json report");
        writeJsonObjectToFile(osaResults.getOsaLibraries(), new File(checkmarxBuildDir, OSA_LIBRARIES_JSON), "OSA libraries json report");
        writeJsonObjectToFile(osaResults.getOsaVulnerabilities(), new File(checkmarxBuildDir, OSA_VULNERABILITIES_JSON), "OSA vulnerabilities json report");
    }

    private String generateHTMLReport(@Nonnull FilePath workspace, File checkmarxBuildDir, CxScanConfig config, ScanResults results) {
        String reportName = null;
        try {
            String reportHTML = SummaryUtils.generateSummary(results.getSastResults(), results.getOsaResults(), config);
            reportName = CxScanResult.resolveHTMLReportName(config.getSastEnabled(), config.getOsaEnabled());
            File reportFile = new File(checkmarxBuildDir, reportName);
            FileUtils.writeStringToFile(reportFile, reportHTML, Charset.defaultCharset());
            writeFileToWorkspaceReports(workspace, reportFile);
        } catch (IOException | TemplateException e) {
            log.warn("Failed to generate HTML report: " + e.getMessage());
        }
        return reportName;

    }

    private void writeJsonObjectToFile(Object jsonObj, File to, String description) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            String json = null;
            json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObj);
            FileUtils.writeStringToFile(to, json);
            //log.info(description + " file generated successfully. location: [" + to.getAbsolutePath() + "]");
            log.info("Copying file [" + to.getName() + "] to workspace [" + to.getAbsolutePath() + "]");
        } catch (Exception e) {
            log.error("Failed to write " + description + " to [" + to.getAbsolutePath() + "]");

        }
    }

    private boolean failTheBuild(Run<?, ?> run, CxScanConfig config, ScanResults ret) {
        //assert if expected exception is thrown  OR when vulnerabilities under threshold OR when policy violated
        String buildFailureResult = "";
        if (config.getSynchronous()) {
            buildFailureResult = ShragaUtils.getBuildFailureResult(config,ret.getSastResults(), ret.getOsaResults());
            if (!StringUtils.isEmpty(buildFailureResult) || ret.getSastWaitException() != null || ret.getSastCreateException() != null ||
                    ret.getOsaCreateException() != null || ret.getOsaWaitException() != null) {
                printBuildFailure(buildFailureResult, ret, log);
                if (resolvedVulnerabilityThresholdResult != null) {
                    run.setResult(resolvedVulnerabilityThresholdResult);
                }

                if (useUnstableOnError(getDescriptor())) {
                    run.setResult(Result.UNSTABLE);
                } else {
                    run.setResult(Result.FAILURE);
                }

            }
        }
        return StringUtils.isEmpty(buildFailureResult);
    }


    private void printBuildFailure(String thDescription, ScanResults ret, CxLoggerAdapter log) {
        log.error("********************************************");
        log.error(" The Build Failed for the Following Reasons: ");
        log.error("********************************************");

        logError(ret.getSastCreateException());
        logError(ret.getSastWaitException());
        logError(ret.getOsaCreateException());
        logError(ret.getOsaWaitException());

        if (thDescription != null) {
            String[] lines = thDescription.split("\\n");
            for (String s : lines) {
                log.error(s);
            }
        }

        log.error("-----------------------------------------------------------------------------------------\n");
        log.error("");
    }

    private void logError(Exception ex) {
        if (ex != null) {
            log.error(ex.getMessage());
        }
    }


    private void addEnvVarAction(Run<?, ?> run, SASTResults sastResults) {
        EnvVarAction envVarAction = new EnvVarAction();
        envVarAction.setCxSastResults(sastResults.getHigh(),
                sastResults.getMedium(),
                sastResults.getLow(),
                sastResults.getInformation());
        run.addAction(envVarAction);
    }

    private int parseInt(String number, CxLoggerAdapter log, String templateMessage, int defaultVal) {
        int ret = defaultVal;
        try {
            ret = Integer.parseInt(number);
        } catch (Exception e) {
            log.warn(String.format(templateMessage, number));
        }
        return ret;
    }

    private void writeFileToWorkspaceReports(FilePath workspace, File file) {

        String remoteDirPath = workspace.getRemote() + "/" + REPORTS_FOLDER;
        FileInputStream fis = null;

        try {
            String remoteFilePath = remoteDirPath + "/" + file.getName();
            log.info("Copying file [" + file.getName() + "] to workspace [" + remoteFilePath + "]");
            FilePath remoteFile = new FilePath(workspace.getChannel(), remoteFilePath);
            fis = new FileInputStream(file);
            remoteFile.copyFrom(fis);

        } catch (Exception e) {
            log.warn("Failed to write file [" + file.getName() + "] to workspace: " + e.getMessage());

        } finally {
            IOUtils.closeQuietly(fis);
        }

    }

    private boolean shouldUseGlobalThreshold() {
        final DescriptorImpl descriptor = getDescriptor();
        //locked by global or (job threshold enabled and points to 'global' and global is enabled)
        return (descriptor.isForcingVulnerabilityThresholdEnabled() && descriptor.isLockVulnerabilitySettings()) || (isVulnerabilityThresholdEnabled() && "global".equals(getThresholdSettings()) && descriptor.isForcingVulnerabilityThresholdEnabled());
    }

    private boolean shouldUseJobThreshold() {
        final DescriptorImpl descriptor = getDescriptor();
        //not locked by global and job threshold enabled and points to 'job'
        return !(descriptor.isForcingVulnerabilityThresholdEnabled() && descriptor.isLockVulnerabilitySettings()) && isVulnerabilityThresholdEnabled();
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
        public static final String DEFAULT_OSA_ARCHIVE_INCLUDE_PATTERNS = CxConfig.getDefaultOsaArchiveIncludePatterns();

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

        private String credentialsId;

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
        private Integer scanTimeoutDuration; // In minutes.
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

        public String getCredentialsId() {
            return credentialsId;
        }

        public void setCredentialsId(String credentialsId) {
            this.credentialsId = credentialsId;
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

        @Nullable
        public Integer getScanTimeoutDuration() {
            return scanTimeoutDuration;
        }

        public void setScanTimeoutDuration(@Nullable Integer scanTimeoutDurationInMinutes) {
            this.scanTimeoutDuration = scanTimeoutDurationInMinutes;
        }

        public FormValidation doCheckScanTimeoutDuration(@QueryParameter final Integer value) {
            return timeoutValid(value);
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
            if (getServerUrl() == null || getServerUrl().isEmpty()) {
                return "not set";
            }

            return "Server URL: " + getServerUrl();

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
        public FormValidation doTestConnection(@QueryParameter final String serverUrl, @QueryParameter final String password,
                                               @QueryParameter final String username, @QueryParameter final String timestamp, @QueryParameter final String credentialsId, @AncestorInPath Item item) {
            // timestamp is not used in code, it is one of the arguments to invalidate Internet Explorer cache

            CxCred cred = null;
            CxShragaClient shragaClient = null;
            try {
                cred = CxCred.resolveCred(true, serverUrl, username, getPasswordPlainText(password), credentialsId, this, item);
                CxCred.validateCxCredentials(cred);
                shragaClient = new CxShragaClient(cred.getServerUrl(), cred.getUsername(), cred.getPssd(), CX_ORIGIN, !this.isEnableCertificateValidation(), serverLog);
            } catch (Exception e) {
                return buildError(e, "Failed to init cx client");
            }

            try {
                shragaClient.login();
                try {
                    shragaClient.getTeamList();
                } catch (Exception e) {
                    return FormValidation.error("Connection Failed.\n" +
                            "Validate the provided login credentials and server URL are correct.\n" +
                            "In addition, make sure the installed plugin version is compatible with the CxSAST version according to CxSAST release notes.\n" +
                            "Error: " + e.getMessage());
                }

                return FormValidation.ok("Success");

            } catch (UnknownHostException e) {
                return buildError(e, "Failed to login to Chekmarx server");

            } catch (Exception e) {
                return buildError(e, "Failed to login to Chekmarx server");

            }
        }

        private FormValidation buildError(Exception e, String errorLogMessage) {
            serverLog.error(errorLogMessage, e);
            return FormValidation.error(e.getMessage());
        }

        // Prepares a cx client object to be connected and logged in
        /*
         *  Note: This method is called concurrently by multiple threads, refrain from using mutable
	     *  shared state to avoid synchronization issues.
	     */
        private CxShragaClient prepareLoggedInClient(CxCred credentials)
                throws IOException, CxClientException, CxTokenExpiredException {
            CxShragaClient ret = new CxShragaClient(credentials.getServerUrl(), credentials.getUsername(), credentials.getPssd(), CX_ORIGIN, !this.isEnableCertificateValidation(), serverLog);
            ret.login();
            return ret;
        }

        /*
         *  Note: This method is called concurrently by multiple threads, refrain from using mutable
         *  shared state to avoid synchronization issues.
         */
        public ComboBoxModel doFillProjectNameItems(@QueryParameter final boolean useOwnServerCredentials, @QueryParameter final String serverUrl,
                                                    @QueryParameter final String username, @QueryParameter final String password, @QueryParameter final String timestamp, @QueryParameter final String credentialsId, @AncestorInPath Item item) {
            // timestamp is not used in code, it is one of the arguments to invalidate Internet Explorer cache
            ComboBoxModel projectNames = new ComboBoxModel();

            try {
                CxCred credentials = CxCred.resolveCred(!useOwnServerCredentials, serverUrl, username, getPasswordPlainText(password), credentialsId, this, item);
                CxShragaClient shragaClient = prepareLoggedInClient(credentials);
                List<Project> projects = shragaClient.getAllProjects();

                for (Project p : projects) {
                    projectNames.add(p.getName());
                }

                return projectNames;

            } catch (Exception e) {
                serverLog.error("Failed to populate project list: " + e.toString(), e);
                return projectNames; // Return empty list of project names
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
                                              @QueryParameter final String username, @QueryParameter final String password, @QueryParameter final String timestamp, @QueryParameter final String credentialsId, @AncestorInPath Item item) {
            // timestamp is not used in code, it is one of the arguments to invalidate Internet Explorer cache
            ListBoxModel listBoxModel = new ListBoxModel();
            try {
                CxCred credentials = CxCred.resolveCred(!useOwnServerCredentials, serverUrl, username, StringEscapeUtils.escapeHtml4(getPasswordPlainText(password)), credentialsId, this, item);
                CxShragaClient shragaClient = prepareLoggedInClient(credentials);

                //todo import preset
                List<com.cx.restclient.sast.dto.Preset> presets = shragaClient.getPresetList();

                for (com.cx.restclient.sast.dto.Preset p : presets) {
                    listBoxModel.add(new ListBoxModel.Option(p.getName(), Integer.toString(p.getId())));
                }
                return listBoxModel;

            } catch (Exception e) {
                serverLog.error("Failed to populate preset list: " + e.toString());
                String message = "Provide Checkmarx server credentials to see presets list";
                listBoxModel.add(new ListBoxModel.Option(message, message));
                return listBoxModel;
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
                                                      @QueryParameter final String username, @QueryParameter final String password, @QueryParameter final String timestamp, @QueryParameter final String credentialsId, @AncestorInPath Item item) {
            // timestamp is not used in code, it is one of the arguments to invalidate Internet Explorer cache
            ListBoxModel listBoxModel = new ListBoxModel();
            try {

                CxCred credentials = CxCred.resolveCred(!useOwnServerCredentials, serverUrl, username, StringEscapeUtils.escapeHtml4(getPasswordPlainText(password)), credentialsId, this, item);

                CxShragaClient shragaClient = prepareLoggedInClient(credentials);
                List<CxNameObj> configurationList = shragaClient.getConfigurationSetList();

                for (CxNameObj cs : configurationList) {
                    listBoxModel.add(new ListBoxModel.Option(cs.getName(), Long.toString(cs.getId())));
                }

            } catch (Exception e) {
                serverLog.error("Failed to populate source encodings list: " + e.getMessage());
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
                                               @QueryParameter final String username, @QueryParameter final String password, @QueryParameter final String timestamp, @QueryParameter final String credentialsId, @AncestorInPath Item item) {
            // timestamp is not used in code, it is one of the arguments to invalidate Internet Explorer cache
            ListBoxModel listBoxModel = new ListBoxModel();
            try {
                CxCred credentials = CxCred.resolveCred(!useOwnServerCredentials, serverUrl, username, StringEscapeUtils.escapeHtml4(getPasswordPlainText(password)), credentialsId, this, item);

                CxShragaClient shragaClient = prepareLoggedInClient(credentials);
                List<Team> teamList = shragaClient.getTeamList();
                for (Team team : teamList) {
                    listBoxModel.add(new ListBoxModel.Option(team.getFullName(), team.getId()));
                }

                return listBoxModel;

            } catch (Exception e) {
                serverLog.error("Failed to populate team list: " + e.toString());
                String message = "Provide Checkmarx server credentials to see teams list";
                listBoxModel.add(new ListBoxModel.Option(message, message));
                return listBoxModel;
            }

        }

        public ListBoxModel doFillFailBuildOnNewSeverityItems() {
            ListBoxModel listBoxModel = new ListBoxModel();
            listBoxModel.add(new ListBoxModel.Option("High", "HIGH"));
            listBoxModel.add(new ListBoxModel.Option("Medium", "MEDIUM"));
            listBoxModel.add(new ListBoxModel.Option("Low", "LOW"));
            return listBoxModel;

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

        private FormValidation timeoutValid(final Integer value) {
            if (value == null || value >= MINIMUM_TIMEOUT_IN_MINUTES) {
                return FormValidation.ok();
            } else {
                return FormValidation.error("Number must be greater than or equal to " + MINIMUM_TIMEOUT_IN_MINUTES);
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

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item item, @QueryParameter String credentialsId) {
            StandardListBoxModel result = new StandardListBoxModel();
            if (item == null) {
                if (!Jenkins.getInstance().hasPermission(Jenkins.ADMINISTER)) {
                    return result.add(credentialsId);
                }
            } else {
                if (!item.hasPermission(Item.EXTENDED_READ)
                        && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
                    return result.add(credentialsId);
                }
            }

            return result
                    .withEmptySelection()
                    .withAll(CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class, item, null, Collections.<DomainRequirement>emptyList()))
                    .withMatching(CredentialsMatchers.withId(credentialsId));
        }

        public boolean isOldCredentials() {
            return StringUtils.isEmpty(credentialsId) && (username != null || password != null);
        }

    }
}
