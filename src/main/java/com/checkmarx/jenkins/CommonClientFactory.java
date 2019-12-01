package com.checkmarx.jenkins;

import com.cx.restclient.CxShragaClient;
import com.cx.restclient.configuration.CxScanConfig;
import com.cx.restclient.dto.ProxyConfig;
import com.cx.restclient.exception.CxClientException;
import hudson.ProxyConfiguration;
import jenkins.model.Jenkins;
import org.slf4j.Logger;

import java.net.MalformedURLException;

class CommonClientFactory {
    private static final String SCAN_ORIGIN = "Jenkins";

    static CxShragaClient getInstance(CxCredentials credentials,
                                      boolean enableCertificateValidation,
                                      Logger log)
            throws MalformedURLException, CxClientException {
        CxScanConfig scanConfig = new CxScanConfig(credentials.getServerUrl(),
                credentials.getUsername(),
                credentials.getPassword(),
                SCAN_ORIGIN,
                !enableCertificateValidation);

        return getInstance(scanConfig, log);
    }

    static CxShragaClient getInstance(CxScanConfig config, Logger log)
            throws MalformedURLException, CxClientException {
        setProxy(config, log);
        return new CxShragaClient(config, log);
    }

    private static void setProxy(CxScanConfig config, Logger log) {
        Jenkins instance = Jenkins.getInstance();
        if (instance != null && instance.proxy != null) {
            ProxyConfiguration jenkinsProxy = instance.proxy;
            ProxyConfig internalProxy = new ProxyConfig();
            internalProxy.setHost(jenkinsProxy.name);
            internalProxy.setPort(jenkinsProxy.port);
            internalProxy.setUsername(jenkinsProxy.getUserName());
            internalProxy.setPassword(jenkinsProxy.getPassword());
            config.setProxyConfig(internalProxy);

            log.trace("Proxy host: " + internalProxy.getHost());
            log.trace("Proxy port: " + internalProxy.getPort());
            log.trace("Proxy user: " + internalProxy.getUsername());
            log.trace("Proxy password: *************");
        }
    }
}
