package com.checkmarx.jenkins;

import com.checkmarx.components.zipper.ZipListener;
import com.checkmarx.components.zipper.Zipper;
import com.checkmarx.ws.CxCLIWebService.*;
import hudson.AbortException;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.*;

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

    private static Logger logger = Logger.getLogger(CxScanBuilder.class);

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

    public void setUseOwnServerCredentials(boolean useOwnServerCredentials) {
        this.useOwnServerCredentials = useOwnServerCredentials;
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

        listener.finished(Result.SUCCESS);
        return true;
    }

    // return can be empty but never null
    @Override
    public Action getProjectAction(AbstractProject<?, ?> project) {
        return new CxProjectResult(project);
    }

    private void initLogger(File checkmarxBuildDir, BuildListener listener)
    {
        WriterAppender writerAppender = new WriterAppender(new PatternLayout("%m%n"),listener.getLogger());
        writerAppender.setThreshold(Level.INFO);
        Logger.getLogger("com.checkmarx").addAppender(writerAppender);
        String logFileName = checkmarxBuildDir.getAbsolutePath() + File.separator + "checkmarx.log";

        try {
            FileAppender fileAppender = new FileAppender(new PatternLayout("%C: [%d] %-5p: %m%n"),logFileName);
            fileAppender.setThreshold(Level.DEBUG);
            Logger.getLogger("com.checkmarx").addAppender(fileAppender);
        } catch (IOException e)
        {
            logger.warn("Could not open log file for writing: " + logFileName);
            logger.debug(e);
        }
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

        logger.info("Starting to zip the workspace");
        try {
            new Zipper().zip(baseDir,this.getFilterPattern(),byteArrayOutputStream,CxConfig.maxZipSize(),zipListener);
        } catch (Zipper.MaxZipSizeReached e)
        {
            throw new AbortException("Checkmarx Scan Failed: Reached maximum upload size limit of " + FileUtils.byteCountToDisplaySize(CxConfig.maxZipSize()));
        } catch (Zipper.NoFilesToZip e)
        {
            throw new AbortException("Checkmarx Scan Failed: No files to scan");
        }


        return cxWebService.scan(createCliScanArgs(byteArrayOutputStream.toByteArray()));
   }

    private CliScanArgs createCliScanArgs(byte[] compressedSources)
    {

        ProjectSettings projectSettings = new ProjectSettings();
        projectSettings.setDescription(getComment());
        // TODO: implement presetID
        projectSettings.setProjectName(getProjectName());
        projectSettings.setScanConfigurationID(0); // TODO: Re-implement source encoding settings

        LocalCodeContainer localCodeContainer = new LocalCodeContainer();
        localCodeContainer.setFileName("src.zip"); // TODO: Check what filename to set
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

        public FormValidation doCheckUseOwnServerCredentials(@QueryParameter boolean value)
        {
            return FormValidation.error("Test: " + value);
        }

        public FormValidation doCheckServerUrl(@QueryParameter String value) {
            try {
                this.cxWebService = null;
                this.cxWebService = new CxWebService(value);
                return FormValidation.ok("Server Validated Successfully");
            } catch (Exception e)
            {
                return FormValidation.error(e.getMessage());
            }
        }


        public FormValidation doCheckPassword(@QueryParameter String value, @QueryParameter String username) {

            String password = value;

            if (this.cxWebService==null) {
                return FormValidation.warning("Server URL not set");
            }

            try {
                this.cxWebService.login(username,password);
                return FormValidation.ok("Login Successful");

            } catch (Exception e)
            {
                return FormValidation.error(e.getMessage());
            }
        }

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
