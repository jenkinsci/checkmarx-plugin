package com.checkmarx.jenkins;

import com.checkmarx.jenkins.legacy8_7.OsaScanResult;
import com.checkmarx.jenkins.legacy8_7.QueryResult;
import com.checkmarx.jenkins.legacy8_7.SastScanResult;
import com.checkmarx.jenkins.legacy8_7.ThresholdConfig;
import com.cx.restclient.configuration.CxScanConfig;
import com.cx.restclient.dto.ScannerType;
import com.cx.restclient.sast.dto.SASTResults;
import hudson.PluginWrapper;
import hudson.model.Action;
import hudson.model.Run;
import hudson.util.IOUtils;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author denis
 * @since 3/11/13
 */
public class CxScanResult implements Action {

    public final Run<?, ?> owner;
    private final long projectId = 0;
    private boolean scanRanAsynchronous = false;
    private String serverUrl = "";

    private long scanId;

    private Boolean sastEnabled;
    private boolean osaEnabled;

    //Resultsalc
    private OsaScanResult osaScanResult;
    private SastScanResult sastScanResult;

    //Thresholds
    private boolean thresholdsEnabled = false;
    private boolean osaThresholdsEnabled = false;
    private ThresholdConfig sastThresholdConfig;
    private ThresholdConfig osaThresholdConfig;
    private boolean isThresholdForNewResultExceeded = false;

    private File pdfReport;
    public static final String PDF_REPORT_NAME = "ScanReport.pdf";
    
    public static final String SCA_PDF_REPORT_NAME = "ScaPdfReport.pdf";
    private boolean osaSuccessful; //osa fails flag for jelly

    private String htmlReportName;

    public String getHtmlReportName() {
        return htmlReportName;
    }

    public void setHtmlReportName(String htmlReportName) {
        this.htmlReportName = htmlReportName;
    }

    public CxScanResult(Run<?, ?> owner, CxScanConfig config) {
        this.scanRanAsynchronous = !config.getSynchronous();
        this.sastEnabled = config.isSastEnabled();
        this.osaEnabled = config.isOsaEnabled();
        this.owner = owner;
        this.resultDeepLink = "";
    }

    public void setSastResults(SASTResults results) {
    	this.criticalCount = results.getCritical();
        this.highCount = results.getHigh();
        this.mediumCount = results.getMedium();
        this.lowCount = results.getLow();
    }

    public Boolean getSastEnabled() {
        return sastEnabled;
    }

    public CxScanResult(Run<?, ?> owner, String serverUrl, long projectId, boolean scanRanAsynchronous) {
        this.owner = owner;
        this.serverUrl = serverUrl;
        this.resultIsValid = false; //sast fails flag for jelly
        this.errorMessage = "No Scan Results"; // error message to appear if results were not parsed
        this.criticalQueryResultList = new LinkedList<>();
        this.highQueryResultList = new LinkedList<>();
        this.mediumQueryResultList = new LinkedList<>();
        this.lowQueryResultList = new LinkedList<>();
        this.infoQueryResultList = new LinkedList<>();
        this.resultDeepLink = "";

    }


    public void setOsaThresholds(ThresholdConfig thresholdConfig) {
        this.osaThresholdConfig = thresholdConfig;
        this.setOsaThresholdsEnabled(true);
        //todo erase when legacy code is no longer needed
        initializeOsaLegacyThresholdVariables(thresholdConfig);
    }

    public void setThresholds(ThresholdConfig thresholdConfig) {
        this.sastThresholdConfig = thresholdConfig;
        this.setThresholdsEnabled(true);
        //todo erase when legacy code is no longer needed
        initializeSastLegacyThresholdVariables(thresholdConfig);
    }

    public void setThresholdForNewResultExceeded(boolean thresholdForNewResultExceeded) {
        isThresholdForNewResultExceeded = thresholdForNewResultExceeded;
    }

    public String getLargeIconFileName() {
        if (isShowResults()) {
            return getIconPath() + "CxIcon48x48.png";
        } else {
            return null;
        }
    }

    @Override
    public String getIconFileName() {
        return null;

    }

    @Override
    public String getDisplayName() {
        return null;

    }

    @Override
    public String getUrlName() {
        if (isShowResults()) {
            return "checkmarx";
        } else {
            return null;
        }
    }

