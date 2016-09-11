package com.checkmarx.jenkins;

import com.checkmarx.jenkins.cryptography.CryptographicCallable;
import com.checkmarx.jenkins.filesystem.FolderPattern;
import com.checkmarx.jenkins.filesystem.FoldersScanner;
import com.checkmarx.jenkins.filesystem.zip.CxZip;
import com.checkmarx.jenkins.opensourceanalysis.*;
import com.checkmarx.jenkins.web.client.RestClient;
import com.checkmarx.jenkins.web.model.ScanRequest;
import com.checkmarx.jenkins.web.model.GetOpenSourceSummaryRequest;
import com.checkmarx.jenkins.web.model.getOpenSourceSummaryResponse;
import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import mockit.Mock;
import mockit.MockUp;
import mockit.integration.junit4.JMockit;

import org.apache.log4j.Logger;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.*;

import static org.junit.Assert.*;

/**
 * @author tsahi
 * @since 02/02/16
 */
@RunWith(JMockit.class)
public class OpenSourceAnalyzerServiceTests {

    @Test
    public void analyze_noIncludes_nothingShouldHappen() throws IOException, InterruptedException {

        new MockUp<FoldersScanner>() {
            @Mock(invocations = 0)
            void $init(List<String> libIncludes, List<String> libExcludes) {
            }
        };

        DependencyFolder folders = new DependencyFolder("", "test");
        OpenSourceAnalyzerService service = new OpenSourceAnalyzerService(null, folders, null, 0, Logger.getLogger(getClass()), null, null, null);
        service.analyze();
    }
    
    @Test
    public void analyze_withDependencies_NoError() throws IOException, InterruptedException {

        new MockUp<FoldersScanner>() {
        };
        new MockUp<Logger>() {
            void info(Object message) {
            }
        };
        new MockUp<CryptographicCallable>() {
            @Mock
            String invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
                return "a94a8fe5ccb19ba61c4c0873d391e987982fbbd3";
            }
        };
        new MockUp<RestClient>() {
            @Mock
            URI createScan(ScanRequest request) {
                return null;
            }
            @Mock
            void waitForScanToFinish(URI uri){
            }
        };
        new MockUp<CxWebService>() {
            @Mock
            void $init(String serverUrl) {
            }
            @Mock
            Boolean isOsaLicenseValid(){
                return true;
            }
        };
        new MockUp<FolderPattern>() {
            @Mock
            String generatePattern(String filterPattern, String excludeFolders) { return ""; }
        };
        new MockUp<CxZip>() {
            @Mock
            FilePath zipSourceCode(String filterPattern) { return null; }
        };

        DependencyFolder folders = new DependencyFolder("test2", "");
        OpenSourceAnalyzerService service = new OpenSourceAnalyzerService(null, folders, new RestClient("", null), 0, Logger.getLogger(getClass()), new CxWebService(null), new CxZip(null, null, null), new FolderPattern(null, null, null));

        service.analyze();
    }

    @Test
    public void analyze_RunAnalysisWithoutErrors_StartAndEndRunMessageShouldBeWritten() throws IOException, InterruptedException {

        new MockUp<OpenSourceAnalyzerService>() {
            @Mock
            getOpenSourceSummaryResponse getOpenSourceSummary()
            {
                return new getOpenSourceSummaryResponse();
            }
        };
        new MockUp<FoldersScanner>() {
        };
        final Set infoMessages= new HashSet();
        new MockUp<Logger>() {
            @Mock
            void info(Object message) {
                infoMessages.add(message);
            }
        };
        new MockUp<CryptographicCallable>() {
            @Mock
            String invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
                return "a94a8fe5ccb19ba61c4c0873d391e987982fbbd3";
            }
        };
        new MockUp<RestClient>() {
            @Mock
            URI createScan(ScanRequest request) {
                return null;
            }
            @Mock
            getOpenSourceSummaryResponse getOpenSourceSummary(GetOpenSourceSummaryRequest request){
                return new getOpenSourceSummaryResponse();
            }
            @Mock
            void waitForScanToFinish(URI uri){

            }
        };
        new MockUp<CxWebService>() {
            @Mock
            void $init(String serverUrl) {
            }
            @Mock
            Boolean isOsaLicenseValid(){
                return true;
            }
        };
        new MockUp<FolderPattern>() {
            @Mock
            String generatePattern(String filterPattern, String excludeFolders) { return ""; }
        };
        new MockUp<CxZip>() {
            @Mock
            FilePath zipSourceCode(String filterPattern) { return null; }
        };

        DependencyFolder folders = new DependencyFolder("test2", "");
        OpenSourceAnalyzerService service = new OpenSourceAnalyzerService(null, folders, new RestClient("", null), 0, Logger.getLogger(getClass()), new CxWebService(null), new CxZip(null, null, null), new FolderPattern(null, null, null));

        service.analyze();

        assertTrue(infoMessages.contains("OSA (open source analysis) Run has started"));
        assertTrue(infoMessages.contains("OSA (open source analysis) Run has finished successfully"));
    }

    @Test
    public void openSourceAnalyzerServiceTests_noLicense_logNoLicense() throws IOException, InterruptedException {

        new MockUp<Logger>() {
            void error(Object message) {
                assertTrue(message.toString().contains(OpenSourceAnalyzerService.NO_LICENSE_ERROR));
            }
        };
        new MockUp<CxWebService>() {
            @Mock
            void $init(String serverUrl) {
            }
            @Mock
            Boolean isOsaLicenseValid(){
                return false;
            }
        };
        new MockUp<FolderPattern>() {
            @Mock
            String generatePattern(String filterPattern, String excludeFolders) { return ""; }
        };
        new MockUp<CxZip>() {
            @Mock
            FilePath zipSourceCode(String filterPattern) { return null; }
        };

        DependencyFolder folders = new DependencyFolder("test2", "");
        OpenSourceAnalyzerService service = new OpenSourceAnalyzerService(null, folders, null, 0, Logger.getLogger(getClass()), new CxWebService(null), new CxZip(null, null, null), new FolderPattern(null, null, null));

        service.analyze();

    }
}
