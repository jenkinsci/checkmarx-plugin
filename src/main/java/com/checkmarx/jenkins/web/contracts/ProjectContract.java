package com.checkmarx.jenkins.web.contracts;

import com.checkmarx.jenkins.CxWebService;
import com.checkmarx.ws.CxJenkinsWebService.CurrentStatusEnum;
import com.checkmarx.ws.CxJenkinsWebService.CxWSBasicRepsonse;
import com.checkmarx.ws.CxJenkinsWebService.CxWSResponseScanStatus;
import com.checkmarx.ws.CxJenkinsWebService.CxWSResponseScanStatusArray;
import hudson.AbortException;

import java.util.List;

/**
 * Created by tsahib on 7/4/2016.
 */
public class ProjectContract {

    private CxWebService cxWebService;
    public ProjectContract(CxWebService cxWebService){
        this.cxWebService = cxWebService;
    }

    public boolean projectHasQueuedScans(long projectId) throws AbortException {
        CxWSResponseScanStatusArray res = cxWebService.getQueuedScans();
        if (!res.isIsSuccesfull()){
            String message = "Checking if project has queued scans failed: \n" + res.getErrorMessage();
            throw new AbortException(message);
        }

        if (res.getStatusArr() == null || res.getStatusArr().getCxWSResponseScanStatus() == null) return false;

        boolean projectHasQueuedScans = projectHasQueuedScans(projectId, res.getStatusArr().getCxWSResponseScanStatus());
        return  projectHasQueuedScans;
    }

    public boolean newProject(String projectName, String groupId){
        final CxWSBasicRepsonse validateProjectResponse = cxWebService.validateProjectName(projectName, groupId);
        return validateProjectResponse.isIsSuccesfull();
    }

    private boolean projectHasQueuedScans(long projectId, List<CxWSResponseScanStatus> scanStatuses){
        for (CxWSResponseScanStatus status : scanStatuses){
            if (scanStatusToAvoid(status.getCurrentStatus()) && status.getProjectId() == projectId){
                return true;
            }
        }
        return false;
    }

    private boolean scanStatusToAvoid(CurrentStatusEnum status){
         switch (status)
         {
             case QUEUED:
             case WAITING_TO_PROCESS:
             case WORKING:
             case UNZIPPING:
                 return true;
         }
        return false;
    }
}
