package com.checkmarx.jenkins.configascode;

import com.typesafe.config.Optional;

public class SastConfig {
    @Optional
    private String preset;
    @Optional
    private String engineConfiguration;
    @Optional
    private String includeExcludePattern;
    @Optional
    private String excludeFolders;
    @Optional
    private boolean incremental;
    @Optional
    private boolean privateScan;
    @Optional
    private int low;
    @Optional
    private int medium;
    @Optional
    private int high;
    @Optional
    private boolean overrideProjectSetting;

    public SastConfig() {
    }

    public String getPreset() {
        return preset;
    }

    public void setPreset(String preset) {
        this.preset = preset;
    }

    public String getEngineConfiguration() {
        return engineConfiguration;
    }

    public void setEngineConfiguration(String engineConfiguration) {
        this.engineConfiguration = engineConfiguration;
    }

    public String getIncludeExcludePattern() {
        return includeExcludePattern;
    }

    public void setIncludeExcludePattern(String includeExcludePattern) {
        this.includeExcludePattern = includeExcludePattern;
    }

    public String getExcludeFolders() {
        return excludeFolders;
    }

    public void setExcludeFolders(String excludeFolders) {
        this.excludeFolders = excludeFolders;
    }

    public boolean isIncremental() {
        return incremental;
    }

    public void setIncremental(boolean incremental) {
        this.incremental = incremental;
    }

    public int getLow() {
        return low;
    }

    public void setLow(int low) {
        this.low = low;
    }

    public int getMedium() {
        return medium;
    }

    public void setMedium(int medium) {
        this.medium = medium;
    }

    public int getHigh() {
        return high;
    }

    public void setHigh(int high) {
        this.high = high;
    }

    public boolean isPrivateScan() {
        return privateScan;
    }

    public void setPrivateScan(boolean privateScan) {
        this.privateScan = privateScan;
    }
    
	public boolean isOverrideProjectSetting() {
		return overrideProjectSetting;
	}

	public void setOverrideProjectSetting(boolean isOverrideProjectSetting) {
		this.overrideProjectSetting = isOverrideProjectSetting;
	}
}
