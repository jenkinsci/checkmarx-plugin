package com.checkmarx.jenkins;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Represents a dependency scan configuration section. The section appears both in job (local) and system (global)
 * configuration pages.
 */
public class DependencyScanConfig {
    /**
     * Only applicable for job configuration.
     */
    @DataBoundSetter
    public boolean overrideGlobalConfig;

    @DataBoundSetter
    public String dependencyScanPatterns;

    @DataBoundSetter
    public String dependencyScanExcludeFolders;

    @DataBoundSetter
    public DependencyScannerType dependencyScannerType;

    @DataBoundSetter
    public String osaArchiveIncludePatterns;

    @DataBoundSetter
    public boolean osaInstallBeforeScan;

    @DataBoundSetter
    public String scaServerUrl;

    @DataBoundSetter
    public String scaAccessControlUrl;

    @DataBoundSetter
    public String scaWebAppUrl;

    @DataBoundSetter
    public String scaCredentialsId;

    @DataBoundSetter
    public String scaTenant;

    @DataBoundSetter
    public String fsaVariables;

    @DataBoundSetter
    public String scaEnvVariables;

    @DataBoundSetter
    public String configFilePath;

    @DataBoundConstructor
    public DependencyScanConfig() {
    }
}
