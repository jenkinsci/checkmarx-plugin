package com.checkmarx.jenkins;

import com.cx.restclient.CxClientDelegator;
import com.cx.restclient.configuration.CxScanConfig;
import com.cx.restclient.dto.ScanResults;
import com.cx.restclient.exception.CxClientException;
import hudson.FilePath;
import hudson.ProxyConfiguration;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import org.jenkinsci.remoting.RoleChecker;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;

public class CxScanCallable implements FilePath.FileCallable<RemoteScanInfo>, Serializable {

    private static final long serialVersionUID = 1L;

    private final CxScanConfig config;
    private final TaskListener listener;
    private ProxyConfiguration jenkinsProxy = null;

    public CxScanCallable(CxScanConfig config, TaskListener listener) {
        this.config = config;
        this.listener = listener;
    }

    public CxScanCallable(CxScanConfig config, TaskListener listener, ProxyConfiguration jenkinsProxy) {
        this.config = config;
        this.listener = listener;
        this.jenkinsProxy = jenkinsProxy;
    }

    @Override
    public RemoteScanInfo invoke(File file, VirtualChannel channel) throws IOException, InterruptedException {
        CxLoggerAdapter log = new CxLoggerAdapter(listener.getLogger());
        config.setSourceDir(file.getAbsolutePath());
        config.setReportsDir(file);

        RemoteScanInfo result = new RemoteScanInfo();
        ScanResults scanResults = new ScanResults();
        result.setScanResults(scanResults);

        CxClientDelegator delegator = null;
        try {
            //todo: add proxy support in new common
            delegator = CommonClientFactory.getClientDelegatorInstance(config, log);
            delegator.init();

            // Make sure CxARMUrl is passed in the result.
            // Cannot pass CxARMUrl in the config object, because this callable can be executed on a Jenkins agent.
            // On a Jenkins agent we'll get a cloned config instead of the original object reference.
            result.setCxARMUrl(config.getCxARMUrl());
        } catch (Exception ex) {
            scanResults.setGeneralException(ex);

            String message = ex.getMessage();
            // Can actually be null e.g. for NullPointerException.
            if (message != null) {
                if (message.contains("Server is unavailable")) {
                    if (delegator != null) {
                        try {
                            delegator.getSastClient().login();
                        } catch (CxClientException e) {
                            throw new IOException(e);
                        }
                    }

                    String errorMsg = "Connection Failed.\n" +
                            "Validate the provided login credentials and server URL are correct.\n" +
                            "In addition, make sure the installed plugin version is compatible with the CxSAST version according to CxSAST release notes.\n" +
                            "Error: " + message;

                    throw new IOException(errorMsg);
                }
                if (message.contains("Creation of the new project")) {
                    return result;
                }
            }
            throw new IOException(message);
        }

        Logger rootLog = null;
        StreamHandler handler = null;
        if (config.isOsaEnabled() || config.isAstScaEnabled()) {
            //---------------------------
            //we do this in order to redirect the logs from the filesystem agent component to the build console
            rootLog = Logger.getLogger("");
            handler = new StreamHandler(listener.getLogger(), new ComponentScanFormatter());
            handler.setLevel(Level.ALL);
            rootLog.addHandler(handler);
            //---------------------------
        }

        delegator.initiateScan();

        if (rootLog != null && handler != null) {
            handler.flush();
            rootLog.removeHandler(handler);
        }

        try {
            scanResults = config.getSynchronous() ? delegator.waitForScanResults() : delegator.getLatestScanResults();
        } catch (Exception e) {
            if (config.getSynchronous() && config.isSastEnabled()) {
                cancelScan(delegator);
            }
            throw e;
        }

        if (config.getEnablePolicyViolations() && (scanResults.getOsaResults() != null || scanResults.getScaResults() != null || scanResults.getSastResults() != null)) {
            delegator.printIsProjectViolated(scanResults);
        }

        return result;
    }

    private void cancelScan(CxClientDelegator delegator) {
        try {
            delegator.getSastClient().cancelSASTScan();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void checkRoles(RoleChecker checker) throws SecurityException {

    }
}