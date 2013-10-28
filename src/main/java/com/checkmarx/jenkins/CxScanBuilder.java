package com.checkmarx.jenkins;

import com.checkmarx.cxconsole.CxConsoleLauncher;
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
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.apache.log4j.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

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
        PropertyConfigurator.configure(CxScanBuilder.class.getResource("log4j.properties"));
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
    private String includeExtensions;
    private String locationPathExclude;

    private boolean visibleToOthers;
    private boolean incremental;
    private String sourceEncoding;
    private String comment;

    //////////////////////////////////////////////////////////////////////////////////////
    // Private variables
    //////////////////////////////////////////////////////////////////////////////////////


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
                         String includeExtensions,
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
        this.includeExtensions = includeExtensions;
        this.locationPathExclude = locationPathExclude;
        this.visibleToOthers = visibleToOthers;
        this.incremental = incremental;
        this.sourceEncoding = sourceEncoding;   // TODO: Convert string to enum
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


    public String getIncludeExtensions() {
        return includeExtensions;
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
        //boolean result = super.perform(build, launcher, listener);

        WriterAppender appender = new WriterAppender(new PatternLayout(),listener.getLogger());
        Logger.getLogger("com.checkmarx.cxconsole").addAppender(appender);
        Logger.getLogger("com.checkmarx.cxviewer").addAppender(appender);
        System.out.println("Hello Jenkins");
        listener.getLogger().println("Hello Jenkins logger");
        listener.getLogger().flush();

        CxConsoleLauncher.main(new String[]{"Scan"});



        return true;//result;
    }



    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }


    /*public String getIconPath() {
        PluginWrapper wrapper = Hudson.getInstance().getPluginManager().getPlugin([YOUR-PLUGIN-MAIN-CLASS].class);
        return Hudson.getInstance().getRootUrl() + "plugin/"+ wrapper.getShortName()+"/";
    }*/

    public String getMyString() {
        return "Hello Jenkins!";
    }
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        public final static String DEFAULT_INCLUDE_EXTENSION = ".java, .c, .cs"; // TODO: set real default value
        public final static String DEFAULT_EXCLUDE_FOLDERS = "target, work, src/main/resources";

        private static Logger logger = Logger.getLogger(DescriptorImpl.class);

        private String webServiceUrl;


        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        //////////////////////////////////////////////////////////////////////////////////////
        // Field value validators
        //////////////////////////////////////////////////////////////////////////////////////

        public FormValidation doCheckServerUrl(@QueryParameter String value) {
            logger.debug("Test logger");
            try {
                this.webServiceUrl = null;
                this.webServiceUrl = resolveWebServiceURL(value);
                return FormValidation.ok();
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
