package com.checkmarx.jenkins.action;

import com.cx.restclient.osa.dto.OSAResults;
import hudson.model.InvisibleAction;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean
public class CxScanOsaResultWrapper extends InvisibleAction {
    @Exported
    public String osaScanId;

    @Exported
    public String osaProjectSummaryLink;

    @Exported
    public boolean osaResultsReady = false;

    @Exported
    public String scanStartTime;

    @Exported
    public String scanEndTime;

    public CxScanOsaResultWrapper(OSAResults results){
        osaScanId = results.getOsaScanId();
        osaProjectSummaryLink = results.getOsaProjectSummaryLink();
        osaResultsReady = results.isOsaResultsReady();
        scanStartTime = results.getScanStartTime();
        scanEndTime = results.getScanEndTime();
    }
}
