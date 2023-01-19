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
    private boolean cxOneSASTEnabled;

	public boolean isCxOneSASTEnabled() {
		return cxOneSASTEnabled;
	}

	public void setCxOneSASTEnabled(boolean cxOneSASTEnabled) {
		this.cxOneSASTEnabled = cxOneSASTEnabled;
	}

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
}
