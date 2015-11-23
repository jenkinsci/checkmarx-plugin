package com.checkmarx.jenkins;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.triggers.SCMTrigger;
import hudson.util.ComboBoxModel;
import hudson.util.FormValidation;
import hudson.util.Secret;
import hudson.util.ListBoxModel;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URLDecoder;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

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
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.checkmarx.components.zipper.Zipper;
import com.checkmarx.ws.CxJenkinsWebService.CliScanArgs;
import com.checkmarx.ws.CxJenkinsWebService.ConfigurationSet;
import com.checkmarx.ws.CxJenkinsWebService.CxWSBasicRepsonse;
import com.checkmarx.ws.CxJenkinsWebService.CxWSReportType;
import com.checkmarx.ws.CxJenkinsWebService.CxWSResponseRunID;
import com.checkmarx.ws.CxJenkinsWebService.Group;
import com.checkmarx.ws.CxJenkinsWebService.LocalCodeContainer;
import com.checkmarx.ws.CxJenkinsWebService.Preset;
import com.checkmarx.ws.CxJenkinsWebService.ProjectDisplayData;
import com.checkmarx.ws.CxJenkinsWebService.ProjectSettings;
import com.checkmarx.ws.CxJenkinsWebService.SourceCodeSettings;
import com.checkmarx.ws.CxJenkinsWebService.SourceLocationType;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

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

    @Nullable private String serverUrl;
    @Nullable private String username;
    @Nullable private String password;
    @Nullable private String projectName;
    @Nullable private String groupId;
    @Nullable private long projectId;

    @Nullable private String preset;
    private boolean presetSpecified;
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
    private int highThreshold;
    private int mediumThreshold;
    private int lowThreshold;
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
    @XStreamOmitField
    private transient Logger instanceLogger = LOGGER; // Instance logger redirects to static logger until
                                                  // it is initialized in perform method
    @XStreamOmitField
    private transient FileAppender fileAppender;

	private JobStatusOnError jobStatusOnError;

    //////////////////////////////////////////////////////////////////////////////////////
    // Constructors
    //////////////////////////////////////////////////////////////////////////////////////

    @DataBoundConstructor
    public CxScanBuilder(boolean useOwnServerCredentials,
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
                         boolean generatePdfReport) {
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

    @Override
    public boolean perform(final AbstractBuild<?, ?> build,
                           final Launcher launcher,
                           final BuildListener listener) throws InterruptedException, IOException {
    	final DescriptorImpl descriptor = (DescriptorImpl) getDescriptor();

        try {
            File checkmarxBuildDir = new File(build.getRootDir(), "checkmarx");
            checkmarxBuildDir.mkdir();

            initLogger(checkmarxBuildDir, listener, instanceLoggerSuffix(build));

            listener.started(null);
            instanceLogger.info("Checkmarx Jenkins plugin version: " + CxConfig.version());

            if (isSkipScan(build)) {
                instanceLogger.info("Checkmarx scan skipped since the build was triggered by SCM. " +
                        "Visit plugin configuration page to disable this skip.");
                listener.finished(Result.SUCCESS);
                return true;
            }

            final String serverUrlToUse = isUseOwnServerCredentials() ? getServerUrl() : descriptor.getServerUrl();
            final String usernameToUse = isUseOwnServerCredentials() ? getUsername() : descriptor.getUsername();
            final String passwordToUse = isUseOwnServerCredentials() ? getPassword() : descriptor.getPassword();

            String serverUrlToUseNotNull = serverUrlToUse != null ? serverUrlToUse : "";
            CxWebService cxWebService = new CxWebService(serverUrlToUseNotNull, instanceLoggerSuffix(build));
            cxWebService.login(usernameToUse, passwordToUse);


            instanceLogger.info("Checkmarx server login successful");

            CxWSResponseRunID cxWSResponseRunID = submitScan(build, cxWebService, listener);
            instanceLogger.info("\nScan job submitted successfully\n");



            if (!isWaitForResultsEnabled() && !descriptor.isForcingVulnerabilityThresholdEnabled()) {
                listener.finished(Result.SUCCESS);
                return true;
            }

            long scanId = cxWebService.trackScanProgress(cxWSResponseRunID, usernameToUse, passwordToUse, descriptor.getScanTimeOutEnabled(), descriptor.getScanTimeoutDuration());

            if (scanId == 0) {
                build.setResult(Result.UNSTABLE);
                listener.finished(Result.UNSTABLE);
                return true;
            }

            File xmlReportFile = new File(checkmarxBuildDir, "ScanReport.xml");
            cxWebService.retrieveScanReport(scanId, xmlReportFile, CxWSReportType.XML);

            if (this.generatePdfReport) {
                File pdfReportFile = new File(checkmarxBuildDir, CxScanResult.PDF_REPORT_NAME);
                cxWebService.retrieveScanReport(scanId, pdfReportFile, CxWSReportType.PDF);
            }


            // Parse scan report and present results in Jenkins

            CxScanResult cxScanResult = new CxScanResult(build, instanceLoggerSuffix(build), serverUrlToUse);
            cxScanResult.readScanXMLReport(xmlReportFile);
            build.addAction(cxScanResult);

			if ((descriptor.isForcingVulnerabilityThresholdEnabled() || isVulnerabilityThresholdEnabled()) && isThresholdCrossed(cxScanResult)) {
                    build.setResult(Result.UNSTABLE); // Marks the build result as UNSTABLE
                    listener.finished(Result.UNSTABLE);
                    return true;
            }

            listener.finished(Result.SUCCESS);
            return true;
		} catch (IOException e) {
			instanceLogger.error(e.getMessage(), e);
			if (useUnstableOnError(descriptor)) {
				build.setResult(Result.UNSTABLE);
				listener.finished(Result.UNSTABLE);
				return true;
			} else {
				throw e;
			}
        } finally {
            closeLogger();
        }
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

    private boolean isThresholdCrossed(@NotNull final CxScanResult cxScanResult)
    {
        @Nullable
        final DescriptorImpl descriptor = getDescriptor();
		boolean highThresholdCrossed;
		boolean mediumThresholdCrossed;
		boolean lowThresholdCrossed;

        if (descriptor!=null && descriptor.isForcingVulnerabilityThresholdEnabled())
        {
            instanceLogger.info("Number of high severity vulnerabilities: " +
                    cxScanResult.getHighCount() + " stability threshold: " + descriptor.getHighThresholdEnforcement());

            instanceLogger.info("Number of medium severity vulnerabilities: " +
                    cxScanResult.getMediumCount() + " stability threshold: " + descriptor.getMediumThresholdEnforcement());

            instanceLogger.info("Number of low severity vulnerabilities: " +
                    cxScanResult.getLowCount() + " stability threshold: " + descriptor.getLowThresholdEnforcement());

			highThresholdCrossed = cxScanResult.getHighCount() > descriptor.getHighThresholdEnforcement();
			mediumThresholdCrossed = cxScanResult.getMediumCount() > descriptor.getMediumThresholdEnforcement();
			lowThresholdCrossed = cxScanResult.getLowCount() > descriptor.getLowThresholdEnforcement();

		} else {
            instanceLogger.info("Number of high severity vulnerabilities: " +
                    cxScanResult.getHighCount() + " stability threshold: " + this.getHighThreshold());

            instanceLogger.info("Number of medium severity vulnerabilities: " +
                    cxScanResult.getMediumCount() + " stability threshold: " + this.getMediumThreshold());

            instanceLogger.info("Number of low severity vulnerabilities: " +
                    cxScanResult.getLowCount() + " stability threshold: " + this.getLowThreshold());

			highThresholdCrossed = cxScanResult.getHighCount() > getHighThreshold();
			mediumThresholdCrossed = cxScanResult.getMediumCount() > getMediumThreshold();
			lowThresholdCrossed = cxScanResult.getLowCount() > getLowThreshold();

		}
        return highThresholdCrossed || mediumThresholdCrossed || lowThresholdCrossed;
    }


    private String instanceLoggerSuffix(final AbstractBuild<?, ?> build)
    {
        return build.getProject().getDisplayName() + "-" + build.getDisplayName();
    }

    private void initLogger(final File checkmarxBuildDir, final BuildListener listener, final String loggerSuffix)
    {
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
        } catch (IOException e)
        {
            LOGGER.warn("Could not open log file for writing: " + logFileName);
            LOGGER.debug(e);
        }
    }

    private void closeLogger() {
        instanceLogger.removeAppender(fileAppender);
        fileAppender.close();
        instanceLogger = LOGGER; // Redirect all logs back to static logger
    }

    private CxWSResponseRunID submitScan(final AbstractBuild<?, ?> build, final CxWebService cxWebService, final BuildListener listener) throws IOException
    {
        isThisBuildIncremental = isThisBuildIncremental(build.getNumber());

        if(isThisBuildIncremental){
            instanceLogger.info("\nScan job started in incremental scan mode\n");
        }
        else{
            instanceLogger.info("\nScan job started in full scan mode\n");
        }

        instanceLogger.info("Started zipping the workspace");

        try {
            // hudson.FilePath will work in distributed Jenkins installation
            FilePath baseDir = build.getWorkspace();

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
            final CxWSBasicRepsonse validateProjectRespnse = cxWebService.validateProjectName(projectName, groupId);						
            CxWSResponseRunID cxWSResponseRunID = null;			
            if (validateProjectRespnse.isIsSuccesfull()){
                cxWSResponseRunID = cxWebService.CreateAndRunProject(cliScanArgs.getPrjSettings(), cliScanArgs.getSrcCodeSettings().getPackagedCode(), true, true, tempFile);
            } else {
                if (projectId == 0) {
                    projectId = cxWebService.getProjectId(cliScanArgs.getPrjSettings());
                }

                cliScanArgs.getPrjSettings().setProjectID(projectId);

                if (incremental) {
                    cxWSResponseRunID = cxWebService.RunIncrementalScan(cliScanArgs.getPrjSettings(), cliScanArgs.getSrcCodeSettings().getPackagedCode(), true, true, tempFile);
                } else {
                    cxWSResponseRunID = cxWebService.RunScanAndAddToProject(cliScanArgs.getPrjSettings(), cliScanArgs.getSrcCodeSettings().getPackagedCode(), true, true, tempFile);
                }
            }

            tempFile.delete();
            instanceLogger.info("Temporary file deleted");

            return cxWSResponseRunID;
        }
        catch (Zipper.MaxZipSizeReached e)
        {
            throw new AbortException("Checkmarx Scan Failed: Reached maximum upload size limit of " + FileUtils.byteCountToDisplaySize(CxConfig.maxZipSize()));
        }
        catch (Zipper.NoFilesToZip e)
        {
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
    private boolean isSkipScan(final AbstractBuild<?, ?> build)
    {

        if (!isSkipSCMTriggers())
        {
            return false;
        }

        final List<Cause> causes = build.getCauses();
        final List<Cause> allowedCauses = new LinkedList<Cause>();

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


    /*public String getIconPath() {
        PluginWrapper wrapper = Hudson.getInstance().getPluginManager().getPlugin([YOUR-PLUGIN-MAIN-CLASS].class);
        return Hudson.getInstance().getRootUrl() + "plugin/"+ wrapper.getShortName()+"/";
    }*/


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
        private boolean scanTimeOutEnabled;
        private int scanTimeoutDuration;
        private final Pattern msGuid = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

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
            return scanTimeoutDuration;
        }

        public void setScanTimeoutDuration(int scanTimeoutDuration) {
            this.scanTimeoutDuration = scanTimeoutDuration;
        }

	    @Override
		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
	        return true;
	    }

	    public DescriptorImpl() {
	        load();
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
	    public FormValidation doTestConnection(final @QueryParameter String serverUrl,
	                                          final @QueryParameter String password,
	                                          final @QueryParameter String username,
	                                          final @QueryParameter String timestamp) {
	        // timestamp is not used in code, it is one of the arguments to invalidate Internet Explorer cache
	        CxWebService cxWebService = null;
	        try {
	            cxWebService = new CxWebService(serverUrl);
	        } catch (Exception e) {
	            return FormValidation.error("Invalid system URL");
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
	    public  ComboBoxModel doFillProjectNameItems(   final @QueryParameter boolean useOwnServerCredentials,
	                                                                final @QueryParameter String serverUrl,
	                                                                final @QueryParameter String username,
	                                                                final @QueryParameter String password,
	                                                                final @QueryParameter String timestamp)
	    {
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

	    public FormValidation doCheckProjectName(final @QueryParameter String projectName,
	                                             final @QueryParameter boolean useOwnServerCredentials,
	                                             final @QueryParameter String serverUrl,
	                                             final @QueryParameter String username,
	                                             final @QueryParameter String password,
	                                             final @QueryParameter String groupId,
	                                             final @QueryParameter String timestamp) {
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


	    /** Provides a list of presets from Checkmarx server for dynamic drop-down list in configuration page
	     *
	     * @param useOwnServerCredentials
	     * @param serverUrl
	     * @param username
	     * @param password
	     * @param timestamp
	     * @return
	     */
	    /*
	     *  Note: This method is called concurrently by multiple threads, refrain from using mutable
	     *  shared state to avoid synchronization issues.
	     */

	    public ListBoxModel doFillPresetItems(   final @QueryParameter boolean useOwnServerCredentials,
	                                                          final @QueryParameter String serverUrl,
	                                                          final @QueryParameter String username,
	                                                          final @QueryParameter String password,
	                                                          final @QueryParameter String timestamp)
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

	    /** Provides a list of source encodings from Checkmarx server for dynamic drop-down list in configuration page
	     *
	     * @param value
	     * @return
	     */
	    /*
	     *  Note: This method is called concurrently by multiple threads, refrain from using mutable
	     *  shared state to avoid synchronization issues.
	     */

	    public FormValidation doCheckFullScanCycle( final @QueryParameter int value)
	    {
	        if(value >= FULL_SCAN_CYCLE_MIN && value <= FULL_SCAN_CYCLE_MAX){
	            return FormValidation.ok();
	        }
	        else{
	            return FormValidation.error("Number must be in the range " + FULL_SCAN_CYCLE_MIN + "-" + FULL_SCAN_CYCLE_MAX);
	        }
	    }

	    public ListBoxModel doFillSourceEncodingItems(   final @QueryParameter boolean useOwnServerCredentials,
	                                                                  final @QueryParameter String serverUrl,
	                                                                  final @QueryParameter String username,
	                                                                  final @QueryParameter String password,
	                                                                  final @QueryParameter String timestamp) {
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

	    public ListBoxModel doFillGroupIdItems(   final @QueryParameter boolean useOwnServerCredentials,
	                                                     final @QueryParameter String serverUrl,
	                                                     final @QueryParameter String username,
	                                                     final @QueryParameter String password,
	                                                     final @QueryParameter String timestamp)
	    {
	        // timestamp is not used in code, it is one of the arguments to invalidate Internet Explorer cache
	        ListBoxModel listBoxModel = new ListBoxModel();
	        try {
	            final CxWebService cxWebService = prepareLoggedInWebservice(useOwnServerCredentials,serverUrl,username,password);
	            final List<Group> groups = cxWebService.getAssociatedGroups();
	            for(Group group : groups)
	            {
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

	    /*
	     *  Note: This method is called concurrently by multiple threads, refrain from using mutable
	     *  shared state to avoid synchronization issues.
	     */

	    public FormValidation doCheckHighThreshold(final @QueryParameter int value)
	    {
	        return checkNonNegativeValue(value);
	    }

	    /*
	     *  Note: This method is called concurrently by multiple threads, refrain from using mutable
	     *  shared state to avoid synchronization issues.
	     */

	    public FormValidation doCheckMediumThreshold(final @QueryParameter int value)
	    {
	        return checkNonNegativeValue(value);
	    }

	    /*
	     *  Note: This method is called concurrently by multiple threads, refrain from using mutable
	     *  shared state to avoid synchronization issues.
	     */

	    public FormValidation doCheckLowThreshold(final @QueryParameter int value)
	    {
	        return checkNonNegativeValue(value);
	    }

	    /*
	     *  Note: This method is called concurrently by multiple threads, refrain from using mutable
	     *  shared state to avoid synchronization issues.
	     */

	    public FormValidation doCheckHighThresholdEnforcement(final @QueryParameter int value)
	    {
	        return checkNonNegativeValue(value);
	    }

	    /*
	     *  Note: This method is called concurrently by multiple threads, refrain from using mutable
	     *  shared state to avoid synchronization issues.
	     */

	    public FormValidation doCheckMediumThresholdEnforcement(final @QueryParameter int value)
	    {
	        return checkNonNegativeValue(value);
	    }

	    /*
	     *  Note: This method is called concurrently by multiple threads, refrain from using mutable
	     *  shared state to avoid synchronization issues.
	     */

	    public FormValidation doCheckLowThresholdEnforcement(final @QueryParameter int value)
	    {
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
	        if (urlComponents.length > 0)
	        {
	            final String jobName = urlComponents[urlComponents.length-1];
	            final String cleanJobName = jobName.replaceAll("[\\s\\\\/]", "");
	            return cleanJobName;
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

	}

}
