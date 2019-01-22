package com.checkmarx.jenkins;

import com.checkmarx.jenkins.exception.CxCredException;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cx.restclient.common.ErrorMessage;

import hudson.model.Item;
import hudson.model.Run;
import org.apache.commons.lang.StringUtils;

import java.util.Collections;


//resolve between global or specific and username+pssd or credential manager
public class CxCred {

    private String serverUrl;
    private String username;
    private String pssd;

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPssd() {
        return pssd;
    }

    public void setPssd(String pssd) {
        this.pssd = pssd;
    }


    public static CxCred resolveCred(CxScanBuilder cxScanBuilder, CxScanBuilder.DescriptorImpl descriptor, Run<?, ?> run) {
        CxCred ret = new CxCred();
        if (cxScanBuilder.isUseOwnServerCredentials()) {
            ret.setServerUrl(cxScanBuilder.getServerUrl());
            if (StringUtils.isNotEmpty(cxScanBuilder.getCredentialsId())) {
                StandardUsernamePasswordCredentials c = CredentialsProvider.findCredentialById(cxScanBuilder.getCredentialsId(), StandardUsernamePasswordCredentials.class, run, Collections.<DomainRequirement>emptyList());
                ret.setUsername(c != null ? c.getUsername() : "");
                ret.setPssd(c != null ? c.getPassword().getPlainText() : "");
                return ret;

            } else {
                ret.setUsername(StringUtils.defaultString(cxScanBuilder.getUsername()));
                ret.setPssd(StringUtils.defaultString(cxScanBuilder.getPasswordPlainText()));
                return ret;
            }

        } else {
            ret.setServerUrl(descriptor.getServerUrl());
            if (StringUtils.isNotEmpty(descriptor.getCredentialsId())) {
                StandardUsernamePasswordCredentials c = CredentialsProvider.findCredentialById(descriptor.getCredentialsId(), StandardUsernamePasswordCredentials.class, run, Collections.<DomainRequirement>emptyList());
                ret.setUsername(c != null ? c.getUsername() : "");
                ret.setPssd(c != null ? c.getPassword().getPlainText() : "");
                return ret;

            } else {
                ret.setUsername(StringUtils.defaultString(descriptor.getUsername()));
                ret.setPssd(StringUtils.defaultString(descriptor.getPasswordPlainText()));
                return ret;
            }
        }
    }


    public static CxCred resolveCred(boolean useOwnServerCredentials, String serverUrl, String username, String pssd, String credId, CxScanBuilder.DescriptorImpl descriptor, Item item) throws CxCredException {

        CxCred ret = new CxCred();
        if (useOwnServerCredentials) {
            ret.setServerUrl(serverUrl);
            if (StringUtils.isNotEmpty(credId)) {

                StandardUsernamePasswordCredentials c = CredentialsMatchers.firstOrNull(CredentialsProvider.lookupCredentials(
                                StandardUsernamePasswordCredentials.class,
                                item,
                                null,
                                Collections.<DomainRequirement>emptyList()),
                        CredentialsMatchers.withId(credId));

                ret.setUsername(c != null ? c.getUsername() : "");
                ret.setPssd(c != null ? c.getPassword().getPlainText() : "");
                return ret;

            } else {
                ret.setUsername(StringUtils.defaultString(username));
                ret.setPssd(StringUtils.defaultString(pssd));
                return ret;
            }

        } else {
            ret.setServerUrl(descriptor.getServerUrl());
            if (StringUtils.isNotEmpty(descriptor.getCredentialsId())) {

                StandardUsernamePasswordCredentials c = CredentialsMatchers.firstOrNull(CredentialsProvider.lookupCredentials(
                        StandardUsernamePasswordCredentials.class,
                        item,
                        null,
                        Collections.<DomainRequirement>emptyList()),
                        CredentialsMatchers.withId(descriptor.getCredentialsId()));

                ret.setUsername(c != null ? c.getUsername() : "");
                ret.setPssd(c != null ? c.getPassword().getPlainText() : "");
                return ret;

            } else {
                ret.setUsername(StringUtils.defaultString(descriptor.getUsername()));
                ret.setPssd(StringUtils.defaultString(descriptor.getPasswordPlainText()));
                return ret;
            }
        }
    }

    public static void validateCxCredentials(CxCred credentials) throws CxCredException {
        if(StringUtils.isEmpty(credentials.getServerUrl()) ||
                StringUtils.isEmpty(credentials.getUsername()) ||
                StringUtils.isEmpty((credentials.getPssd()))){
            throw new CxCredException(ErrorMessage.CHECKMARX_SERVER_CONNECTION_FAILED.getErrorMessage());
        }
    }
}