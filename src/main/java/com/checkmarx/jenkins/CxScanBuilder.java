package com.checkmarx.jenkins;

import com.checkmarx.cxconsole.CxConsoleLauncher;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.SimpleLayout;
import org.apache.log4j.WriterAppender;
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

    /*

    V -comment <text>                             Scan comment. Example: -comment 'important scan1'. Optional.
    V -Configuration <configuration>  (either Default Configuration or Japanese (Shift-JIS))            If configuration is not set, "Default Configuration" will be used for a
                                                new project. Optional.
    V -CxPassword <password>                      Login password
    V -CxServer <server>                          IP address or resolvable name of CxSuite web server
    V -CxUser <username>                          Login username
    V -incremental                                Run incremental scan instead of full scan. Optional.
    R -LocationBranch <branch>                    Sources GIT branch. Required if -LocationType is GIT. Optional.
    R -LocationPassword <password>                Source control or network password. Required if -LocationType is
                                                TFS/SVN/shared.
    D -LocationPath <path>                        Local or shared path to sources or source repository branch. Required if
                                                -LocationType is folder/shared.
    V -LocationPathExclude <file list>            List of ignored folders. Relative paths are resolved retalive to
                                                -LocationPath. Example: -LocationPathExclude test* log_*. Optional.
    R -LocationPort <url>                         Source control system port. Default 8080/80 (TFS/SVN). Optional.
    R -LocationPrivateKey <file>                  GIT private key location. Required  if -LocationType is GIT in SSH mode.
    R -LocationPublicKey <file>                   GIT public key location. Required  if -LocationType is GIT in SSH mode.
    D -LocationType <folder|shared|TFS|SVN|GIT>   Source location type: folder, shared folder, source repository: SVN,
                                                TFS, GIT
    R -LocationURL <url>                          Source control URL. Required if -LocationType is TFS/SVN/GIT.
    R -LocationUser <username>                    Source control or network username. Required if -LocationType is
                                                TFS/SVN/shared.
    D -log <file>                                 Log file. Optional.
    V -Preset <preset>                            If preset is not specified, will use the predefined preset for an
                                                existing project, and Default preset for a new project. Optional.
    V -private                                    Scan will not be visible to other users. Optional.
    V -comment <text>                             Scan comment. Example: -comment 'important scan1'. Optional.
    D  -v,--verbose                                Turns on verbose mode. All messages and events will be sent to the
                                                console/log file.  Optional.
     */

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

        public static String DEFAULT_INCLUDE_EXTENSION = ".java, .c, .cs"; // TODO: set real default value
        public static String DEFAULT_EXCLUDE_FOLDERS = "target, work, src/main/resources";


        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        //////////////////////////////////////////////////////////////////////////////////////
        // Field value validators
        //////////////////////////////////////////////////////////////////////////////////////

        public FormValidation doCheckServerUrl(@QueryParameter String value) {
            // TODO: Implement server url verification after web service code is integrated
            try {
                URL u = new URL(value);
                return FormValidation.ok();
            } catch (MalformedURLException e)
            {
                return FormValidation.error(e.getMessage());
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
