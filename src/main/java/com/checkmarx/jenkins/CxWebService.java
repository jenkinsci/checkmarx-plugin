package com.checkmarx.jenkins;


import com.checkmarx.ws.CxJenkinsWebService.*;
import com.checkmarx.ws.CxWSResolver.*;
import hudson.AbortException;
import hudson.util.IOUtils;
import org.apache.log4j.Logger;

import javax.xml.namespace.QName;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: denis
 * Date: 13/11/2013
 * Time: 18:12
 * Description:
 */
public class CxWebService {

    private final static Logger logger = Logger.getLogger(CxWebService.class);
    private final static QName CXWSRESOLVER_QNAME = new QName("http://Checkmarx.com", "CxWSResolver");
    private final static QName CXCLIWEBSERVICE_QNAME = new QName("http://Checkmarx.com/v7", "CxCLIWebService");
    private final static int WEBSERVICE_API_VERSION = 1;
    private final static String CXWSRESOLVER_PATH = "/cxwebinterface/cxwsresolver.asmx";
    private final static int LCID = 1033; // English

    private String sessionId;
    private CxJenkinsWebServiceSoap cxCLIWebServiceSoap;

    public CxWebService(String serverUrl) throws MalformedURLException, AbortException
    {
        logger.info("Establishing connection with Checkmarx server at: " + serverUrl);
        URL serverUrlUrl = new URL(serverUrl);
        if (serverUrlUrl.getPath().length() > 0)
        {
            String message = "Checkmarx server url must not contain path: " + serverUrl;
            logger.debug(message);
            throw new AbortException(message);
        }
        URL resolverUrl = new URL(serverUrl + CXWSRESOLVER_PATH);

        logger.debug("Resolver url: " + resolverUrl);
        CxWSResolver cxWSResolver;
        try {
            cxWSResolver = new CxWSResolver(resolverUrl,CXWSRESOLVER_QNAME);
        } catch (javax.xml.ws.WebServiceException e){
            logger.error("Failed to resolve Checkmarx webservice url with resolver at: " + resolverUrl);
            logger.error(e);
            throw new AbortException("Checkmarx server was not found on url: " + serverUrl);
        }
        CxWSResolverSoap cxWSResolverSoap =  cxWSResolver.getCxWSResolverSoap();
        CxWSResponseDiscovery cxWSResponseDiscovery = cxWSResolverSoap.getWebServiceUrl(CxClientType.JENKINS,WEBSERVICE_API_VERSION);
        if (!cxWSResponseDiscovery.isIsSuccesfull())
        {
            String message = "Failed to resolve Checkmarx webservice url: \n" + cxWSResponseDiscovery.getErrorMessage();
            logger.error(message);
            throw new AbortException(message);
        }

        URL webServiceUrl = new URL(cxWSResponseDiscovery.getServiceURL());
        logger.debug("Webservice url: " + webServiceUrl);
        CxJenkinsWebService cxCLIWebService = new CxJenkinsWebService(webServiceUrl,CXCLIWEBSERVICE_QNAME);
        cxCLIWebServiceSoap = cxCLIWebService.getCxJenkinsWebServiceSoap();

    }

    public void login(String username, String password) throws AbortException
    {
        sessionId=null;
        Credentials credentials = new Credentials();
        credentials.setUser(username);
        credentials.setPass(password);
        CxWSResponseLoginData cxWSResponseLoginData = cxCLIWebServiceSoap.login(credentials,LCID);

        if (!cxWSResponseLoginData.isIsSuccesfull())
        {
            logger.error("Login to Checkmarx server failed:");
            logger.error(cxWSResponseLoginData.getErrorMessage());
            throw new AbortException(cxWSResponseLoginData.getErrorMessage());
        }

        sessionId = cxWSResponseLoginData.getSessionId();
        logger.debug("Login successful, sessionId: " + sessionId);
    }

