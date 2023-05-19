package com.checkmarx.jenkins;

import com.checkmarx.configprovider.ConfigProvider;
import com.checkmarx.configprovider.dto.ResourceType;
import com.checkmarx.configprovider.dto.interfaces.ConfigReader;
import com.checkmarx.jenkins.configascode.ConfigAsCode;
import com.checkmarx.jenkins.configascode.ProjectConfig;
import com.checkmarx.jenkins.configascode.SastConfig;
import com.checkmarx.jenkins.configascode.ScaConfig;
import com.checkmarx.jenkins.exception.CxCredException;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cx.restclient.CxClientDelegator;
import com.cx.restclient.ast.dto.sca.AstScaConfig;
import com.cx.restclient.ast.dto.sca.AstScaResults;
import com.cx.restclient.common.summary.SummaryUtils;
import com.cx.restclient.configuration.CxScanConfig;
import com.cx.restclient.dto.*;
import com.cx.restclient.dto.scansummary.ScanSummary;
import com.cx.restclient.exception.CxClientException;
import com.cx.restclient.osa.dto.OSAResults;
import com.cx.restclient.sast.dto.Project;
import com.cx.restclient.sast.dto.*;
import com.cx.restclient.sast.utils.LegacyClient;
import com.cx.restclient.sca.utils.CxSCAFileSystemUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import freemarker.template.TemplateException;
import hudson.*;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.triggers.SCMTrigger;
import hudson.util.ComboBoxModel;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import io.netty.util.internal.StringUtil;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kohsuke.stapler.*;
import org.kohsuke.stapler.verb.POST;

