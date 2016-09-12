package com.checkmarx.jenkins.opensourceanalysis;

import com.checkmarx.jenkins.web.client.ScanClient;
import org.apache.log4j.Logger;

/**
 * Created by tsahib on 9/12/2016.
 */
public class ScanFactory {
    private ScanClient scanClient;
    private long projectId;
    private transient Logger logger;

    public ScanFactory(ScanClient scanClient, long projectId, Logger logger){
        this.scanClient = scanClient;
        this.logger = logger;
        this.projectId = projectId;
    }
    public Scanner create(boolean asynchronous){
        if (asynchronous){
            return new ScannerInitiator(scanClient, projectId);
        }else {
            return new ResultsAwaitingScanner(scanClient, projectId, logger);
        }
    }
}