    public CxWSResponseRunID scan(CliScanArgs args) throws AbortException
    {
        assert sessionId!=null : "Trying to scan before login";

        CxWSResponseRunID cxWSResponseRunID = cxCLIWebServiceSoap.scan(sessionId,args);
        if (!cxWSResponseRunID.isIsSuccesfull())
        {
            String message = "Submission of sources for scan failed: \n" + cxWSResponseRunID.getErrorMessage();
            logger.error(message);
            throw new AbortException(message);
        }

        return cxWSResponseRunID;
    }


    private CxWSResponseScanStatus getScanStatus(CxWSResponseRunID cxWSResponseRunID) throws AbortException
    {
        assert sessionId!=null : "Trying to get scan status before login";
        CxWSResponseScanStatus cxWSResponseScanStatus = cxCLIWebServiceSoap.getStatusOfSingleScan(sessionId,cxWSResponseRunID.getRunId());
        if (!cxWSResponseScanStatus.isIsSuccesfull())
        {
            String message = "Error communicating with Checkmarx server: \n" + cxWSResponseScanStatus.getErrorMessage();
            logger.error(message);
            throw new AbortException(message);
        }
        return cxWSResponseScanStatus;
    }



    public long trackScanProgress(CxWSResponseRunID cxWSResponseRunID) throws AbortException
    {
        assert sessionId!=null : "Trying to track scan progress before login";

        boolean locReported = false;
        while (true)
        {
            CxWSResponseScanStatus status = this.getScanStatus(cxWSResponseRunID);
            switch (status.getCurrentStatus())
            {
                // In progress states
                case WAITING_TO_PROCESS:
                    logger.info("Scan job waiting for processing");
                    break ;

                case QUEUED:
                    if (!locReported)
                    {
                        logger.info("Source contains: " + status.getLOC() + " lines of code.");
                        locReported = true;
                    }
                    logger.info("Scan job queued at position: " + status.getQueuePosition());
                    break ;

                case UNZIPPING:
                    logger.info("Unzipping: " + status.getCurrentStagePercent() + "% finished");
                    logger.info("LOC: " + status.getLOC());
                    logger.info("StageMessage: " + status.getStageMessage());
                    logger.info("StepMessage: " + status.getStepMessage());
                    logger.info("StepDetails: " + status.getStepDetails());

                    break ;

                case WORKING:
                    logger.info("Scanning: " + status.getStageMessage() + " " + status.getStepDetails() +
                            " (stage: " + status.getCurrentStagePercent() + "%, total: "+ status.getTotalPercent() + "%)");
                    break ;


                // End of progress states
                case FINISHED:
                    logger.info("Scan Finished Successfully -  RunID: " + status.getRunId() + " ScanID:" + status.getScanId());
                    return status.getScanId();

                case FAILED:
                case DELETED:
                case UNKNOWN:
                case CANCELED:
                    String message = "Scan " + status.getStageName() + " -  RunID: " + status.getRunId() + " ScanID:" + status.getScanId();
                    logger.info(message);
                    logger.info("Stage Message" +  status.getStageMessage());
                    throw new AbortException(message);
            }

            try {
                Thread.sleep(10*1000);
            } catch (InterruptedException e)
            {
                String err = "Process interrupted while waiting for scan results";
                logger.error(err);
                logger.error(e);
                throw new AbortException(err);
            }
        }



    }

