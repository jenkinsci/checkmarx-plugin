package com.checkmarx.jenkins.action;

import com.cx.restclient.configuration.CxScanConfig;
import hudson.model.InvisibleAction;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean
public class CxScanConfigWrapper extends InvisibleAction {
    @Exported
    public Boolean sastEnabled = false;

    @Exported
    public Boolean osaEnabled = false;

    @Exported
    public String cxOrigin;

    @Exported
    public String sourceDir;

    @Exported
    public String username;

    @Exported
    public String url;

    @Exported
    public String projectName;

    @Exported
    public String teamPath;

    @Exported
    public String teamId;

    @Exported
    public Boolean denyProject = false;

    @Exported
    public Boolean isPublic = true;

    @Exported
    public Boolean forceScan = false;

    @Exported
    public String presetName;

    @Exported
    public Integer presetId;

    @Exported
    public String sastFolderExclusions;

    @Exported
    public String sastFilterPattern;

    @Exported
    public Integer sastScanTimeoutInMinutes;

    @Exported
    public String scanComment;

    @Exported
    public Boolean isIncremental = false;

    @Exported
    public Boolean isSynchronous = false;

    @Exported
    public Boolean sastThresholdsEnabled = false;

    @Exported
    public Integer sastHighThreshold;

    @Exported
    public Integer sastMediumThreshold;

    @Exported
    public Integer sastLowThreshold;

    @Exported
    public Boolean sastNewResultsThresholdEnabled = false;

    @Exported
    public String sastNewResultsThresholdSeverity;

    @Exported
    public Boolean generatePDFReport = false;

    @Exported
    public Integer engineConfigurationId = 1;

    @Exported
    public String osaFolderExclusions;

    @Exported
    public String osaFilterPattern;

    @Exported
    public String osaArchiveIncludePatterns;

    @Exported
    public Boolean osaGenerateJsonReport = true;

    @Exported
    public Boolean osaRunInstall = false;

    @Exported
    public Boolean osaThresholdsEnabled = false;

    @Exported
    public Integer osaHighThreshold;

    @Exported
    public Integer osaMediumThreshold;

    @Exported
    public Integer osaLowThreshold;

    @Exported
    public String osaDependenciesJson;

    @Exported
    public Boolean generateXmlReport = true;

    public CxScanConfigWrapper(CxScanConfig config) {
        teamPath = config.getTeamPath();
        teamId = config.getTeamId();
        projectName = config.getProjectName();
        cxOrigin = config.getCxOrigin();
        denyProject = config.getDenyProject();
        engineConfigurationId = config.getEngineConfigurationId();
        forceScan = config.getForceScan();
        generatePDFReport = config.getGeneratePDFReport();
        generateXmlReport = config.getGenerateXmlReport();
        isIncremental = config.getIncremental();
        osaArchiveIncludePatterns = config.getOsaArchiveIncludePatterns();
        osaDependenciesJson = config.getOsaDependenciesJson();
        osaEnabled = config.getOsaEnabled();
        osaFilterPattern = config.getOsaFilterPattern();
        osaFolderExclusions = config.getOsaFolderExclusions();
        osaGenerateJsonReport = config.getOsaGenerateJsonReport();
        osaHighThreshold = config.getOsaHighThreshold();
        osaLowThreshold = config.getOsaLowThreshold();
        osaMediumThreshold = config.getOsaMediumThreshold();
        osaRunInstall = config.getOsaRunInstall();
        osaThresholdsEnabled = config.getOsaThresholdsEnabled();
        presetId = config.getPresetId();
        presetName = config.getPresetName();
        isPublic = config.getPublic();
        sastEnabled = config.getSastEnabled();
        sastFilterPattern = config.getSastFilterPattern();
        sastFolderExclusions = config.getSastFolderExclusions();
        sastHighThreshold = config.getSastHighThreshold();
        sastMediumThreshold = config.getSastMediumThreshold();
        sastLowThreshold = config.getSastLowThreshold();
        sastNewResultsThresholdEnabled = config.getSastNewResultsThresholdEnabled();
        sastNewResultsThresholdSeverity = config.getSastNewResultsThresholdSeverity();
        sastScanTimeoutInMinutes = config.getSastScanTimeoutInMinutes();
        sastThresholdsEnabled = config.getSastThresholdsEnabled();
        scanComment = config.getScanComment();
        sourceDir = config.getSourceDir();
        isSynchronous = config.getSynchronous();
        url = config.getUrl();
        username = config.getUsername();
    }
}