    @NotNull
    public String getIconPath() {
        return Optional.ofNullable(Jenkins.getInstance())
                .map(Jenkins::getPluginManager)
                .map(pm -> pm.getPlugin("checkmarx"))
                .map(PluginWrapper::getShortName)
                .map(shortName -> "/plugin/" + shortName + "/")
                .orElse("");
    }

    public boolean isShowResults() {
        @Nullable
        CxScanBuilder.DescriptorImpl descriptor = (CxScanBuilder.DescriptorImpl) Jenkins.getInstance().getDescriptor(CxScanBuilder.class);
        return descriptor != null && !descriptor.isHideResults();
    }

    public boolean isRemoveAsyncHtml() {
        @Nullable
        CxScanBuilder.DescriptorImpl descriptor = (CxScanBuilder.DescriptorImpl) Jenkins.getInstance().getDescriptor(CxScanBuilder.class);
        return descriptor != null && (!descriptor.isAsyncHtmlRemoval() || !this.scanRanAsynchronous );
    }

 // Check if neither the current report nor the older report is found	
	public boolean isEmptyReportAsync() {
		try {
			String htmlReport = getHtmlReport();
			return htmlReport == null || htmlReport.isEmpty()
					|| htmlReport.equals("<h1>Checkmarx HTML report not found<h1>");
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}	 

    public boolean isOsaEnabled() {
        return osaEnabled;
    }

    public void setOsaEnabled(boolean osaEnabled) {
        this.osaEnabled = osaEnabled;
    }

    public boolean isThresholdsEnabled() {
        return thresholdsEnabled;
    }

    public void setThresholdsEnabled(boolean thresholdsEnabled) {
        this.thresholdsEnabled = thresholdsEnabled;
    }

    public boolean isOsaThresholdsEnabled() {
        return osaThresholdsEnabled;
    }

    public void setOsaThresholdsEnabled(boolean osaThresholdsEnabled) {
        this.osaThresholdsEnabled = osaThresholdsEnabled;
    }

    public ThresholdConfig getSastThresholdConfig() {
        return sastThresholdConfig;
    }

    public void setSastThresholdConfig(ThresholdConfig sastThresholdConfig) {
        this.sastThresholdConfig = sastThresholdConfig;
    }

    public ThresholdConfig getOsaThresholdConfig() {
        return osaThresholdConfig;
    }

    public void setOsaThresholdConfig(ThresholdConfig osaThresholdConfig) {
        this.osaThresholdConfig = osaThresholdConfig;
    }

    public boolean isPdfReportReady() {
        File buildDirectory = owner.getRootDir();
        pdfReport = new File(buildDirectory, "/checkmarx/" + PDF_REPORT_NAME);
        return pdfReport.exists();
    }

    public String getPdfReportUrl() {
        return "/pdfReport";
    }

    public void doPdfReport(StaplerRequest req, StaplerResponse rsp) throws IOException {
        rsp.setContentType("application/pdf");
        ServletOutputStream outputStream = rsp.getOutputStream();
        File buildDirectory = owner.getRootDir();
        File a = new File(buildDirectory, "/checkmarx/" + PDF_REPORT_NAME);

        IOUtils.copy(a, outputStream);

        outputStream.flush();
        outputStream.close();
    }

	public void doScaPdfReport(StaplerRequest req, StaplerResponse rsp) throws IOException {
		rsp.setContentType("application/pdf");
		ServletOutputStream outputStream = rsp.getOutputStream();
		File buildDirectory = owner.getRootDir();
		File b = new File(buildDirectory, "/checkmarx/" + SCA_PDF_REPORT_NAME);
		
		IOUtils.copy(b, outputStream);
		
		outputStream.flush();
		outputStream.close();
	}

    static String resolveHTMLReportName(boolean sastEnabled, ScannerType dependencyScanner) {
        final String POSTFIX = ".html";
        String result = "Report";
        if (sastEnabled) {
            result += "_CxSAST";
        }
        if (dependencyScanner == ScannerType.OSA || dependencyScanner == ScannerType.AST_SCA) {
            result += "_" + dependencyScanner.getDisplayName();
        }
        return result + POSTFIX;
    }

    public String getHtmlReport() throws IOException {
        String htmlReport;
        File cxBuildDirectory = new File(owner.getRootDir(), "checkmarx");

        //backward compatibility (up to version 8.80.0)
        if (htmlReportName == null) {
            File oldReport = new File(cxBuildDirectory, "report.html");
            if (oldReport.exists()) {
                htmlReport = FileUtils.readFileToString(oldReport, Charset.defaultCharset());
                Pattern patt = Pattern.compile("(<div[^>]*)(\\s*/>)");
                Matcher mattcher = patt.matcher(htmlReport);
                if (mattcher.find()) {
                    htmlReport = mattcher.replaceAll("$1></div>");
                }
                return htmlReport;
            }
        }

        Collection<File> files = FileUtils.listFiles(cxBuildDirectory, null, false);

        for (File f : files) {
            if (htmlReportName != null && htmlReportName.equals(f.getName())) {
                htmlReport = FileUtils.readFileToString(f, Charset.defaultCharset());
                return htmlReport;
            }
        }

        return "<h1>Checkmarx HTML report not found<h1>";
    }

    /**
     * Gets the test result of the previous build, if it's recorded, or null.
     */

    public CxScanResult getPreviousResult() {
        Run<?, ?> b = owner;
        while (true) {
            b = b.getPreviousBuild();
            if (b == null) {
                return null;
            }
            CxScanResult r = b.getAction(CxScanResult.class);
            if (r != null) {
                return r;
            }
        }
    }


    public long getProjectId() {
        return projectId;
    }

    public boolean isScanRanAsynchronous() {
        return scanRanAsynchronous;
    }

    public String getProjectStateUrl() {
        return serverUrl + "/CxWebClient/portal#/projectState/" + projectId + "/Summary";
    }

    public String getOsaProjectStateUrl() {
        return serverUrl + "/CxWebClient/SPA/#/viewer/project/" + projectId;
    }

    //    http://localhost/CxWebClient/ViewerMain.aspx?scanid=1030692&projectid=40565
    public String getCodeViewerUrl() {
        return serverUrl + "/CxWebClient/ViewerMain.aspx";
    }

    public OsaScanResult getOsaScanResult() {
        return osaScanResult;
    }

    public void setOsaScanResult(OsaScanResult osaScanResult) {
        this.osaScanResult = osaScanResult;

        //todo erase when legacy code is no longer needed
        if (osaScanResult.isOsaLicense()) {
            initializeOsaLegacyVariables(osaScanResult);
        }
    }

    public SastScanResult getSastScanResult() {
        return sastScanResult;
    }

    public void setSastScanResult(SastScanResult sastScanResult) {
        this.sastScanResult = sastScanResult;
        //todo erase when legacy code is no longer needed
        initializeSastLegacyVariables(sastScanResult);
    }

    public void setScanId(long scanId) {
        this.scanId = scanId;
    }

    public long getScanId() {
        return scanId;
    }

    public boolean getIsThresholdForNewResultExceeded() {
        return isThresholdForNewResultExceeded;
    }


    public boolean isThresholdExceeded() {
        boolean ret = isThresholdExceededByLevel(sastScanResult.getCriticalCount(), sastThresholdConfig.getCriticalSeverity());
        ret |= isThresholdExceededByLevel(sastScanResult.getHighCount(), sastThresholdConfig.getHighSeverity());
        ret |= isThresholdExceededByLevel(sastScanResult.getMediumCount(), sastThresholdConfig.getMediumSeverity());
        ret |= isThresholdExceededByLevel(sastScanResult.getLowCount(), sastThresholdConfig.getLowSeverity());
        return ret;
    }

    public boolean isOsaThresholdExceeded() {
        boolean ret = isThresholdExceededByLevel(osaScanResult.getOsaCriticalCount(), osaThresholdConfig.getCriticalSeverity());
        ret |= isThresholdExceededByLevel(osaScanResult.getOsaHighCount(), osaThresholdConfig.getHighSeverity());
        ret |= isThresholdExceededByLevel(osaScanResult.getOsaMediumCount(), osaThresholdConfig.getMediumSeverity());
        ret |= isThresholdExceededByLevel(osaScanResult.getOsaLowCount(), osaThresholdConfig.getLowSeverity());
        return ret;
    }

    private boolean isThresholdExceededByLevel(int count, Integer threshold) {
        boolean ret = false;
        if (threshold != null && count > threshold) {
            ret = true;
        }
        return ret;
    }
/********************************************************************************************************************/
/********************************************************************************************************************/
/********************************************************************************************************************/
    //todo remove from class once UI is adjusted to use the above DTO's instead of the legacy variables
    //(when we stop supporting 8.4.1 and down)
    /*******************Legacy Variables for UI backward computability****************************************/

    private int criticalCount;
    private int highCount;
    private int mediumCount;
    private int lowCount;
    private int infoCount;

    private LinkedList<QueryResult> criticalQueryResultList;
    private LinkedList<QueryResult> highQueryResultList;
    private LinkedList<QueryResult> mediumQueryResultList;
    private LinkedList<QueryResult> lowQueryResultList;
    private LinkedList<QueryResult> infoQueryResultList;

    @NotNull
    private String resultDeepLink;

    @Nullable
    private String scanStart;
    @Nullable
    private String scanEnd;
    @Nullable
    private String linesOfCodeScanned;
    @Nullable
    private String filesScanned;
    @Nullable
    private String scanType;

    private boolean resultIsValid;
    private String errorMessage;


    public void initializeSastLegacyVariables(SastScanResult sastScanResult) {
    	this.criticalCount = sastScanResult.getCriticalCount();
    	this.highCount = sastScanResult.getHighCount();
        this.mediumCount = sastScanResult.getMediumCount();
        this.lowCount = sastScanResult.getLowCount();
        this.infoCount = sastScanResult.getInfoCount();

        this.criticalQueryResultList = sastScanResult.getCriticalQueryResultList();
        this.highQueryResultList = sastScanResult.getHighQueryResultList();
        this.mediumQueryResultList = sastScanResult.getMediumQueryResultList();
        this.lowQueryResultList = sastScanResult.getLowQueryResultList();
        this.infoQueryResultList = sastScanResult.getInfoQueryResultList();

        this.resultDeepLink = sastScanResult.getResultDeepLink();
        this.scanStart = sastScanResult.getScanStart();
        this.scanEnd = sastScanResult.getScanEnd();
        this.linesOfCodeScanned = sastScanResult.getLinesOfCodeScanned();
        this.filesScanned = sastScanResult.getFilesScanned();
        this.scanType = sastScanResult.getScanType();

        this.resultIsValid = sastScanResult.isResultIsValid();
        this.errorMessage = sastScanResult.getErrorMessage();
    }

    public int getCriticalCount() {
        return criticalCount;
    }
    
    public int getHighCount() {
        return highCount;
    }

    public int getMediumCount() {
        return mediumCount;
    }

    public int getLowCount() {
        return lowCount;
    }

    public int getInfoCount() {
        return infoCount;
    }

    @NotNull
    public String getResultDeepLink() {
        return resultDeepLink;
    }

    @Nullable
    public String getScanStart() {
        return scanStart;
    }

    @Nullable
    public String getScanEnd() {
        return scanEnd;
    }

    @Nullable
    public String getLinesOfCodeScanned() {
        return linesOfCodeScanned;
    }

    @Nullable
    public String getFilesScanned() {
        return filesScanned;
    }

    @Nullable
    public String getScanType() {
        return scanType;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean isResultIsValid() {
        return resultIsValid;
    }
    
    public List<QueryResult> getCriticalQueryResultList() {
        return criticalQueryResultList;
    }

    public List<QueryResult> getHighQueryResultList() {
        return highQueryResultList;
    }

    public List<QueryResult> getMediumQueryResultList() {
        return mediumQueryResultList;
    }

    public List<QueryResult> getLowQueryResultList() {
        return lowQueryResultList;
    }

    public List<QueryResult> getInfoQueryResultList() {
        return infoQueryResultList;
    }

    //osa results
    private int osaCriticalCount;
    private int osaHighCount;
    private int osaMediumCount;
    private int osaLowCount;
    private int osaVulnerableAndOutdatedLibs;
    private int osaNoVulnerabilityLibs;

    public void initializeOsaLegacyVariables(OsaScanResult osaScanResult) {
        if (osaScanResult != null) {
        	this.osaCriticalCount = osaScanResult.getOsaCriticalCount();
            this.osaHighCount = osaScanResult.getOsaHighCount();
            this.osaMediumCount = osaScanResult.getOsaMediumCount();
            this.osaLowCount = osaScanResult.getOsaLowCount();
            this.osaNoVulnerabilityLibs = osaScanResult.getOsaNoVulnerabilityLibs();
            this.osaVulnerableAndOutdatedLibs = osaScanResult.getOsaVulnerableAndOutdatedLibs();
        }
    }

    public int getOsaCriticalCount() {
        return osaCriticalCount;
    }
    
    public int getOsaHighCount() {
        return osaHighCount;
    }

    public int getOsaMediumCount() {
        return osaMediumCount;
    }

    public int getOsaLowCount() {
        return osaLowCount;
    }

    public int getOsaVulnerableAndOutdatedLibs() {
        return osaVulnerableAndOutdatedLibs;
    }

    public int getOsaNoVulnerabilityLibs() {
        return osaNoVulnerabilityLibs;
    }

    @Nullable
    private Integer criticalThreshold;
    @Nullable
    private Integer highThreshold;
    @Nullable
    private Integer mediumThreshold;
    @Nullable
    private Integer lowThreshold;
    @Nullable
    private Integer osaCriticalThreshold;
    @Nullable
    private Integer osaHighThreshold;
    @Nullable
    private Integer osaMediumThreshold;
    @Nullable
    private Integer osaLowThreshold;

    private void initializeSastLegacyThresholdVariables(ThresholdConfig thresholdConfig) {
    	this.setCriticalThreshold(thresholdConfig.getCriticalSeverity());
    	this.setHighThreshold(thresholdConfig.getHighSeverity());
        this.setMediumThreshold(thresholdConfig.getMediumSeverity());
        this.setLowThreshold(thresholdConfig.getLowSeverity());
    }

    private void initializeOsaLegacyThresholdVariables(ThresholdConfig thresholdConfig) {
    	this.setOsaCriticalThreshold(thresholdConfig.getCriticalSeverity());
    	this.setOsaHighThreshold(thresholdConfig.getHighSeverity());
        this.setOsaMediumThreshold(thresholdConfig.getMediumSeverity());
        this.setOsaLowThreshold(thresholdConfig.getLowSeverity());
    }

    @Nullable
    public Integer getCriticalThreshold() {
        return criticalThreshold;
    }
    
    public void setCriticalThreshold(@Nullable Integer criticalThreshold) {
        this.criticalThreshold = criticalThreshold;
    }
    
    @Nullable
    public Integer getHighThreshold() {
        return highThreshold;
    }

    public void setHighThreshold(@Nullable Integer highThreshold) {
        this.highThreshold = highThreshold;
    }

    @Nullable
    public Integer getMediumThreshold() {
        return mediumThreshold;
    }

    public void setMediumThreshold(@Nullable Integer mediumThreshold) {
        this.mediumThreshold = mediumThreshold;
    }

    @Nullable
    public Integer getLowThreshold() {
        return lowThreshold;
    }

    public void setLowThreshold(@Nullable Integer lowThreshold) {
        this.lowThreshold = lowThreshold;
    }

    @Nullable
    public Integer getOsaCriticalThreshold() {
        return osaCriticalThreshold;
    }

    public void setOsaCriticalThreshold(@Nullable Integer osaCriticalThreshold) {
        this.osaCriticalThreshold = osaCriticalThreshold;
    }
    
    @Nullable
    public Integer getOsaHighThreshold() {
        return osaHighThreshold;
    }

    public void setOsaHighThreshold(@Nullable Integer osaHighThreshold) {
        this.osaHighThreshold = osaHighThreshold;
    }

    @Nullable
    public Integer getOsaMediumThreshold() {
        return osaMediumThreshold;
    }

    public void setOsaMediumThreshold(@Nullable Integer osaMediumThreshold) {
        this.osaMediumThreshold = osaMediumThreshold;
    }

    @Nullable
    public Integer getOsaLowThreshold() {
        return osaLowThreshold;
    }

    public void setOsaLowThreshold(@Nullable Integer osaLowThreshold) {
        this.osaLowThreshold = osaLowThreshold;
    }

    public void setOsaSuccessful(boolean osaSuccessful) {
        this.osaSuccessful = osaSuccessful;
    }

    public boolean isOsaSuccessful() {
        return osaSuccessful;
    }
}