    public void retrieveScanReport(long scanId, File reportFile, CxWSReportType reportType) throws AbortException
    {
        assert sessionId!=null : "Trying to retrieve scan report before login";

        CxWSReportRequest cxWSReportRequest = new CxWSReportRequest();
        cxWSReportRequest.setScanID(scanId);
        cxWSReportRequest.setType(reportType);
        logger.info("Requesting " + reportType.toString().toUpperCase() + " Scan Report Generation");
        CxWSCreateReportResponse cxWSCreateReportResponse = cxCLIWebServiceSoap.createScanReport(sessionId,cxWSReportRequest);
        if (!cxWSCreateReportResponse.isIsSuccesfull())
        {
            String message = "Error requesting scan report generation: " + cxWSCreateReportResponse.getErrorMessage();
            logger.error(message);
            throw new AbortException(message);
        }



        // Wait for the report to become ready

        while (true)
        {
            CxWSReportStatusResponse cxWSReportStatusResponse = cxCLIWebServiceSoap.getScanReportStatus(sessionId,cxWSCreateReportResponse.getID());
            if (!cxWSReportStatusResponse.isIsSuccesfull())
            {
                String message = "Error retrieving scan report status: " + cxWSReportStatusResponse.getErrorMessage();
                logger.error(message);
                throw new AbortException(message);
            }
            if (cxWSReportStatusResponse.isIsFailed())
            {
                String message = "Failed to create scan report";
                logger.error("Web method getScanReportStatus returned status response with isFailed field set to true");
                logger.error(message);
                throw new AbortException(message);
            }

            if (cxWSReportStatusResponse.isIsReady())
            {
                logger.info("Scan report generated on Checkmarx server");
                break;
            }

            logger.info(reportType.toString().toUpperCase() + " Report generation in progress");
            try {
                Thread.sleep(5*1000);
            } catch (InterruptedException e)
            {
                String err = "Process interrupted while waiting for scan results";
                logger.error(err);
                logger.error(e);
                throw new AbortException(err);
            }
        }

        CxWSResponseScanResults cxWSResponseScanResults = cxCLIWebServiceSoap.getScanReport(sessionId,cxWSCreateReportResponse.getID());
        if (!cxWSResponseScanResults.isIsSuccesfull()) {
            String message = "Error retrieving scan report: " + cxWSResponseScanResults.getErrorMessage();
            logger.error(message);
            throw new AbortException(message);
        }

        // Save results on disk
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(reportFile);
            IOUtils.write(cxWSResponseScanResults.getScanResults(),fileOutputStream);
            fileOutputStream.close();

        } catch (IOException e)
        {
            logger.debug(e);
            String message = "Can't create report file: " + reportFile.getAbsolutePath();
            logger.info(message);
            throw new AbortException(message);
        }
        logger.info("Scan report written to: " + reportFile.getAbsolutePath());
    }

    public List<ProjectDisplayData> getProjectsDisplayData() throws AbortException
    {
        assert sessionId!=null : "Trying to retrieve projects display data before login";

        CxWSResponseProjectsDisplayData cxWSResponseProjectsDisplayData = this.cxCLIWebServiceSoap.getProjectsDisplayData(this.sessionId);
        if (!cxWSResponseProjectsDisplayData.isIsSuccesfull())
        {
            String message = "Error retrieving projects display data from server: "  + cxWSResponseProjectsDisplayData.getErrorMessage();
            logger.error(message);
            throw new AbortException(message);
        }

        return cxWSResponseProjectsDisplayData.getProjectList().getProjectDisplayData();
    }

    public List<Preset> getPresets() throws AbortException
    {
        assert sessionId!=null : "Trying to retrieve presetes before login";
        CxWSResponsePresetList cxWSResponsePresetList = this.cxCLIWebServiceSoap.getPresetList(this.sessionId);
        if (!cxWSResponsePresetList.isIsSuccesfull())
        {
            String message = "Error retrieving presets from server: "  + cxWSResponsePresetList.getErrorMessage();
            logger.error(message);
            throw new AbortException(message);
        }
        return cxWSResponsePresetList.getPresetList().getPreset();
    }

    // Source encoding is called "configuration" in server terms
    public List<ConfigurationSet> getSourceEncodings() throws AbortException
    {
        assert sessionId!=null : "Trying to retrieve configurations before login";
        CxWSResponseConfigSetList cxWSResponseConfigSetList = this.cxCLIWebServiceSoap.getConfigurationSetList(sessionId);
        if (!cxWSResponseConfigSetList.isIsSuccesfull())
        {
            String message = "Error retrieving configurations from server: "  + cxWSResponseConfigSetList.getErrorMessage();
            logger.error(message);
            throw new AbortException(message);
        }
        return cxWSResponseConfigSetList.getConfigSetList().getConfigurationSet();
    }




    public boolean isLoggedIn()
    {
        return this.sessionId!=null;
    }

}
