package com.checkmarx.jenkins.utils;

import com.checkmarx.jenkins.CxScanBuilder;
import com.checkmarx.jenkins.logger.CxPluginLogger;
import hudson.AbortException;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

/**
 * Handles configuring Proxy for Checkmarx server connections
 * 
 * @author randy@checkmarx.com
 * @since 08/2/18
 */
public class CxProxyUtility {
    
    private static final ProxySelector DEFAULT_PROXY = ProxySelector.getDefault();
    
    public static void configureProxy(@NotNull String cxUrl,
                @NotNull CxPluginLogger logger) throws AbortException {
        
        CxScanBuilder.DescriptorImpl descriptor = (CxScanBuilder.DescriptorImpl) 
                Jenkins.getInstance().getDescriptor(CxScanBuilder.class);
        if (descriptor != null && descriptor.isUseProxy()) {
            configureProxy(cxUrl, true,
                    descriptor.getProxyHost(), 
                    descriptor.getProxyPort(),
                    logger);
        } else {
            // set default proxy in case Cx proxy was removed.
            setDefaultProxy(logger);
        }
    }
    
    public static void configureProxy(@NotNull String cxUrl,
                boolean useProxy, String proxyHost, Integer proxyPort,
                @NotNull CxPluginLogger logger) throws AbortException {
        if (useProxy) {
            if (StringUtils.isEmpty(proxyHost))
                throw new IllegalArgumentException("Proxy host cannot be empty");
            
            final ProxySelector cxProxy = new CxProxySelector(
                    DEFAULT_PROXY,
                    cxUrl,
                    proxyHost,
                    (proxyPort == null) ? 80 : proxyPort,
                    logger
            );
            ProxySelector.setDefault(cxProxy);
            logger.info("Checkmarx proxy configured");
        } else {
            // set default proxy in case Cx proxy was removed.
            setDefaultProxy(logger);
        }
    }

    private static void setDefaultProxy(CxPluginLogger logger) {
            logger.info("No proxy configured for Checkmarx");
            ProxySelector.setDefault(DEFAULT_PROXY);
    }
    
    public static class CxProxySelector extends ProxySelector {
        
        private transient CxPluginLogger logger;
        
        private final ProxySelector defaultProxy;
        private final URL cxUrl;
        private final String proxyHost;
        private final int proxyPort;
        private final List<Proxy> proxyList = new ArrayList<Proxy>();
        private final Proxy cxProxy;
        
        public CxProxySelector(@NotNull String cxUrl,
                @NotNull String proxyHost, int proxyPort, 
                @NotNull CxPluginLogger logger) throws AbortException {
            this(ProxySelector.getDefault(), cxUrl, proxyHost, proxyPort, logger);
        }

        public CxProxySelector(@NotNull ProxySelector defaultProxy,
                @NotNull String cxUrl,
                @NotNull String proxyHost, int proxyPort, 
                @NotNull CxPluginLogger logger) throws AbortException {
            
            this.defaultProxy = defaultProxy;
            this.cxUrl = initUrl(cxUrl);
            this.cxProxy = initProxy(proxyHost, proxyPort);
            this.proxyList.add(cxProxy);
            this.proxyHost = proxyHost;
            this.proxyPort = proxyPort;
            this.logger = logger;
            
            logger.info(String.format("Configuring proxy for Checkmarx: url=%s; proxy=%s:%d", 
                    cxUrl, proxyHost, proxyPort));
        }
        
        private URL initUrl(String cxUrl) throws AbortException {
            try {
                return new URL(cxUrl);
            } catch (MalformedURLException ex) {
                final String msg = "Checkmarx server url is malformed.  Correct and retry.";
                logger.error(msg, ex);
                throw new AbortException(msg);
            }
        }

        private Proxy initProxy(String proxyHost, int proxyPort) throws AbortException {
            
            try {
                final InetAddress addr = InetAddress.getByName(proxyHost);
                final SocketAddress sa = new InetSocketAddress(addr, proxyPort);
                return new Proxy(Type.HTTP, sa);
            } catch (Exception ex) {
                final String msg = "Proxy address is malformed.  Correct and retry.";
                logger.error(msg, ex);
                throw new AbortException(msg);
            }
        }
        
        @Override
        public List<Proxy> select(URI uri) {
            if (uri == null) {
                throw new IllegalArgumentException("URI can't be null.");
            }
            //logger.info("Selecting proxy for uri: " + uri.toString());
            
            if (uriMatchesCx(uri)) {
                //logger.info(String.format("Using proxy for Checkmarx: %s:%d", proxyHost, proxyPort));
                return proxyList;
            }
            return defaultProxy.select(uri);
        }

        @Override
        public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
            final String msg = 
                    String.format("Connection could not be established to Checkmarx proxy: %s:%d",
                            this.proxyHost, this.proxyPort);
            logger.error(msg, ioe);
            throw new RuntimeException(msg, ioe);
        }

        private boolean uriMatchesCx(URI uri) {
            return uri.getHost().equalsIgnoreCase(cxUrl.getHost());
        }

    }
    
}
