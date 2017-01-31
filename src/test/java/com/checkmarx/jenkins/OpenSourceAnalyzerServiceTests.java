package com.checkmarx.jenkins;

/**
 * @author tsahi
 * @since 02/02/16
 */
/*
@RunWith(JMockit.class)
public class OpenSourceAnalyzerServiceTests {

    @Test
    public void analyze_noIncludes_nothingShouldHappen() throws IOException, InterruptedException {
        DependencyFolder folders = new DependencyFolder("", "test");
        ScanService service = new ScanService(folders, Logger.getLogger(getClass()), null, null, null, null, new ScanSender(new ScanClient("", null), 0));
        service.scan(true);
    }

    @Test
    public void analyze_withDependencies_NoError() throws IOException, InterruptedException {
        new MockUp<Logger>() {
            void info(Object message) {
            }
        };
        new MockUp<ScanClient>() {
            @Mock
            URI createScan(CreateScanRequest request) {
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
        ScanService service = new ScanService(folders, Logger.getLogger(getClass()), new CxWebService(null), new CxZip(null, null, null), new FolderPattern(null, null, null), null, new ScanSender(new ScanClient("", null), 0));
        service.scan(true);
    }

    @Test
    public void analyze_RunAnalysisWithoutErrors_StartAndEndRunMessageShouldBeWritten() throws IOException, InterruptedException {
        final Set infoMessages= new HashSet();
        new MockUp<Logger>() {
            @Mock
            void info(Object message) {
                infoMessages.add(message);
            }
        };
        new MockUp<ScanClient>() {
            @Mock
            URI createScan(CreateScanRequest request) {
                return null;
            }
            @Mock
            GetOpenSourceSummaryResponse getOpenSourceSummary(GetOpenSourceSummaryRequest request){
                return new GetOpenSourceSummaryResponse();
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
        new MockUp<ScanSender>() {
            @Mock
            GetOpenSourceSummaryResponse send(FilePath sourceCodeZip) {
                return null;
            }
        };
        new MockUp<ScanResultsPresenter>() {
            @Mock
            void printResultsToOutput(GetOpenSourceSummaryResponse results) {
            }
        };

        DependencyFolder folders = new DependencyFolder("test2", "");
        ScanService service = new ScanService(folders, Logger.getLogger(getClass()), new CxWebService(null), new CxZip(null, null, null), new FolderPattern(null, null, null), new ScanResultsPresenter(null), new ScanSender(new ScanClient("", null), 0));
        service.scan(false);

        assertTrue(infoMessages.contains("OSA (open source analysis) Run has started"));
        assertTrue(infoMessages.contains("OSA (open source analysis) Run has finished successfully"));
    }

    @Test
    public void openSourceAnalyzerServiceTests_noLicense_logNoLicense() throws IOException, InterruptedException {

        new MockUp<Logger>() {
            void error(Object message) {
                assertTrue(message.toString().contains(ScanService.NO_LICENSE_ERROR));
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
        ScanService service = new ScanService(folders, Logger.getLogger(getClass()), new CxWebService(null), new CxZip(null, null, null), new FolderPattern(null, null, null), null, new ScanSender(new ScanClient("", null), 0));
        service.scan(true);

    }
}
*/