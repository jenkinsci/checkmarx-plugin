package com.checkmarx.jenkins;

import com.checkmarx.components.zipper.Zipper;
import com.checkmarx.ws.CxJenkinsWebService.*;
import com.thoughtworks.xstream.annotations.XStreamOmitField;
import hudson.AbortException;
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
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.cxf.configuration.jsse.SSLUtils;
import org.apache.log4j.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URLDecoder;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

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
    private final static Logger staticLogger = Logger.getLogger(CxScanBuilder.class);
    @XStreamOmitField
    private Logger instanceLogger = staticLogger; // Instance logger redirects to static logger until
                                                  // it is initialized in perform method
    @XStreamOmitField
    private FileAppender fileAppender;

    //////////////////////////////////////////////////////////////////////////////////////
    // Constructors
    //////////////////////////////////////////////////////////////////////////////////////

    @DataBoundConstructor
    public CxScanBuilder(boolean useOwnServerCredentials,
                         @Nullable String serverUrl,
                         @Nullable String username,
                         @Nullable String password,
                         @Nullable String projectName,
                         @Nullable String groupId,
                         @Nullable String preset,
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
                         boolean generatePdfReport)
    {
        this.useOwnServerCredentials = useOwnServerCredentials;
        this.serverUrl = serverUrl;
        this.username = username;
        this.password = password;
        this.projectName = projectName;
        this.groupId = groupId;
        this.preset = preset;
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
        return password;
    }

    @Nullable
    public String getProjectName() {
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

        try {
            File checkmarxBuildDir = new File(build.getRootDir(),"checkmarx");
            checkmarxBuildDir.mkdir();

            initLogger(checkmarxBuildDir, listener, instanceLoggerSuffix(build));

            listener.started(null);
            instanceLogger.info("Checkmarx Jenkins plugin version: " + CxConfig.version());

            if (isSkipScan(build))
            {
                instanceLogger.info("Checkmarx scan skipped since the build was triggered by SCM. "+
                        "Visit plugin configuration page to disable this skip.");
                listener.finished(Result.SUCCESS);
                return true;
            }

            final String serverUrlToUse = isUseOwnServerCredentials() ? getServerUrl() : getDescriptor().getServerUrl();
            final String usernameToUse  = isUseOwnServerCredentials() ? getUsername()  : getDescriptor().getUsername();
            final String passwordToUse  = isUseOwnServerCredentials() ? getPassword()  : getDescriptor().getPassword();

            String serverUrlToUseNotNull = serverUrlToUse != null ? serverUrlToUse : "";
            CxWebService cxWebService = new CxWebService(serverUrlToUseNotNull,instanceLoggerSuffix(build));
            cxWebService.login(usernameToUse,passwordToUse);


            instanceLogger.info("Checkmarx server login successful");

            CxWSResponseRunID cxWSResponseRunID = submitScan(build, cxWebService,listener);
            instanceLogger.info("\nScan job submitted successfully\n");

            @Nullable
            final CxScanBuilder.DescriptorImpl descriptor = (CxScanBuilder.DescriptorImpl) Jenkins.getInstance().getDescriptor(CxScanBuilder.class);

            if (!isWaitForResultsEnabled() && !descriptor.isForcingVulnerabilityThresholdEnabled())
            {
                listener.finished(Result.SUCCESS);
                return true;
            }

            long scanId =  cxWebService.trackScanProgress(cxWSResponseRunID,usernameToUse,passwordToUse);

            File xmlReportFile = new File(checkmarxBuildDir,"ScanReport.xml");
            cxWebService.retrieveScanReport(scanId,xmlReportFile,CxWSReportType.XML);

            if (this.generatePdfReport)
            {
                File pdfReportFile = new File(checkmarxBuildDir, CxScanResult.PDF_REPORT_NAME);
                cxWebService.retrieveScanReport(scanId, pdfReportFile, CxWSReportType.PDF);
            }


            // Parse scan report and present results in Jenkins

            CxScanResult cxScanResult = new CxScanResult(build,instanceLoggerSuffix(build),serverUrlToUse);
            cxScanResult.readScanXMLReport(xmlReportFile);
            build.addAction(cxScanResult);

            if (descriptor.isForcingVulnerabilityThresholdEnabled() || this.isVulnerabilityThresholdEnabled())
            {
                if (isThresholdCrossed(cxScanResult))
                {
                    build.setResult(Result.UNSTABLE);    // Marks the build result as UNSTABLE
                    listener.finished(Result.UNSTABLE);
                    return true;
                }
            }

            listener.finished(Result.SUCCESS);
            return true;
        } catch (Error e)
        {
            instanceLogger.error(e.getMessage(),e);
            throw e;
        } catch (AbortException e)
        {
            instanceLogger.error(e.getMessage(),e);
            throw e;
        } catch (IOException e)
        {
            instanceLogger.error(e.getMessage(),e);
            throw e;
        } finally {
            closeLogger();
        }
    }

    private boolean isThresholdCrossed(@NotNull final CxScanResult cxScanResult)
    {
        @Nullable
        final CxScanBuilder.DescriptorImpl descriptor = (CxScanBuilder.DescriptorImpl) Jenkins.getInstance().getDescriptor(CxScanBuilder.class);
        boolean highThresholdCrossed = false;
        boolean mediumThresholdCrossed = false;
        boolean lowThresholdCrossed = false;

        if (descriptor!=null && descriptor.isForcingVulnerabilityThresholdEnabled())
        {
            instanceLogger.info("Number of high severity vulnerabilities: " +
                    cxScanResult.getHighCount() + " stability threshold: " + descriptor.getHighThresholdEnforcement());

            instanceLogger.info("Number of medium severity vulnerabilities: " +
                    cxScanResult.getMediumCount() + " stability threshold: " + descriptor.getMediumThresholdEnforcement());

            instanceLogger.info("Number of low severity vulnerabilities: " +
                    cxScanResult.getLowCount() + " stability threshold: " + descriptor.getLowThresholdEnforcement());

            highThresholdCrossed =   (cxScanResult.getHighCount()   > descriptor.getHighThresholdEnforcement()   && descriptor.getHighThresholdEnforcement()   > 0);
            mediumThresholdCrossed = (cxScanResult.getMediumCount() > descriptor.getMediumThresholdEnforcement() && descriptor.getMediumThresholdEnforcement() > 0);
            lowThresholdCrossed =    (cxScanResult.getLowCount()    > descriptor.getLowThresholdEnforcement()    && descriptor.getLowThresholdEnforcement()    > 0);
        } else {
            instanceLogger.info("Number of high severity vulnerabilities: " +
                    cxScanResult.getHighCount() + " stability threshold: " + this.getHighThreshold());

            instanceLogger.info("Number of medium severity vulnerabilities: " +
                    cxScanResult.getMediumCount() + " stability threshold: " + this.getMediumThreshold());

            instanceLogger.info("Number of low severity vulnerabilities: " +
                    cxScanResult.getLowCount() + " stability threshold: " + this.getLowThreshold());

            highThresholdCrossed =   (cxScanResult.getHighCount()   > this.getHighThreshold()   && this.getHighThreshold()   > 0);
            mediumThresholdCrossed = (cxScanResult.getMediumCount() > this.getMediumThreshold() && this.getMediumThreshold() > 0);
            lowThresholdCrossed =    (cxScanResult.getLowCount()    > this.getLowThreshold()    && this.getLowThreshold()    > 0);
        }
        return highThresholdCrossed || mediumThresholdCrossed || lowThresholdCrossed;
    }


    private String instanceLoggerSuffix(final AbstractBuild<?, ?> build)
    {
        return build.getProject().getDisplayName() + "-" + build.getDisplayName();
    }

    private void initLogger(final File checkmarxBuildDir, final BuildListener listener, final String loggerSuffix)
    {
        instanceLogger = CxLogUtils.loggerWithSuffix(getClass(),loggerSuffix);
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
            staticLogger.warn("Could not open log file for writing: " + logFileName);
            staticLogger.debug(e);
        }
    }

    private void closeLogger()
    {
        instanceLogger.removeAppender(fileAppender);
        fileAppender.close();
        instanceLogger = staticLogger; // Redirect all logs back to static logger
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



            String combinedFilterPattern = this.getFilterPattern() + "," + processExcludeFolders(this.getExcludeFolders());
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
            final CliScanArgs cliScanArgs = createCliScanArgs(new byte[]{});
            final CxWSResponseRunID cxWSResponseRunID = cxWebService.scan(cliScanArgs, tempFile);
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

    private CliScanArgs createCliScanArgs(byte[] compressedSources)
    {

        ProjectSettings projectSettings = new ProjectSettings();

        long presetLong = 0; // Default value to use in case of exception
        try {
            presetLong = Long.parseLong(getPreset());
        } catch (Exception e)
        {
            instanceLogger.error("Encountered illegal preset value: " + getPreset() + ". Using default preset.");
        }

        projectSettings.setPresetID(presetLong);
        projectSettings.setProjectName(getProjectName());
        projectSettings.setAssociatedGroupID(getGroupId());

        long configuration = 0; // Default value to use in case of exception
        try {
            configuration = Long.parseLong(getSourceEncoding());
        } catch (Exception e)
        {
            instanceLogger.error("Encountered illegal source encoding (configuration) value: " + getSourceEncoding() + ". Using default configuration.");
        }
        projectSettings.setScanConfigurationID(configuration);

        LocalCodeContainer localCodeContainer = new LocalCodeContainer();
        localCodeContainer.setFileName("src.zip");
        localCodeContainer.setZippedFile(compressedSources);

        SourceCodeSettings sourceCodeSettings = new SourceCodeSettings();
        sourceCodeSettings.setSourceOrigin(SourceLocationType.LOCAL);
        sourceCodeSettings.setPackagedCode(localCodeContainer);

        String commentText = getComment()!=null ? getComment() : "";
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
        boolean shouldBeFullScan = (buildNumber % (fullScanCycle+1) == 1);

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

        public final static String DEFAULT_FILTER_PATTERNS = CxConfig.defaultFilterPattern();
        public final static int FULL_SCAN_CYCLE_MIN = 1;
        public final static int FULL_SCAN_CYCLE_MAX = 99;

        private final static Logger logger = Logger.getLogger(DescriptorImpl.class);

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

        public void setPassword(@Nullable String password) {
            this.password = password;
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
        public String getCurrentTime()
        {
            return String.valueOf(System.currentTimeMillis());
        }

        //////////////////////////////////////////////////////////////////////////////////////
        // Field value validators
        //////////////////////////////////////////////////////////////////////////////////////

        /*
         *  Note: This method is called concurrently by multiple threads, refrain from using mutable
         *  shared state to avoid synchronization issues.
         */
        public FormValidation doCheckServerUrl(final @QueryParameter String serverUrl,
                                               final @QueryParameter String timestamp) {
            // timestamp is not used in code, it is one of the arguments to invalidate Internet Explorer cache
            try {
                new CxWebService(serverUrl);
                return FormValidation.ok("Server Validated Successfully");
            } catch (Exception e)
            {
                return FormValidation.error(e.getMessage());
            }
        }

        /*
         *  Note: This method is called concurrently by multiple threads, refrain from using mutable
         *  shared state to avoid synchronization issues.
         */
        public FormValidation doCheckPassword(final @QueryParameter String serverUrl,
                                              final @QueryParameter String password,
                                              final @QueryParameter String username,
                                              final @QueryParameter String timestamp) {
            // timestamp is not used in code, it is one of the arguments to invalidate Internet Explorer cache
            CxWebService cxWebService = null;
            try {
                cxWebService = new CxWebService(serverUrl);
            } catch (Exception e) {
                return FormValidation.warning("Server URL not set");
            }

            try {
                cxWebService.login(username,password);
                return FormValidation.ok("Login Successful");

            } catch (Exception e)
            {
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
            logger.debug("prepareLoggedInWebservice: server: " + serverUrlToUse + " user: " + usernameToUse + " pass: " + passwordToUse);

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
                                                 final @QueryParameter String timestamp)
        {
            // timestamp is not used in code, it is one of the arguments to invalidate Internet Explorer cache
            try {
                final CxWebService cxWebService = prepareLoggedInWebservice(useOwnServerCredentials,serverUrl,username,password);
                CxWSBasicRepsonse cxWSBasicRepsonse = cxWebService.validateProjectName(projectName,groupId);
                if (cxWSBasicRepsonse.isIsSuccesfull())
                {
                    return FormValidation.ok("Project Name Validated Successfully");
                }
                else {
                    if (cxWSBasicRepsonse.getErrorMessage().startsWith("project name validation failed: duplicate name, project name") ||
                        cxWSBasicRepsonse.getErrorMessage().equalsIgnoreCase("Project name already exists"))
                    {
                        return FormValidation.ok("Scan will be added to existing project");
                    }
                    else if (cxWSBasicRepsonse.getErrorMessage().equalsIgnoreCase("project name validation failed: unauthorized user") ||
                             cxWSBasicRepsonse.getErrorMessage().equalsIgnoreCase("Unauthorized user"))
                    {
                        return FormValidation.error("The user is not authorized to create/run Checkmarx projects");
                    }
                    else if (cxWSBasicRepsonse.getErrorMessage().startsWith("Exception occurred at IsValidProjectCreationRequest:"))
                    {
                        logger.warn("Couldn't validate project name with Checkmarx sever:\n" + cxWSBasicRepsonse.getErrorMessage());
                        return FormValidation.warning(cxWSBasicRepsonse.getErrorMessage());
                    }
                    else {
                        return FormValidation.error(cxWSBasicRepsonse.getErrorMessage());
                    }
                }
            } catch (Exception e)
            {
                logger.warn("Couldn't validate project name with Checkmarx sever:\n" + e.getLocalizedMessage());
                return FormValidation.warning("Can't reach server to validate project name");
            }

        }


        // Provides a list of presets from checkmarx server for dynamic drop-down list in configuration page
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

        // Provides a list of source encodings from checkmarx server for dynamic drop-down list in configuration page
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
                                                                      final @QueryParameter String timestamp)
        {
            // timestamp is not used in code, it is one of the arguments to invalidate Internet Explorer cache
            ListBoxModel listBoxModel = new ListBoxModel();
            try {
                final CxWebService cxWebService = prepareLoggedInWebservice(useOwnServerCredentials,serverUrl,username,password);

                final List<ConfigurationSet> sourceEncodings = cxWebService.getSourceEncodings();
                for(ConfigurationSet cs : sourceEncodings)
                {
                    listBoxModel.add(new ListBoxModel.Option(cs.getConfigSetName(),Long.toString(cs.getID())));
                }

                logger.debug("Source encodings list: " + listBoxModel.size());
                return listBoxModel;

            } catch (Exception e) {
                logger.debug("Source encodings list: empty");
                String message = "Provide Checkmarx server credentials to see source encodings list";
                listBoxModel.add(new ListBoxModel.Option(message,message));
                return listBoxModel; // Return empty list of project names
            }

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
        private FormValidation checkNonNegativeValue(final int value)
        {
            if (value >= 0)
            {
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
                final String cleanJobName = jobName.replaceAll("[^\\w\\s_]", "");
                return cleanJobName;
            }
            // This is a fallback if the above code fails
            return "";
        }

        /**
         * This human readable name is used in the configuration screen.
         */
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



    }
}
