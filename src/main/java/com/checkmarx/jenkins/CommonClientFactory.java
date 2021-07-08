package com.checkmarx.jenkins;

import com.cx.restclient.CxClientDelegator;
import com.cx.restclient.configuration.CxScanConfig;
import com.cx.restclient.exception.CxClientException;
import com.cx.restclient.sast.utils.LegacyClient;
import org.slf4j.Logger;

import java.net.MalformedURLException;

class CommonClientFactory {
    private static final String SCAN_ORIGIN = "Jenkins";

    static LegacyClient getInstance(CxCredentials credentials,
                                    boolean enableCertificateValidation,
                                    Logger log, boolean isProxy)
            throws MalformedURLException, CxClientException {
        CxScanConfig scanConfig = new CxScanConfig(credentials.getServerUrl(),
                credentials.getUsername(),
                Aes.decrypt(credentials.getPassword(), credentials.getUsername()),
                SCAN_ORIGIN,
                !enableCertificateValidation);

        if (isProxy) {
            scanConfig.setProxyConfig(ProxyHelper.getProxyConfig());
        } else {
            scanConfig.setProxy(false);
        }

        return getInstance(scanConfig, log);
    }

    static LegacyClient getInstance(CxScanConfig config, Logger log)
            throws MalformedURLException, CxClientException {
        return new LegacyClient(config, log) {
        };
    }

    static CxClientDelegator getClientDelegatorInstance(CxScanConfig config, Logger log)
            throws MalformedURLException, CxClientException {
        return new CxClientDelegator(config, log);
    }
}
