package com.checkmarx.jenkins;

import com.cx.restclient.dto.ProxyConfig;
import hudson.ProxyConfiguration;
import jenkins.model.Jenkins;

class ProxyHelper {
    /**
     * Gets proxy settings defined globally for current Jenkins instance.
     *
     * @return Jenkins proxy settings converted to an internal object.
     */
    static ProxyConfig getProxyConfig() {
        ProxyConfig internalProxy = null;
        Jenkins instance = Jenkins.getInstance();

        // getInstance() is marked as @Nonnull, but it can still return null if we happen to execute this code
        // in a Jenkins agent.
        // noinspection ConstantConditions
        if (instance != null && instance.proxy != null) {
            ProxyConfiguration jenkinsProxy = instance.proxy;
            internalProxy = new ProxyConfig();
            internalProxy.setHost(jenkinsProxy.name);
            internalProxy.setPort(jenkinsProxy.port);
            internalProxy.setUsername(jenkinsProxy.getUserName());
            internalProxy.setPassword(jenkinsProxy.getPassword());
        }
        return internalProxy;
    }
}
