package com.checkmarx.jenkins;

import com.cx.restclient.dto.ScanResults;

import java.io.Serializable;

/**
 * Scan results and additional configuration info that are obtained during the execution of a Jenkins build,
 * possibly on a remote agent.
 */
public class RemoteScanInfo implements Serializable {
    private ScanResults scanResults;
    private String cxARMUrl;
    private String version;
    private String hotFix;
    private String enginePackVersion;

    public void setScanResults(ScanResults scanResults) {
        this.scanResults = scanResults;
    }

    public ScanResults getScanResults() {
        return scanResults;
    }

    public void setCxARMUrl(String cxARMUrl) {
        this.cxARMUrl = cxARMUrl;
    }

    public String getCxARMUrl() {
        return cxARMUrl;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }
    
    public void setHotFix(String hotFix) {
        this.hotFix = hotFix;
    }

    public String getHotFix() {
        return hotFix;
    }
    
    public void setEnginePackVersion(String enginePackVersion) {
        this.enginePackVersion = enginePackVersion;
    }

    public String getEnginePackVersion() {
        return enginePackVersion;
    }
}