package com.checkmarx.jenkins;

import com.checkmarx.components.zipper.ZipListener;
import com.checkmarx.components.zipper.Zipper;
import com.checkmarx.ws.CxCLIWebService.*;
import com.checkmarx.ws.CxWSResolver.CxClientType;
import com.checkmarx.ws.CxWSResolver.CxWSResolver;
import com.checkmarx.ws.CxWSResolver.CxWSResolverSoap;
import com.checkmarx.ws.CxWSResolver.CxWSResponseDiscovery;
import com.sun.xml.internal.ws.wsdl.parser.InaccessibleWSDLException;
import hudson.AbortException;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.xml.namespace.QName;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.LinkedList;

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

    private String serverUrl;
    private String username;
    private String password;
    private String projectName;

    private String preset;
    private boolean presetSpecified;
    private String extensionsExclude;
    private String locationPathExclude;

    private boolean visibleToOthers;
    private boolean incremental;
    private String sourceEncoding;
    private String comment;

    private boolean vulnerabilityThresholdEnabled;
    private int highThreshold;

    //////////////////////////////////////////////////////////////////////////////////////
    // Private variables
    //////////////////////////////////////////////////////////////////////////////////////

    private static Logger logger = Logger.getLogger(CxScanBuilder.class);
    private final long MAX_ZIP_SIZE = 200 * 1024 * 1024;

    //////////////////////////////////////////////////////////////////////////////////////
    // Constructors
    //////////////////////////////////////////////////////////////////////////////////////



    @DataBoundConstructor
    public CxScanBuilder(String serverUrl,
                         String username,
                         String password,
                         String projectName,
                         String preset,
                         boolean presetSpecified,
                         String extensionsExclude,
                         String locationPathExclude,
                         boolean visibleToOthers,
                         boolean incremental,
                         String sourceEncoding,
                         String comment,
                         boolean vulnerabilityThresholdEnabled,
                         int highThreshold)
    {
        this.serverUrl = serverUrl;
        this.username = username;
        this.password = password;
        this.projectName = projectName;
        this.preset = preset;
        this.presetSpecified = presetSpecified;
        this.extensionsExclude = extensionsExclude;
        this.locationPathExclude = locationPathExclude;
        this.visibleToOthers = visibleToOthers;
        this.incremental = incremental;
        this.sourceEncoding = sourceEncoding;
        this.comment = comment;
        this.vulnerabilityThresholdEnabled = vulnerabilityThresholdEnabled;
        this.highThreshold = highThreshold;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    // Configuration fields getters
    //////////////////////////////////////////////////////////////////////////////////////

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


    public String getExtensionsExclude() {
        return extensionsExclude;
    }

    public String getLocationPathExclude() {
        return locationPathExclude;
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

    public boolean isVulnerabilityThresholdEnabled() {
        return vulnerabilityThresholdEnabled;
    }

    public int getHighThreshold() {
        return highThreshold;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,BuildListener listener) throws InterruptedException, IOException {

        File checkmarxBuildDir = new File(build.getRootDir(),"checkmarx");
        File reportFile = new File(checkmarxBuildDir,"ScanReport.xml");

        initLogger(checkmarxBuildDir,listener);

        CxWebService cxWebService = new CxWebService(getServerUrl());
        cxWebService.login(getUsername(),getPassword());
        listener.getLogger().append("Checkmarx server login successful\n");

        CxWSResponseRunID cxWSResponseRunID = submitScan(build, listener, cxWebService);
        listener.getLogger().append("\nScan job submitted successfully\n");

        cxWebService.trackScanProgress(cxWSResponseRunID,listener);


        // Old code
        /*
        WriterAppender appender = new WriterAppender(new PatternLayout(),listener.getLogger());
        appender.setThreshold(Level.INFO);
        Logger.getLogger("com.checkmarx.cxconsole.commands").addAppender(appender);

        // Debug appenders
        ConsoleAppender debugAppender = new ConsoleAppender();
        debugAppender.setThreshold(Level.DEBUG);
        Logger.getLogger("com.checkmarx.cxconsole").addAppender(debugAppender);
        Logger.getLogger("com.checkmarx.cxviewer").addAppender(debugAppender);

        File checkmarxBuildDir = new File(build.getRootDir(),"checkmarx");
        File reportFile = new File(checkmarxBuildDir,"ScanReport.xml");

        listener.started(null);
        int cxConsoleLauncherExitCode = CxConsoleLauncher.runCli(createCliCommandLineArgs(build,checkmarxBuildDir,reportFile));

        if (cxConsoleLauncherExitCode != CxConsoleCommand.CODE_OK)
        {
            listener.finished(Result.FAILURE);
            logger.debug("Checkmarx build step finished with: AbortException and Result.FAILURE");
            throw new AbortException("Checkmarx Scan Failed"); // This exception marks the build as failed
        }

        CxScanResult cxScanResult = new CxScanResult(build);
        cxScanResult.readScanXMLReport(reportFile);
        build.addAction(cxScanResult);

        if (this.isVulnerabilityThresholdEnabled())
        {
            if (cxScanResult.getHighCount() >  this.getHighThreshold())
            {
                listener.finished(Result.UNSTABLE);
                logger.debug("Checkmarx build step finished with: Result.UNSTABLE");
                return true;
            }
        }

        listener.finished(Result.SUCCESS);
        logger.debug("Checkmarx build step finished with: Result.SUCCESS");*/
        return true;
    }

    // return can be empty but never null
    @Override
    public Action getProjectAction(AbstractProject<?, ?> project) {
        AbstractBuild<?, ?> lastBuild = project.getLastBuild();
        if (lastBuild!=null)
        {
            Action a = lastBuild.getAction(CxScanResult.class);
            if (a != null)
            {
                return a;
            }
        }

        // Return empty action
        return new Action() {
            @Override
            public String getIconFileName() {
                return null;
            }

            @Override
            public String getDisplayName() {
                return null;
            }

            @Override
            public String getUrlName() {
                return null;
            }
        };
    }

    private void initLogger(File checkmarxBuildDir, BuildListener listener)
    {
        WriterAppender writerAppender = new WriterAppender(new PatternLayout("%m%n"),listener.getLogger());
        writerAppender.setThreshold(Level.INFO);
        Logger.getLogger("com.checkmarx").addAppender(writerAppender);
        String logFileName = checkmarxBuildDir.getAbsolutePath() + File.separator + "checkmarx.log";

        try {
            FileAppender fileAppender = new FileAppender(new PatternLayout("%d %-5p: %m%n"),logFileName);
            fileAppender.setThreshold(Level.DEBUG);
            Logger.getLogger("com.checkmarx.jenkins").addAppender(fileAppender);
        } catch (IOException e)
        {
            logger.warn("Could not open log file for writing: " + logFileName);
            logger.debug(e);
        }
    }

    private String[] createCliCommandLineArgs(AbstractBuild<?, ?> build, File checkmarxBuildDir, File reportFile) throws IOException
    {
        LinkedList<String> args = new LinkedList<String>();
        args.add("Scan");
        args.add("-comment");  args.add(this.getComment());
        if ("1".equals(getSourceEncoding()))
        {
            args.add("-Configuration");
            args.add("Japanese (Shift-JIS)");
        }

        args.add("-CxPassword"); args.add(this.getPassword());
        args.add("-CxServer");   args.add(this.getServerUrl());
        args.add("-CxUser");     args.add(this.getUsername());
        if (this.isIncremental())
        {
            args.add("-incremental");
        }

        if (build.getWorkspace().isRemote())
        {
            logger.error("Workspace is on remote machine");
            throw new IOException("Workspace is on remote machine");
        }
        args.add("-LocationPath"); args.add(build.getWorkspace().getRemote());

        String[] excludeFolders = StringUtils.split(getLocationPathExclude()," ,;:");
        if (excludeFolders.length > 0)
        {
            args.add("-LocationPathExclude");
            for (String excludeFolder : excludeFolders)
            {
                args.add(excludeFolder);
            }
        }

        String[] excludeExtensions = StringUtils.split(getExtensionsExclude()," ,;:");
        if (excludeExtensions.length > 0)
        {
            args.add("-ExtensionsExclude");
            for (String excludeExtension : excludeExtensions)
            {
                args.add(excludeExtension);
            }
        }

        args.add("-LocationType"); args.add("folder");
        if (this.isPresetSpecified())
        {
            args.add("-Preset"); args.add(this.getPreset());
        }

        if (!this.isVisibleToOthers())
        {
            args.add("-private");
        }

        args.add("-ProjectName"); args.add(this.getProjectName());
        args.add("-ReportXML"); args.add(reportFile.getAbsolutePath());

        args.add("-v");
        File logFile = new File(checkmarxBuildDir,"cx_scan.log");
        args.add("-log"); args.add(logFile.getAbsolutePath());


        String[] result =  args.toArray(new String[0]);

        logger.debug("CLI Command Arguments: " + StringUtils.join(result," "));
        return result;
    }




    private CxWSResponseRunID submitScan(AbstractBuild<?, ?> build, final BuildListener listener, CxWebService cxWebService) throws AbortException, IOException
    {

        ZipListener zipListener = new ZipListener() {
            @Override
            public void updateProgress(String fileName, long size) {
                listener.getLogger().append("Zipping (" + FileUtils.byteCountToDisplaySize(size) + "): " + fileName + "\n");
            }

        };

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        File baseDir = new File(build.getWorkspace().getRemote());

        listener.getLogger().append("\nStarting to zip the workspace\n\n");
        try {
            new Zipper().zip(baseDir,this.getLocationPathExclude(),byteArrayOutputStream,MAX_ZIP_SIZE,zipListener);
        } catch (Zipper.MaxZipSizeReached e)
        {
            throw new AbortException("Checkmarx Scan Failed: Reached maximum upload size limit of " + FileUtils.byteCountToDisplaySize(MAX_ZIP_SIZE));
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

        public final static String DEFAULT_EXCLUDE_EXTENSION = "DS_Store, ipr, iws, bak, tmp, aac, aif, iff, m3u, mid, mp3, mpa, ra, wav, wma, 3g2, 3gp, asf, asx, avi, flv, mov, mp4, mpg, rm, swf, vob, wmv, bmp, gif, jpg, png, psd, tif, swf, jar, zip, rar, exe, dll, pdb, 7z, gz, tar.gz, tar, gz,ahtm, ahtml, fhtml, hdm, hdml, hsql, ht, hta, htc, htd, htm, html, htmls, ihtml, mht, mhtm, mhtml, ssi, stm, stml, ttml, txn, xhtm, xhtml, class, iml";
        public final static String DEFAULT_EXCLUDE_FOLDERS = "_cvs, .svn, .hg, .git, .bzr, bin, obj, backup, .idea";

        private static Logger logger = Logger.getLogger(DescriptorImpl.class);

        private String webServiceUrl;


        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        //////////////////////////////////////////////////////////////////////////////////////
        // Field value validators
        //////////////////////////////////////////////////////////////////////////////////////

        public FormValidation doCheckServerUrl(@QueryParameter String value) {
            try {
                this.webServiceUrl = null;
                this.webServiceUrl = resolveWebServiceURL(value);
                return FormValidation.ok("Server Validated Successfully");
            } catch (Exception e)
            {
                return FormValidation.error(e.getMessage());
            }
        }


        public FormValidation doCheckPassword(@QueryParameter String value, @QueryParameter String username) {
            final int LCID = 1033;

            String password = value;

            if (this.webServiceUrl==null) {
                return FormValidation.warning("Server URL not set");
            }

            try {
                // TODO: Use CxWebService for implementation
                CxCLIWebService cxCLIWebService = new CxCLIWebService(new URL(this.webServiceUrl),new QName("http://Checkmarx.com/v7", "CxCLIWebService"));
                CxCLIWebServiceSoap cxCLIWebServiceSoap = cxCLIWebService.getCxCLIWebServiceSoap();
                Credentials credentials = new Credentials();
                credentials.setUser(username);
                credentials.setPass(password);
                CxWSResponseLoginData cxWSResponseLoginData = cxCLIWebServiceSoap.login(credentials, LCID);
                if (cxWSResponseLoginData.isIsSuccesfull())
                {
                    return FormValidation.ok("Login Successful");
                } else {
                    return FormValidation.error(cxWSResponseLoginData.getErrorMessage());
                }

            } catch (InaccessibleWSDLException e)
            {
                return FormValidation.error("Error connecting to the server");
            } catch (Exception e)
            {
                return FormValidation.error(e.getMessage());
            }


        }

        private String resolveWebServiceURL(String serverUrl) throws Exception
        {
            final String WS_RESOLVER_PATH = "/cxwebinterface/cxWSResolver.asmx";
            final int WS_CLI_INTERFACE_VERSION = 0;
            final String NO_CONNECTION_ERROR_MESSAGE = "Checkmarx server did not respond on the specified URL";

            try {
                if (serverUrl==null || serverUrl.isEmpty())
                {
                    throw new Exception("Provide Server URL");
                }

                URL url = new URL(serverUrl);
                if (!url.getPath().isEmpty())
                {
                    throw new Exception("URL Must not contain path");
                }
                if (url.getQuery()!=null)
                {
                    throw new Exception("URL Must not contain query parameters");
                }
                url = new URL(url.toString() + WS_RESOLVER_PATH);

                // TODO: Use CxWebServer for implementation
                CxWSResolver cxWSResolver = new CxWSResolver(url,new QName("http://Checkmarx.com", "CxWSResolver"));
                CxWSResolverSoap cxWSResolverSoap = cxWSResolver.getCxWSResolverSoap();
                CxWSResponseDiscovery cxWSResponseDiscovery = cxWSResolverSoap.getWebServiceUrl(CxClientType.CLI,WS_CLI_INTERFACE_VERSION);
                if (cxWSResponseDiscovery.isIsSuccesfull())
                {
                    return cxWSResponseDiscovery.getServiceURL();
                } else {
                    throw new Exception(NO_CONNECTION_ERROR_MESSAGE);
                }
            } catch (InaccessibleWSDLException e)
            {
                logger.debug(e);
                throw new Exception(NO_CONNECTION_ERROR_MESSAGE);
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

            // save();
            return super.configure(req,formData);
        }


    }
}
