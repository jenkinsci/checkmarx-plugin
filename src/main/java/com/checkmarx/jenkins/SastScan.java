package com.checkmarx.jenkins;

import com.checkmarx.jenkins.web.contracts.ProjectContract;
import com.checkmarx.ws.CxJenkinsWebService.CliScanArgs;
import com.checkmarx.ws.CxJenkinsWebService.CxWSBasicRepsonse;
import com.checkmarx.ws.CxJenkinsWebService.CxWSResponseRunID;
import hudson.AbortException;
import hudson.FilePath;

/**
 * Created by tsahib on 7/6/2016.
 */
public class SastScan {

    private CxWebService cxWebService;
    private ProjectContract projectContract;
    private final CliScanArgs cliScanArgs;

    public SastScan(CxWebService cxWebService, CliScanArgs cliScanArgs, ProjectContract projectContract){
        this.cliScanArgs = cliScanArgs;
        this.cxWebService = cxWebService;
        this.projectContract = projectContract;
    }

    public CxWSResponseRunID scan(String groupId, FilePath zipFile, boolean isThisBuildIncremental) throws AbortException {
        CxWSResponseRunID cxWSResponseRunId;
        boolean isNewProject = projectContract.newProject(cliScanArgs.getPrjSettings().getProjectName(), groupId);
        if (isNewProject){
            cxWSResponseRunId = cxWebService.createAndRunProject(cliScanArgs.getPrjSettings(),
                    cliScanArgs.getSrcCodeSettings().getPackagedCode(), true, true, zipFile, cliScanArgs.getComment());
        } else {
            updateProjectId();
            if (isThisBuildIncremental) {
                cxWSResponseRunId = cxWebService.runIncrementalScan(cliScanArgs.getPrjSettings(), cliScanArgs.getSrcCodeSettings()
                        .getPackagedCode(), true, true, zipFile, cliScanArgs.getComment());
            } else {
                cxWSResponseRunId = cxWebService.runScanAndAddToProject(cliScanArgs.getPrjSettings(), cliScanArgs.getSrcCodeSettings().getPackagedCode(), true, true, zipFile, cliScanArgs.getComment());
            }
        }
        return  cxWSResponseRunId;
    }


    private void updateProjectId() throws AbortException {
        if (cliScanArgs.getPrjSettings().getProjectID() == 0){
            long projectId = cxWebService.getProjectId(cliScanArgs.getPrjSettings());
            cliScanArgs.getPrjSettings().setProjectID(projectId);
        }
    }
}
