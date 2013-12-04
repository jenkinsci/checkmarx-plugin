package com.checkmarx.jenkins;

import com.checkmarx.components.zipper.ZipListener;
import com.checkmarx.components.zipper.Zipper;
import com.checkmarx.ws.CxCLIWebService.*;
import com.thoughtworks.xstream.annotations.XStreamOmitField;
import hudson.AbortException;
import hudson.Extension;
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

    private String preset;
    private boolean presetSpecified;
    private String excludeFolders;
    private String filterPattern;

    private boolean visibleToOthers;
    private boolean incremental;
    private String sourceEncoding;
    private String comment;

    private boolean waitForResultsEnabled;

    private boolean vulnerabilityThresholdEnabled;
    private int highThreshold;

    //////////////////////////////////////////////////////////////////////////////////////
    // Private variables
    //////////////////////////////////////////////////////////////////////////////////////
    static {
         BasicConfigurator.configure();  // Set the log4j system to log to console
    }
    private static Logger logger = Logger.getLogger(CxScanBuilder.class);

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
                         String preset,
                         boolean presetSpecified,
                         String excludeFolders,
                         String filterPattern,
                         boolean visibleToOthers,
                         boolean incremental,
                         String sourceEncoding,
                         String comment,
                         boolean waitForResultsEnabled,
                         boolean vulnerabilityThresholdEnabled,
                         int highThreshold)
    {
        this.useOwnServerCredentials = useOwnServerCredentials;
        this.serverUrl = serverUrl;
        this.username = username;
        this.password = password;
        this.projectName = projectName;
        this.preset = preset;
        this.presetSpecified = presetSpecified;
        this.excludeFolders = excludeFolders;
        this.filterPattern = filterPattern;
        this.visibleToOthers = visibleToOthers;
        this.incremental = incremental;
        this.sourceEncoding = sourceEncoding;
        this.comment = comment;
        this.waitForResultsEnabled = waitForResultsEnabled;
        this.vulnerabilityThresholdEnabled = vulnerabilityThresholdEnabled;
        this.highThreshold = highThreshold;
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

    public boolean isVisibleToOthers() {
        return visibleToOthers;
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

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,BuildListener listener) throws InterruptedException, IOException {

        File checkmarxBuildDir = new File(build.getRootDir(),"checkmarx");
        checkmarxBuildDir.mkdir();
        File reportFile = new File(checkmarxBuildDir,"ScanReport.xml");

        initLogger(checkmarxBuildDir,listener);

        listener.started(null);
        logger.info("Checkmarx Jenkins plugin version: " + CxConfig.version());

        String serverUrlToUse = isUseOwnServerCredentials() ? getServerUrl() : getDescriptor().getServerUrl();
        String usernameToUse  = isUseOwnServerCredentials() ? getUsername()  : getDescriptor().getUsername();
        String passwordToUse  = isUseOwnServerCredentials() ? getPassword()  : getDescriptor().getPassword();
        CxWebService cxWebService = new CxWebService(serverUrlToUse);
        cxWebService.login(usernameToUse,passwordToUse);


        logger.info("Checkmarx server login successful");

        CxWSResponseRunID cxWSResponseRunID = submitScan(build, listener, cxWebService);
        logger.info("\nScan job submitted successfully\n");

        if (!isWaitForResultsEnabled())
        {
            listener.finished(Result.SUCCESS);
            return true;
        }

        long scanId =  cxWebService.trackScanProgress(cxWSResponseRunID);

        cxWebService.retrieveScanReport(scanId,reportFile);

        // Parse scan report and present results in Jenkins

        CxScanResult cxScanResult = new CxScanResult(build);
        cxScanResult.readScanXMLReport(reportFile);
        build.addAction(cxScanResult);

        logger.info("Number of high severity vulnerabilities: " +
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

        closeLogger();
        listener.finished(Result.SUCCESS);
        return true;
    }

    private void initLogger(File checkmarxBuildDir, BuildListener listener)
    {
        WriterAppender writerAppender = new WriterAppender(new PatternLayout("%m%n"),listener.getLogger());
        writerAppender.setThreshold(Level.INFO);
        Logger.getLogger("com.checkmarx").addAppender(writerAppender);
        String logFileName = checkmarxBuildDir.getAbsolutePath() + File.separator + "checkmarx.log";

        try {
            fileAppender = new FileAppender(new PatternLayout("%C: [%d] %-5p: %m%n"),logFileName);
            fileAppender.setThreshold(Level.DEBUG);
            Logger.getLogger("com.checkmarx").addAppender(fileAppender);
        } catch (IOException e)
        {
            logger.warn("Could not open log file for writing: " + logFileName);
            logger.debug(e);
        }
    }

    private void closeLogger()
    {
        Logger.getLogger("com.checkmarx").removeAppender(fileAppender);
        fileAppender.close();
    }

    private CxWSResponseRunID submitScan(AbstractBuild<?, ?> build, final BuildListener listener, CxWebService cxWebService) throws AbortException, IOException
    {

        ZipListener zipListener = new ZipListener() {
            @Override
            public void updateProgress(String fileName, long size) {
                logger.info("Zipping (" + FileUtils.byteCountToDisplaySize(size) + "): " + fileName);
            }

        };

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        File baseDir = new File(build.getWorkspace().getRemote());

        String combinedFilterPattern = this.getFilterPattern() + "," + processExcludeFolders(this.getExcludeFolders());

        logger.info("Starting to zip the workspace");
        try {
            new Zipper().zip(baseDir,combinedFilterPattern,byteArrayOutputStream,CxConfig.maxZipSize(),zipListener);
        } catch (Zipper.MaxZipSizeReached e)
        {
            throw new AbortException("Checkmarx Scan Failed: Reached maximum upload size limit of " + FileUtils.byteCountToDisplaySize(CxConfig.maxZipSize()));
        } catch (Zipper.NoFilesToZip e)
        {
            throw new AbortException("Checkmarx Scan Failed: No files to scan");
        }


        return cxWebService.scan(createCliScanArgs(byteArrayOutputStream.toByteArray()));
    }

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
        logger.debug("Exclude folders converted to: " +result.toString());
        return result.toString();
    }

    private CliScanArgs createCliScanArgs(byte[] compressedSources)
    {

        ProjectSettings projectSettings = new ProjectSettings();
        projectSettings.setDescription(getComment());
        long presetLong = 0; // Default value to use in case of exception
        try {
            presetLong = Long.parseLong(getPreset());
        } catch (Exception e)
        {
            logger.error("Encountered illegal preset value: " + getPreset() + ". Using default preset.");
        }

        projectSettings.setPresetID(presetLong);
        projectSettings.setProjectName(getProjectName());

        long configuration = 0; // Default value to use in case of exception
        try {
            configuration = Long.parseLong(getSourceEncoding());
        } catch (Exception e)
        {
            logger.error("Encountered illegal source encoding (configuration) value: " + getSourceEncoding() + ". Using default configuration.");
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
        args.setIsPrivateScan(!isVisibleToOthers());
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
        private static Logger logger = Logger.getLogger(DescriptorImpl.class);

        @XStreamOmitField // The @XStreamOmitField annotation makes the xStream serialization
        // system ignore this field while saving class state to a file
        private CxWebService cxWebService;

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
        // Field value validators
        //////////////////////////////////////////////////////////////////////////////////////

        /* This method is synchronized to avoid multiple threads performing simultaneous login web service calls.
         * Simultaneous login calls result in all session id, except the most recently generated to be invalid.
         * Using an invalid session id, results in ReConnect error message coming from server. Call to this method
         * is performed concurrently with other doCheckXXX and doFillXXXItems methods.
         */

        public synchronized FormValidation doCheckServerUrl(@QueryParameter String value) {
            try {
                this.cxWebService = null;
                this.cxWebService = new CxWebService(value);
                return FormValidation.ok("Server Validated Successfully");
            } catch (Exception e)
            {
                return FormValidation.error(e.getMessage());
            }
        }

        /* This method is synchronized to avoid multiple threads performing simultaneous login web service calls.
         * Simultaneous login calls result in all session id, except the most recently generated to be invalid.
         * Using an invalid session id, results in ReConnect error message coming from server. Call to this method
         * is performed concurrently with other doCheckXXX and doFillXXXItems methods.
         */

        public synchronized FormValidation doCheckPassword(@QueryParameter String serverUrl,
                                              @QueryParameter String password,
                                              @QueryParameter String username) {


            if (this.cxWebService==null) {
                try {
                    this.cxWebService = new CxWebService(serverUrl);
                } catch (Exception e) {
                    return FormValidation.warning("Server URL not set");
                }
            }

            try {
                this.cxWebService.login(username,password);
                return FormValidation.ok("Login Successful");

            } catch (Exception e)
            {
                return FormValidation.error(e.getMessage());
            }
        }

        // Prepares a this.cxWebService object to be connected and logged in
        private void prepareLoggedInWebservice(boolean useOwnServerCredentials,
                                               String serverUrl,
                                               String username,
                                               String password)
                throws AbortException, MalformedURLException
        {
            String serverUrlToUse = !useOwnServerCredentials ? serverUrl : getServerUrl();
            String usernameToUse  = !useOwnServerCredentials ? username  : getUsername();
            String passwordToUse  = !useOwnServerCredentials ? password  : getPassword();
            logger.debug("prepareLoggedInWebservice: server: " + serverUrlToUse + " user: " + usernameToUse + " pass: " + passwordToUse);

            if (this.cxWebService == null) {
                this.cxWebService = new CxWebService(serverUrlToUse);
                logger.debug("prepareLoggedInWebservice: created cxWebService");
            }

            if (!this.cxWebService.isLoggedIn()) {
                this.cxWebService.login(usernameToUse, passwordToUse);
                logger.debug("prepareLoggedInWebservice: logged in");
            }
        }

        /* This method is synchronized to avoid multiple threads performing simultaneous login web service calls.
         * Simultaneous login calls result in all session id, except the most recently generated to be invalid.
         * Using an invalid session id, results in ReConnect error message coming from server. Call to this method
         * is performed concurrently with other doCheckXXX and doFillXXXItems methods.
         */

        public synchronized ComboBoxModel doFillProjectNameItems(@QueryParameter boolean useOwnServerCredentials,
                                                    @QueryParameter String serverUrl,
                                                    @QueryParameter String username,
                                                    @QueryParameter String password)
        {


            ComboBoxModel projectNames = new ComboBoxModel();

            try {
                prepareLoggedInWebservice(useOwnServerCredentials,serverUrl,username,password);

                List<ProjectDisplayData> projectsDisplayData = this.cxWebService.getProjectsDisplayData();
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

        public FormValidation doCheckProjectName(@QueryParameter String projectName)
        {

            if (projectName.length() > 200)
            {
                return FormValidation.error("Project name must be shorter than 200 characters");
            }
            // TODO: Add project validation using IsValidProjectName web service

            return FormValidation.ok();
        }


        // Provides a list of presets from checkmarx server for dynamic drop-down list in configuration page

        /* This method is synchronized to avoid multiple threads performing simultaneous login web service calls.
         * Simultaneous login calls result in all session id, except the most recently generated to be invalid.
         * Using an invalid session id, results in ReConnect error message coming from server. Call to this method
         * is performed concurrently with other doCheckXXX and doFillXXXItems methods.
         */

        public synchronized ListBoxModel doFillPresetItems(@QueryParameter boolean useOwnServerCredentials,
                                              @QueryParameter String serverUrl,
                                              @QueryParameter String username,
                                              @QueryParameter String password)
        {
            ListBoxModel listBoxModel = new ListBoxModel();
            try {
                prepareLoggedInWebservice(useOwnServerCredentials,serverUrl,username,password);

                List<Preset> presets = this.cxWebService.getPresets();
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

        /* This method is synchronized to avoid multiple threads performing simultaneous login web service calls.
         * Simultaneous login calls result in all session id, except the most recently generated to be invalid.
         * Using an invalid session id, results in ReConnect error message coming from server. Call to this method
         * is performed concurrently with other doCheckXXX and doFillXXXItems methods.
         */


        public synchronized ListBoxModel doFillSourceEncodingItems(@QueryParameter boolean useOwnServerCredentials,
                                              @QueryParameter String serverUrl,
                                              @QueryParameter String username,
                                              @QueryParameter String password)
        {
            ListBoxModel listBoxModel = new ListBoxModel();
            try {
                prepareLoggedInWebservice(useOwnServerCredentials,serverUrl,username,password);

                List<ConfigurationSet> sourceEncodings = this.cxWebService.getSourceEncodings();
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

        public FormValidation doCheckHighThreshold(@QueryParameter int value)
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
