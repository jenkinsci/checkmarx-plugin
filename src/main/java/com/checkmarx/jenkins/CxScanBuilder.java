package com.checkmarx.jenkins;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cx.restclient.CxClientDelegator;
import com.cx.restclient.common.summary.SummaryUtils;
import com.cx.restclient.configuration.CxScanConfig;
import com.cx.restclient.dto.*;
import com.cx.restclient.dto.scansummary.ScanSummary;
import com.cx.restclient.exception.CxClientException;
import com.cx.restclient.osa.dto.OSAResults;
import com.cx.restclient.sast.dto.CxNameObj;
import com.cx.restclient.sast.dto.Preset;
import com.cx.restclient.sast.dto.Project;
import com.cx.restclient.sast.dto.SASTResults;
import com.cx.restclient.ast.dto.sca.AstScaConfig;
import com.cx.restclient.ast.dto.sca.AstScaResults;
import com.cx.restclient.dto.SourceLocationType;
import com.cx.restclient.sast.utils.LegacyClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import freemarker.template.TemplateException;
import hudson.*;
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
import java.io.*;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
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
    public static final String OSA_SUMMERY_JSON = "OSASummary.json";
    public static final String OSA_LIBRARIES_JSON = "OSALibraries.json";
    public static final String OSA_VULNERABILITIES_JSON = "OSAVulnerabilities.json";

    public static final String SCA_SUMMERY_JSON = "SCASummary.json";
    public static final String SCA_LIBRARIES_JSON = "SCALibraries.json";
    public static final String SCA_VULNERABILITIES_JSON = "SCAVulnerabilities.json";

    private static final String PDF_URL_TEMPLATE = "/%scheckmarx/pdfReport";
    private static final String PDF_URL = "checkmarx/pdfReport";
    private static final String REQUEST_ORIGIN = "Jenkins";

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
    @Nullable
    private Integer osaHighThreshold;
    @Nullable
    private Integer osaMediumThreshold;
    @Nullable
    private Integer osaLowThreshold;

    // Fields marked as transient are preserved for backward compatibility.
    // They are read from job config, but are not written back when the data is saved.
    private transient boolean osaEnabled;
    @Nullable
    private transient String includeOpenSourceFolders;//OSA include/exclude wildcard patterns
    @Nullable
    private transient String excludeOpenSourceFolders;//OSA exclude folders
    @Nullable
    private transient String osaArchiveIncludePatterns;
    private transient boolean osaInstallBeforeScan;

    /**
     * null value means that dependency scan is disabled.
     */
    @Nullable
    private DependencyScanConfig dependencyScanConfig;

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
    private Boolean generateXmlReport = true;

    public static final int MINIMUM_TIMEOUT_IN_MINUTES = 1;
    public static final String REPORTS_FOLDER = "Checkmarx/Reports";

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
            @Nullable Integer osaHighThreshold,
            @Nullable Integer osaMediumThreshold,
            @Nullable Integer osaLowThreshold,
            boolean generatePdfReport,
            boolean enableProjectPolicyEnforcement,
            String thresholdSettings,
            String vulnerabilityThresholdResult,
            boolean avoidDuplicateProjectScans,
            Boolean generateXmlReport
    ) {
        this.useOwnServerCredentials = useOwnServerCredentials;
        this.serverUrl = serverUrl;
        this.username = username;
        this.password = Secret.fromString(password).getEncryptedValue();
        this.credentialsId = credentialsId;
        // Workaround for compatibility with Conditional BuildStep Plugin
        this.projectName = (projectName == null) ? buildStep : projectName;
        this.projectId = projectId;
        this.groupId = (groupId != null && !groupId.startsWith("Provide Checkmarx")) ? groupId : null;
        this.teamPath = teamPath;
        this.sastEnabled = sastEnabled;
        this.preset = (preset != null && !preset.startsWith("Provide Checkmarx")) ? preset : null;
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
        this.osaHighThreshold = osaHighThreshold;
        this.osaMediumThreshold = osaMediumThreshold;
        this.osaLowThreshold = osaLowThreshold;
        this.generatePdfReport = generatePdfReport;
        this.enableProjectPolicyEnforcement = enableProjectPolicyEnforcement;
        this.thresholdSettings = thresholdSettings;
        if (vulnerabilityThresholdResult != null) {
            this.vulnerabilityThresholdResult = Result.fromString(vulnerabilityThresholdResult);
        }
        this.avoidDuplicateProjectScans = avoidDuplicateProjectScans;
        this.generateXmlReport = (generateXmlReport == null) ? true : generateXmlReport;
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

    @Nullable
    public boolean isOsaInstallBeforeScan() {
        return osaInstallBeforeScan;
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


    public Boolean getGenerateXmlReport() {
        return generateXmlReport;
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
    public void setJobStatusOnError(JobStatusOnError jobStatusOnError) {
        this.jobStatusOnError = jobStatusOnError;
    }

    @DataBoundSetter
    public void setAvoidDuplicateProjectScans(boolean avoidDuplicateProjectScans) {
        this.avoidDuplicateProjectScans = avoidDuplicateProjectScans;
    }

    @DataBoundSetter
    public void setGenerateXmlReport(Boolean generateXmlReport) {
        this.generateXmlReport = generateXmlReport;
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

    public DependencyScanConfig getDependencyScanConfig() {
        return dependencyScanConfig;
    }

    @DataBoundSetter
    public void setDependencyScanConfig(DependencyScanConfig dependencyScanConfig) {
        this.dependencyScanConfig = dependencyScanConfig;
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
        if (!config.isSastEnabled() && !config.isAstScaEnabled() && !config.isOsaEnabled()) {
            log.error("Both SAST and dependency scan are disabled. Exiting.");
            run.setResult(Result.FAILURE);
            return;
        }

        final CxScanCallable action = new CxScanCallable(config, listener);

        //create scans and retrieve results (in jenkins agent)
        RemoteScanInfo scanInfo = workspace.act(action);
        ScanResults scanResults = scanInfo.getScanResults();

        // We'll need this for the HTML report.
        config.setCxARMUrl(scanInfo.getCxARMUrl());

        CxScanResult cxScanResult = new CxScanResult(run, config);

        //write reports to build dir
        File checkmarxBuildDir = new File(run.getRootDir(), "checkmarx");
        checkmarxBuildDir.mkdir();

        if (config.getGeneratePDFReport()) {
            String path = "";
            // run.getUrl() returns a URL path similar to job/MyJobName/124/
            //getRootUrl() will return the value of "Manage Jenkins->configuration->Jenkins URL"
            String baseUrl = Jenkins.getInstance().getRootUrl();
            if (StringUtils.isNotEmpty(baseUrl)) {
                URL parsedUrl = new URL(baseUrl);
                path = parsedUrl.getPath();
            }
            if (!(path.equals("/"))) {
                //to handle this Jenkins root url,EX: http://localhost:8081/jenkins
                Path pdfUrlPath = Paths.get(path, run.getUrl(), PDF_URL);
                scanResults.getSastResults().setSastPDFLink(pdfUrlPath.toString());
            } else {
                //to handle this Jenkins root url,EX: http://localhost:8081/
                String pdfUrl = String.format(PDF_URL_TEMPLATE, run.getUrl());
                scanResults.getSastResults().setSastPDFLink(pdfUrl);
            }
        }

        //in case of async mode, do not create reports (only the report of the latest scan)
        //and don't assert threshold vulnerabilities

        failTheBuild(run, config, scanResults);
        if (config.getSynchronous()) {

            //generate html report
            String reportName = generateHTMLReport(workspace, checkmarxBuildDir, config, scanResults);
            cxScanResult.setHtmlReportName(reportName);
            run.addAction(cxScanResult);


            //create sast reports
            SASTResults sastResults = scanResults.getSastResults();
            if (sastResults != null && sastResults.isSastResultsReady()) {
                if (config.getGenerateXmlReport() == null || config.getGenerateXmlReport()) {
                    createSastReports(sastResults, checkmarxBuildDir, workspace);
                }
                addEnvVarAction(run, sastResults);
                cxScanResult.setSastResults(sastResults);
            }

            //create osa reports
            OSAResults osaResults = scanResults.getOsaResults();
            AstScaResults scaResults = scanResults.getScaResults();
            if (osaResults != null && osaResults.isOsaResultsReady()) {
                createOsaReports(osaResults, checkmarxBuildDir);
            } else if (scaResults != null && scaResults.isScaResultReady()) {
                createScaReports(scaResults, checkmarxBuildDir);
            }
            return;
        }
        //Asynchronous scan - add note message and previous build reports
        String reportName = generateHTMLReport(workspace, checkmarxBuildDir, config, scanResults);
        cxScanResult.setHtmlReportName(reportName);
        run.addAction(cxScanResult);

    }

    private void createScaReports(AstScaResults scaResults, File checkmarxBuildDir) {
        writeJsonObjectToFile(scaResults.getSummary(), new File(checkmarxBuildDir, SCA_SUMMERY_JSON), "OSA summary json report");
        writeJsonObjectToFile(scaResults.getPackages(), new File(checkmarxBuildDir, SCA_LIBRARIES_JSON), "OSA libraries json report");
        writeJsonObjectToFile(scaResults.getFindings(), new File(checkmarxBuildDir, SCA_VULNERABILITIES_JSON), "OSA vulnerabilities json report");
    }

    private CxScanConfig resolveConfiguration(Run<?, ?> run, DescriptorImpl descriptor, EnvVars env, CxLoggerAdapter log) {
        CxScanConfig ret = new CxScanConfig();

        //general
        ret.setCxOrigin(REQUEST_ORIGIN);
        ret.setDisableCertificateValidation(!descriptor.isEnableCertificateValidation());
        ret.setProxyConfig(ProxyHelper.getProxyConfig());
        ret.setMvnPath(descriptor.getMvnPath());

        //cx server
        CxCredentials cxCredentials = CxCredentials.resolveCred(this, descriptor, run);
        ret.setUrl(cxCredentials.getServerUrl().trim());
        ret.setUsername(cxCredentials.getUsername());
        ret.setPassword(Aes.decrypt(cxCredentials.getPassword(), cxCredentials.getUsername()));

        //project
        ret.setProjectName(env.expand(projectName.trim()));
        ret.setTeamPath(teamPath);
        ret.setTeamId(groupId);

        //scan control
        boolean isaAsync = !isWaitForResultsEnabled() && !(descriptor.isForcingVulnerabilityThresholdEnabled() && descriptor.isLockVulnerabilitySettings());
        ret.setSynchronous(!isaAsync);
        ret.setDenyProject(descriptor.isProhibitProjectCreation());

        //sast
        ret.setSastEnabled(this.sastEnabled == null || sastEnabled); //for backward compatibility, assuming if sastEnabled is not set, then sast is enabled

        if (ret.isSastEnabled()) {
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
            ret.setAvoidDuplicateProjectScans(avoidDuplicateProjectScans);
            ret.setGenerateXmlReport(generateXmlReport);

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

        configureDependencyScan(run, descriptor, env, ret);

        if (!ret.getSynchronous()) {
            enableProjectPolicyEnforcement = false;
        }
        ret.setEnablePolicyViolations(enableProjectPolicyEnforcement);

        return ret;
    }

    private void configureDependencyScan(Run<?, ?> run, DescriptorImpl descriptor, EnvVars env, CxScanConfig config) {
        boolean dependencyScanEnabled = dependencyScanConfig != null;
        if (!dependencyScanEnabled) {
            return;
        }

        DependencyScanConfig effectiveConfig;
        if (dependencyScanConfig.overrideGlobalConfig) {
            log.info("Using job-specific dependency scan configuration.");
            effectiveConfig = dependencyScanConfig;
        } else {
            log.info("Using globally defined dependency scan configuration.");
            effectiveConfig = descriptor.getDependencyScanConfig();
        }

        if (effectiveConfig == null) {
            return;
        }

        config.addScannerType(effectiveConfig.dependencyScannerType);

        config.setOsaFilterPattern(env.expand(effectiveConfig.dependencyScanPatterns));
        config.setOsaFolderExclusions(env.expand(effectiveConfig.dependencyScanExcludeFolders));

        boolean useGlobalThreshold = shouldUseGlobalThreshold();
        boolean useJobThreshold = shouldUseJobThreshold();
        config.setOsaThresholdsEnabled(useGlobalThreshold || useJobThreshold);

        if (useGlobalThreshold) {
            config.setOsaHighThreshold(descriptor.getOsaHighThresholdEnforcement());
            config.setOsaMediumThreshold(descriptor.getOsaMediumThresholdEnforcement());
            config.setOsaLowThreshold(descriptor.getOsaLowThresholdEnforcement());
        } else if (useJobThreshold) {
            config.setOsaHighThreshold(getOsaHighThreshold());
            config.setOsaMediumThreshold(getOsaMediumThreshold());
            config.setOsaLowThreshold(getOsaLowThreshold());
        }

        if (config.isOsaEnabled()) {
            config.setOsaArchiveIncludePatterns(effectiveConfig.osaArchiveIncludePatterns.trim());
            config.setOsaRunInstall(effectiveConfig.osaInstallBeforeScan);
        } else if (config.isAstScaEnabled()) {
            config.setAstScaConfig(getScaConfig(run, effectiveConfig));
        }
    }

    private AstScaConfig getScaConfig(Run<?, ?> run, DependencyScanConfig dsConfig) {
        AstScaConfig result = new AstScaConfig();
        result.setApiUrl(dsConfig.scaServerUrl);
        result.setAccessControlUrl(dsConfig.scaAccessControlUrl);
        result.setWebAppUrl(dsConfig.scaWebAppUrl);
        result.setTenant(dsConfig.scaTenant);

        UsernamePasswordCredentials credentials = CxCredentials.getCredentialsById(dsConfig.scaCredentialsId, run);
        if (credentials != null) {
            result.setUsername(credentials.getUsername());
            result.setPassword(credentials.getPassword().getPlainText());
        } else {
            log.warn("CxSCA credentials are not specified.");
        }
        return result;
    }

    private ScannerType getDependencyScannerType(CxScanConfig config) {
        ScannerType result;
        if (config.isOsaEnabled()) {
            result = ScannerType.OSA;
        } else if (config.isAstScaEnabled()) {
            result = ScannerType.AST_SCA;
        } else {
            result = null;
        }
        return result;
    }

    private void printConfiguration(CxScanConfig config, CxLoggerAdapter log) {
        log.info("---------------------------------------Configurations:------------------------------------");
        log.info("plugin version: {}", CxConfig.version());
        log.info("server url: " + config.getUrl());
        log.info("username: " + config.getUsername());
        log.info("project name: " + config.getProjectName());
        log.info("team id: " + config.getTeamId());
        log.info("is synchronous mode: " + config.getSynchronous());
        log.info("deny new project creation: " + config.getDenyProject());
        log.info("SAST scan enabled: " + config.isSastEnabled());
        log.info("avoid duplicated projects scans: " + config.isAvoidDuplicateProjectScans());
        log.info("enable Project Policy Enforcement: " + config.getEnablePolicyViolations());

        ScannerType scannerType = getDependencyScannerType(config);
        String dependencyScannerType = scannerType != null ? scannerType.getDisplayName() : "NONE";

        log.info("Dependency scanner type: {}", dependencyScannerType);
        if (config.isSastEnabled()) {
            log.info("preset id: " + config.getPresetId());
            log.info("SAST folder exclusions: " + config.getSastFolderExclusions());
            log.info("SAST filter pattern: " + config.getSastFilterPattern());
            log.info("SAST timeout: " + config.getSastScanTimeoutInMinutes());
            log.info("SAST scan comment: " + config.getScanComment());
            log.info("is incremental scan: " + config.getIncremental());
            log.info("is generate full XML report: " + config.getGenerateXmlReport());
            log.info("is generate pfd report: " + config.getGeneratePDFReport());
            log.info("source code encoding id: " + config.getEngineConfigurationId());
            log.info("SAST thresholds enabled: " + config.getSastThresholdsEnabled());
            if (config.getSastThresholdsEnabled()) {
                log.info("SAST high threshold: " + config.getSastHighThreshold());
                log.info("SAST medium threshold: " + config.getSastMediumThreshold());
                log.info("SAST low threshold: " + config.getSastLowThreshold());
            }
        }

        if (config.isOsaEnabled() || config.isAstScaEnabled()) {
            log.info("Dependency scan configuration:");
            log.info("  folder exclusions: " + config.getOsaFolderExclusions());
            log.info("  filter patterns: " + config.getOsaFilterPattern());
            log.info("  thresholds enabled: " + config.getOsaThresholdsEnabled());
            if (config.getOsaThresholdsEnabled()) {
                log.info("  high threshold: " + config.getOsaHighThreshold());
                log.info("  medium threshold: " + config.getOsaMediumThreshold());
                log.info("  low threshold: " + config.getOsaLowThreshold());
            }
            if (config.isOsaEnabled()) {
                log.info("  OSA archive includes: " + config.getOsaArchiveIncludePatterns());
                log.info("  OSA run Execute dependency managers install packages command before Scan: " + config.getOsaRunInstall());
            }
        }

        ProxyConfig proxyConfig = config.getProxyConfig();
        if (proxyConfig != null) {
            log.info("Proxy configuration:");
            log.info("  host: " + proxyConfig.getHost());
            log.info("  port: " + proxyConfig.getPort());
            log.info("  user: " + proxyConfig.getUsername());
            log.info("  password: *************");
        } else {
            log.info("Proxy: not set");
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
            String reportHTML = SummaryUtils.generateSummary(results.getSastResults(), results.getOsaResults(), results.getScaResults(), config);
            List<String> log = Readfile("C:\\Users\\Administrator\\Desktop\\Logs\\20201110T234653-11_a7c48c9c-7618-46f6-b3a4-89cc72b95213.log");

            CxResultsInfo resultsInfo = new CxResultsInfo(log);
            resultsInfo.generateResults();

            // Update HTML with resultsInfo vars
            // For example: replace TotalFiles with resultsInfo.getTotalFiles()
            // (...)

            String reportLogHTML = "" +
                    "\n" +
                    "<style>\n" +
                    "    \n" +
                    "    .cx-report .results-report .chart .bar-4{\n" +
                    "        position: relative;\n" +
                    "        background-color: #00cc00;\n" +
                    "    }\n" +
                    "    .cx-report .results-report .chart .bar-5{\n" +
                    "        position: relative;\n" +
                    "        background-color: #ffff00;\n" +
                    "    }\n" +
                    "    .cx-report .results-report .chart .bar-6 {\n" +
                    "        position: relative;\n" +
                    "        background-color: #ff0000;\n" +
                    "    }\n" +
                    "</style>" +
                    "<head>\n" +
                    "    <script src=\"https://kit.fontawesome.com/a076d05399.js\"></script>\n" +
                    "</head>\n" +
                    "<div id=\"cx-report\" class=\"cx-report\">\n" +
                    "    <div class=\"report-title\">\n" +
                    "        <div class=\"cx-report-title\">Checkmarx Log Report</div>\n" +
                    "<div id=\"results-report\" class=\"results-report\">\n" +
                    "        <div class=\"summary-section\">\n" +
                    "            <div id=\"summary-results\" class=\"summary-results\">\n" +
                    "                <div class=\"sast-summary chart-large\" id=\"sast-summary\">\n" +
                    "                    <div class=\"summary-report-title sast\">\n" +
                    "                        <div class=\"summary-title-text sast\">CxSAST Risk Gauge</div>\n" +
                    "                            <div id=\"sast-title-links\" class=\"title-links\">\n" +
                    "                            </div>\n" +
                    "                    </div>\n" +
                    "                    <div id=\"chart_div\" style=\"width: 400px; height: 120px;\" ></div>\n" +
                    "            </div>\n" +
                    "        </div>" +
                    "</div>" +

                    "    <div id=\"results-report\" class=\"results-report\">\n" +
                    "\n" +
                    "        <div class=\"summary-section\">\n" +
                    "            <div id=\"summary-results\" class=\"summary-results\">\n" +
                    "\n" +
                    "                <div class=\"sast-summary chart-large\" id=\"sast-summary\">\n" +
                    "                    <div class=\"summary-report-title sast\">\n" +
                    "                        <div class=\"summary-title-text sast\">CxSAST Log Status</div>\n" +
                    "                            <div id=\"sast-title-links\" class=\"title-links\">\n" +
                    "                            </div>\n" +
                    "                    </div>\n" +
                    "                        <!--sast-chart-->\n" +
                    "                        <div class=\"summary-chart\" id=\"sast-results\">\n" +
                    "                            <div class=\"top-of-chart\">\n" +
                    "                            </div>\n" +
                    "\n" +
                    "                            <ul class=\"chart\">\n" +
                    "                                <!--sast-high-->\n" +
                    "                                <li>\n" +
                    "                                        <span class=\"bar-4\" id=\"bar-high\" style=\"height: 214.2px\">\n" +
                    "                                            <div id=\"tooltip-high\">\n" +
                    "                                            </div>\n" +
                    "                                            <div id=\"high-new-scans\" class=\"new-scans\"\n" +
                    "                                                 style=\"height: 0px\"></div>\n" +
                    "                                            <div id=\"high-recurrent-scans\" \n" +
                    "                                                 style=\"height: 214.2px\"></div>\n" +
                    "                                        </span>\n" +
                    "\n" +
                    "                                    <div class=\"bar-title-wrapper\">\n" +
                    "                                        <div class=\"bar-title-container\">\n" +
                    "                                            <div class=\"bar-title\"><i class=\"fas fa-file-code\"></i> Files -</div>\n" +
                    "                                            <div class=\"bar-count\" id=\"bar-count-high\" >" + resultsInfo.getTotalFiles() +"</div> <!--VALUE-->\n" +
                    "                                        </div>\n" +
                    "                                    </div>\n" +
                    "                                </li>\n" +
                    "\n" +
                    "                                <!--sast-medium-->\n" +
                    "                                <li>\n" +
                    "                                        <span class=\"bar-5\" id=\"bar-med\" style=\"height: 142.8px\">\n" +
                    "                                                <div id=\"tooltip-med\">\n" +
                    "                                                </div>\n" +
                    "                                                <div id=\"med-new-scans\" class=\"new-scans\"\n" +
                    "                                                     style=\"height: 0px\"></div>\n" +
                    "                                                <div id=\"med-recurrent-scans\" \n" +
                    "                                                     style=\"height: 142.8px\"></div>\n" +
                    "                                        </span>\n" +
                    "                                    <div class=\"bar-title-wrapper\">\n" +
                    "                                        <div class=\"bar-title-container\">\n" +
                    "                                            <div class=\"bar-title\"><i class=\"fas fa-object-group\"></i> DOM -</div>\n" +
                    "                                            <div class=\"bar-count\" id=\"bar-count-med\">" + resultsInfo.getNumberOfDomObjects() +"</div> <!--VALUE-->\n" +
                    "                                        </div>\n" +
                    "                                    </div>\n" +
                    "                                </li>\n" +
                    "\n" +
                    "                                <!--sast-low-->\n" +
                    "                                <li>\n" +
                    "                                        <span class=\"bar-6\" id=\"bar-low\" style=\"height: 142.8px\">\n" +
                    "                                            <div id=\"tooltip-low\">\n" +
                    "                                            </div>\n" +
                    "                                            <div id=\"low-new-scans\" class=\"new-scans\"\n" +
                    "                                                 style=\"height: 0px\"></div>\n" +
                    "                                            <div id=\"low-recurrent-scans\" \n" +
                    "                                                 style=\"height: 142.8px\"></div>\n" +
                    "                                        </span>\n" +
                    "                                    <div class=\"bar-title-wrapper\">\n" +
                    "                                        <div class=\"bar-title-container\">\n" +
                    "                                            <div class=\"bar-title\"><i class=\"fas fa-bug\"></i> Exceptions -</div>\n" +
                    "                                            <div class=\"bar-count\" id=\"bar-count-low\"></div> <!--VALUE-->\n" +
                    "                                        </div>\n" +
                    "                                    </div>\n" +
                    "                                </li>\n" +
                    "                            </ul>\n" +
                    "                        </div>\n" +
                    "                </div>\n" +
                    "\n" +
                    "    </div>\n" +
                    "</div>\n" +
                    "\n" +
                    "\n" +
                    "            <div id=\"sast-full\" class=\"sast-full full-results-section\">\n" +
                    "                <div class=\"summary-table-row cxsast-full\">\n" +
                    "                    <div class=\"title-column\">\n" +
                    "                        <div class=\"summary-title\">\n" +
                    "                            <div class=\"sum1\">CxSAST</div>\n" +
                    "                            <div class=\"sum1\">Log Analysis</div>\n" +
                    "                        </div>\n" +
                    "                        <div class=\"detailed-report\">\n" +
                    "                            <div class=\"full-downloads sast-downloads\">\n" +
                    "                                <div class=\"report-link link-to-result\">\n" +
                    "                                    <a href=\"http://2020hackaton/CxWebClient/ViewerMain.aspx?scanId=1000005&ProjectID=3\" class=\"pdf-report\" id=\"sast-code-viewer-link\"\n" +
                    "                                       target=\"_top\">\n" +
                    "                                    </a>\n" +
                    "                                </div>\n" +
                    "                            </div>\n" +
                    "                        </div>\n" +
                    "                    </div>\n" +
                    "                    <div class=\"main-column\">\n" +
                    "                        <div class=\"full-start-end\">\n" +
                    "                            <div class=\"full-start\">\n" +
                    "                                <div class=\"full-start-end-text-date\">\n" +
                    "                                    <div class=\"full-start-end-text\">\n" +
                    "                                        <i class=\"far fa-clock\"></i> Scan Time:\n" +
                    "                                    </div>\n" +
                    "                                    <div class=\"full-start-end-date\"\n" +
                    "                                         id=\"sast-full-start-date\">" + resultsInfo.getScanDurationPercentage() +"</div>   <!--VALUE-->\n" +
                    "                                </div>\n" +
                    "                            </div>\n" +
                    "\n" +
                    "                            <!--sast-full-end-->\n" +
                    "                            <div class=\"full-end\">\n" +
                    "                                <div class=\"full-start-end-text-date\">\n" +
                    "                                    <div class=\"full-start-end-text\">\n" +
                    "                                        <i class=\"fas fa-tachometer-alt\"></i> Scan Coverage:\n" +
                    "                                    </div>\n" +
                    "                                    <div class=\"full-start-end-date\" id=\"sast-full-end-date\">" + resultsInfo.getScanCoverage() +"</div><!--VALUE-->\n" +
                    "                                </div>\n" +
                    "                            </div>\n" +
                    "\n" +
                    "                            <!--sast-full-files-->\n" +
                    "                            <div class=\"full-files\">\n" +
                    "                                <div class=\"full-start-end-text-date\">\n" +
                    "                                    <div class=\"full-start-end-text\">\n" +
                    "                                        <i class=\"far fa-object-group\"></i> DOM Objects:\n" +
                    "                                    </div>\n" +
                    "                                    <div class=\"full-start-end-date\" id=\"sast-full-files\">17</div>\n" +
                    "                                </div>\n" +
                    "                            </div>\n" +
                    "\n" +
                    "                        </div>\n" +
                    "\n" +
                    "                            <div id=\"sast-cve-table-high-container\">\n" +
                    "                                <div class=\"full-severity-title\">\n" +
                    "                                    <div class=\"severity-title-name\"><i class=\"far fa-file-code\"></i> Files</i>\n" +
                    "                                        </div>\n" +
                    "                                </div>\n" +
                    "                                <table id=\"sast-cve-table-high\" class=\"cve-table sast-cve-table sast-cve-table-high\">\n" +
                    "                                    <tr>\n" +
                    "                                        <th>Types</th>\n" +
                    "                                        <th>##</th>\n" +
                    "                                    </tr>\n" +
                    "                                            <tr>\n" +
                    "                                                <td>Total Files</td>\n" +
                    "                                                <td>" + resultsInfo.getTotalFiles() +"</td><!--VALUE-->\n" +
                    "                                            </tr>\n" +
                    "\t\t\t\t\t\t\t\t\t\t\t<tr>\n" +
                    "                                                <td>Good Files</td>\n" +
                    "                                                <td>" + resultsInfo.getGoodFiles() +"</td><!--VALUE-->\n" +
                    "                                            </tr>\n" +
                    "                                            <tr>\n" +
                    "                                                <td>Partially Good Files</td>\n" +
                    "                                                <td>" + resultsInfo.getGoodFiles() +"</td><!--VALUE-->\n" +
                    "                                            </tr>\n" +
                    "                                            <tr>\n" +
                    "                                                <td>Bad Files</td>\n" +
                    "                                                <td>" + resultsInfo.getBadFiles() +"</td><!--VALUE-->\n" +
                    "                                            </tr>\n" +
                    "                                </table>\n" +
                    "                            </div>\n" +
                    "\n" +
                    "                            <div id=\"sast-cve-table-medium-container\">\n" +
                    "                                <div class=\"full-severity-title\">\n" +
                    "                                    <div class=\"severity-title-name\"><i class=\"fas fa-code\"></i> Lines of Code</div>\n" +
                    "                                </div>\n" +
                    "                                <table id=\"sast-cve-table-medium\"\n" +
                    "                                       class=\"cve-table sast-cve-table sast-cve-table-medium\">\n" +
                    "                                    <tr>\n" +
                    "                                        <th>Types</th>\n" +
                    "                                        <th>##</th>\n" +
                    "                                    </tr>\n" +
                    "                                            <tr>\n" +
                    "                                                <td>Total LOC</td>\n" +
                    "                                                <td>" + resultsInfo.getParsedLoc() +"</td><!--VALUE-->\n" +
                    "                                            </tr>\n" +
                    "                                            <tr>\n" +
                    "                                                <td>Good LOC</td>\n" +
                    "                                                <td>" + resultsInfo.getGoodLoc() +"</td><!--VALUE-->\n" +
                    "                                            </tr>\n" +
                    "                                            <tr>\n" +
                    "                                                <td>Bad LOC</td>\n" +
                    "                                                <td>" + resultsInfo.getBadLoc() +"</td><!--VALUE-->\n" +
                    "                                            </tr>\n" +
                    "                                </table>\n" +
                    "                            </div>\n" +
                    "\n" +
                    "                            <div id=\"sast-cve-table-low-container\">\n" +
                    "                                <div class=\"full-severity-title\">\n" +
                    "                                    <div class=\"severity-title-name\"><i class=\"fas fa-sticky-note\"></i> Messages</div>\n" +
                    "                                </div>\n" +
                    "                                <table id=\"sast-cve-table-low\" class=\"cve-table sast-cve-table sast-cve-table-low\">\n" +
                    "                                    <tr>\n" +
                    "                                        <th>Types</th>\n" +
                    "                                        <th>##</th>\n" +
                    "                                    </tr>\n" +
                    "                                            <tr>\n" +
                    "                                                <td>Exceptions</td>\n" +
                    "                                                <td>1</td><!--VALUE-->\n" +
                    "                                            </tr>\n" +
                    "                                            <tr>\n" +
                    "                                                <td>Antlr Exceptions</td>\n" +
                    "                                                <td>1</td><!--VALUE-->\n" +
                    "                                            </tr>\n" +
                    "                                            <tr>\n" +
                    "                                                <td>Error</td>\n" +
                    "                                                <td>1</td><!--VALUE-->\n" +
                    "                                            </tr>\n" +
                    "                                            <tr>\n" +
                    "                                                <td>Debug</td>\n" +
                    "                                                <td>1</td><!--VALUE-->\n" +
                    "                                            </tr>\n" +
                    "                                            <tr>\n" +
                    "                                                <td>Info</td>\n" +
                    "                                                <td>1</td><!--VALUE-->\n" +
                    "                                            </tr>\n" +
                    "                                </table>\n" +
                    "                            </div>\n" +
                    "\n" +
                    "                            <div id=\"sast-cve-table-low-container\">\n" +
                    "                                <div class=\"full-severity-title\">\n" +
                    "                                    <div class=\"severity-title-name\"><i class=\"fas fa-save\"></i> Memory</div>\n" +
                    "                                </div>\n" +
                    "                                <table id=\"sast-cve-table-low\" class=\"cve-table sast-cve-table sast-cve-table-low\">\n" +
                    "                                    <tr>\n" +
                    "                                        <th>Types</th>\n" +
                    "                                        <th>##</th>\n" +
                    "                                    </tr>\n" +
                    "                                            <tr>\n" +
                    "                                                <td>Max Memory</td>\n" +
                    "                                                <td>1</td><!--VALUE-->\n" +
                    "                                            </tr>\n" +
                    "                                            <tr>\n" +
                    "                                                <td>Average Memory</td>\n" +
                    "                                                <td>1</td><!--VALUE-->\n" +
                    "                                            </tr>\n" +
                    "\n" +
                    "                                </table>\n" +
                    "                            </div>\n" +
                    "                    </div>\n" +
                    "                </div>\n" +
                    "            </div>\n" +
                    "\n" +
                    "\n" +
                    "    </div>\n" +
                    "</div>\n" +
                    "\n    " +
                    "<script type=\"text/javascript\" src=\"https://www.gstatic.com/charts/loader.js\"></script>\n" +
                    "   <script type=\"text/javascript\">\n" +
                    "      google.charts.load('current', {'packages':['gauge']});\n" +
                    "      google.charts.setOnLoadCallback(drawChart);\n" +
                    "\n" +
                    "      function drawChart() {\n" +
                    "\n        " +
                    "       var data = google.visualization.arrayToDataTable([\n" +
                    "          ['Label', 'Value'],\n" +
                    "          ['Risk', 80],//VALUE\n" +
                    "          ['Memory', 55],//VALUE\n" +
                    "          ['Queries', 68],//VALUE\n" +
                    "          ['Exceptions', 45],//VALUE\n" +
                    "          ['LOC', 12],//VALUE\n" +
                    "          ['Files', 25]//VALUE\n" +
                    "        ]);\n" +
                    "\n" +
                    "        var options = {\n" +
                    "          width: 700, height: 120,\n" +
                    "          redFrom: 90, redTo: 100,\n" +
                    "          yellowFrom:75, yellowTo: 90,\n" +
                    "          minorTicks: 5\n" +
                    "        };" +
                    "        var chart = new google.visualization.Gauge(document.getElementById('chart_div'));\n" +
                    "\n" +
                    "        chart.draw(data, options);\n" +
                    "      }\n" +
                    "    </script>" +

                    "</body>\n" +
                    "</html>";

            reportHTML = reportHTML + "\n" + reportLogHTML;


            reportName = CxScanResult.resolveHTMLReportName(config.isSastEnabled(), getDependencyScannerType(config));
            File reportFile = new File(checkmarxBuildDir, reportName);
            FileUtils.writeStringToFile(reportFile, reportHTML, Charset.defaultCharset());
            writeFileToWorkspaceReports(workspace, reportFile);
        } catch (IOException | TemplateException e) {
            log.error("Failed to generate HTML report.", e);
        }
        return reportName;
    }


    public static List<String> Readfile(String filePath) throws FileNotFoundException {
        List<String> lines = new ArrayList<>();
        File file = new File(filePath);

        if (file.exists())
        {

            Scanner reader = new Scanner(file);
            while (reader.hasNextLine()){

                String line = reader.nextLine();
                lines.add(line);

            }

            reader.close();
            return lines;
        }

        else
        {
            throw new FileNotFoundException("Cannot");
        }
    }


    public static String GetDOMStatistics(List<String> log, DOMStatiscs type)
    {
        String pattern;
        String value;

        switch (type)
        {
            case TotalFiles:
                pattern = "Total files\\s*(?<number>(\\d*))";
                break;
            case GoodFiles:
                pattern = "Good files:\\s*(?<number>(\\d*))";
                break;
            case BadFiles:
                pattern = "Bad files:\\s*(?<number>(\\d*))";
                break;
            case GoodLOC:
                pattern = "Good LOC:\\s*(?<number>(\\d*))";
                break;
            case BadLOC:
                pattern = "Bad LOC:\\s*(?<number>(\\d*))";
                break;
            case ScanCoverage:
                pattern = "Scan coverage:\\s*(?<number>(\\d.*))";
                break;
            default:
                pattern = "";
                break;
        }


        if (pattern != null && !pattern.isEmpty())
        {
            for (String line: log)
            {

                Pattern p = Pattern.compile(pattern);//. represents single character
                Matcher m = p.matcher(line);
                boolean b = m.matches();

                if (b) {

                    value = m.group(1);
                    return value;
                }
            }
        }
        return "";

    }

    public enum DOMStatiscs
    {
        TotalFiles,
        GoodFiles,
        BadFiles,
        GoodLOC,
        BadLOC,
        ScanCoverage
    }

    private void writeJsonObjectToFile(Object jsonObj, File to, String description) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            String json = null;
            json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObj);
            FileUtils.writeStringToFile(to, json);
            log.info("Copying file [" + to.getName() + "] to workspace [" + to.getAbsolutePath() + "]");
        } catch (Exception e) {
            log.error("Failed to write " + description + " to [" + to.getAbsolutePath() + "]");

        }
    }

    private void failTheBuild(Run<?, ?> run, CxScanConfig config, ScanResults ret) {
        //assert if expected exception is thrown  OR when vulnerabilities under threshold OR when policy violated
        ScanSummary scanSummary = new ScanSummary(config, ret.getSastResults(), ret.getOsaResults(), ret.getScaResults());
        if (scanSummary.hasErrors() || ret.getGeneralException() != null ||
                (ret.getSastResults() != null && (ret.getSastResults().getCreateException() != null || ret.getSastResults().getWaitException() != null)) ||
                (ret.getOsaResults() != null && (ret.getOsaResults().getCreateException() != null || ret.getOsaResults().getWaitException() != null)) ||
                (ret.getScaResults() != null && (ret.getScaResults().getCreateException() != null || ret.getScaResults().getWaitException() != null))) {
            printBuildFailure(scanSummary.toString(), ret, log);
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

    private void printBuildFailure(String thDescription, ScanResults ret, CxLoggerAdapter log) {
        log.error("********************************************");
        log.error(" The Build Failed for the Following Reasons: ");
        log.error("********************************************");

        logError(ret.getGeneralException());
        if (ret.getSastResults() != null) {
            logError(ret.getSastResults().getCreateException());
            logError(ret.getSastResults().getWaitException());
        }
        if (ret.getOsaResults() != null) {
            logError(ret.getOsaResults().getCreateException());
            logError(ret.getOsaResults().getWaitException());
        }
        if (ret.getScaResults() != null) {
            logError(ret.getScaResults().getCreateException());
            logError(ret.getScaResults().getWaitException());
        }

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
            log.info("Copying file {} to workspace {}", file.getName(), remoteFilePath);
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

    /**
     * Called when this plugin is initialized during Jenkins startup. Invoked by Jenkins using reflection.
     * Invoked when all the fields of the current object are deserialized.
     *
     * @return modified instance of the current object.
     */
    protected Object readResolve() {
        PluginDataMigration migration = new PluginDataMigration(serverLog);
        migration.migrate(this);
        return this;
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
        public static final String DEFAULT_SCA_SERVER_URL = CxConfig.getDefaultScaServerUrl();
        public static final String DEFAULT_SCA_ACCESS_CONTROL_URL = CxConfig.getDefaultScaAccessControlUrl();
        public static final String DEFAULT_SCA_WEB_APP_URL = CxConfig.getDefaultScaWebAppUrl();
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
        private String mvnPath;

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

        private final String DEPENDENCY_SCAN_CONFIG_PROP = "dependencyScanConfig";
        private DependencyScanConfig dependencyScanConfig;

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

        public String getMvnPath() {
            return mvnPath;
        }

        public void setMvnPath(String mvnPath) {
            this.mvnPath = mvnPath;
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
                                               @QueryParameter final String username, @QueryParameter final String timestamp,
                                               @QueryParameter final String credentialsId, @AncestorInPath Item item) {
            // timestamp is not used in code, it is one of the arguments to invalidate Internet Explorer cache

            CxCredentials cred;
            LegacyClient commonClient = null;
            try {
                try {
                    cred = CxCredentials.resolveCred(true, serverUrl, username, getPasswordPlainText(password), credentialsId, this, item);
                    CxCredentials.validateCxCredentials(cred);
                    //todo: add proxy support
                    commonClient = CommonClientFactory.getInstance(cred, this.isEnableCertificateValidation(), serverLog);
                } catch (Exception e) {
                    return buildError(e, "Failed to init cx client");
                }

                try {
                    commonClient.login();
                    try {
                        commonClient.getTeamList();
                    } catch (Exception e) {
                        return FormValidation.error("Connection Failed.\n" +
                                "Validate the provided login credentials and server URL are correct.\n" +
                                "In addition, make sure the installed plugin version is compatible with the CxSAST version according to CxSAST release notes.\n" +
                                "Error: " + e.getMessage());
                    }
                    return FormValidation.ok("Success");
                } catch (Exception e) {
                    return buildError(e, "Failed to login to Checkmarx server");
                }
            } finally {
                if (commonClient != null) {
                    commonClient.close();
                }
            }
        }

        public FormValidation doValidateMvnPath(@QueryParameter final String mvnPath) throws InterruptedException {
            boolean mvnPathExists = false;
            FilePath path = new FilePath(new File(mvnPath));
            String errorMsg = "Was not able to access specified path";
            try {
                if (!path.child("mvn").exists()) {
                    errorMsg = "Maven was not found on the specified path";
                } else {
                    mvnPathExists = true;
                }
            } catch (IOException e) {
                e.printStackTrace();
                errorMsg = e.getMessage();
            }

            return mvnPathExists ? FormValidation.ok("Maven is found") : FormValidation.error(errorMsg);
        }

        public FormValidation doTestScaConnection(@QueryParameter String scaServerUrl,
                                                  @QueryParameter String scaAccessControlUrl,
                                                  @QueryParameter String scaCredentialsId,
                                                  @QueryParameter String scaTenant,
                                                  @AncestorInPath Item item) {
            try {
                CxScanConfig config = new CxScanConfig();
                config.setCxOrigin(REQUEST_ORIGIN);
                config.setDisableCertificateValidation(!isEnableCertificateValidation());

                AstScaConfig scaConfig = new AstScaConfig();
                scaConfig.setAccessControlUrl(scaAccessControlUrl);
                scaConfig.setApiUrl(scaServerUrl);
                scaConfig.setTenant(scaTenant);

                UsernamePasswordCredentials credentials = CxCredentials.getCredentialsById(scaCredentialsId, item);
                scaConfig.setUsername(credentials.getUsername());
                scaConfig.setPassword(credentials.getPassword().getPlainText());
                scaConfig.setSourceLocationType(SourceLocationType.LOCAL_DIRECTORY);
                scaConfig.setRemoteRepositoryInfo(null);
                config.setAstScaConfig(scaConfig);
                config.addScannerType(ScannerType.AST_SCA);

                ProxyConfig proxyConfig = ProxyHelper.getProxyConfig();
                config.setProxyConfig(proxyConfig);

                CxClientDelegator commonClient = CommonClientFactory.getClientDelegatorInstance(config, serverLog);
                commonClient.getScaClient().testScaConnection();
                return FormValidation.ok("Success");
            } catch (Exception e) {
                return buildError(e, "Failed to verify CxSCA connection.");
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
        private LegacyClient prepareLoggedInClient(CxCredentials credentials)
                throws IOException, CxClientException {
            //todo: add proxy support
            LegacyClient ret = CommonClientFactory.getInstance(credentials, this.isEnableCertificateValidation(), serverLog);
            ret.login();
            return ret;
        }

        /*
         *  Note: This method is called concurrently by multiple threads, refrain from using mutable
         *  shared state to avoid synchronization issues.
         */
        public ComboBoxModel doFillProjectNameItems(@QueryParameter final boolean useOwnServerCredentials, @QueryParameter final String serverUrl,
                                                    @QueryParameter final String username, @QueryParameter final String password,
                                                    @QueryParameter final String timestamp, @QueryParameter final String credentialsId, @AncestorInPath Item item) {
            // timestamp is not used in code, it is one of the arguments to invalidate Internet Explorer cache
            ComboBoxModel projectNames = new ComboBoxModel();
            LegacyClient commonClient = null;
            try {
                CxCredentials credentials = CxCredentials.resolveCred(!useOwnServerCredentials, serverUrl, username, getPasswordPlainText(password), credentialsId, this, item);
                commonClient = prepareLoggedInClient(credentials);
                List<Project> projects = commonClient.getAllProjects();

                for (Project p : projects) {
                    projectNames.add(p.getName());
                }

                return projectNames;
            } catch (Exception e) {
                serverLog.error("Failed to populate project list: " + e.toString(), e);
                return projectNames; // Return empty list of project names
            } finally {
                if (commonClient != null) {
                    commonClient.close();
                }
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
                                              @QueryParameter final String username, @QueryParameter final String password,
                                              @QueryParameter final String timestamp, @QueryParameter final String credentialsId, @AncestorInPath Item item) {
            // timestamp is not used in code, it is one of the arguments to invalidate Internet Explorer cache
            ListBoxModel listBoxModel = new ListBoxModel();
            try {
                CxCredentials credentials = CxCredentials.resolveCred(!useOwnServerCredentials, serverUrl, username, StringEscapeUtils.escapeHtml4(getPasswordPlainText(password)), credentialsId, this, item);
                LegacyClient commonClient = prepareLoggedInClient(credentials);

                //todo import preset
                List<Preset> presets = commonClient.getPresetList();

                for (Preset p : presets) {
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
                                                      @QueryParameter final String username, @QueryParameter final String password,
                                                      @QueryParameter final String timestamp, @QueryParameter final String credentialsId, @AncestorInPath Item item) {
            // timestamp is not used in code, it is one of the arguments to invalidate Internet Explorer cache
            ListBoxModel listBoxModel = new ListBoxModel();
            LegacyClient commonClient = null;
            try {
                CxCredentials credentials = CxCredentials.resolveCred(!useOwnServerCredentials, serverUrl, username, StringEscapeUtils.escapeHtml4(getPasswordPlainText(password)), credentialsId, this, item);

                commonClient = prepareLoggedInClient(credentials);
                List<CxNameObj> configurationList = commonClient.getConfigurationSetList();

                for (CxNameObj cs : configurationList) {
                    listBoxModel.add(new ListBoxModel.Option(cs.getName(), Long.toString(cs.getId())));
                }

            } catch (Exception e) {
                serverLog.error("Failed to populate source encodings list: " + e.getMessage());
                String message = "Provide Checkmarx server credentials to see source encodings list";
                listBoxModel.add(new ListBoxModel.Option(message, message));
            } finally {
                if (commonClient != null) {
                    commonClient.close();
                }
            }

            return listBoxModel;
        }


        // Provides a list of source encodings from checkmarx server for dynamic drop-down list in configuration page
        /*
         *  Note: This method is called concurrently by multiple threads, refrain from using mutable
         *  shared state to avoid synchronization issues.
         */

        public ListBoxModel doFillGroupIdItems(@QueryParameter final boolean useOwnServerCredentials, @QueryParameter final String serverUrl,
                                               @QueryParameter final String username, @QueryParameter final String password,
                                               @QueryParameter final String timestamp, @QueryParameter final String credentialsId, @AncestorInPath Item item) {
            // timestamp is not used in code, it is one of the arguments to invalidate Internet Explorer cache
            ListBoxModel listBoxModel = new ListBoxModel();
            LegacyClient commonClient = null;
            try {
                CxCredentials credentials = CxCredentials.resolveCred(!useOwnServerCredentials, serverUrl, username, StringEscapeUtils.escapeHtml4(getPasswordPlainText(password)), credentialsId, this, item);
                commonClient = prepareLoggedInClient(credentials);
                List<Team> teamList = commonClient.getTeamList();
                for (Team team : teamList) {
                    listBoxModel.add(new ListBoxModel.Option(team.getFullName(), team.getId()));
                }

                return listBoxModel;

            } catch (Exception e) {
                serverLog.error("Failed to populate team list: " + e.toString());
                String message = "Provide Checkmarx server credentials to see teams list";
                listBoxModel.add(new ListBoxModel.Option(message, message));
                return listBoxModel;
            } finally {
                if (commonClient != null) {
                    commonClient.close();
                }
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

            String decodedUrl;
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

            JSONObject pluginData = formData.getJSONObject("checkmarx");

            // Set dependency scan config to null when user turns off the 'Globally define dependency scan settings'
            // option.
            if (!pluginData.has(DEPENDENCY_SCAN_CONFIG_PROP)) {
                pluginData.put(DEPENDENCY_SCAN_CONFIG_PROP, null);
            }

            req.bindJSON(this, pluginData);
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
            return getCredentialList(item, credentialsId);
        }

        public ListBoxModel doFillScaCredentialsIdItems(@AncestorInPath Item item, @QueryParameter String scaCredentialsId) {
            return getCredentialList(item, scaCredentialsId);
        }

        private ListBoxModel getCredentialList(Item item, String credentialsId) {
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

            List<StandardUsernamePasswordCredentials> standardCredentials = CredentialsProvider.lookupCredentials(
                    StandardUsernamePasswordCredentials.class,
                    item,
                    null,
                    Collections.emptyList());

            return result
                    .withEmptySelection()
                    .withAll(standardCredentials)
                    .withMatching(CredentialsMatchers.withId(credentialsId));
        }

        public boolean isOldCredentials() {
            return StringUtils.isEmpty(credentialsId) && (username != null || password != null);
        }

        public DependencyScanConfig getDependencyScanConfig() {
            return dependencyScanConfig;
        }

        @DataBoundSetter
        public void setDependencyScanConfig(DependencyScanConfig dependencyScanConfig) {
            this.dependencyScanConfig = dependencyScanConfig;
        }
    }
}
