package com.checkmarx.jenkins;

import com.cx.restclient.CxShragaClient;
import com.cx.restclient.configuration.CxScanConfig;
import com.cx.restclient.dto.DependencyScanResults;
import com.cx.restclient.dto.DependencyScannerType;
import com.cx.restclient.dto.ScanResults;
import com.cx.restclient.exception.CxClientException;
import com.cx.restclient.sast.dto.SASTResults;
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
        scanResults.setSastResults(new SASTResults());
        scanResults.setDependencyScanResults(new DependencyScanResults());
        result.setScanResults(scanResults);

        boolean sastCreated = false;
        boolean dependencyScanCreated = false;

        CxShragaClient shraga = null;
        try {
            //todo: add proxy support in new common
            shraga = CommonClientFactory.getInstance(config, log);
            shraga.init();

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
                    if (shraga != null) {
                        try {
                            shraga.login();
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

        if (config.getDependencyScannerType() != DependencyScannerType.NONE) {
            //---------------------------
            //we do this in order to redirect the logs from the filesystem agent component to the build console
            Logger rootLog = Logger.getLogger("");
            StreamHandler handler = new StreamHandler(listener.getLogger(), new ComponentScanFormatter());
            handler.setLevel(Level.ALL);
            rootLog.addHandler(handler);
            //---------------------------

            try {
                shraga.createDependencyScan();
                dependencyScanCreated = true;
            } catch (CxClientException e) {
                log.error("Failed to create dependency scan.", e);
                scanResults.setOsaCreateException(e);
            } finally {
                handler.flush();
                rootLog.removeHandler(handler);
            }
        }

        if (config.getSastEnabled()) {
            try {
                shraga.createSASTScan();
                sastCreated = true;
            } catch (IOException | CxClientException e) {
                log.warn("Failed to create SAST scan: " + e.getMessage(), e);
                scanResults.setSastCreateException(e);
            }
        }
        if (sastCreated) {
            try {
                SASTResults sastResults = config.getSynchronous() ? shraga.waitForSASTResults() : shraga.getLatestSASTResults();
                scanResults.setSastResults(sastResults);
            } catch (InterruptedException e) {
                if (config.getSynchronous()) {
                    cancelScan(shraga);
                }
                throw e;

            } catch (CxClientException | IOException e) {
                log.error("Failed to get SAST scan results: " + e.getMessage());
                scanResults.setSastWaitException(e);
            }
        }

        if (dependencyScanCreated) {
            try {
                DependencyScanResults dsResults = config.getSynchronous() ?
                        shraga.waitForDependencyScanResults() :
                        shraga.getLatestDependencyScanResults();

                scanResults.setDependencyScanResults(dsResults);
            } catch (CxClientException e) {
                log.error("Failed to get dependency scan results: " + e.getMessage());
                scanResults.setOsaWaitException(e);
            }
        }

        if (config.getEnablePolicyViolations() && (scanResults.getDependencyScanResults() != null  || scanResults.getSastResults() != null)) {
            shraga.printIsProjectViolated();
        }

        return result;
    }

    private void cancelScan(CxShragaClient shraga) {
        try {
            shraga.cancelSASTScan();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void checkRoles(RoleChecker checker) throws SecurityException {

    }
}