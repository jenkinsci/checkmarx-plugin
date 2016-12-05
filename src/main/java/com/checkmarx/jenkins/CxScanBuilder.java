package com.checkmarx.jenkins;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.triggers.SCMTrigger;
import hudson.util.ComboBoxModel;
import hudson.util.FormValidation;
import hudson.util.Secret;
import jenkins.tasks.SimpleBuildStep;
import hudson.util.ListBoxModel;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URLDecoder;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

import javax.xml.ws.WebServiceException;

import net.sf.json.JSONObject;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.WriterAppender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.checkmarx.components.zipper.Zipper;
import com.checkmarx.ws.CxJenkinsWebService.CliScanArgs;
import com.checkmarx.ws.CxJenkinsWebService.ConfigurationSet;
import com.checkmarx.ws.CxJenkinsWebService.CxWSBasicRepsonse;
import com.checkmarx.ws.CxJenkinsWebService.CxWSCreateReportResponse;
import com.checkmarx.ws.CxJenkinsWebService.CxWSReportType;
import com.checkmarx.ws.CxJenkinsWebService.CxWSResponseRunID;
import com.checkmarx.ws.CxJenkinsWebService.Group;
import com.checkmarx.ws.CxJenkinsWebService.LocalCodeContainer;
import com.checkmarx.ws.CxJenkinsWebService.Preset;
import com.checkmarx.ws.CxJenkinsWebService.ProjectDisplayData;
import com.checkmarx.ws.CxJenkinsWebService.ProjectSettings;
import com.checkmarx.ws.CxJenkinsWebService.SourceCodeSettings;
import com.checkmarx.ws.CxJenkinsWebService.SourceLocationType;

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

    final private boolean useOwnServerCredentials;

    @Nullable private String serverUrl;
    @Nullable private String username;
    @Nullable private String password;
    @Nullable private String projectName;
    @Nullable private String groupId;
    @Nullable private long projectId;

    @Nullable private String preset;
    final private boolean presetSpecified;
    @Nullable private String excludeFolders;
    @Nullable private String filterPattern;

    private boolean incremental;
    private boolean fullScansScheduled;
    private int fullScanCycle;

    private boolean isThisBuildIncremental;

    @Nullable private String sourceEncoding;
    @Nullable private String comment;

    private boolean skipSCMTriggers;
    private boolean waitForResultsEnabled;

    private boolean vulnerabilityThresholdEnabled;
    final private int highThreshold;
    final private int mediumThreshold;
    final private int lowThreshold;
    private boolean generatePdfReport;

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

    //////////////////////////////////////////////////////////////////////////////////////
    // Constructors
    //////////////////////////////////////////////////////////////////////////////////////

	@DataBoundConstructor
	public CxScanBuilder(
			 boolean useOwnServerCredentials,
             @Nullable String serverUrl,
             @Nullable String username,
             @Nullable String password,
             String projectName,
             long projectId,
             String buildStep,
             String groupId,
             @Nullable String preset,
             String jobStatusOnError,
             boolean presetSpecified,
             String excludeFolders,
             String filterPattern,
             boolean incremental,
             boolean fullScansScheduled,
             int fullScanCycle,
             @Nullable String sourceEncoding,
             @Nullable String comment,
             boolean skipSCMTriggers,
             boolean waitForResultsEnabled,
             boolean vulnerabilityThresholdEnabled,
             int highThreshold,
             int mediumThreshold,
             int lowThreshold,
             boolean generatePdfReport,
             String vulnerabilityThresholdResult) {
        this.useOwnServerCredentials = useOwnServerCredentials;
        this.serverUrl = serverUrl;
        this.username = username;
        this.password = password;
        this.projectName = projectName;
        this.projectId = projectId;
        this.groupId = groupId;
        this.preset = preset;
        
        if(!StringUtils.isEmpty(jobStatusOnError))
        {
        	this.jobStatusOnError = JobStatusOnError.valueOf(jobStatusOnError);
        }
        
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
        this.generatePdfReport =  generatePdfReport;
        this.thresholdSettings = null;
        if(!StringUtils.isEmpty(vulnerabilityThresholdResult))
        {
        	this.vulnerabilityThresholdResult=Result.fromString(vulnerabilityThresholdResult);
        }
        
        if(null == this.jobStatusOnError)
        {
        	this.jobStatusOnError = JobStatusOnError.FAILURE;
        }
        
        init();
    }

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
             int highThreshold,
             int mediumThreshold,
             int lowThreshold,
             boolean generatePdfReport,
             String thresholdSettings,
             Result vulnerabilityThresholdResult) {
        this.useOwnServerCredentials = useOwnServerCredentials;
        this.serverUrl = serverUrl;
        this.username = username;
        this.password = Secret.fromString(password).getEncryptedValue();
        // Workaround for compatibility with Conditional BuildStep Plugin
        this.projectName = (projectName==null)? buildStep:projectName;
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
        this.generatePdfReport =  generatePdfReport;
        this.thresholdSettings = thresholdSettings;
        this.vulnerabilityThresholdResult = vulnerabilityThresholdResult;
        
        init();
    }

    private void init() {
		updateJobOnGlobalConfigChange();
	}

	private void updateJobOnGlobalConfigChange() {
		if(!getDescriptor().isForcingVulnerabilityThresholdEnabled() && shouldUseGlobalThreshold()){
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
        return Secret.fromString(password).getPlainText();
    }

    @Nullable
    public String getProjectName() {
        return projectName;
    }
    
    public long getProjectId() {
		return projectId;
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

    public boolean isFullScansScheduled(){
        return fullScansScheduled;
    }

    public int getFullScanCycle(){
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

    public int getHighThreshold() {
        return highThreshold;
    }

    public int getMediumThreshold() {
        return mediumThreshold;
    }

    public int getLowThreshold() {
        return lowThreshold;
    }

    public boolean isGeneratePdfReport() {
        return generatePdfReport;
    }
    

	public void setThresholdSettings(String thresholdSettings) {
		this.thresholdSettings = thresholdSettings;
	}
	
	public String getThresholdSettings(){
		return thresholdSettings;
	}

	public void setVulnerabilityThresholdResult(Result result){
		this.vulnerabilityThresholdResult = result;
	}
	
	public Result getVulnerabilityThresholdResult(){
		return vulnerabilityThresholdResult;
	}
	
	@DataBoundSetter
	public void setServerUrl(String serverUrl) {
		this.serverUrl = serverUrl;
	}
	@DataBoundSetter
	public void setUsername(String username) {
		this.username = username;
	}
	@DataBoundSetter
	public void setPassword(String password) {
		this.password = password;
	}
	@DataBoundSetter
	public void setProjectName(String projectName) {
		this.projectName = projectName;
	}
	@DataBoundSetter
	public void setGroupId(String groupId) {
		this.groupId = groupId;
	}
	@DataBoundSetter
	public void setProjectId(long projectId) {
		this.projectId = projectId;
	}
	@DataBoundSetter
	public void setPreset(String preset) {
		this.preset = preset;
	}
	@DataBoundSetter
	public void setExcludeFolders(String excludeFolders) {
		this.excludeFolders = excludeFolders;
	}
	@DataBoundSetter
	public void setFilterPattern(String filterPattern) {
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
	public void setSourceEncoding(String sourceEncoding) {
		this.sourceEncoding = sourceEncoding;
	}
	@DataBoundSetter
	public void setComment(String comment) {
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
	public void setGeneratePdfReport(boolean generatePdfReport) {
		this.generatePdfReport = generatePdfReport;
	}
	@DataBoundSetter
	public void setJobStatusOnError(JobStatusOnError jobStatusOnError) {
		this.jobStatusOnError = jobStatusOnError;
	}

	@Override
    public boolean perform(final AbstractBuild<?, ?> build,
                           final Launcher launcher,
                           final BuildListener listener) throws InterruptedException, IOException {
    	this.perform(build, build.getWorkspace(), launcher, listener);
    	return true;
    }
    	
    @Override
    public void perform(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener)
    														throws InterruptedException, IOException {

		final DescriptorImpl descriptor = getDescriptor();

		CxWSResponseRunID cxWSResponseRunID = null;
		CxWebService cxWebService = null;
		CxWSCreateReportResponse reportResponse = null;

        try {
            File checkmarxBuildDir = new File(build.getRootDir(), "checkmarx");
            checkmarxBuildDir.mkdir();

            initLogger(checkmarxBuildDir, listener, instanceLoggerSuffix(build));

            instanceLogger.info("Checkmarx Jenkins plugin version: " + CxConfig.version());

            if (isSkipScan(build)) {
                instanceLogger.info("Checkmarx scan skipped since the build was triggered by SCM. " +
                        "Visit plugin configuration page to disable this skip.");
                return;
            }

            final String serverUrlToUse = isUseOwnServerCredentials() ? getServerUrl() : descriptor.getServerUrl();
            final String usernameToUse = isUseOwnServerCredentials() ? getUsername() : descriptor.getUsername();
            final String passwordToUse = isUseOwnServerCredentials() ? getPassword() : descriptor.getPassword();

            String serverUrlToUseNotNull = serverUrlToUse != null ? serverUrlToUse : "";
            cxWebService = new CxWebService(serverUrlToUseNotNull, instanceLoggerSuffix(build));	
            cxWebService.login(usernameToUse, passwordToUse);

            instanceLogger.info("Checkmarx server login successful");

			cxWSResponseRunID = submitScan(build, workspace, cxWebService, listener);
            instanceLogger.info("\nScan job submitted successfully\n");

            if (!isWaitForResultsEnabled() && !(descriptor.isForcingVulnerabilityThresholdEnabled() && descriptor.isLockVulnerabilitySettings())) {
                return;
            }

            long scanId = cxWebService.trackScanProgress(cxWSResponseRunID, usernameToUse, passwordToUse, descriptor.getScanTimeOutEnabled(), descriptor.getScanTimeoutDuration());

            if (scanId == 0) {
                build.setResult(Result.UNSTABLE);
                return;
            }

			reportResponse = cxWebService.generateScanReport(scanId, CxWSReportType.XML);
            File xmlReportFile = new File(checkmarxBuildDir, "ScanReport.xml");
            cxWebService.retrieveScanReport(reportResponse.getID(), xmlReportFile, CxWSReportType.XML);

            if (generatePdfReport) {
				reportResponse = cxWebService.generateScanReport(scanId, CxWSReportType.PDF);
                File pdfReportFile = new File(checkmarxBuildDir, CxScanResult.PDF_REPORT_NAME);
				cxWebService.retrieveScanReport(reportResponse.getID(), pdfReportFile, CxWSReportType.PDF);
            }

            // Parse scan report and present results in Jenkins
            CxScanResult cxScanResult = new CxScanResult(build, instanceLoggerSuffix(build), serverUrlToUse);
            cxScanResult.readScanXMLReport(xmlReportFile);
            build.addAction(cxScanResult);
            
            ThresholdConfig thresholdConfig = createThresholdConfig();

			if ((descriptor.isForcingVulnerabilityThresholdEnabled() && descriptor.isLockVulnerabilitySettings() || isVulnerabilityThresholdEnabled())
					&& isThresholdCrossed(thresholdConfig, cxScanResult)) {

				build.setResult(thresholdConfig.getBuildStatus());
				return;
			}

            return;
		} catch (IOException | WebServiceException e) {
			if (useUnstableOnError(descriptor)) {
				build.setResult(Result.UNSTABLE);
				instanceLogger.error(e.getMessage(), e);
				return;
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

	private ThresholdConfig createThresholdConfig() {
		ThresholdConfig config = new ThresholdConfig();
		
		if (shouldUseGlobalThreshold()) {
			final DescriptorImpl descriptor = getDescriptor();
			config.setHighSeverity( descriptor.getHighThresholdEnforcement());
			config.setMediumSeverity( descriptor.getMediumThresholdEnforcement());
			config.setLowSeverity( descriptor.getLowThresholdEnforcement());
			config.setBuildStatus(Result.fromString(descriptor.getJobGlobalStatusOnThresholdViolation().name()));
		} else {
			config.setHighSeverity(getHighThreshold());
			config.setMediumSeverity(getMediumThreshold());
			config.setLowSeverity (getLowThreshold());
			config.setBuildStatus(getVulnerabilityThresholdResult());
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
	 * @param descriptor
	 *            Descriptor of the current build step
	 * @return if job should fail with <code>UNSTABLE</code> status instead of <code>FAILED</code>
	 */
	private boolean useUnstableOnError(final DescriptorImpl descriptor) {
		return JobStatusOnError.UNSTABLE.equals(getJobStatusOnError())
				|| (JobStatusOnError.GLOBAL.equals(getJobStatusOnError()) && JobGlobalStatusOnError.UNSTABLE.equals(descriptor
						.getJobGlobalStatusOnError()));
	}

	private boolean isThresholdCrossed(ThresholdConfig thresholdConfig, @NotNull final CxScanResult cxScanResult) {
		logFoundVulnerabilities("high", cxScanResult.getHighCount(), thresholdConfig.getHighSeverity());
		logFoundVulnerabilities("medium", cxScanResult.getMediumCount(), thresholdConfig.getMediumSeverity());
		logFoundVulnerabilities("low", cxScanResult.getLowCount(), thresholdConfig.getLowSeverity());

		return cxScanResult.getHighCount() > thresholdConfig.getHighSeverity()
				|| cxScanResult.getMediumCount() > thresholdConfig.getMediumSeverity()
				|| cxScanResult.getLowCount() > thresholdConfig.getLowSeverity();
	}

	private void logFoundVulnerabilities(String severity, int actualNumber, int configuredHighThreshold) {
		instanceLogger.info("Number of " + severity + " severity vulnerabilities: " + actualNumber + " stability threshold: "
				+ configuredHighThreshold);
	}

    private String instanceLoggerSuffix(final Run<?, ?> build) {
        return build.getParent().getDisplayName() + "-" + build.getDisplayName();
    }

    private void initLogger(final File checkmarxBuildDir, final TaskListener listener, final String loggerSuffix) {
        instanceLogger = CxLogUtils.loggerWithSuffix(getClass(), loggerSuffix);
        final WriterAppender writerAppender = new WriterAppender(new PatternLayout("%m%n"),listener.getLogger());
        writerAppender.setThreshold(Level.INFO);
        final Logger parentLogger = CxLogUtils.parentLoggerWithSuffix(loggerSuffix);
        parentLogger.addAppender(writerAppender);
        String logFileName = checkmarxBuildDir.getAbsolutePath() + File.separator + "checkmarx.log";

        try {
            fileAppender = new FileAppender(new PatternLayout("%C: [%d] %-5p: %m%n"),logFileName);
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

    private CxWSResponseRunID submitScan(final Run<?, ?> build, final FilePath workspace, final CxWebService cxWebService, final TaskListener listener) throws IOException
    {
        isThisBuildIncremental = isThisBuildIncremental(build.getNumber());

        if(isThisBuildIncremental){
            instanceLogger.info("\nScan job started in incremental scan mode\n");
		} else {
            instanceLogger.info("\nScan job started in full scan mode\n");
        }
        
        instanceLogger.info("Started zipping the workspace");

        try {
            // hudson.FilePath will work in distributed Jenkins installation
            FilePath baseDir = workspace;
			if (baseDir == null) {
				throw new AbortException(
						"Checkmarx Scan Failed: cannot acquire Jenkins workspace location. It can be due to workspace residing on a disconnected slave.");
			}
			EnvVars env = build.getEnvironment(listener);

			String combinedFilterPattern = env.expand(getFilterPattern()) + "," + processExcludeFolders(env.expand(getExcludeFolders()));
            // Implementation of FilePath.FileCallable allows extracting a java.io.File from
            // hudson.FilePath and still working with it in remote mode
			CxZipperCallable zipperCallable = new CxZipperCallable(combinedFilterPattern);

            final CxZipResult zipResult = baseDir.act(zipperCallable);
            instanceLogger.info(zipResult.getLogMessage());
            final FilePath tempFile = zipResult.getTempFile();
            final int numOfZippedFiles = zipResult.getNumOfZippedFiles();

            instanceLogger.info("Zipping complete with " + numOfZippedFiles + " files, total compressed size: " +
                    FileUtils.byteCountToDisplaySize(tempFile.length() / 8 * 6)); // We print here the size of compressed sources before encoding to base 64
            instanceLogger.info("Temporary file with zipped and base64 encoded sources was created at: " + tempFile.getRemote());
            listener.getLogger().flush();
            // Create cliScanArgs object with dummy byte array for zippedFile field
            // Streaming scan web service will nullify zippedFile filed and use tempFile
            // instead

            final CliScanArgs cliScanArgs = createCliScanArgs(new byte[]{}, env);

			// Check if the project already exists
            final CxWSBasicRepsonse validateProjectRespnse = cxWebService.validateProjectName(cliScanArgs.getPrjSettings().getProjectName(), groupId);
			CxWSResponseRunID cxWSResponseRunID;
            if (validateProjectRespnse.isIsSuccesfull()){
				cxWSResponseRunID = cxWebService.createAndRunProject(cliScanArgs.getPrjSettings(),
						cliScanArgs.getSrcCodeSettings().getPackagedCode(), true, true, tempFile);
            } else {
                if (projectId == 0) {
                    projectId = cxWebService.getProjectId(cliScanArgs.getPrjSettings());
                }

                cliScanArgs.getPrjSettings().setProjectID(projectId);

                if (isThisBuildIncremental) {
					cxWSResponseRunID = cxWebService.runIncrementalScan(cliScanArgs.getPrjSettings(), cliScanArgs.getSrcCodeSettings()
							.getPackagedCode(), true, true, tempFile);
                } else {
                    cxWSResponseRunID = cxWebService.runScanAndAddToProject(cliScanArgs.getPrjSettings(), cliScanArgs.getSrcCodeSettings().getPackagedCode(), true, true, tempFile);
                }
            }

            tempFile.delete();
            instanceLogger.info("Temporary file deleted");

            return cxWSResponseRunID;
		} catch (Zipper.MaxZipSizeReached e) {
			throw new AbortException("Checkmarx Scan Failed: Reached maximum upload size limit of "
					+ FileUtils.byteCountToDisplaySize(CxConfig.maxZipSize()));
		} catch (Zipper.NoFilesToZip e) {
			throw new AbortException("Checkmarx Scan Failed: No files to scan");
		}
        catch (InterruptedException e) {
            throw new AbortException("Remote operation failed on slave node: " + e.getMessage());
        }
    }

    @NotNull
    private String processExcludeFolders(String excludeFolders)
    {
        if (excludeFolders==null)
        {
            return "";
        }
        StringBuilder result = new StringBuilder();
        String[] patterns = StringUtils.split(excludeFolders, ",\n");
        for(String p : patterns)
        {
            p = p.trim();
            if (p.length()>0)
            {
                result.append("!**/");
                result.append(p);
                result.append("/**/*, ");
            }
        }
        instanceLogger.debug("Exclude folders converted to: " +result.toString());
        return result.toString();
    }

    private CliScanArgs createCliScanArgs(byte[] compressedSources, EnvVars env) {

        ProjectSettings projectSettings = new ProjectSettings();

        long presetLong = 0; // Default value to use in case of exception
        try {
            presetLong = Long.parseLong(getPreset());
        } catch (Exception e) {
            instanceLogger.error("Encountered illegal preset value: " + getPreset() + ". Using default preset.");
        }

        projectSettings.setPresetID(presetLong);
        projectSettings.setProjectName(env.expand(getProjectName()));
        projectSettings.setAssociatedGroupID(getGroupId());

        long configuration = 0; // Default value to use in case of exception
        try {
            configuration = Long.parseLong(getSourceEncoding());
        } catch (Exception e) {
            instanceLogger.error("Encountered illegal source encoding (configuration) value: " + getSourceEncoding() + ". Using default configuration.");
        }
        projectSettings.setScanConfigurationID(configuration);

        LocalCodeContainer localCodeContainer = new LocalCodeContainer();
        localCodeContainer.setFileName("src.zip");
        localCodeContainer.setZippedFile(compressedSources);

        SourceCodeSettings sourceCodeSettings = new SourceCodeSettings();
        sourceCodeSettings.setSourceOrigin(SourceLocationType.LOCAL);
        sourceCodeSettings.setPackagedCode(localCodeContainer);

        String commentText = getComment()!=null ? env.expand(getComment()) : "";
        commentText = commentText.trim();


        CliScanArgs args = new CliScanArgs();
        args.setIsIncremental(isThisBuildIncremental);
        args.setIsPrivateScan(false);
        args.setPrjSettings(projectSettings);
        args.setSrcCodeSettings(sourceCodeSettings);
        args.setComment(commentText);

        return args;
    }

    private boolean isThisBuildIncremental(int buildNumber) {

        boolean askedForIncremental = isIncremental();
        if(!askedForIncremental){
            return false;
        }

        boolean askedForPeriodicFullScans = isFullScansScheduled();
        if(!askedForPeriodicFullScans){
            return true;
        }

        // if user entered invalid value for full scan cycle - all scans will be incremental
        if (fullScanCycle < DescriptorImpl.FULL_SCAN_CYCLE_MIN || fullScanCycle > DescriptorImpl.FULL_SCAN_CYCLE_MAX){
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
    private boolean isSkipScan(final Run<?, ?> build)
    {

        if (!isSkipSCMTriggers())
        {
            return false;
        }

        final List<Cause> causes = build.getCauses();
		final List<Cause> allowedCauses = new LinkedList<>();

        for(Cause c : causes)
        {
            if (!(c instanceof SCMTrigger.SCMTriggerCause))
            {
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
        return (DescriptorImpl)super.getDescriptor();
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

	    @Nullable private String serverUrl;
	    @Nullable private String username;
	    @Nullable private String password;
	    private boolean hideResults;
	    private boolean enableCertificateValidation;


	    private boolean forcingVulnerabilityThresholdEnabled;
	    private int highThresholdEnforcement;
	    private int mediumThresholdEnforcement;
	    private int lowThresholdEnforcement;
		private JobGlobalStatusOnError jobGlobalStatusOnError;
		private JobGlobalStatusOnError jobGlobalStatusOnThresholdViolation = JobGlobalStatusOnError.FAILURE;
        private boolean scanTimeOutEnabled;
        private int scanTimeoutDuration;
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
	        return Secret.fromString(password).getPlainText();
	    }

	    public void setPassword(@Nullable String password) {
	        this.password = Secret.fromString(password).getEncryptedValue();
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

	        if (!this.enableCertificateValidation && enableCertificateValidation)
	        {
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
	    public boolean isForcingVulnerabilityThresholdEnabled() {
	        return forcingVulnerabilityThresholdEnabled;
	    }

	    public void setForcingVulnerabilityThresholdEnabled(boolean forcingVulnerabilityThresholdEnabled) {
	        this.forcingVulnerabilityThresholdEnabled = forcingVulnerabilityThresholdEnabled;
	    }

		public int getHighThresholdEnforcement() {
	        return highThresholdEnforcement;
	    }

	    public void setHighThresholdEnforcement(int highThresholdEnforcement) {
	        this.highThresholdEnforcement = highThresholdEnforcement;
	    }

	    public int getMediumThresholdEnforcement() {
	        return mediumThresholdEnforcement;
	    }

	    public void setMediumThresholdEnforcement(int mediumThresholdEnforcement) {
	        this.mediumThresholdEnforcement = mediumThresholdEnforcement;
	    }

	    public int getLowThresholdEnforcement() {
	        return lowThresholdEnforcement;
	    }

	    public void setLowThresholdEnforcement(int lowThresholdEnforcement) {
	        this.lowThresholdEnforcement = lowThresholdEnforcement;
	    }

        public boolean getScanTimeOutEnabled() {
            return scanTimeOutEnabled;
        }

        public void setScanTimeOutEnabled(boolean scanTimeOutEnabled) {
            this.scanTimeOutEnabled = scanTimeOutEnabled;
        }

        public int getScanTimeoutDuration() {
            if (scanTimeoutDuration < 1) {
                scanTimeoutDuration  = 1;
            }

            return scanTimeoutDuration;
        }

        public void setScanTimeoutDuration(int scanTimeoutDuration) {
            if (scanTimeoutDuration >= 1) {
                this.scanTimeoutDuration = scanTimeoutDuration;
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
	    public String getCredentialsDescription()
	    {
	        if (getServerUrl()==null || getServerUrl().isEmpty() ||
	            getUsername()==null || getUsername().isEmpty())
	        {
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
		public FormValidation doTestConnection(@QueryParameter final String serverUrl, @QueryParameter final String password,
				@QueryParameter final String username, @QueryParameter final String timestamp) {
			// timestamp is not used in code, it is one of the arguments to invalidate Internet Explorer cache
	        CxWebService cxWebService = null;
	        try {
	        	cxWebService = new CxWebService(serverUrl);
	        } catch (Exception e) {
				logger.debug(e);
	            return FormValidation.error("Invalid system URL " + serverUrl + ": " + e.getMessage());
	        }

	        try {
	            cxWebService.login(username,password);
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
	            throws AbortException, MalformedURLException
	    {
	        String serverUrlToUse = !useOwnServerCredentials ? serverUrl : getServerUrl();
	        String usernameToUse  = !useOwnServerCredentials ? username  : getUsername();
	        String passwordToUse  = !useOwnServerCredentials ? password  : getPassword();
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
	            final CxWebService cxWebService = prepareLoggedInWebservice(useOwnServerCredentials,serverUrl,username,password);

	            List<ProjectDisplayData> projectsDisplayData = cxWebService.getProjectsDisplayData();
	            for(ProjectDisplayData pd : projectsDisplayData)
	            {
	                projectNames.add(pd.getProjectName());
	            }

	            logger.debug("Projects list: " + projectNames.size());
	            return projectNames;

	        } catch (Exception e) {
	            logger.debug("Projects list: empty");
	            return projectNames; // Return empty list of project names
	        }
	    }

	    /*
	     *  Note: This method is called concurrently by multiple threads, refrain from using mutable
	     *  shared state to avoid synchronization issues.
	     */

		public FormValidation doCheckProjectName(@QueryParameter final String projectName, @QueryParameter final boolean useOwnServerCredentials,
				@QueryParameter final String serverUrl, @QueryParameter final String username, @QueryParameter final String password,
				@QueryParameter final String groupId, @QueryParameter final String timestamp) {
            // timestamp is not used in code, it is one of the arguments to invalidate Internet Explorer cache

            try {
                final CxWebService cxWebService = prepareLoggedInWebservice(useOwnServerCredentials, serverUrl, username, password);

                if (msGuid.matcher(groupId).matches()) {
                    CxWSBasicRepsonse cxWSBasicRepsonse = cxWebService.validateProjectName(projectName, groupId);
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
				@QueryParameter final String username, @QueryParameter final String password, @QueryParameter final String timestamp)
	    {
	        // timestamp is not used in code, it is one of the arguments to invalidate Internet Explorer cache
	        ListBoxModel listBoxModel = new ListBoxModel();
	        try {
	            final CxWebService cxWebService = prepareLoggedInWebservice(useOwnServerCredentials,serverUrl,username,password);

	            final List<Preset> presets = cxWebService.getPresets();
	            for(Preset p : presets)
	            {
	                listBoxModel.add(new ListBoxModel.Option(p.getPresetName(),Long.toString(p.getID())));
	            }

	            logger.debug("Presets list: " + listBoxModel.size());
	            return listBoxModel;

	        } catch (Exception e) {
	            logger.debug("Presets list: empty");
	            String message = "Provide Checkmarx server credentials to see presets list";
	            listBoxModel.add(new ListBoxModel.Option(message,message));
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

		public FormValidation doCheckFullScanCycle(@QueryParameter final int value)
	    {
	        if(value >= FULL_SCAN_CYCLE_MIN && value <= FULL_SCAN_CYCLE_MAX){
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
	            final CxWebService cxWebService = prepareLoggedInWebservice(useOwnServerCredentials,serverUrl,username,password);

	            final List<ConfigurationSet> sourceEncodings = cxWebService.getSourceEncodings();
				for (ConfigurationSet cs : sourceEncodings) {
					listBoxModel.add(new ListBoxModel.Option(cs.getConfigSetName(), Long.toString(cs.getID())));
				}

	            logger.debug("Source encodings list: " + listBoxModel.size());
	        } catch (Exception e) {
	            logger.debug("Source encodings list: empty");
	            String message = "Provide Checkmarx server credentials to see source encodings list";
	            listBoxModel.add(new ListBoxModel.Option(message,message));
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
	            final CxWebService cxWebService = prepareLoggedInWebservice(useOwnServerCredentials,serverUrl,username,password);
	            final List<Group> groups = cxWebService.getAssociatedGroups();
	            for(Group group : groups) {
	                listBoxModel.add(new ListBoxModel.Option(group.getGroupName(),group.getID()));
	            }

	            logger.debug("Associated groups list: " + listBoxModel.size());
	            return listBoxModel;

	        } catch (Exception e) {
	            logger.debug("Associated groups: empty");
	            String message = "Provide Checkmarx server credentials to see teams list";
	            listBoxModel.add(new ListBoxModel.Option(message,message));
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

		public FormValidation doCheckHighThreshold(@QueryParameter final int value) {
			return checkNonNegativeValue(value);
		}

		/*
		 * Note: This method is called concurrently by multiple threads, refrain from using mutable shared state to
		 * avoid synchronization issues.
		 */

		public FormValidation doCheckMediumThreshold(@QueryParameter final int value) {
			return checkNonNegativeValue(value);
		}

		/*
		 * Note: This method is called concurrently by multiple threads, refrain from using mutable shared state to
		 * avoid synchronization issues.
		 */

		public FormValidation doCheckLowThreshold(@QueryParameter final int value) {
			return checkNonNegativeValue(value);
		}

		/*
		 * Note: This method is called concurrently by multiple threads, refrain from using mutable shared state to
		 * avoid synchronization issues.
		 */

		public FormValidation doCheckHighThresholdEnforcement(@QueryParameter final int value) {
			return checkNonNegativeValue(value);
		}

		/*
		 * Note: This method is called concurrently by multiple threads, refrain from using mutable shared state to
		 * avoid synchronization issues.
		 */

		public FormValidation doCheckMediumThresholdEnforcement(@QueryParameter final int value) {
			return checkNonNegativeValue(value);
		}

		/*
		 * Note: This method is called concurrently by multiple threads, refrain from using mutable shared state to
		 * avoid synchronization issues.
		 */

		public FormValidation doCheckLowThresholdEnforcement(@QueryParameter final int value) {
			return checkNonNegativeValue(value);
		}

	    /*
	     *  Note: This method is called concurrently by multiple threads, refrain from using mutable
	     *  shared state to avoid synchronization issues.
	     */
		private FormValidation checkNonNegativeValue(final int value) {
			if (value >= 0) {
				return FormValidation.ok();
			} else {
				return FormValidation.error("Number must be non-negative");
			}
		}

	    public String getDefaultProjectName()
	    {
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
	        return super.configure(req,formData);
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
