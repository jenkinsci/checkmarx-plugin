package com.checkmarx.jenkins;

import com.checkmarx.jenkins.web.contracts.ProjectContract;
import com.checkmarx.ws.CxJenkinsWebService.ArrayOfCxWSResponseScanStatus;
import com.checkmarx.ws.CxJenkinsWebService.CxWSResponseScanStatus;
import com.checkmarx.ws.CxJenkinsWebService.CxWSResponseScanStatusArray;
import hudson.AbortException;
import mockit.Mock;
import mockit.MockUp;
import org.junit.Test;


import javax.ws.rs.WebApplicationException;
import java.net.MalformedURLException;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Created by tsahib on 7/7/2016.
 */
public class ProjectContractTests {

    @Test
    public void projectHasQueuedScans_noScans_noQueuedScans() throws AbortException, MalformedURLException {
        new MockUp<CxWebService>() {
            @Mock
            void $init(String serverUrl){

            }
            @Mock
            CxWSResponseScanStatusArray getQueuedScans() {
                CxWSResponseScanStatusArray arr = new CxWSResponseScanStatusArray();
                arr.setIsSuccesfull(true);
                return arr;
            }
        };
        ProjectContract projectContract = new ProjectContract(new CxWebService(""));
        boolean result = projectContract.projectHasQueuedScans(2);
        assertFalse(result);
    }

    @Test(expected=AbortException.class)
    public void projectHasQueuedScans_resultNotSuccessful_exceptionThrown() throws AbortException, MalformedURLException {
        new MockUp<CxWebService>() {
            @Mock
            void $init(String serverUrl){

            }
            @Mock
            CxWSResponseScanStatusArray getQueuedScans() {
                CxWSResponseScanStatusArray arr = new CxWSResponseScanStatusArray();
                return arr;
            }
        };
        ProjectContract projectContract = new ProjectContract(new CxWebService(""));
        boolean result = projectContract.projectHasQueuedScans(1);
    }

    @Test
    public void projectHasQueuedScans_hasQueuedScans_returnTrue() throws AbortException, MalformedURLException {
        new MockUp<CxWebService>() {
            @Mock
            void $init(String serverUrl){
            }
            @Mock
            CxWSResponseScanStatusArray getQueuedScans() {
                CxWSResponseScanStatusArray arr = new CxWSResponseScanStatusArray();
                arr.setIsSuccesfull(true);
                arr.setStatusArr(new ArrayOfCxWSResponseScanStatus());
                return arr;
            }
        };
        new MockUp<ProjectContract>() {
            @Mock
            boolean projectHasQueuedScans(long projectId, List<CxWSResponseScanStatus> scanStatuses) {
                return true;
            }
        };

        ProjectContract projectContract = new ProjectContract(new CxWebService(""));
        boolean result = projectContract.projectHasQueuedScans(2);
        assertTrue(result);
    }

    @Test
    public void projectHasQueuedScans_noQueuedScans_returnFalse() throws AbortException, MalformedURLException {
        new MockUp<CxWebService>() {
            @Mock
            void $init(String serverUrl){
            }
            @Mock
            CxWSResponseScanStatusArray getQueuedScans() {
                CxWSResponseScanStatusArray arr = new CxWSResponseScanStatusArray();
                arr.setIsSuccesfull(true);
                arr.setStatusArr(new ArrayOfCxWSResponseScanStatus());
                return arr;
            }
        };
        new MockUp<ProjectContract>() {
            @Mock
            boolean projectHasQueuedScans(long projectId, List<CxWSResponseScanStatus> scanStatuses) {
                return false;
            }
        };

        ProjectContract projectContract = new ProjectContract(new CxWebService(""));
        boolean result = projectContract.projectHasQueuedScans(3);
        assertFalse(result);
    }

    @Test
    public void projectHasQueuedScans_newProject_returnFalse() throws AbortException, MalformedURLException {
        new MockUp<CxWebService>() {
            @Mock
            void $init(String serverUrl){
            }
            @Mock
            CxWSResponseScanStatusArray getQueuedScans() {
                CxWSResponseScanStatusArray arr = new CxWSResponseScanStatusArray();
                arr.setIsSuccesfull(true);
                arr.setStatusArr(new ArrayOfCxWSResponseScanStatus());
                return arr;
            }
        };
        new MockUp<ProjectContract>() {
            @Mock
            boolean projectHasQueuedScans(long projectId, List<CxWSResponseScanStatus> scanStatuses) {
                return false;
            }
        };

        ProjectContract projectContract = new ProjectContract(new CxWebService(""));
        boolean result = projectContract.projectHasQueuedScans(0);
        assertFalse(result);
    }
}
