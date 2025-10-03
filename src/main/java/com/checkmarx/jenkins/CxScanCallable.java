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
import com.cx.restclient.sast.dto.SASTResults;
import com.cx.restclient.sast.dto.CxXMLResults;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CxScanCallable implements FilePath.FileCallable<RemoteScanInfo>, Serializable {

    private static final long serialVersionUID = 1L;

    private static final Semaphore semaphore = new Semaphore(1);

    private final CxScanConfig config;
    private final TaskListener listener;
    private ProxyConfiguration jenkinsProxy = null;
    private boolean hideDebugLogs;
    private Map<String, String> fsaVars;


    public CxScanCallable(CxScanConfig config, TaskListener listener, boolean hideDebugLogs, Map<String, String> fsaVars) {
        this.config = config;
        this.listener = listener;
        this.hideDebugLogs = hideDebugLogs;
        this.fsaVars = fsaVars;
    }

    public CxScanCallable(CxScanConfig config, TaskListener listener, ProxyConfiguration jenkinsProxy,
                          boolean hideDebugLogs, Map<String, String> fsaVars) {
        this.config = config;
        this.listener = listener;
        this.jenkinsProxy = jenkinsProxy;
        this.hideDebugLogs = hideDebugLogs;
        this.fsaVars = fsaVars;
    }

    @Override
    public RemoteScanInfo invoke(File file, VirtualChannel channel) throws IOException, InterruptedException {
        CxLoggerAdapter log = new CxLoggerAdapter(listener.getLogger());
        if (hideDebugLogs) {
            log.setDebugEnabled(false);
            log.setTraceEnabled(false);
        } else {
            log.setDebugEnabled(true);
            log.setTraceEnabled(true);
        }

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
        OsaConsoleHandler handler = null;
        ScanResults createScanResults;
        try {
            semaphore.acquire();
            if (config.isOsaEnabled()) {
                setFsaConfiguration();
                //---------------------------
                //we do this in order to redirect the logs from the filesystem agent component to the build console
                rootLog = Logger.getLogger("");
                handler = new OsaConsoleHandler();
                handler.setLevel(Level.ALL);
                handler.setFormatter(new ComponentScanFormatter());
                rootLog.addHandler(handler);
                //---------------------------
            }

            createScanResults = delegator.initiateScan();
            results.add(createScanResults);

            if (rootLog != null) {
                handler.flush();
                rootLog.removeHandler(handler);
            }
        } finally {
            semaphore.release();
        }
        
        ScanResults scanResults = config.getSynchronous() ? delegator.waitForScanResults() : delegator.getLatestScanResults();
        SASTResults sast = scanResults.getSastResults();
        if (sast != null && sast.getQueryList() != null) {
            List<CxXMLResults.Query> filteredQueries = new ArrayList<>();

            for (CxXMLResults.Query q : new ArrayList<>(sast.getQueryList())) {
                if (q.getResult() != null) {
                    q.getResult().removeIf(r -> "1".equals(String.valueOf(r.getState())));
                }

                if (q.getResult() == null || q.getResult().isEmpty()) {
                    log.info("Skipping query (0 after NE filter): " + q.getName());
                    continue;
                }

                filteredQueries.add(q);
            }

            sast.setQueryList(filteredQueries);
        }
        results.add(scanResults);
        if (config.getSynchronous() && config.isSastEnabled() &&
                ((createScanResults.getSastResults() != null && createScanResults.getSastResults().getException() != null && createScanResults.getSastResults().getScanId() > 0) || (scanResults.getSastResults() != null && scanResults.getSastResults().getException() != null))) {
            cancelScan(delegator);
        }
        if (((config.isSastEnabled()||config.isOsaEnabled()) && config.getEnablePolicyViolations()) || (config.isAstScaEnabled() && config.getEnablePolicyViolationsSCA())) {
            delegator.printIsProjectViolated(scanResults);
        }
        ScanResults finalScanResults = getFinalScanResults(results);
        result.setScanResults(finalScanResults);
        
    	if(config.getCxVersion()!=null) {
    		result.setVersion(config.getCxVersion().getVersion());
    		result.setHotFix(config.getCxVersion().getHotFix());
    		result.setEnginePackVersion(config.getCxVersion().getEnginePackVersion());
    	}
        return result;
    }

    private void setFsaConfiguration() {
        for (Map.Entry<String, String> entry : fsaVars.entrySet()) {
            System.setProperty(entry.getKey(), entry.getValue());
        }
    }

    class OsaConsoleHandler extends ConsoleHandler {
        protected void setOutputStream(OutputStream out) throws SecurityException {
            super.setOutputStream(listener.getLogger());
        }
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