import javax.annotation.Nonnull;
import javax.naming.ConfigurationException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    
    private static final String SUPPRESS_BENIGN_ERRORS = "suppressBenignErrors";

    //////////////////////////////////////////////////////////////////////////////////////
    // Persistent plugin configuration parameters
    //////////////////////////////////////////////////////////////////////////////////////
    private boolean useOwnServerCredentials;
    
    private boolean overrideProjectSetting;

    private boolean configAsCode;
    @Nullable
    private String serverUrl;
    @Nullable
    private String username;
    @Nullable
    private String password;
    private String credentialsId;
    //used for SCA Exploitable path feature
    private String sastCredentialsId;
    private Boolean isProxy = true;
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
    private String customFields;
    private boolean forceScan;
    private boolean incremental;
    private boolean fullScansScheduled;
    private int fullScanCycle;
    private boolean isThisBuildIncremental;
    private int postScanActionId;
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

    private boolean hideDebugLogs;

    //////////////////////////////////////////////////////////////////////////////////////
    // Private variables
    //////////////////////////////////////////////////////////////////////////////////////

    //server log, will NOT print to job console
    private static final JenkinsServerLogger serverLog = new JenkinsServerLogger();

    private static final String CONFIG_AS_CODE_FILE_NAME = "cx.config";
    //Print to job console, initialized within perform
    CxLoggerAdapter log;

    private JobStatusOnError jobStatusOnError;
    private String exclusionsSetting;
    private String thresholdSettings;
    private Result vulnerabilityThresholdResult;
    private Result resolvedVulnerabilityThresholdResult;
    private boolean avoidDuplicateProjectScans;
    private boolean addGlobalCommenToBuildCommet;
    private Boolean generateXmlReport = true;

    public static final int MINIMUM_TIMEOUT_IN_MINUTES = 1;
    public static final String REPORTS_FOLDER = "Checkmarx/Reports";

    @DataBoundConstructor
    public CxScanBuilder(
            boolean useOwnServerCredentials,
            @Nullable String serverUrl,
            @Nullable String username,
            @Nullable String password,
            Boolean isProxy,
            String credentialsId,
            String sastCredentialsId,
            boolean configAsCode,
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
            int postScanActionId,
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
            boolean addGlobalCommenToBuildCommet,
            Boolean generateXmlReport,
            boolean hideDebugLogs,
            boolean forceScan,
            String customFields
    ) {
        this.useOwnServerCredentials = useOwnServerCredentials;
        this.serverUrl = serverUrl;
        this.username = username;
        this.password = Secret.fromString(password).getEncryptedValue();
        this.credentialsId = credentialsId;
        this.sastCredentialsId = sastCredentialsId;
        this.configAsCode = configAsCode;
        // Workaround for compatibility with Conditional BuildStep Plugin
        this.isProxy = (isProxy == null) ? true : isProxy;
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
        this.postScanActionId = postScanActionId;
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
        this.addGlobalCommenToBuildCommet = addGlobalCommenToBuildCommet;
        this.generateXmlReport = (generateXmlReport == null) ? true : generateXmlReport;
        this.hideDebugLogs = hideDebugLogs;
        this.forceScan = forceScan;
        this.customFields = customFields;

    }

    // Configuration fields getters
    public boolean isUseOwnServerCredentials() {
        return useOwnServerCredentials;
    }

    public boolean isConfigAsCode() {
        return configAsCode;
    }

    @DataBoundSetter
    public void setConfigAsCode(boolean configAsCode) {
        this.configAsCode = configAsCode;
    }
    
    public boolean isOverrideProjectSetting() {
		return overrideProjectSetting;
	}

    @DataBoundSetter
	public void setOverrideProjectSetting(boolean overrideProjectSetting) {
		this.overrideProjectSetting = overrideProjectSetting;
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


    public String getSastCredentialsId() {
        return sastCredentialsId;
    }

    public void setSastCredentialsId(String sastCredentialsId) {
        this.sastCredentialsId = sastCredentialsId;
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

    public int getPostScanActionId() {
        return postScanActionId;
    }

    @DataBoundSetter
    public void setPostScanActionId(Integer postScanActionId) {
        this.postScanActionId = postScanActionId;
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

    @DataBoundSetter
    public void setExcludeOpenSourceFolders(@Nullable String excludeOpenSourceFolders) {
        this.excludeOpenSourceFolders = excludeOpenSourceFolders;
    }

    @Nullable
    public String getIncludeOpenSourceFolders() {
        return includeOpenSourceFolders;
    }

    @DataBoundSetter
    public void setIncludeOpenSourceFolders(@Nullable String includeOpenSourceFolders) {
        this.includeOpenSourceFolders = includeOpenSourceFolders;
    }

    @Nullable
    public String getOsaArchiveIncludePatterns() {
        return osaArchiveIncludePatterns;
    }

    @DataBoundSetter
    public void setOsaArchiveIncludePatterns(@Nullable String osaArchiveIncludePatterns) {
        this.osaArchiveIncludePatterns = osaArchiveIncludePatterns;
    }

    @Nullable
    public boolean isOsaInstallBeforeScan() {
        return osaInstallBeforeScan;
    }

    @DataBoundSetter
    public void setOsaInstallBeforeScan(boolean osaInstallBeforeScan) {
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

    public boolean isAddGlobalCommenToBuildCommet() {
        return addGlobalCommenToBuildCommet;
    }

    public Boolean getIsProxy() {
        return isProxy;
    }

    public Boolean getGenerateXmlReport() {
        return generateXmlReport;
    }

    public boolean isHideDebugLogs() {
        return hideDebugLogs;
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
    public void setAddGlobalCommenToBuildCommet(boolean addGlobalCommenToBuildCommet) {
        this.addGlobalCommenToBuildCommet = addGlobalCommenToBuildCommet;
    }

    @DataBoundSetter
    public void setGenerateXmlReport(Boolean generateXmlReport) {
        this.generateXmlReport = generateXmlReport;
    }

    @DataBoundSetter
    public void setIsProxy(Boolean proxy) {
        this.isProxy = proxy;
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


    public String getCustomFields() {
        return customFields;
    }

    @DataBoundSetter
    public void setCustomFields(String customFields) {
        this.customFields = customFields;
    }

    public boolean isForceScan() {
        return forceScan;
    }

    @DataBoundSetter
    public void setForceScan(boolean forceScan) {
        this.forceScan = forceScan;
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

    @DataBoundSetter
    public void setHideDebugLogs(Boolean hideDebugLogs) {
        this.hideDebugLogs = hideDebugLogs;
    }
    /**
     * Using environment injection plugin you can add the JVM proxy settings.
     * For example using EnvInject plugin the following can be applied under 'Properties Content':
     *
     *  http.proxyHost={HOST}
     *  http.proxyPass={PORT}
     *  http.proxyUser={USER}
     *  http.proxyPassword={PASS}
     *  http.nonProxyHosts={HOSTS}
     */
    private void setJvmVars(EnvVars env) {
        for (Map.Entry<String, String> entry : env.entrySet()) {
            if (entry.getKey().contains("http.proxy") ||
                    entry.getKey().contains("https.proxy") ||
                    entry.getKey().contains("http.nonProxyHosts")) {
                if (StringUtils.isNotEmpty(entry.getValue())) {
                    System.setProperty(entry.getKey(), entry.getValue());
                }
            }
        }
    }
    private Map<String, String> getAllFsaVars(EnvVars env, String workspacePath) {
        Map<String, String> sumFsaVars = new HashMap<>();
        // As job environment variable
        for (Map.Entry<String, String> entry : env.entrySet()) {
            if (entry.getKey().contains("CX_") ||
                    entry.getKey().contains("FSA_")) {
                if (StringUtils.isNotEmpty(entry.getValue())) {
                    sumFsaVars.put(entry.getKey().trim(), entry.getValue().trim());
                }
            }
        }
        // As custom field - for pipeline jobs
        String fsaVars = dependencyScanConfig != null ? dependencyScanConfig.fsaVariables : "";
        if (StringUtils.isNotEmpty(fsaVars)) {
            fsaVars = fsaVars.contains("${WORKSPACE}") ? fsaVars.replace("${WORKSPACE}", workspacePath) : fsaVars;
            try {
                String[] vars = fsaVars.replaceAll("[\\n\\r]", "").trim().split(",");
                for (String var : vars) {
                    if (var.startsWith("FSA_CONFIGURATION")) {
                        String fsaConfig = var.substring(18);
                        sumFsaVars.put("FSA_CONFIGURATION", fsaConfig);
                    } else {
                        String[] entry = var.split("=");
                        if (entry.length == 1) {
                            sumFsaVars.put(entry[0], "");
                        } else {
                            sumFsaVars.put(entry[0], entry[1]);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Fail to add comment FSA vars");
            }
        }
        return sumFsaVars;
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws InterruptedException, IOException {

        log = new CxLoggerAdapter(listener.getLogger());

        log.info("Hide debug logs: " + isHideDebugLogs());
        if (isHideDebugLogs()) {
            log.setDebugEnabled(false);
            log.setTraceEnabled(false);
        } else {
            log.setDebugEnabled(true);
            log.setTraceEnabled(true);
        }

        if ((sastEnabled == null || sastEnabled) && isSkipScan(run)) {
            log.info("Checkmarx scan skipped since the build was triggered by SCM. " +
                    "Visit plugin configuration page to disable this skip.");
            return;
        }

        //resolve configuration
        final DescriptorImpl descriptor = getDescriptor();
        EnvVars env = run.getEnvironment(listener);
        setJvmVars(env);
        Map<String, String> fsaVars = getAllFsaVars(env, workspace.getRemote());
        CxScanConfig config = resolveConfiguration(run, descriptor, env, log);

        if (configAsCode) {
            try {
                overrideConfigAsCode(config, workspace);
            } catch (ConfigurationException e) {
                log.warn("couldn't load config file: " + e.getMessage(), e);
            }
        }


        //print configuration
        printConfiguration(config, log);

        //validate at least one scan type is enabled
        if (!config.isSastEnabled() && !config.isAstScaEnabled() && !config.isOsaEnabled()) {
            log.error("Both SAST and dependency scan are disabled. Exiting.");
            run.setResult(Result.FAILURE);            
            return;
        }

        Jenkins instance = Jenkins.getInstance();
        final CxScanCallable action;
        if (instance != null && instance.proxy != null &&
        		 ((!isCxURLinNoProxyHost(useOwnServerCredentials ? this.serverUrl : getDescriptor().getServerUrl(), instance.proxy.getNoProxyHostPatterns()))
                         || (config.isScaProxy()))) 
        {
            action = new CxScanCallable(config, listener, instance.proxy, isHideDebugLogs(), fsaVars);
        } else {
            action = new CxScanCallable(config, listener, isHideDebugLogs(), fsaVars);
        }

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
        if (!descriptor.isAsyncHtmlRemoval() || config.getSynchronous()) {
            String reportName = generateHTMLReport(workspace, checkmarxBuildDir, config, scanResults);
            cxScanResult.setHtmlReportName(reportName);
        }
        run.addAction(cxScanResult);
    }

    private void overrideConfigAsCode(CxScanConfig config, FilePath workspace) throws ConfigurationException {
        String configFilePath =
                workspace.getRemote() + File.separator + ".checkmarx" + File.separator + CONFIG_AS_CODE_FILE_NAME;
        com.checkmarx.configprovider.readers.FileReader reader =
                new com.checkmarx.configprovider.readers.FileReader(ResourceType.YAML, configFilePath);

        ConfigAsCode configAsCode = getConfigAsCode(reader);
        overrideConfigAsCode(configAsCode, config);
    }

    private ConfigAsCode getConfigAsCode(ConfigReader reader) throws ConfigurationException {
        ConfigProvider configProvider = ConfigProvider.getInstance();
        String CX_ORIGIN = "jenkins";

        configProvider.init(CX_ORIGIN, reader);

        if (!configProvider.hasAnyConfiguration(CX_ORIGIN))
            throw new ConfigurationException(String.format("Config file %s not found or couldn't", ".checkmarx/"
                    + CONFIG_AS_CODE_FILE_NAME));


        ConfigAsCode configAsCodeFromFile = new ConfigAsCode();

        if (configProvider.hasConfiguration(CX_ORIGIN, "project"))
            configAsCodeFromFile.setProject(
                    configProvider.getConfiguration(CX_ORIGIN, "project",ProjectConfig.class));

        if (configProvider.hasConfiguration(CX_ORIGIN, "team"))
            configAsCodeFromFile.setTeam(
                    configProvider.getStringConfiguration(CX_ORIGIN, "team"));

        if (configProvider.hasConfiguration(CX_ORIGIN, "sast"))
            configAsCodeFromFile.setSast(
                    configProvider.getConfiguration(CX_ORIGIN, "sast", SastConfig.class));

        if (configProvider.hasConfiguration(CX_ORIGIN, "sca"))
            configAsCodeFromFile.setSca(
                    configProvider.getConfiguration(CX_ORIGIN, "sca", ScaConfig.class));
        return configAsCodeFromFile;
    }

    private void overrideConfigAsCode(ConfigAsCode configAsCodeFromFile, CxScanConfig scanConfig) {
        Map<String, String> overridesResults = new HashMap<>();

        //map global
        Optional.ofNullable(configAsCodeFromFile).ifPresent(cac -> {
            if (StringUtils.isNotEmpty(cac.getProject().getFullPath())) {
                scanConfig.setProjectName(cac.getProject().getFullPath());
                overridesResults.put("Project Name:", String.valueOf(cac.getProject().getFullPath()));
            }

            if (StringUtils.isNotEmpty(cac.getTeam())) {
                scanConfig.setTeamPath(cac.getTeam());
                overridesResults.put("Team Name:", String.valueOf(cac.getTeam()));
            }
        });

        mapSastConfiguration(Optional.ofNullable(configAsCodeFromFile.getSast()), scanConfig, overridesResults);
        mapScaConfiguration(Optional.ofNullable(configAsCodeFromFile.getSca()), scanConfig, overridesResults);

        if (!overridesResults.isEmpty()) {
            log.info("The following fields are overridden using config as code file : ");
            overridesResults.keySet().forEach(key -> log.info(String.format("%s = %s", key, overridesResults.get(key))));
        }
    }


    private void mapScaConfiguration(Optional<ScaConfig> sca, CxScanConfig scanConfig, Map<String, String> overridesResults) {

        AtomicReference<String> fileInclude = new AtomicReference<>("");
        AtomicReference<String> fileExclude = new AtomicReference<>("");

        sca.map(ScaConfig::getFileExclude)
                .filter(StringUtils::isNotBlank)
                .ifPresent(pValue -> {
                    fileExclude.set(pValue);
                    overridesResults.put("Sca File Exclude", pValue);
                });

        sca.map(ScaConfig::getFileInclude)
                .filter(StringUtils::isNotBlank)
                .ifPresent(pValue -> {
                    fileInclude.set(pValue);
                    overridesResults.put("Sca File Include", pValue);
                });

        sca.map(ScaConfig::getPathExclude)
                .filter(StringUtils::isNotBlank)
                .ifPresent(pValue -> {
                    scanConfig.setOsaFolderExclusions(pValue);
                    overridesResults.put("Sca Folder Exclude", pValue);
                });

        sca.map(ScaConfig::getHigh)
                .filter(n -> n > 0)
                .ifPresent(pValue -> {
                    scanConfig.setOsaThresholdsEnabled(true);
                    scanConfig.setOsaHighThreshold(pValue);
                    overridesResults.put("Sca High", String.valueOf(pValue));
                });

        sca.map(ScaConfig::getMedium)
                .filter(n -> n > 0)
                .ifPresent(pValue -> {
                    scanConfig.setOsaThresholdsEnabled(true);
                    scanConfig.setOsaMediumThreshold(pValue);
                    overridesResults.put("Sca Medium", String.valueOf(pValue));
                });

        sca.map(ScaConfig::getLow)
                .filter(n -> n > 0)
                .ifPresent(pValue -> {
                    scanConfig.setOsaThresholdsEnabled(true);
                    scanConfig.setOsaLowThreshold(pValue);
                    overridesResults.put("Sca Low", String.valueOf(pValue));
                });

        //build include/exclude file pattern
        if (!fileExclude.get().isEmpty() || !fileInclude.get().isEmpty())
            setDependencyScanFilterPattern(scanConfig, fileInclude.get(), fileExclude.get());

    }

    private void setDependencyScanFilterPattern(CxScanConfig scanConfig, String includedFiles, String excludedFiles) {
        String filterPattern = null;
        if (includedFiles != null) {
            if (excludedFiles != null) {
                filterPattern = includedFiles + ", " + excludedFiles;
            } else
                filterPattern = includedFiles;
        } else if (excludedFiles != null) {
            filterPattern = excludedFiles;
        }

        scanConfig.setOsaFilterPattern(filterPattern);
    }

    private void mapSastConfiguration(Optional<SastConfig> sast, CxScanConfig scanConfig, Map<String, String> overridesResults) {
        sast.map(SastConfig::getEngineConfiguration)
                .filter(StringUtils::isNotBlank)
                .ifPresent(pValue -> {
                    scanConfig.setEngineConfigurationName(pValue);
                    overridesResults.put("Configuration", pValue);
                });

        sast.map(SastConfig::isIncremental)
                .ifPresent(pValue -> {
                    scanConfig.setIncremental(pValue);
                    overridesResults.put("Is Incremental", String.valueOf(pValue));
                });
        
        sast.map(SastConfig::isOverrideProjectSetting)
        .ifPresent(pValue -> {
            scanConfig.setIsOverrideProjectSetting(pValue);
            overridesResults.put("Is OverrideProjectSetting", String.valueOf(pValue));
        });

        sast.map(SastConfig::isPrivateScan)
                .ifPresent(pValue -> {
                    scanConfig.setPublic(!pValue);
                    overridesResults.put("Is Private", String.valueOf(pValue));
                });

        sast.map(SastConfig::getLow)
                .filter(n -> n > 0)
                .ifPresent(pValue -> {
                    scanConfig.setSastThresholdsEnabled(true);
                    scanConfig.setSastLowThreshold(pValue);
                    overridesResults.put("Low", String.valueOf(pValue));
                });

        sast.map(SastConfig::getMedium)
                .filter(n -> n > 0)
                .ifPresent(pValue -> {
                    scanConfig.setSastThresholdsEnabled(true);
                    scanConfig.setSastMediumThreshold(pValue);
                    overridesResults.put("Medium", String.valueOf(pValue));
                });

        sast.map(SastConfig::getHigh)
                .filter(n -> n > 0)
                .ifPresent(pValue -> {
                    scanConfig.setSastThresholdsEnabled(true);
                    scanConfig.setSastHighThreshold(pValue);
                    overridesResults.put("High", String.valueOf(pValue));
                });
        sast.map(SastConfig::getPreset)
                .filter(StringUtils::isNotBlank)
                .ifPresent(pValue -> {
                    scanConfig.setPresetName(pValue);
                    scanConfig.setPresetId(null);
                    overridesResults.put("Preset", pValue);
                });
       

        sast.map(SastConfig::getExcludeFolders)
                .filter(StringUtils::isNotBlank)
                .ifPresent(pValue -> {
                    if (StringUtils.isNotEmpty(scanConfig.getSastFolderExclusions())) {
                        pValue = Stream.of(scanConfig.getSastFolderExclusions().split(","), pValue.split(","))
                                .flatMap(x -> Arrays.stream(x))
                                .map(String::trim)
                                .distinct()
                                .collect(Collectors.joining(","));
                    }
                    scanConfig.setSastFolderExclusions(pValue);
                    overridesResults.put("Folder Exclusions", pValue);
                });

        sast.map(SastConfig::getIncludeExcludePattern)
                .filter(StringUtils::isNotBlank)
                .ifPresent(pValue -> {
                    if (StringUtils.isNotEmpty(scanConfig.getSastFilterPattern())) {
                        pValue = Stream.of(scanConfig.getSastFilterPattern().split(","), pValue.split(","))
                                .flatMap(x -> Arrays.stream(x))
                                .map(String::trim)
                                .distinct()
                                .collect(Collectors.joining(","));
                    }
                    scanConfig.setSastFilterPattern(pValue);
                    overridesResults.put("Include/Exclude pattern", pValue);
                });
    }


    private void createScaReports(AstScaResults scaResults, File checkmarxBuildDir) {
        writeJsonObjectToFile(scaResults.getSummary(), new File(checkmarxBuildDir, SCA_SUMMERY_JSON), "OSA summary json report");
        writeJsonObjectToFile(scaResults.getPackages(), new File(checkmarxBuildDir, SCA_LIBRARIES_JSON), "OSA libraries json report");
        writeJsonObjectToFile(scaResults.getFindings(), new File(checkmarxBuildDir, SCA_VULNERABILITIES_JSON), "OSA vulnerabilities json report");
    }

    /**
     * Method validate if CxServerURL is part of 'No proxy host'
     *
     * @param serverUrl
     * @param noProxyHostPatterns
     * @return
     */
    private Boolean isCxURLinNoProxyHost(String serverUrl, List<Pattern> noProxyHostPatterns) {

        if ((noProxyHostPatterns != null) && (!noProxyHostPatterns.isEmpty()) && (serverUrl != null) && (!serverUrl.isEmpty())) {

            Pattern pattern;
            String tempSt;
            for (Pattern noProxyHostPattern : noProxyHostPatterns) {
                pattern = noProxyHostPattern;
                tempSt = pattern.toString();
                while ((tempSt.contains("\\")) ||
                        (tempSt.contains("..")) ||
                        (tempSt.contains(".*")) ||
                        (tempSt.contains("*"))) {
                    tempSt = tempSt.replace("\\", "");
                    tempSt = tempSt.replace("..", ".");
                    tempSt = tempSt.replace(".*", "");
                    tempSt = tempSt.replace("*", "");
                }

                if (serverUrl.contains(tempSt)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String getJenkinURLForTheJob(EnvVars env) {
        String passedURL = "";
        try {
            String jobName = env.get("JOB_NAME");
            jobName = URLDecoder.decode(jobName, "UTF-8");
            jobName = jobName.replaceAll("[^.a-zA-Z0-9\\s]", " ");
            String jenURL = env.get("JENKINS_URL");
            jenURL = jenURL.substring((jenURL.lastIndexOf("://")) + 3);
            String hostName = "";
            if (jenURL.indexOf(":") != -1) {
                hostName = jenURL.substring(0, jenURL.lastIndexOf(":"));
            } else {
                hostName = jenURL;
            }
            passedURL = "Jenkins/" + CxConfig.version()+ " " + hostName + " " + jobName;
            // 50 is the maximum number of characters allowed by SAST server
            if (passedURL.length() > 50) {
                passedURL = passedURL.substring(0, 45);
                passedURL = passedURL + "...";
            } else {
                passedURL = passedURL;
            }
        } catch (UnsupportedEncodingException e) {
            log.error("Failed to get Jenkins URL of the JOB: " + e.getMessage());
        }
        return passedURL;
    }


    private String getCxOriginUrl(EnvVars env, CxLoggerAdapter log) {
        String jenURL = env.get("JENKINS_URL");
        String jobName = env.get("JOB_NAME");
        String originUrl = jenURL + "job/" + jobName;
        if (originUrl.length() > 120) {
            originUrl = originUrl.substring(0, 115) + "...";
        } else {
            originUrl = originUrl;
        }
        return originUrl;
    }
    private Boolean verifyCustomCharacters(String inputString) {
    	 Pattern pattern = Pattern.compile("(^([a-zA-Z0-9#._]*):([a-zA-Z0-9#._]*)+(,([a-zA-Z0-9#._]*):([a-zA-Z0-9#._]*)+)*$)");
         Matcher match = pattern.matcher(inputString);
         if (!StringUtil.isNullOrEmpty(inputString) && !match.find()) {
        	 return false;
         }
    	return true;
    }
    private CxScanConfig resolveConfiguration(Run<?, ?> run, DescriptorImpl descriptor, EnvVars env, CxLoggerAdapter log) throws IOException {
        CxScanConfig ret = new CxScanConfig();
        
        ret.setIsOverrideProjectSetting(overrideProjectSetting);

        if (isIncremental() && isForceScan()) {
            throw new IOException("Force scan and incremental scan can not be configured in pair for SAST. Configure either Incremental or Force scan option");
        }
        String originUrl = getCxOriginUrl(env, log);
        ret.setCxOriginUrl(originUrl);
        String jenkinURL = getJenkinURLForTheJob(env);

        //general
        ret.setCxOrigin(jenkinURL);
        log.info("  ORIGIN FROM JENKIN :: " + jenkinURL);
        log.info("  ORIGIN URL FROM JENKIN :: " + originUrl);

        if(getPostScanActionId() == 0)
        	ret.setPostScanActionId(null);
        else
        	ret.setPostScanActionId(getPostScanActionId());
        	
        ret.setDisableCertificateValidation(!descriptor.isEnableCertificateValidation());
        ret.setMvnPath(descriptor.getMvnPath());
        ret.setOsaGenerateJsonReport(false);
        
        if(StringUtils.isNotEmpty(getCustomFields())) {
	        if(!verifyCustomCharacters(getCustomFields())) {
	        	throw new CxClientException("Custom Fields must have given format: key1:val1,key2:val2. \\nCustom field allows to use these special characters: # . _ ");
	        }
	        ret.setCustomFields(apiFormat(getCustomFields()));
        }
        ret.setForceScan(isForceScan());

        //cx server
        CxConnectionDetails cxConnectionDetails = CxConnectionDetails.resolveCred(this, descriptor, run);
        ret.setUrl(cxConnectionDetails.getServerUrl().trim());
        ret.setUsername(cxConnectionDetails.getUsername());
        ret.setPassword(Aes.decrypt(cxConnectionDetails.getPassword(), cxConnectionDetails.getUsername()));
        if (cxConnectionDetails.isProxy()) {
            Jenkins instance = Jenkins.getInstance();
            if (instance.proxy != null) {
                boolean sastProxy = false;

                if (!isCxURLinNoProxyHost(useOwnServerCredentials ? this.serverUrl : descriptor.getServerUrl(), instance.proxy.getNoProxyHostPatterns())) {
                    ret.setProxy(true);
                    ret.setProxyConfig(new ProxyConfig(instance.proxy.name, instance.proxy.port,
                            instance.proxy.getUserName(), instance.proxy.getPassword(), false));
                    sastProxy = true;
                }

                DependencyScanConfig depScanConf;
                if (dependencyScanConfig != null) {
                    depScanConf = dependencyScanConfig; // Local
                } else {
                    depScanConf = descriptor.getDependencyScanConfig(); // Global
                }

                if (depScanConf != null) {
                    if (!isCxURLinNoProxyHost(depScanConf.scaAccessControlUrl, instance.proxy.getNoProxyHostPatterns())) {
                        if (!sastProxy) {
                            ret.setProxy(false);
                        }
                        ret.setScaProxy(true);
                        ret.setScaProxyConfig(new ProxyConfig(instance.proxy.name, instance.proxy.port,
                                instance.proxy.getUserName(), instance.proxy.getPassword(), false));
                    } else {
                        ret.setScaProxy(false);
                    }
                }
            } else {
                ret.setProxy(false);
                ret.setScaProxy(false);
            }
        } else {
            ret.setProxy(false);
            ret.setScaProxy(false);
        }

        /*
         * Pipeline script can provide grouoId or teamPath
         * teamPath will take precedence if it is not empty.
         * Freestyle job always send groupId, hence initializing teamPath using groupId
         */
        if (!StringUtil.isNullOrEmpty(groupId) && StringUtil.isNullOrEmpty(teamPath)) {
            teamPath = getTeamNameFromId(cxConnectionDetails, descriptor, groupId, ret);
        }
        //project
        ret.setProjectName(env.expand(projectName.trim()));
        ret.setTeamPath(teamPath);
        //Jenkins UI does not send teamName but team Id
        ret.setTeamId(groupId);

        //scan control
        boolean isaAsync = !isWaitForResultsEnabled();
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
            if (addGlobalCommenToBuildCommet) {
                if ((env.expand(descriptor.sastcomment)) != null) {
                    ret.setScanComment(env.expand(comment) + " " + env.expand(descriptor.sastcomment));
                } else {
                    ret.setScanComment(env.expand(comment));
                }
            }

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

        if (isOsaEnabled() && getDependencyScanConfig() == null) {
            DependencyScanConfig config = new DependencyScanConfig();
            config.overrideGlobalConfig = true;
            config.dependencyScannerType = DependencyScannerType.OSA;
            config.dependencyScanPatterns = getIncludeOpenSourceFolders();
            config.dependencyScanExcludeFolders = getExcludeOpenSourceFolders();
            config.osaArchiveIncludePatterns = getOsaArchiveIncludePatterns();
            config.osaInstallBeforeScan = isOsaInstallBeforeScan();
            setDependencyScanConfig(config);
        }

        configureDependencyScan(run, descriptor, env, ret);

        if (!ret.getSynchronous()) {
            enableProjectPolicyEnforcement = false;
        }
        ret.setEnablePolicyViolations(enableProjectPolicyEnforcement);
        
        // Set the Continue build flag to Configuration object if Option from UI is choosen as useContinueBuildOnError
        if (useContinueBuildOnError(getDescriptor())) {
            ret.setContinueBuild(Boolean.TRUE);
        }
        
        //Ignore errors that can be suppressed for ex. duplicate scan,source folder is empty, no files to zip.
        String suppressBenignErrors = System.getProperty(SUPPRESS_BENIGN_ERRORS);
        if(suppressBenignErrors == null || Boolean.parseBoolean(suppressBenignErrors))
        	ret.setIgnoreBenignErrors(true);
        
        return ret;
    }

    private String apiFormat(String customFields) {
        if (!StringUtil.isNullOrEmpty(customFields)) {
            customFields = customFields.replaceAll(":", "\":\"");
            customFields = customFields.replaceAll(",", "\",\"");
            customFields = "{\"".concat(customFields).concat("\"}");
        }
        return customFields;
    }

    private String getTeamNameFromId(CxConnectionDetails credentials, DescriptorImpl descriptor, String teamId, CxScanConfig scanConfig) {
        LegacyClient commonClient = null;
        String teamName = null;
        try {

            commonClient = prepareLoggedInClient(credentials, descriptor, scanConfig);
            teamName = commonClient.getTeamNameById(teamId);

        } catch (Exception e) {
            serverLog.error("Failed to get team name by team id: " + e.toString());
        } finally {
            if (commonClient != null) {
                commonClient.close();
            }
        }
        return teamName;
    }

    // Prepares a cx client object to be connected and logged in
    /*
     *  Note: This method is called concurrently by multiple threads, refrain from using mutable
     *  shared state to avoid synchronization issues.
     */
    private LegacyClient prepareLoggedInClient(CxConnectionDetails credentials, DescriptorImpl descriptor, CxScanConfig scanConfig)
            throws IOException, CxClientException {
        LegacyClient ret;
        Jenkins instance = Jenkins.getInstance();

        if (credentials.isProxy()) {
            if (instance != null && instance.proxy != null) {
                boolean isSastProxy = false;
                if (!isCxURLinNoProxyHost(useOwnServerCredentials ? this.serverUrl : descriptor.getServerUrl(), instance.proxy.getNoProxyHostPatterns())) {
                    credentials.setProxy(true);
                    isSastProxy = true;
                }
                if (scanConfig.isScaProxy()) {
                    credentials.setScaProxy(true);
                    if (!isSastProxy || !getSastEnabled()) {
                        credentials.setProxy(false);
                    }
                }
            }
            ret = CommonClientFactory.getInstance(credentials, descriptor.isEnableCertificateValidation(), serverLog);
        } else {
            credentials.setProxy(false);
            credentials.setScaProxy(false);
            ret = CommonClientFactory.getInstance(credentials, descriptor.isEnableCertificateValidation(), serverLog);
        }

        ret.login();
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

        ScannerType scannerType = null;
        if (effectiveConfig.dependencyScannerType == DependencyScannerType.OSA) {
            scannerType = ScannerType.OSA;
        } else if (effectiveConfig.dependencyScannerType == DependencyScannerType.SCA) {
            scannerType = ScannerType.AST_SCA;
        }

        if (scannerType != null) {
            config.addScannerType(scannerType);
        }

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
            config.setAstScaConfig(getScaConfig(run, env, dependencyScanConfig, descriptor));
            config.setSCAScanTimeoutInMinutes(dependencyScanConfig.scaTimeout);
        }
    }

    private AstScaConfig getScaConfig(Run<?, ?> run, EnvVars env, DependencyScanConfig dsConfigJobLevel, DescriptorImpl descriptor) {


        DependencyScanConfig dsConfig;
        boolean globalSettingsInUse = false;
        if (dsConfigJobLevel.overrideGlobalConfig) {
            dsConfig = dsConfigJobLevel;
        } else {
            globalSettingsInUse = true;
            dsConfig = descriptor.getDependencyScanConfig();
        }

        AstScaConfig result = new AstScaConfig();
        result.setApiUrl(dsConfig.scaServerUrl);
        result.setAccessControlUrl(dsConfig.scaAccessControlUrl);
        result.setWebAppUrl(dsConfig.scaWebAppUrl);
        result.setTenant(dsConfig.scaTenant);
        result.setTeamPath(dsConfig.scaTeamPath);
        result.setTeamId(dsConfig.scaTeamId);
        result.setIncludeSources(dsConfig.isIncludeSources);

        //add SCA Resolver code here
        if (dsConfig.enableScaResolver != null
                && SCAScanType.SCA_RESOLVER.toString().equalsIgnoreCase(dsConfig.enableScaResolver.toString())) {
//            scaResolverPathExist(dsConfig.pathToScaResolver);
            validateScaResolverParams(dsConfig.scaResolverAddParameters);
            result.setEnableScaResolver(true);
        }
        else
            result.setEnableScaResolver(false);

        result.setPathToScaResolver(dsConfig.pathToScaResolver);
        result.setScaResolverAddParameters(dsConfig.scaResolverAddParameters);

        UsernamePasswordCredentials credentials = CxConnectionDetails.getCredentialsById(dsConfig.scaCredentialsId, run);
        if (credentials != null) {
            result.setUsername(credentials.getUsername());
            result.setPassword(credentials.getPassword().getPlainText());
        } else {
            log.warn("CxSCA credentials are not specified.");
        }
        if (StringUtils.isNotEmpty(dsConfig.scaEnvVariables)) {
            result.setEnvVariables(CxSCAFileSystemUtils.convertStringToKeyValueMap(env.expand(dsConfig.scaEnvVariables)));
        }
        String filePath = dsConfig.scaConfigFile;
        if (!StringUtils.isEmpty(filePath)) {
            String[] strArrayFile = filePath.split(",");
            result.setConfigFilePaths(Arrays.asList(strArrayFile));
        }


        String derivedProjectName = projectName;
        String derivedProjectId = null;
        UsernamePasswordCredentials scaSASTCred = null;
        String scaSASTServerUrl = null;
        if (dsConfig.isExploitablePath) {

            scaSASTCred = CxConnectionDetails.getCredentialsById(dsConfig.sastCredentialsId, run);
            scaSASTServerUrl = dsConfig.scaSastServerUrl;

            if (!globalSettingsInUse) {
                if (!dsConfig.useJobLevelSastDetails) {
                    scaSASTCred = CxConnectionDetails.getCredentialsById(descriptor.getDependencyScanConfig().sastCredentialsId, run);
                    scaSASTServerUrl = descriptor.getDependencyScanConfig().scaSastServerUrl;
                }
                derivedProjectName = dsConfig.scaSASTProjectFullPath;
                derivedProjectId = dsConfig.scaSASTProjectID;
            }
            if (scaSASTCred != null) {
                result.setSastServerUrl(scaSASTServerUrl);
                result.setSastUsername(scaSASTCred.getUsername());
                result.setSastPassword(scaSASTCred.getPassword().getPlainText());
            }
            result.setSastProjectName(derivedProjectName);
            result.setSastProjectId(derivedProjectId);

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
        //Print correct value only for local project proxy setup
        //useOwnServerCredentials == true once it's un-checked on job config and false once its checked
        boolean proxyEnabled = ((!useOwnServerCredentials ? getIsProxy() : config.getProxyConfig()) != null);
        log.info("is using Jenkins server proxy: " + proxyEnabled);
        if (proxyEnabled) {
            if (Jenkins.getInstance().proxy != null)
                log.info("No Proxy Host: " + printNoProxyHost());
        }
        log.info("project name: " + config.getProjectName());
        log.info("team id: " + config.getTeamId());
        log.info("is synchronous mode: " + config.getSynchronous());
        log.info("deny new project creation: " + config.getDenyProject());
        log.info("SAST scan enabled: " + config.isSastEnabled());
        log.info("avoid duplicated projects scans: " + config.isAvoidDuplicateProjectScans());
        log.info("enable Project Policy Enforcement: " + config.getEnablePolicyViolations());
        log.info("continue build when timed out: " + config.getContinueBuild());
        log.info("post scan action: " + config.getPostScanActionId());
        log.info("is force scan: " + config.getForceScan());
        log.info("scan level custom fields: " + config.getCustomFields());
        log.info("overrideProjectSetting value: " + overrideProjectSetting);

        ScannerType scannerType = getDependencyScannerType(config);
        String dependencyScannerType = scannerType != null ? scannerType.getDisplayName() : "NONE";

        if (config.isSastEnabled()) {
            log.info("preset id: " + config.getPresetId());
            log.info("SAST folder exclusions: " + config.getSastFolderExclusions());
            log.info("SAST filter pattern: " + config.getSastFilterPattern());
            log.info("SAST timeout: " + config.getSastScanTimeoutInMinutes());
            log.info("SAST scan comment: " + config.getScanComment());
            log.info("is incremental scan: " + config.getIncremental());
            log.info("is force scan: " + config.getForceScan());
            log.info("is generate full XML report: " + config.getGenerateXmlReport());
            log.info("is generate PDF report: " + config.getGeneratePDFReport());
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
            if (config.isAstScaEnabled() && config.getAstScaConfig() != null){
                log.info("Use CxSCA dependency scanner is enabled");
                log.info("CxSCA API URL: " + config.getAstScaConfig().getApiUrl());
                log.info("Access control server URL: " + config.getAstScaConfig().getAccessControlUrl());
                log.info("CxSCA web app URL: " + config.getAstScaConfig().getWebAppUrl());
                log.info("Account: " + config.getAstScaConfig().getTenant());
                log.info("Team: " + config.getAstScaConfig().getTeamPath());
            }
        }

        log.info("------------------------------------------------------------------------------------------");
    }

    private String printNoProxyHost() {
        String noProxyHost = "";
        ProxyConfiguration proxy = Jenkins.getInstance().proxy;
        if (proxy.getNoProxyHostPatterns() != null) {
            List<Pattern> noProxyHostPatterns = proxy.getNoProxyHostPatterns();
            for (Pattern noProxyHostPattern : noProxyHostPatterns) {
                String tempString = noProxyHostPattern.toString();
                tempString = tempString.replace("\\.", ".").replace(".*", "*");
                if (noProxyHost.isEmpty()) {
                    noProxyHost = noProxyHost + tempString;
                } else {
                    noProxyHost = noProxyHost + ", " + tempString;
                }
            }
            return noProxyHost;
        }
        return noProxyHost;
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
            reportName = CxScanResult.resolveHTMLReportName(config.isSastEnabled(), getDependencyScannerType(config));
            File reportFile = new File(checkmarxBuildDir, reportName);
            FileUtils.writeStringToFile(reportFile, reportHTML, Charset.defaultCharset());
            writeFileToWorkspaceReports(workspace, reportFile);
        } catch (IOException | TemplateException e) {
            log.error("Failed to generate HTML report. {}", e.getMessage());
        } catch (NullPointerException e) {
            String message = "";
            if (results.getSastResults() != null && !results.getSastResults().isSastResultsReady()) {
                message = "SAST results are empty.";
            } else if (results.getOsaResults() != null && !results.getOsaResults().isOsaResultsReady()) {
                message = "OSA results are empty.";
            } else if (results.getScaResults() != null && !results.getScaResults().isScaResultReady()) {
                message = "SCA results are empty.";
            }
            log.error("Failed to generate HTML report. {}", message);
        }
        return reportName;
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

    private void failTheBuild(Run<?, ?> run, CxScanConfig config, ScanResults ret) throws AbortException {
        //assert if expected exception is thrown  OR when vulnerabilities under threshold OR when policy violated
        ScanSummary scanSummary = new ScanSummary(config, ret.getSastResults(), ret.getOsaResults(), ret.getScaResults());
        if (scanSummary.hasErrors() || ret.getGeneralException() != null ||
                (ret.getSastResults() != null && ret.getSastResults().getException() != null) ||
                (ret.getOsaResults() != null && ret.getOsaResults().getException() != null) ||
                (ret.getScaResults() != null && ret.getScaResults().getException() != null)) {
            printBuildFailure(scanSummary.toString(), ret, log);
            
            String statusToReturn = "";
            String msgPrefix = "";
            if (!scanSummary.getThresholdErrors().isEmpty() || (config.getSastNewResultsThresholdEnabled() && scanSummary.isSastThresholdForNewResultsExceeded() ) ) {
            	resolvedVulnerabilityThresholdResult = resolvedVulnerabilityThresholdResult == null? 
            			Result.fromString(JobStatusOnError.FAILURE.toString()): resolvedVulnerabilityThresholdResult;
            	run.setResult(resolvedVulnerabilityThresholdResult);
            	statusToReturn = resolvedVulnerabilityThresholdResult.toString();
            	msgPrefix = "Threshold exceeded.";
            }else {
            	msgPrefix = "Scan error occurred.";
            	statusToReturn = getReturnStatusOnError(getDescriptor());
            	run.setResult(Result.fromString(statusToReturn));
            }
            
            if(JobStatusOnError.ABORTED.toString().equalsIgnoreCase(statusToReturn)) {
            	String msg = msgPrefix + "Job is configured to return ABORTED and stop the build/pipeline.";
            	log.warn(msg);
            	throw new AbortException(msg);
       	    }                     

        }
    }

    private void printBuildFailure(String thDescription, ScanResults ret, CxLoggerAdapter log) {
        log.error("********************************************");
        log.error(" The Build Failed for the Following Reasons: ");
        log.error("********************************************");

        logError(ret.getGeneralException());

        Map<ScannerType, Results> resultsMap = ret.getResults();
        for (Results results : resultsMap.values()) {
            if (results != null && results.getException() != null) {
                logError(results.getException());
            }
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
    
    private String getReturnStatusOnError(final DescriptorImpl descriptor) {
        
    	String status = JobStatusOnError.FAILURE.toString();
    	
    	if (JobStatusOnError.GLOBAL.equals(getJobStatusOnError()))
    			status = descriptor.getJobGlobalStatusOnError().toString();
    	else
    		status = getJobStatusOnError().toString();
    	
    	return status;
    }

    /**
     * Checks if job should fail with <code>UNSTABLE</code> status instead of <code>FAILED</code>
     *
     * @param descriptor Descriptor of the current build step
     * @return if job should fail with <code>UNSTABLE</code> status instead of <code>FAILED</code>
     */
    private boolean useContinueBuildOnError(final DescriptorImpl descriptor) {
        return descriptor.getContinueBuildWhenTimedOut();
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

    private boolean scaResolverPathExist(String pathToResolver) {
        pathToResolver = pathToResolver + File.separator + "ScaResolver";
        if(!SystemUtils.IS_OS_UNIX)
            pathToResolver = pathToResolver + ".exe";

        File file = new File(pathToResolver);
        if(!file.exists())
        {
            throw new CxClientException("SCA Resolver path does not exist. Path="+file.getAbsolutePath());
        }
        return true;
    }

    private void validateScaResolverParams(String additionalParams) {

        String[] arguments = additionalParams.split(" ");
        Map<String, String> params = new HashMap<>();

        for (int i = 0; i <  arguments.length ; i++) {
            if(arguments[i].startsWith("-") && (i+1 != arguments.length && !arguments[i+1].startsWith("-")))
                params.put(arguments[i], arguments[i+1]);
            else
                params.put(arguments[i], "");
        }

        String dirPath = params.get("-s");
        if(StringUtils.isEmpty(dirPath))
            throw new CxClientException("Source code path (-s <source code path>) is not provided.");
//        fileExists(dirPath);

        String projectName = params.get("-n");
        if(StringUtils.isEmpty(projectName))
            throw new CxClientException("Project name parameter (-n <project name>) must be provided to ScaResolver.");

    }

    private void fileExists(String file) {

        File resultPath = new File(file);
        if (!resultPath.exists()) {
            throw new CxClientException("Path does not exist. Path= " + resultPath.getAbsolutePath());
        }
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
        private boolean isProxy = true;

        private boolean prohibitProjectCreation;
        private boolean hideResults;
        private boolean asyncHtmlRemoval;

        private boolean enableCertificateValidation;
        @Nullable
        private String excludeFolders;
        @Nullable
        private String filterPattern;

        public String getSastcomment() {
            return sastcomment;
        }

        public void setSastcomment(String sastcomment) {
            this.sastcomment = sastcomment;
        }

        private String sastcomment;

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
        private boolean globallyDefineScanSettings;
        private boolean continueBuildWhenTimedOut;
        private Integer scanTimeoutDuration; // In minutes.
        private boolean lockVulnerabilitySettings = true;

        private final transient Pattern msGuid = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

        private final String DEPENDENCY_SCAN_CONFIG_PROP = "dependencyScanConfig";
        private DependencyScanConfig dependencyScanConfig;
        private boolean hideDebugLogs = false;

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

        public boolean getIsProxy() {
            return this.isProxy;
        }

        public void setIsProxy(final boolean isProxy) {
            this.isProxy = isProxy;
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

        public boolean isAsyncHtmlRemoval() {
            return asyncHtmlRemoval;
        }

        public void setAsyncHtmlRemoval(boolean asyncHtmlRemoval) {
            this.asyncHtmlRemoval = asyncHtmlRemoval;
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

        public boolean getContinueBuildWhenTimedOut() {
            return continueBuildWhenTimedOut;
        }

        public void setContinueBuildWhenTimedOut(boolean continueBuildWhenTimedOut) {
            this.continueBuildWhenTimedOut = continueBuildWhenTimedOut;
        }

        public boolean getGloballyDefineScanSettings() {
            return globallyDefineScanSettings;
        }

        public void setGloballyDefineScanSettings(boolean globallyDefineScanSettings) {
            this.globallyDefineScanSettings = globallyDefineScanSettings;
        }

        @Nullable
        public Integer getScanTimeoutDuration() {
            return scanTimeoutDuration;
        }

        public void setScanTimeoutDuration(@Nullable Integer scanTimeoutDurationInMinutes) {
            this.scanTimeoutDuration = scanTimeoutDurationInMinutes;
        }

        public final boolean isHideDebugLogs() {
            return hideDebugLogs;
        }

        public final void setHideDebugLogs(boolean hideDebugLogs) {
            this.hideDebugLogs = hideDebugLogs;
        }

        @POST
        public FormValidation doCheckScanTimeoutDuration(@QueryParameter final Integer value) {
            Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
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

        /**
         * Method validate if CxServerURL is part of 'No proxy host'
         *
         * @param serverUrl
         * @param noProxyHostPatterns
         * @return
         */
        private Boolean isCxURLinNoProxyHost(String serverUrl, List<Pattern> noProxyHostPatterns) {
            if ((noProxyHostPatterns != null) && (!noProxyHostPatterns.isEmpty()) && (serverUrl != null) && (!serverUrl.isEmpty())) {
                Pattern pattern;
                String tempSt;
                for (Pattern noProxyHostPattern : noProxyHostPatterns) {
                    pattern = noProxyHostPattern;
                    tempSt = pattern.toString();
                    while ((tempSt.contains("\\")) ||
                            (tempSt.contains("..")) ||
                            (tempSt.contains(".*")) ||
                            (tempSt.contains("*"))) {
                        tempSt = tempSt.replace("\\", "");
                        tempSt = tempSt.replace("..", ".");
                        tempSt = tempSt.replace(".*", "");
                        tempSt = tempSt.replace("*", "");
                    }

                    if (serverUrl.contains(tempSt)) {
                        return true;
                    }
                }
            }
            return false;
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
        @POST
        public FormValidation doTestConnection(@QueryParameter final String serverUrl, @QueryParameter final String password,
                                               @QueryParameter final String username, @QueryParameter final String timestamp,
                                               @QueryParameter final String credentialsId, @QueryParameter final boolean isProxy, @AncestorInPath Item item) {
            if(item==null){
                Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
            }else if(item!=null){
                item.checkPermission(Item.CONFIGURE);
            }
            // timestamp is not used in code, it is one of the arguments to invalidate Internet Explorer cache

            CxConnectionDetails cred;
            LegacyClient commonClient = null;
            try {
                try {
                    cred = CxConnectionDetails.resolveCred(true, serverUrl, username, getPasswordPlainText(password), credentialsId, isProxy, this, item);
                    CxConnectionDetails.validateCxCredentials(cred);
                    Jenkins instance = Jenkins.getInstance();
                    if (cred.isProxy()) {
                        if (instance != null && instance.proxy != null && isCxURLinNoProxyHost(serverUrl, instance.proxy.getNoProxyHostPatterns())) {
                            cred.setProxy(false);
                        }
                        commonClient = CommonClientFactory.getInstance(cred, this.isEnableCertificateValidation(), serverLog);
                    } else {
                        cred.setProxy(false);
                        commonClient = CommonClientFactory.getInstance(cred, this.isEnableCertificateValidation(), serverLog);
                    }
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

        /**
         * Performs on-the-fly validation of the form field 'value'.
         *
         * @param value This parameter receives the value that the user has typed.
         * @return Indicates the outcome of the validation. This is sent to the
         * browser.
         */
        @POST
        public FormValidation doCheckScaSASTProjectID(@QueryParameter String value, @QueryParameter String scaSASTProjectFullPath,@AncestorInPath Item item) {
        	if (item == null) {
                return FormValidation.ok();
        	}    
        	item.checkPermission(Item.CONFIGURE);
            if (StringUtil.isNullOrEmpty(value) && StringUtil.isNullOrEmpty(scaSASTProjectFullPath)) {
                return FormValidation.error("Must provide value for either 'Project Full Path' or 'Project Id'.");
            }
            return FormValidation.ok();
        }


        /**
         * This method verify correct format for Custom Fields
         *
         * @param value
         * @return
         */
        @POST
        public FormValidation doCheckCustomFields(@QueryParameter String value,@AncestorInPath Item item) {
        	if (item == null) {
                return FormValidation.ok();
        	}
            item.checkPermission(Item.CONFIGURE);
            Pattern pattern = Pattern.compile("(^([a-zA-Z0-9#._]*):([a-zA-Z0-9#._]*)+(,([a-zA-Z0-9#._]*):([a-zA-Z0-9#._]*)+)*$)");
            Matcher match = pattern.matcher(value);
            if (!StringUtil.isNullOrEmpty(value) && !match.find()) {
            	return FormValidation.error("Custom Fields must have given format: key1:val1,key2:val2. \nCustom field allows to use these special characters: # . _ ");
            }

            return FormValidation.ok();
        }

        /**
         * This method verify if force scan is checked
         *
         * @param value
         * @return
         */
        public FormValidation doCheckForceScan(@QueryParameter boolean value, @QueryParameter boolean incremental,@AncestorInPath Item item) {
        	if (item == null) {
                return FormValidation.ok();
        	}
            item.checkPermission(Item.CONFIGURE);
            if (incremental && value) {
                return FormValidation.error("Force scan and incremental scan can not be configured in pair for SAST");
            }

            return FormValidation.ok();
        }

        /**
         * This method verifies if force scan and incremental scan both configured
         *
         * @param value
         * @return
         */
        public FormValidation doCheckIncremental(@QueryParameter boolean value, @QueryParameter boolean forceScan,@AncestorInPath Item item) {
        	if (item == null) {
                return FormValidation.ok();
        	}
            item.checkPermission(Item.CONFIGURE);
            if (forceScan && value) {
                forceScan = false;

                return FormValidation.error("Force scan and incremental scan can not be configured in pair for SAST");
            }

            return FormValidation.ok();
        }

        @POST
        public FormValidation doTestScaSASTConnection(@QueryParameter final String scaSastServerUrl, @QueryParameter final String password,
                                                      @QueryParameter final String username, @QueryParameter final String timestamp,
                                                      @QueryParameter final String sastCredentialsId, @QueryParameter final boolean isProxy,
                                                      @AncestorInPath Item item) {
            if(item==null){
                Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
            }else if(item!=null){
                item.checkPermission(Item.CONFIGURE);
            }
            // timestamp is not used in code, it is one of the arguments to
            // invalidate Internet Explorer cache
            CxConnectionDetails cred;
            LegacyClient commonClient = null;
            try {
                try {
                    cred = CxConnectionDetails.resolveCred(true, scaSastServerUrl, username, getPasswordPlainText(password),
                            sastCredentialsId, isProxy, this, item);
                    CxConnectionDetails.validateCxCredentials(cred);
                    Jenkins instance = Jenkins.getInstance();

                    if (cred.isProxy()) {
                        if (instance != null && instance.proxy != null && isCxURLinNoProxyHost(serverUrl, instance.proxy.getNoProxyHostPatterns())) {
                            cred.setScaProxy(false);
                        }
                        commonClient = CommonClientFactory.getInstance(cred, this.isEnableCertificateValidation(), serverLog);
                    } else {
                        cred.setScaProxy(false);
                        commonClient = CommonClientFactory.getInstance(cred, this.isEnableCertificateValidation(), serverLog);
                    }
                } catch (Exception e) {
                    return buildError(e, "Failed to init cx client");
                }

                try {
                    commonClient.login();
                    try {
                        commonClient.getTeamList();
                    } catch (Exception e) {
                        return FormValidation.error("Connection Failed.\n"
                                + "Validate the provided login credentials and server URL are correct.\n"
                                + "In addition, make sure the installed plugin version is compatible with the CxSAST version according to CxSAST release notes.\n"
                                + "Error: " + e.getMessage());
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

        @POST
        public FormValidation doValidateMvnPath(@QueryParameter final String mvnPath) throws InterruptedException {
            Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
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

        @POST
        public FormValidation doTestScaConnection(@QueryParameter String scaServerUrl,
                                                  @QueryParameter String scaAccessControlUrl,
                                                  @QueryParameter String scaCredentialsId,
                                                  @QueryParameter String scaTenant,
                                                  @QueryParameter Integer scaTimeout,
                                                  @AncestorInPath Item item) {
            if(item==null){
                Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
            }else if(item!=null){
                item.checkPermission(Item.CONFIGURE);
            }

            try {
                CxScanConfig config = new CxScanConfig();
                config.setCxOrigin(REQUEST_ORIGIN);
                config.setDisableCertificateValidation(!isEnableCertificateValidation());
                config.setOsaGenerateJsonReport(false);

                AstScaConfig scaConfig = new AstScaConfig();
                scaConfig.setAccessControlUrl(scaAccessControlUrl);
                scaConfig.setApiUrl(scaServerUrl);
                scaConfig.setTenant(scaTenant);


                UsernamePasswordCredentials credentials = CxConnectionDetails.getCredentialsById(scaCredentialsId, item);
                if (credentials == null) {
                    throw new CxCredException("Sca connection failed. Please recheck the account name and CxSCA credentials you provided and try again.");
                }
                scaConfig.setUsername(credentials.getUsername());
                scaConfig.setPassword(credentials.getPassword().getPlainText());
                scaConfig.setSourceLocationType(SourceLocationType.LOCAL_DIRECTORY);
                scaConfig.setRemoteRepositoryInfo(null);
                config.setAstScaConfig(scaConfig);
                config.addScannerType(ScannerType.AST_SCA);
                config.setSCAScanTimeoutInMinutes(scaTimeout);

                try {
                    Jenkins instance = Jenkins.getInstance();
                    if (instance != null && instance.proxy != null) {
                        if (isProxy && !(isCxURLinNoProxyHost(scaConfig.getAccessControlUrl(), instance.proxy.getNoProxyHostPatterns()))) {
                            config.setScaProxy(true);
                        } else {
                            config.setScaProxy(false);
                        }
                        ProxyConfig proxyConfig = ProxyHelper.getProxyConfig();
                        config.setScaProxyConfig(proxyConfig);
                    }
                } catch (Exception e) {
                    return buildError(e, "Failed to init cx client");
                }

                CxClientDelegator commonClient = CommonClientFactory.getClientDelegatorInstance(config, serverLog);
                try {
                    commonClient.getScaClient().testScaConnection();
                } catch (CxClientException e) {
                    throw new CxCredException("Sca connection failed. Please recheck the account name and CxSCA credentials you provided and try again.");
                }
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
        private LegacyClient prepareLoggedInClient(CxConnectionDetails connDetails)
                throws IOException, CxClientException {
            LegacyClient ret;
            Jenkins instance = Jenkins.getInstance();

            if (connDetails.isProxy()) {
                if (instance != null && instance.proxy != null && isCxURLinNoProxyHost(serverUrl, instance.proxy.getNoProxyHostPatterns())) {
                    connDetails.setProxy(false);
                }
                ret = CommonClientFactory.getInstance(connDetails, this.isEnableCertificateValidation(), serverLog);
            } else {
                connDetails.setProxy(false);
                ret = CommonClientFactory.getInstance(connDetails, this.isEnableCertificateValidation(), serverLog);
            }

            ret.login();
            return ret;
        }

        @POST
        public ListBoxModel doFillPostScanActionIdItems(@QueryParameter final boolean useOwnServerCredentials, @QueryParameter final String serverUrl,
                                                        @QueryParameter final String username, @QueryParameter final String password,
                                                        @QueryParameter final String timestamp, @QueryParameter final String credentialsId,
                                                        @QueryParameter final boolean isProxy, @AncestorInPath Item item) {
        	if (item == null) {
                return new ListBoxModel();
        	}
            item.checkPermission(Item.CONFIGURE);
            // timestamp is not used in code, it is one of the arguments to invalidate Internet Explorer cache
            ListBoxModel listBoxModel = new ListBoxModel();
            LegacyClient commonClient = null;
            try {
                CxConnectionDetails connDetails = CxConnectionDetails.resolveCred(!useOwnServerCredentials, serverUrl, username,
                        StringEscapeUtils.escapeHtml4(getPasswordPlainText(password)), credentialsId, isProxy, this, item);
                commonClient = prepareLoggedInClient(connDetails);
                List<PostAction> teamList = commonClient.getPostScanActionList();
                if (listBoxModel.isEmpty() && !listBoxModel.contains("")){
                    listBoxModel.add(new ListBoxModel.Option("", Integer.toString(0)));
                }
                for (PostAction postAction : teamList) {
                    if (postAction.getType().contains("POST_SCAN_COMMAND")){
                        listBoxModel.add(new ListBoxModel.Option(postAction.getName(), Integer.toString(postAction.getId())));
                    }else {
                        continue;
                    }

                }
                return listBoxModel;

            } catch (Exception e) {
                serverLog.error("Failed to populate post action list: " + e.toString());
                String message = "Provide Checkmarx server credentials to see teams list";
                listBoxModel.add(new ListBoxModel.Option(message, message));
                return listBoxModel;
            } finally {
                if (commonClient != null) {
                    commonClient.close();
                }
            }
        }

        /*
         *  Note: This method is called concurrently by multiple threads, refrain from using mutable
         *  shared state to avoid synchronization issues.
         */
        @POST
        public ComboBoxModel doFillProjectNameItems(@QueryParameter final boolean useOwnServerCredentials, @QueryParameter final String serverUrl,
                                                    @QueryParameter final String username, @QueryParameter final String password,
                                                    @QueryParameter final String timestamp, @QueryParameter final String credentialsId,
                                                    @QueryParameter final boolean isProxy, @AncestorInPath Item item) {
        	if (item == null) {
                return new ComboBoxModel();
        	}
            item.checkPermission(Item.CONFIGURE);
            // timestamp is not used in code, it is one of the arguments to invalidate Internet Explorer cache
            ComboBoxModel projectNames = new ComboBoxModel();
            LegacyClient commonClient = null;
            try {
                CxConnectionDetails connDetails = CxConnectionDetails.resolveCred(!useOwnServerCredentials, serverUrl, username,
                        getPasswordPlainText(password), credentialsId, isProxy, this, item);
                commonClient = prepareLoggedInClient(connDetails);
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
        @POST
        public ListBoxModel doFillPresetItems(@QueryParameter final boolean useOwnServerCredentials, @QueryParameter final String serverUrl,
                                              @QueryParameter final String username, @QueryParameter final String password,
                                              @QueryParameter final String timestamp, @QueryParameter final String credentialsId,
                                              @QueryParameter final boolean isProxy, @AncestorInPath Item item) {
        	if (item == null) {
                return new ListBoxModel();
        	}
            item.checkPermission(Item.CONFIGURE);
            // timestamp is not used in code, it is one of the arguments to invalidate Internet Explorer cache
            ListBoxModel listBoxModel = new ListBoxModel();
            try {
                CxConnectionDetails connDetails = CxConnectionDetails.resolveCred(!useOwnServerCredentials, serverUrl, username,
                        StringEscapeUtils.escapeHtml4(getPasswordPlainText(password)), credentialsId, isProxy, this, item);
                LegacyClient commonClient = prepareLoggedInClient(connDetails);

                //todo import preset
                List<Preset> presets = commonClient.getPresetList();
                listBoxModel.add(new ListBoxModel.Option(LegacyClient.PRESETNAME_PROJET_SETTING_DEFAULT, LegacyClient.PRESETID_PROJET_SETTING_DEFAULT));
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
        @POST
        public FormValidation doCheckFullScanCycle(@QueryParameter final int value , @AncestorInPath Item item) {
        	if (item == null) {
                return FormValidation.ok();
        	}
            item.checkPermission(Item.CONFIGURE);
            if (value >= FULL_SCAN_CYCLE_MIN && value <= FULL_SCAN_CYCLE_MAX) {
                return FormValidation.ok();
            } else {
                return FormValidation.error("Number must be in the range " + FULL_SCAN_CYCLE_MIN + "-" + FULL_SCAN_CYCLE_MAX);
            }
        }

        @POST
        public ListBoxModel doFillSourceEncodingItems(@QueryParameter final boolean useOwnServerCredentials, @QueryParameter final String serverUrl,
                                                      @QueryParameter final String username, @QueryParameter final String password,
                                                      @QueryParameter final String timestamp, @QueryParameter final String credentialsId,
                                                      @QueryParameter final boolean isProxy, @AncestorInPath Item item) {
        	if (item == null) {
                return new ListBoxModel();
        	}
            item.checkPermission(Item.CONFIGURE);
            // timestamp is not used in code, it is one of the arguments to invalidate Internet Explorer cache
            ListBoxModel listBoxModel = new ListBoxModel();
            LegacyClient commonClient = null;
            try {
                CxConnectionDetails connDetails = CxConnectionDetails.resolveCred(!useOwnServerCredentials, serverUrl, username,
                        StringEscapeUtils.escapeHtml4(getPasswordPlainText(password)), credentialsId, isProxy, this, item);

                commonClient = prepareLoggedInClient(connDetails);
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

        @POST
        public ListBoxModel doFillGroupIdItems(@QueryParameter final boolean useOwnServerCredentials, @QueryParameter final String serverUrl,
                                               @QueryParameter final String username, @QueryParameter final String password,
                                               @QueryParameter final String timestamp, @QueryParameter final String credentialsId,
                                               @QueryParameter final boolean isProxy, @AncestorInPath Item item) {
        	if (item == null) {
                return new ListBoxModel();
        	}
            item.checkPermission(Item.CONFIGURE);
            // timestamp is not used in code, it is one of the arguments to invalidate Internet Explorer cache
            ListBoxModel listBoxModel = new ListBoxModel();
            LegacyClient commonClient = null;
            try {
                CxConnectionDetails connDetails = CxConnectionDetails.resolveCred(!useOwnServerCredentials, serverUrl, username,
                        StringEscapeUtils.escapeHtml4(getPasswordPlainText(password)), credentialsId, isProxy, this, item);
                commonClient = prepareLoggedInClient(connDetails);

                commonClient.getTeamList().stream().sorted(
                        (firstElmnt, secondElmnt) ->
                                firstElmnt.getFullName().compareToIgnoreCase(secondElmnt.fullName))
                        .forEach(team ->
                                listBoxModel.add(new ListBoxModel.Option(team.getFullName(), team.getId())));

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
        @POST
        public ListBoxModel doFillFailBuildOnNewSeverityItems(@AncestorInPath Item item) {
        	if (item == null) {
                return new ListBoxModel();
        	}
            item.checkPermission(Item.CONFIGURE);
            ListBoxModel listBoxModel = new ListBoxModel();
            listBoxModel.add(new ListBoxModel.Option("High", "HIGH"));
            listBoxModel.add(new ListBoxModel.Option("Medium", "MEDIUM"));
            listBoxModel.add(new ListBoxModel.Option("Low", "LOW"));
            return listBoxModel;

        }

        @POST
        public ListBoxModel doFillVulnerabilityThresholdResultItems(@AncestorInPath Item item) {
        	if (item == null) {
                return new ListBoxModel();
        	}
            item.checkPermission(Item.CONFIGURE);
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
        @POST
        public FormValidation doCheckHighThreshold(@QueryParameter final Integer value,@AncestorInPath Item item) {
        	if (item == null) {
                return FormValidation.ok();
        	}
            item.checkPermission(Item.CONFIGURE);
            return checkNonNegativeValue(value);
        }

        /*
         * Note: This method is called concurrently by multiple threads, refrain from using mutable shared state to
         * avoid synchronization issues.
         */
        @POST
        public FormValidation doCheckMediumThreshold(@QueryParameter final Integer value,@AncestorInPath Item item) {
        	if (item == null) {
                return FormValidation.ok();
        	} 
            item.checkPermission(Item.CONFIGURE);
            return checkNonNegativeValue(value);
        }

        /*
         * Note: This method is called concurrently by multiple threads, refrain from using mutable shared state to
         * avoid synchronization issues.
         */
        @POST
        public FormValidation doCheckLowThreshold(@QueryParameter final Integer value,@AncestorInPath Item item) {
        	if (item == null) {
                return FormValidation.ok();
        	} 
            item.checkPermission(Item.CONFIGURE);
            return checkNonNegativeValue(value);
        }

        /*
         * Note: This method is called concurrently by multiple threads, refrain from using mutable shared state to
         * avoid synchronization issues.
         */
        @POST
        public FormValidation doCheckHighThresholdEnforcement(@QueryParameter final Integer value) {
            Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
            return checkNonNegativeValue(value);
        }

        /*
         * Note: This method is called concurrently by multiple threads, refrain from using mutable shared state to
         * avoid synchronization issues.
         */
        @POST
        public FormValidation doCheckMediumThresholdEnforcement(@QueryParameter final Integer value) {
            Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
            return checkNonNegativeValue(value);
        }

        /*
         * Note: This method is called concurrently by multiple threads, refrain from using mutable shared state to
         * avoid synchronization issues.
         */
        @POST
        public FormValidation doCheckLowThresholdEnforcement(@QueryParameter final Integer value) {
            Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
            return checkNonNegativeValue(value);
        }

        /*
         *  Note: This method is called concurrently by multiple threads, refrain from using mutable
         *  shared state to avoid synchronization issues.
         */

        @POST
        public FormValidation doCheckOsaHighThreshold(@QueryParameter final Integer value,@AncestorInPath Item item) {
        	if (item == null) {
                return FormValidation.ok();
        	} 
            item.checkPermission(Item.CONFIGURE);
            return checkNonNegativeValue(value);
        }

        /*
         * Note: This method is called concurrently by multiple threads, refrain from using mutable shared state to
         * avoid synchronization issues.
         */
        @POST
        public FormValidation doCheckOsaMediumThreshold(@QueryParameter final Integer value,@AncestorInPath Item item) {
        	if (item == null) {
                return FormValidation.ok();
        	} 
            item.checkPermission(Item.CONFIGURE);
            return checkNonNegativeValue(value);
        }

        /*
         * Note: This method is called concurrently by multiple threads, refrain from using mutable shared state to
         * avoid synchronization issues.
         */
        @POST
        public FormValidation doCheckOsaLowThreshold(@QueryParameter final Integer value,@AncestorInPath Item item) {
        	if (item == null) {
                return FormValidation.ok();
        	} 
            item.checkPermission(Item.CONFIGURE);
            return checkNonNegativeValue(value);
        }

        @POST
            public FormValidation doCheckOsaHighThresholdEnforcement(@QueryParameter final Integer value) {
            Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
            return checkNonNegativeValue(value);
        }

        /*
         * Note: This method is called concurrently by multiple threads, refrain from using mutable shared state to
         * avoid synchronization issues.
         */
        @POST
        public FormValidation doCheckOsaMediumThresholdEnforcement(@QueryParameter final Integer value) {
            Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
            return checkNonNegativeValue(value);
        }

        /*
         * Note: This method is called concurrently by multiple threads, refrain from using mutable shared state to
         * avoid synchronization issues.
         */
        @POST
        public FormValidation doCheckOsaLowThresholdEnforcement(@QueryParameter final Integer value) {
            Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
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
            // Have put the below line to fix AB # 493 - "Globally define dependency scan settings" selection is not retained.
            // Line pluginData.put(DEPENDENCY_SCAN_CONFIG_PROP, null); should have solved the problem but putting null is actually not working. JSONObject.NULL
            // API also no more available
            setGloballyDefineScanSettings(pluginData.has(DEPENDENCY_SCAN_CONFIG_PROP));
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

        @POST
        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item item, @QueryParameter String credentialsId) {
            if(item==null){
                Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
            }else if(item!=null){
                item.checkPermission(Item.CONFIGURE);
            }
            return getCredentialList(item, credentialsId);
        }

        @POST
        public ListBoxModel doFillScaCredentialsIdItems(@AncestorInPath Item item, @QueryParameter String scaCredentialsId) {
            if(item==null){
                Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
            }else if(item!=null){
                item.checkPermission(Item.CONFIGURE);
            }
            return getCredentialList(item, scaCredentialsId);
        }

        @POST
        public ListBoxModel doFillSastCredentialsIdItems(@AncestorInPath Item item, @QueryParameter String sastCredentialsId) {
            if(item==null){
                Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
            }else if(item!=null){
                item.checkPermission(Item.CONFIGURE);
            }
            return getCredentialList(item, sastCredentialsId);
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
