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
import hudson.util.ComboBoxModel;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import net.sf.json.JSONObject;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.*;
import org.jetbrains.annotations.NotNull;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.*;
import java.net.MalformedURLException;
import java.util.List;

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
    private String serverUrl;
    private String username;
    private String password;
    private String projectName;
    private String groupId;

    private String preset;
    private boolean presetSpecified;
    private String excludeFolders;
    private String filterPattern;

    private boolean incremental;
    private String sourceEncoding;
    private String comment;

    private boolean waitForResultsEnabled;

    private boolean vulnerabilityThresholdEnabled;
    private int highThreshold;
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
                         String serverUrl,
                         String username,
                         String password,
                         String projectName,
                         String groupId,
                         String preset,
                         boolean presetSpecified,
                         String excludeFolders,
                         String filterPattern,
                         boolean incremental,
                         String sourceEncoding,
                         String comment,
                         boolean waitForResultsEnabled,
                         boolean vulnerabilityThresholdEnabled,
                         int highThreshold,
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
        this.sourceEncoding = sourceEncoding;
        this.comment = comment;
        this.waitForResultsEnabled = waitForResultsEnabled;
        this.vulnerabilityThresholdEnabled = vulnerabilityThresholdEnabled;
        this.highThreshold = highThreshold;
        this.generatePdfReport =  generatePdfReport;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    // Configuration fields getters
    //////////////////////////////////////////////////////////////////////////////////////


    public boolean isUseOwnServerCredentials() {
        return useOwnServerCredentials;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getPreset() {
        return preset;
    }

    public boolean isPresetSpecified() {
        return presetSpecified;
    }

    public String getExcludeFolders() {
        return excludeFolders;
    }

    public String getFilterPattern() {
        return filterPattern;
    }

    public boolean isIncremental() {
        return incremental;
    }

    public String getSourceEncoding() {
        return sourceEncoding;
    }

    public String getComment() {
        return comment;
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

            String serverUrlToUse = isUseOwnServerCredentials() ? getServerUrl() : getDescriptor().getServerUrl();
            String usernameToUse  = isUseOwnServerCredentials() ? getUsername()  : getDescriptor().getUsername();
            String passwordToUse  = isUseOwnServerCredentials() ? getPassword()  : getDescriptor().getPassword();
            CxWebService cxWebService = new CxWebService(serverUrlToUse,instanceLoggerSuffix(build));
            cxWebService.login(usernameToUse,passwordToUse);


            instanceLogger.info("Checkmarx server login successful");

            CxWSResponseRunID cxWSResponseRunID = submitScan(build, cxWebService,listener);
            instanceLogger.info("\nScan job submitted successfully\n");


            if (!isWaitForResultsEnabled())
            {
                listener.finished(Result.SUCCESS);
                return true;
            }

            long scanId =  cxWebService.trackScanProgress(cxWSResponseRunID);

            File xmlReportFile = new File(checkmarxBuildDir,"ScanReport.xml");
            cxWebService.retrieveScanReport(scanId,xmlReportFile,CxWSReportType.XML);

            if (this.generatePdfReport)
            {
                File pdfReportFile = new File(checkmarxBuildDir,"ScanReport.pdf");
                cxWebService.retrieveScanReport(scanId, pdfReportFile, CxWSReportType.PDF);
            }


            // Parse scan report and present results in Jenkins

            CxScanResult cxScanResult = new CxScanResult(build,instanceLoggerSuffix(build));
            cxScanResult.readScanXMLReport(xmlReportFile);
            build.addAction(cxScanResult);

            instanceLogger.info("Number of high severity vulnerabilities: " +
                    cxScanResult.getHighCount() + " stability threshold: " + this.getHighThreshold());

            if (this.isVulnerabilityThresholdEnabled())
            {
                if (cxScanResult.getHighCount() >  this.getHighThreshold())
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
        projectSettings.setDescription(getComment()); // TODO: Move comment to other web service
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

        CliScanArgs args = new CliScanArgs();
        args.setIsIncremental(isIncremental());
        args.setIsPrivateScan(false);
        args.setPrjSettings(projectSettings);
        args.setSrcCodeSettings(sourceCodeSettings);

        return args;
    }


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
        private final static Logger logger = Logger.getLogger(DescriptorImpl.class);

        //////////////////////////////////////////////////////////////////////////////////////
        //  Persistent plugin global configuration parameters
        //////////////////////////////////////////////////////////////////////////////////////

        private String serverUrl;
        private String username;
        private String password;

        public String getServerUrl() {
            return serverUrl;
        }

        public void setServerUrl(String serverUrl) {
            this.serverUrl = serverUrl;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
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

        //////////////////////////////////////////////////////////////////////////////////////
        // Field value validators
        //////////////////////////////////////////////////////////////////////////////////////

        /*
         *  Note: This method is called concurrently by multiple threads, refrain from using mutable
         *  shared state to avoid synchronization issues.
         */
        public FormValidation doCheckServerUrl(final @QueryParameter String value) {
            try {
                new CxWebService(value);
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
        public FormValidation doCheckPassword(   final @QueryParameter String serverUrl,
                                                              final @QueryParameter String password,
                                                              final @QueryParameter String username) {
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
                                                                    final @QueryParameter String password)
        {


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
                                                 final @QueryParameter String groupId)
        {
            try {
                final CxWebService cxWebService = prepareLoggedInWebservice(useOwnServerCredentials,serverUrl,username,password);
                CxWSBasicRepsonse cxWSBasicRepsonse = cxWebService.validateProjectName(projectName,groupId);
                if (cxWSBasicRepsonse.isIsSuccesfull())
                {
                    return FormValidation.ok("Project Name Validated Successfully");
                } else {
                    if (cxWSBasicRepsonse.getErrorMessage().equalsIgnoreCase("Illegal project name"))
                    {
                        return FormValidation.error("Illegal project name");
                    } else {
                        logger.warn("Couldn't validate project name with Checkmarx sever:\n" + cxWSBasicRepsonse.getErrorMessage());
                        return FormValidation.warning("Can't reach server to validate project name");
                    }
                }
            } catch (Exception e)
            {
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
                                                              final @QueryParameter String password)
        {
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

        public ListBoxModel doFillSourceEncodingItems(   final @QueryParameter boolean useOwnServerCredentials,
                                                                      final @QueryParameter String serverUrl,
                                                                      final @QueryParameter String username,
                                                                      final @QueryParameter String password)
        {
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

        };


        // Provides a list of source encodings from checkmarx server for dynamic drop-down list in configuration page
        /*
         *  Note: This method is called concurrently by multiple threads, refrain from using mutable
         *  shared state to avoid synchronization issues.
         */

        public ListBoxModel doFillGroupIdItems(   final @QueryParameter boolean useOwnServerCredentials,
                                                         final @QueryParameter String serverUrl,
                                                         final @QueryParameter String username,
                                                         final @QueryParameter String password)
        {
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

        };

        /*
         *  Note: This method is called concurrently by multiple threads, refrain from using mutable
         *  shared state to avoid synchronization issues.
         */

        public FormValidation doCheckHighThreshold(final @QueryParameter int value)
        {
            if (value >= 0)
            {
                return FormValidation.ok();
            } else {
                return FormValidation.error("Number must be non-negative");
            }
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
