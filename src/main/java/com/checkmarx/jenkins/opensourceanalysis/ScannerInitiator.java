package com.checkmarx.jenkins.opensourceanalysis;
import com.checkmarx.jenkins.web.client.ScanClient;
import hudson.FilePath;


/**
 * Created by tsahib on 9/12/2016.
 */
public class ScannerInitiator extends Scanner {
    public ScannerInitiator(ScanClient scanClient, long projectId) {
        super(scanClient, projectId);
    }

    public void scan(FilePath sourceCodeZip) throws Exception {
        createScan(sourceCodeZip);
    }
}
