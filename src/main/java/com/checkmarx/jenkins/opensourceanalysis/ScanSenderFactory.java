package com.checkmarx.jenkins.opensourceanalysis;

import com.checkmarx.jenkins.web.client.ScanClient;
import org.apache.log4j.Logger;

/**
 * Created by tsahib on 9/12/2016.
 */
public class ScanSenderFactory {
    private ScanClient scanClient;
    private long projectId;
    private transient Logger logger;

    public ScanSenderFactory(ScanClient scanClient, long projectId, Logger logger){
        this.scanClient = scanClient;
        this.logger = logger;
        this.projectId = projectId;
    }

    public ScanSender create(boolean asynchronous){
        if (asynchronous){
            return new ScanSender(scanClient, projectId);
        }else {
            return new SynchronousScanSender(scanClient, projectId, logger);
        }
    }
}
