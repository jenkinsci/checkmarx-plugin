package com.checkmarx.jenkins;

import com.checkmarx.jenkins.exception.CxCredException;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cx.restclient.common.ErrorMessage;
import hudson.model.Item;
import hudson.model.Run;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
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
            return getCxCredentials(run, ret, cxScanBuilder.getCredentialsId(), cxScanBuilder.getUsername(), cxScanBuilder.getPasswordPlainText());

        } else {
            ret.setServerUrl(descriptor.getServerUrl());
            return getCxCredentials(run, ret, descriptor.getCredentialsId(), descriptor.getUsername(), descriptor.getPasswordPlainText());
        }
    }

    @NotNull
    private static CxCredentials getCxCredentials(Run<?, ?> run, CxCredentials ret, String credentialsId, String username, String passwordPlainText) {
        return getCxCredentials(username, passwordPlainText, credentialsId, ret, getCredentialsById(credentialsId, run));
    }


    public static CxCredentials resolveCred(boolean useOwnServerCredentials, String serverUrl, String username, String pssd, String credId, CxScanBuilder.DescriptorImpl descriptor, Item item) throws CxCredException {

        CxCredentials ret = new CxCredentials();
        if (useOwnServerCredentials) {
            ret.setServerUrl(serverUrl);
            return getCxCredentials(username, pssd, credId, ret, getCredentialsById(credId, item));

        } else {
            ret.setServerUrl(descriptor.getServerUrl());
            return getCxCredentials(descriptor.getUsername(), descriptor.getPasswordPlainText(), descriptor.getCredentialsId(), ret, getCredentialsById(descriptor.getCredentialsId(), item));
        }
    }

    @NotNull
    private static CxCredentials getCxCredentials(String username, String pssd, String credId, CxCredentials ret, UsernamePasswordCredentials credentialsById) {
        if (StringUtils.isNotEmpty(credId)) {
            UsernamePasswordCredentials c = credentialsById;
            ret.setUsername(c != null ? c.getUsername() : "");
            ret.setPassword(c != null ? Aes.encrypt(c.getPassword().getPlainText(), ret.getUsername()) : "");
            return ret;

        } else {
            ret.setUsername(StringUtils.defaultString(username));
            ret.setPassword(Aes.encrypt(StringUtils.defaultString(pssd), ret.getUsername()));
            return ret;
        }
    }

    static UsernamePasswordCredentials getCredentialsById(String credentialsId, Run<?, ?> run) {
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