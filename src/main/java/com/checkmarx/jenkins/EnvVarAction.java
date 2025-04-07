package com.checkmarx.jenkins;

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.EnvironmentContributingAction;
import hudson.model.Run;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

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

        add(cxPrefix + "CRITICAL", Integer.toString(cxScanResult.getCriticalCount()));
        add(cxPrefix + "HIGH", Integer.toString(cxScanResult.getHighCount()));
        add(cxPrefix + "MEDIUM", Integer.toString(cxScanResult.getMediumCount()));
        add(cxPrefix + "LOW", Integer.toString(cxScanResult.getLowCount()));
        add(cxPrefix + "INFO", Integer.toString(cxScanResult.getInfoCount()));
    }

    public void setCxSastResults(int critical, int high, int medium, int low, int info) {
        final String cxPrefix = "CXSAST_RESULTS_";

        add(cxPrefix + "CRITICAL", Integer.toString(critical));
        add(cxPrefix + "HIGH", Integer.toString(high));
        add(cxPrefix + "MEDIUM", Integer.toString(medium));
        add(cxPrefix + "LOW", Integer.toString(low));
        add(cxPrefix + "INFO", Integer.toString(info));
    }

    public void buildEnvironment(@Nonnull Run<?, ?> run, @Nonnull EnvVars env) {
        if (data != null) {
            env.putAll(data);
        }
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