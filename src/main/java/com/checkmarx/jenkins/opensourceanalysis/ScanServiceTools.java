package com.checkmarx.jenkins.opensourceanalysis;

import com.checkmarx.jenkins.CxWebService;
import com.checkmarx.jenkins.web.client.OsaScanClient;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import org.apache.log4j.Logger;

/**
 * Created by zoharby on 10/01/2017.
 */
public class ScanServiceTools {

    private static Logger logger;

    private DependencyFolder dependencyFolder;
    private CxWebService webServiceClient;
    private OsaScanClient osaScanClient;
    private AbstractBuild<?, ?> build;
    private BuildListener listener;
    private long projectId;

    public DependencyFolder getDependencyFolder() {
        return dependencyFolder;
    }

    public void setDependencyFolder(DependencyFolder dependencyFolder) {
        this.dependencyFolder = dependencyFolder;
    }

    public CxWebService getWebServiceClient() {
        return webServiceClient;
    }

    public void setWebServiceClient(CxWebService webServiceClient) {
        this.webServiceClient = webServiceClient;
    }

    public OsaScanClient getOsaScanClient() {
        return osaScanClient;
    }

    public void setOsaScanClient(OsaScanClient osaScanClient) {
        this.osaScanClient = osaScanClient;
    }

    public AbstractBuild<?, ?> getBuild() {
        return build;
    }

    public void setBuild(AbstractBuild<?, ?> build) {
        this.build = build;
    }

    public BuildListener getListener() {
        return listener;
    }

    public void setListener(BuildListener listener) {
        this.listener = listener;
    }

    public Logger getLogger() {
        return logger;
    }

    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    public long getProjectId() {
        return projectId;
    }

    public void setProjectId(long projectId) {
        this.projectId = projectId;
    }
}
