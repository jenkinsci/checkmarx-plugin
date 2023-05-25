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
    public String scaEnvVariables;
    
    @DataBoundSetter
    public boolean isExploitablePath;

    @DataBoundSetter
    public boolean useJobLevelSastDetails;
    
    @DataBoundSetter
    public String scaConfigFile;

    @DataBoundSetter
    public String scaSASTProjectFullPath;
    
    @DataBoundSetter
    public String scaSASTProjectID;
    
    @DataBoundSetter
    public String SASTUserName;
    
    @DataBoundSetter
    public String scaSastServerUrl;    

    @DataBoundSetter
    public String scaWebAppUrl;

    @DataBoundSetter
    public String scaCredentialsId;

    @DataBoundSetter
    public String scaTenant;
    
    @DataBoundSetter
    public String scaTeamPath;
    
    @DataBoundSetter
    public String scaTeamId;
    
    @DataBoundSetter
    public Integer scaTimeout;
    
    @DataBoundSetter
    public boolean generateScaReport;
    
    @DataBoundSetter
    public boolean isIncludeSources;

    @DataBoundSetter
    public SCAScanType enableScaResolver;

    @DataBoundSetter
    public String pathToScaResolver;

    @DataBoundSetter
    public String scaResolverAddParameters;
    
    @DataBoundSetter
    public String globalScaResolverAddParameters;
    
    @DataBoundSetter
    public boolean isExploitablePathByScaResolver;
    
    @DataBoundSetter
    public boolean isGlobalExploitablePathByScaResolver;
    
    @DataBoundSetter
    public String fsaVariables;
    
    @DataBoundSetter
    public String sastCredentialsId;

    @DataBoundConstructor
    public DependencyScanConfig() {
    }
}