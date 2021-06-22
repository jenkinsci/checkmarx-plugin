package com.checkmarx.jenkins;

import com.cx.restclient.CxClientDelegator;
import com.cx.restclient.configuration.CxScanConfig;
import com.cx.restclient.dto.ProxyConfig;
import com.cx.restclient.dto.Results;
import com.cx.restclient.dto.ScanResults;
import com.cx.restclient.dto.ScannerType;
import com.cx.restclient.exception.CxClientException;
import hudson.FilePath;
import hudson.ProxyConfiguration;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import org.jenkinsci.remoting.RoleChecker;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        if (jenkinsProxy != null) {
            config.setProxyConfig(new ProxyConfig(jenkinsProxy.name, jenkinsProxy.port,
                    jenkinsProxy.getUserName(), jenkinsProxy.getPassword(), false));
            log.debug("Proxy configuration:");
            log.debug("Proxy host: " + jenkinsProxy.name);
            log.debug("Proxy port: " + jenkinsProxy.port);
            log.debug("Proxy user: " + jenkinsProxy.getUserName());
            log.debug("Proxy password: *************");
        }

        RemoteScanInfo result = new RemoteScanInfo();
        CxClientDelegator delegator = null;
        List<ScanResults> results = new ArrayList<>();

        try {
            delegator = CommonClientFactory.getClientDelegatorInstance(config, log);
            ScanResults initResults = delegator.init();
            results.add(initResults);

            // Make sure CxARMUrl is passed in the result.
            // Cannot pass CxARMUrl in the config object, because this callable can be executed on a Jenkins agent.
            // On a Jenkins agent we'll get a cloned config instead of the original object reference.
            result.setCxARMUrl(config.getCxARMUrl());
        } catch (Exception ex) {
            ScanResults scanResults = new ScanResults();
            scanResults.setGeneralException(ex);
            result.setScanResults(scanResults);

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

        ScanResults createScanResults = delegator.initiateScan();
        results.add(createScanResults);

        if (rootLog != null) {
            handler.flush();
            rootLog.removeHandler(handler);
        }

        ScanResults scanResults = config.getSynchronous() ? delegator.waitForScanResults() : delegator.getLatestScanResults();
        results.add(scanResults);

        if (config.getSynchronous() && config.isSastEnabled() &&
                ((createScanResults.getSastResults() != null && createScanResults.getSastResults().getException() != null && createScanResults.getSastResults().getScanId() > 0) || (scanResults.getSastResults() != null && scanResults.getSastResults().getException() != null))) {
            cancelScan(delegator);
        }

        if (config.getEnablePolicyViolations()) {
            delegator.printIsProjectViolated(scanResults);
        }

        ScanResults finalScanResults = getFinalScanResults(results);
        result.setScanResults(finalScanResults);
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

    private ScanResults getFinalScanResults(List<ScanResults> results) {
        ScanResults scanResults = new ScanResults();

        for (int i = 0; i < results.size(); i++) {
            Map<ScannerType, Results> resultsMap = results.get(i).getResults();
            for (Map.Entry<ScannerType, Results> entry : resultsMap.entrySet()) {
                if (entry != null && entry.getValue() != null && entry.getValue().getException() != null && scanResults.get(entry.getKey()) == null) {
                    scanResults.put(entry.getKey(), entry.getValue());
                }
                if (i == results.size() - 1 && entry != null && entry.getValue() != null && entry.getValue().getException() == null) {
                    scanResults.put(entry.getKey(), entry.getValue());
                }
            }
        }

        return scanResults;
    }
}