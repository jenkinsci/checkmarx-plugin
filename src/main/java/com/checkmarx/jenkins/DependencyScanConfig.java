package com.checkmarx.jenkins;

import com.cx.restclient.dto.DependencyScannerType;
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

    @DataBoundConstructor
    public DependencyScanConfig() {
    }
}
