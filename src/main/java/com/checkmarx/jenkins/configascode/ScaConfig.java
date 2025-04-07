package com.checkmarx.jenkins.configascode;

import com.typesafe.config.Optional;

public class ScaConfig {
    @Optional
    private String fileInclude;
    @Optional
    private String fileExclude;
    @Optional
    private String pathExclude;
    @Optional
    private int low;
    @Optional
    private int medium;
    @Optional
    private int high;
    @Optional
    private int critical;

    public ScaConfig() {
    }

    public String getFileInclude() {
        return fileInclude;
    }

    public void setFileInclude(String fileInclude) {
        this.fileInclude = fileInclude;
    }

    public String getFileExclude() {
        return fileExclude;
    }

    public void setFileExclude(String fileExclude) {
        this.fileExclude = fileExclude;
    }

    public String getPathExclude() {
        return pathExclude;
    }

    public void setPathExclude(String pathExclude) {
        this.pathExclude = pathExclude;
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
    
    public int getCritical() {
        return critical;
    }

    public void setCritical(int critical) {
        this.critical = critical;
    }
}