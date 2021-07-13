package com.checkmarx.jenkins;

import com.cx.restclient.CxClientDelegator;
import com.cx.restclient.configuration.CxScanConfig;
import com.cx.restclient.exception.CxClientException;
import com.cx.restclient.sast.utils.LegacyClient;
import org.slf4j.Logger;

import java.net.MalformedURLException;

class CommonClientFactory {
    private static final String SCAN_ORIGIN = "Jenkins";

    static LegacyClient getInstance(CxConnectionDetails connDetails,
                                    boolean enableCertificateValidation,
                                    Logger log)
            throws MalformedURLException, CxClientException {
        CxScanConfig scanConfig = new CxScanConfig(connDetails.getServerUrl(),
                connDetails.getUsername(),
                Aes.decrypt(connDetails.getPassword(), connDetails.getUsername()),
                SCAN_ORIGIN,
                !enableCertificateValidation);

        if (connDetails.isProxy()) {
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
