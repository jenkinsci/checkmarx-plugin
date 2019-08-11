package com.checkmarx.jenkins.action;

import com.cx.restclient.sast.dto.SASTResults;
import hudson.model.InvisibleAction;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean
public class CxScanSastResultWrapper extends InvisibleAction {
    @Exported
    public long scanId;

    @Exported
    public boolean sastResultsReady = false;

    @Exported
    public int high = 0;

    @Exported
    public int medium = 0;

    @Exported
    public int low = 0;

    @Exported
    public int information = 0;

    @Exported
    public int newHigh = 0;

    @Exported
    public int newMedium = 0;

    @Exported
    public int newLow = 0;

    @Exported
    public int newInfo = 0;

    @Exported
    public String sastScanLink;

    @Exported
    public String sastProjectLink;

    @Exported
    public String scanStart;

    @Exported
    public String scanTime;

    @Exported
    public String scanStartTime;

    @Exported
    public String scanEndTime;

    @Exported
    public String filesScanned;

    @Exported
    public String LOC;

    public CxScanSastResultWrapper(SASTResults results) {
        scanId = results.getScanId();
        sastResultsReady = results.isSastResultsReady();

        high = results.getHigh();
        medium = results.getMedium();
        low = results.getLow();
        information = results.getInformation();

        newHigh = results.getNewHigh();
        newMedium = results.getNewMedium();
        newLow = results.getNewLow();
        newInfo = results.getNewInfo();

        sastProjectLink = results.getSastProjectLink();
        sastScanLink = results.getSastScanLink();

        scanStart = results.getScanStart();
        scanStartTime = results.getScanStartTime();
        scanEndTime = results.getScanEndTime();
        scanTime = results.getScanTime();

        filesScanned = results.getFilesScanned();
        LOC = results.getLOC();
    }
}
