package com.checkmarx.jenkins;

import com.checkmarx.jenkins.exception.CxCredException;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cx.restclient.common.ErrorMessage;
import hudson.model.Item;
import hudson.model.Run;
import hudson.util.Secret;
import org.apache.commons.lang.StringUtils;

import java.util.Collections;
import java.util.List;


//resolve between global or specific and username+pssd or credential manager
public class CxCredentials {

    private String serverUrl;
    private String username;
    private String encryptedPassword;

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

    public String getPassword() {
        return encryptedPassword;
    }

    public void setPassword(String encryptedPassword) {
        this.encryptedPassword = encryptedPassword;
    }

    public static CxCredentials resolveCred(CxScanBuilder cxScanBuilder, CxScanBuilder.DescriptorImpl descriptor, Run<?, ?> run) {
        CxCredentials ret = new CxCredentials();
        cxScanBuilder.setGenerateXmlReport((cxScanBuilder.getGenerateXmlReport() == null) ? true : cxScanBuilder.getGenerateXmlReport());
        if (cxScanBuilder.isUseOwnServerCredentials()) {
            ret.setServerUrl(cxScanBuilder.getServerUrl());
            if (StringUtils.isNotEmpty(cxScanBuilder.getCredentialsId())) {
                UsernamePasswordCredentials c = getCredentialsById(cxScanBuilder.getCredentialsId(), run);
                ret.setUsername(c != null ? c.getUsername() : "");
                ret.setPassword(c != null ? Aes.encrypt(c.getPassword().getPlainText(), ret.getUsername()) : "");
                return ret;

            } else {
                ret.setUsername(StringUtils.defaultString(cxScanBuilder.getUsername()));
                ret.setPassword(Aes.encrypt(StringUtils.defaultString(cxScanBuilder.getPasswordPlainText()), ret.getUsername()));
                return ret;
            }

        } else {
            ret.setServerUrl(descriptor.getServerUrl());
            if (StringUtils.isNotEmpty(descriptor.getCredentialsId())) {
                UsernamePasswordCredentials c = getCredentialsById(descriptor.getCredentialsId(), run);
                ret.setUsername(c != null ? c.getUsername() : "");
                ret.setPassword(c != null ? Aes.encrypt(c.getPassword().getPlainText(), ret.getUsername()) : "");
                return ret;

            } else {
                ret.setUsername(StringUtils.defaultString(descriptor.getUsername()));
                ret.setPassword(Aes.encrypt(StringUtils.defaultString(descriptor.getPasswordPlainText()), ret.getUsername()));
                return ret;
            }
        }
    }


    public static CxCredentials resolveCred(boolean useOwnServerCredentials, String serverUrl, String username, String pssd, String credId, CxScanBuilder.DescriptorImpl descriptor, Item item) throws CxCredException {

        CxCredentials ret = new CxCredentials();
        if (useOwnServerCredentials) {
            ret.setServerUrl(serverUrl);
            if (StringUtils.isNotEmpty(credId)) {
                UsernamePasswordCredentials c = getCredentialsById(credId, item);
                ret.setUsername(c != null ? c.getUsername() : "");
                ret.setPassword(c != null ? Aes.encrypt(c.getPassword().getPlainText(), ret.getUsername()) : "");
                return ret;

            } else {
                ret.setUsername(StringUtils.defaultString(username));
                ret.setPassword(Aes.encrypt(StringUtils.defaultString(pssd), ret.getUsername()));
                return ret;
            }

        } else {
            ret.setServerUrl(descriptor.getServerUrl());
            if (StringUtils.isNotEmpty(descriptor.getCredentialsId())) {
                UsernamePasswordCredentials c = getCredentialsById(descriptor.getCredentialsId(), item);
                ret.setUsername(c != null ? c.getUsername() : "");
                ret.setPassword(c != null ? Aes.encrypt(c.getPassword().getPlainText(), ret.getUsername()) : "");
                return ret;

            } else {
                ret.setUsername(StringUtils.defaultString(descriptor.getUsername()));
                ret.setPassword(Aes.encrypt(StringUtils.defaultString(descriptor.getPasswordPlainText()), ret.getUsername()));
                return ret;
            }
        }
    }

    static UsernamePasswordCredentials getCredentialsById(String credentialsId, Run run) {
        return CredentialsProvider.findCredentialById(
                credentialsId,
                StandardUsernamePasswordCredentials.class,
                run,
                Collections.emptyList());
    }

    static UsernamePasswordCredentials getCredentialsById(String credentialsId, Item item) {
        List<StandardUsernamePasswordCredentials> credentials = CredentialsProvider.lookupCredentials(
                StandardUsernamePasswordCredentials.class,
                item,
                null,
                Collections.emptyList());

        return CredentialsMatchers.firstOrNull(credentials, CredentialsMatchers.withId(credentialsId));
    }

    public static void validateCxCredentials(CxCredentials credentials) throws CxCredException {
        if(StringUtils.isEmpty(credentials.getServerUrl()) ||
                StringUtils.isEmpty(credentials.getUsername()) ||
                StringUtils.isEmpty((Aes.decrypt(credentials.getPassword(), credentials.getUsername())))){
            throw new CxCredException(ErrorMessage.CHECKMARX_SERVER_CONNECTION_FAILED.getErrorMessage());
        }
    }
}