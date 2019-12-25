package com.checkmarx.jenkins;

import com.cx.restclient.CxShragaClient;
import com.cx.restclient.configuration.CxScanConfig;
import com.cx.restclient.exception.CxClientException;
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

        scanConfig.setProxyConfig(ProxyHelper.getProxyConfig());

        return getInstance(scanConfig, log);
    }

    static CxShragaClient getInstance(CxScanConfig config, Logger log)
            throws MalformedURLException, CxClientException {

        return new CxShragaClient(config, log);
    }
}
