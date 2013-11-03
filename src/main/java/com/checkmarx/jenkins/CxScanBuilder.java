package com.checkmarx.jenkins;

import com.checkmarx.cxconsole.CxConsoleLauncher;
import com.checkmarx.cxconsole.commands.CxConsoleCommand;
import com.checkmarx.cxviewer.ws.generated.Credentials;
import com.checkmarx.cxviewer.ws.generated.CxCLIWebService;
import com.checkmarx.cxviewer.ws.generated.CxCLIWebServiceSoap;
import com.checkmarx.cxviewer.ws.generated.CxWSResponseLoginData;
import com.checkmarx.cxviewer.ws.resolver.CxClientType;
import com.checkmarx.cxviewer.ws.resolver.CxWSResolver;
import com.checkmarx.cxviewer.ws.resolver.CxWSResolverSoap;
import com.checkmarx.cxviewer.ws.resolver.CxWSResponseDiscovery;
import com.sun.xml.internal.ws.wsdl.parser.InaccessibleWSDLException;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.*;
import org.codehaus.groovy.runtime.ArrayUtil;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
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
    // Static Initializer
    //////////////////////////////////////////////////////////////////////////////////////

    static {
        //PropertyConfigurator.configure(CxScanBuilder.class.getResource("log4j.properties"));
        BasicConfigurator.configure();  // TODO: Find out why the property configuration does not work
    }

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

    //////////////////////////////////////////////////////////////////////////////////////
    // Private variables
    //////////////////////////////////////////////////////////////////////////////////////

    private static Logger logger = Logger.getLogger(CxScanBuilder.class);

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
                         String comment)
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



    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {

        WriterAppender appender = new WriterAppender(new PatternLayout(),listener.getLogger());
        appender.setThreshold(Level.INFO);
        Logger.getLogger("com.checkmarx.cxconsole.commands").addAppender(appender);

        // Debug appenders
        ConsoleAppender debugAppender = new ConsoleAppender();
        debugAppender.setThreshold(Level.DEBUG);
        Logger.getLogger("com.checkmarx.cxconsole").addAppender(debugAppender);
        Logger.getLogger("com.checkmarx.cxviewer").addAppender(debugAppender);

        File checkmarxBuildDir = new File(build.getRootDir(),"checkmarx");
        int cxConsoleLauncherExitCode = CxConsoleCommand.CODE_OK;// CxConsoleLauncher.runCli(createCliCommandLineArgs(build,checkmarxBuildDir));

        build.addAction(new CxScanResult());

        return cxConsoleLauncherExitCode == CxConsoleCommand.CODE_OK; // Return true if exit code == CODE_OK
    }

    private String[] createCliCommandLineArgs(AbstractBuild<?, ?> build, File checkmarxBuildDir) throws IOException
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
        File reportFile = new File(checkmarxBuildDir,"ScanReport.xml");
        args.add("-ReportXML"); args.add(reportFile.getAbsolutePath());

        args.add("-v");
        File logFile = new File(checkmarxBuildDir,"cx_scan.log");
        args.add("-log"); args.add(logFile.getAbsolutePath());


        String[] result =  args.toArray(new String[0]);

        logger.debug("CLI Command Arguments: " + StringUtils.join(result," "));
        return result;
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

                CxCLIWebService cxCLIWebService = new CxCLIWebService(new URL(this.webServiceUrl));
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

                CxWSResolver cxWSResolver = new CxWSResolver(url);
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
