package com.checkmarx.jenkins;

import java.util.HashMap;
import java.util.Map;

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.EnvironmentContributingAction;

/**
 * This class is used to inject the scan results into the build environment as a
 * variable so that it can be used elsewhere
 */
public class EnvVarAction implements EnvironmentContributingAction {
    // Decided not to record this data in build.xml, so marked transient:
    private transient Map<String, String> data = new HashMap<String, String>();

    private void add(String key, String val) {
        data.put(key, val);
    }


    public void setCxSastResults(CxScanResult cxScanResult) {
        final String cxPrefix = "CXSAST_RESULTS_";

        add(cxPrefix + "HIGH", Integer.toString(cxScanResult.getHighCount()));
        add(cxPrefix + "MEDIUM", Integer.toString(cxScanResult.getMediumCount()));
        add(cxPrefix + "LOW", Integer.toString(cxScanResult.getLowCount()));
        add(cxPrefix + "INFO", Integer.toString(cxScanResult.getInfoCount()));
    }

    public void buildEnvVars(AbstractBuild<?, ?> build, EnvVars env) {
        env.putAll(data);
    }

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return null;
    }

    public String getUrlName() {
        return null;
    }
}
