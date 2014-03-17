package com.checkmarx.jenkins;


import com.checkmarx.ws.CxJenkinsWebService.*;
import com.checkmarx.ws.CxJenkinsWebService.CxWSBasicRepsonse;
import com.checkmarx.ws.CxWSResolver.*;
import hudson.AbortException;
import hudson.FilePath;
import hudson.util.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.*;
import java.net.HttpURLConnection;
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

    private final static int WEBSERVICE_API_VERSION = 1;
    private final static String CXWSRESOLVER_PATH = "/cxwebinterface/cxwsresolver.asmx";
    private final static int LCID = 1033; // English

    private final Logger logger;
    private String sessionId;
    private CxJenkinsWebServiceSoap cxJenkinsWebServiceSoap;
    private final URL webServiceUrl;


    public CxWebService(String serverUrl) throws MalformedURLException, AbortException
    {
        this(serverUrl,null);
    }

    public CxWebService(@NotNull final String serverUrl,@Nullable final String loggerSuffix) throws MalformedURLException, AbortException
    {
        logger = CxLogUtils.loggerWithSuffix(getClass(),loggerSuffix);
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
            cxWSResolver = new CxWSResolver(resolverUrl);  // TODO: Remove qname
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

        webServiceUrl = new URL(cxWSResponseDiscovery.getServiceURL());
        logger.debug("Webservice url: " + webServiceUrl);
        CxJenkinsWebService cxJenkinsWebService = new CxJenkinsWebService(webServiceUrl); // TODO: Remove qname
        cxJenkinsWebServiceSoap = cxJenkinsWebService.getCxJenkinsWebServiceSoap();

    }

    public void login(String username, String password) throws AbortException
    {
        sessionId=null;
        Credentials credentials = new Credentials();
        credentials.setUser(username);
        credentials.setPass(password);
        CxWSResponseLoginData cxWSResponseLoginData = cxJenkinsWebServiceSoap.login(credentials,LCID);

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

        CxWSResponseRunID cxWSResponseRunID = cxJenkinsWebServiceSoap.scan(sessionId, args);
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
        CxWSResponseScanStatus cxWSResponseScanStatus = cxJenkinsWebServiceSoap.getStatusOfSingleScan(sessionId,cxWSResponseRunID.getRunId());
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
        CxWSCreateReportResponse cxWSCreateReportResponse = cxJenkinsWebServiceSoap.createScanReport(sessionId,cxWSReportRequest);
        if (!cxWSCreateReportResponse.isIsSuccesfull())
        {
            String message = "Error requesting scan report generation: " + cxWSCreateReportResponse.getErrorMessage();
            logger.error(message);
            throw new AbortException(message);
        }



        // Wait for the report to become ready

        while (true)
        {
            CxWSReportStatusResponse cxWSReportStatusResponse = cxJenkinsWebServiceSoap.getScanReportStatus(sessionId,cxWSCreateReportResponse.getID());
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

        CxWSResponseScanResults cxWSResponseScanResults = cxJenkinsWebServiceSoap.getScanReport(sessionId,cxWSCreateReportResponse.getID());
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

        CxWSResponseProjectsDisplayData cxWSResponseProjectsDisplayData = this.cxJenkinsWebServiceSoap.getProjectsDisplayData(this.sessionId);
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
        CxWSResponsePresetList cxWSResponsePresetList = this.cxJenkinsWebServiceSoap.getPresetList(this.sessionId);
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
        CxWSResponseConfigSetList cxWSResponseConfigSetList = this.cxJenkinsWebServiceSoap.getConfigurationSetList(sessionId);
        if (!cxWSResponseConfigSetList.isIsSuccesfull())
        {
            String message = "Error retrieving configurations from server: "  + cxWSResponseConfigSetList.getErrorMessage();
            logger.error(message);
            throw new AbortException(message);
        }
        return cxWSResponseConfigSetList.getConfigSetList().getConfigurationSet();
    }

    public CxWSBasicRepsonse validateProjectName(String cxProjectName)
    {
        assert sessionId!=null : "Trying to validate project name before login";
        return this.cxJenkinsWebServiceSoap.isValidProjectName(sessionId,cxProjectName,""); // TODO: Specify group id
    }


    private Pair<byte[],byte[]> createScanSoapMessage(CliScanArgs args)
    {

        final String soapMessageHead = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<soap:Envelope xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
                "xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" " +
                "xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" +
                "  <soap:Body>\n";

        final String soapMessageTail = "\n  </soap:Body>\n</soap:Envelope>";
        final String zippedFileOpenTag = "<ZippedFile>";
        final String zippedFileCloseTag = "</ZippedFile>";

        try {
            final JAXBContext context = JAXBContext.newInstance(Scan.class);
            final Marshaller marshaller = context.createMarshaller();

            StringWriter scanMessage = new StringWriter();
            scanMessage.write(soapMessageHead);

            // Nullify the zippedFile field, and save its old value for restoring later
            final byte[] oldZippedFileValue = args.getSrcCodeSettings().getPackagedCode().getZippedFile();
            args.getSrcCodeSettings().getPackagedCode().setZippedFile(new byte[]{});
            Scan scan = new Scan();
            scan.setArgs(args);
            scan.setSessionId(sessionId);
            marshaller.setProperty(Marshaller.JAXB_FRAGMENT, true);
            marshaller.marshal(scan, scanMessage);
            args.getSrcCodeSettings().getPackagedCode().setZippedFile(oldZippedFileValue); // Restore the old value


            scanMessage.write(soapMessageTail);
            final String[] parts = scanMessage.toString().split(zippedFileOpenTag + zippedFileCloseTag);
            assert parts.length == 2;
            final String startPart = parts[0] + zippedFileOpenTag;
            final String endPart   = zippedFileCloseTag + parts[1];
            Pair<byte[],byte[]> result = Pair.of(startPart.getBytes("UTF-8"), endPart.getBytes("UTF-8"));

            return result;
        } catch (JAXBException e)
        {
            // Getting here indicates a bug
            logger.error(e.getMessage(),e);
            throw new Error(e);
        } catch (UnsupportedEncodingException e)
        {
            // Getting here indicates a bug
            logger.error(e.getMessage(),e);
            throw new Error(e);
        }
    }

    private CxWSResponseRunID parseXmlResponse(InputStream inputStream) throws XMLStreamException, JAXBException
    {
        XMLInputFactory xif = XMLInputFactory.newFactory();
        XMLStreamReader xsr = xif.createXMLStreamReader(inputStream);
        xsr.nextTag();
        while(!xsr.getLocalName().equals("ScanResponse")) {
            xsr.nextTag();
        }

        final JAXBContext context = JAXBContext.newInstance(ScanResponse.class);
        final Unmarshaller unmarshaller = context.createUnmarshaller();
        final ScanResponse scanResponse = (ScanResponse)unmarshaller.unmarshal(xsr);
        xsr.close();
        return scanResponse.getScanResult();
    }




    /**
     * Same as "scan" method, but works by streaming the LocalCodeContainer.zippedFile contents.
     * NOTE: The attribute LocalCodeContainer.zippedFile inside args is REPLACED by empty byte array,
     * and base64ZipFile temp file is used instead.
     * @param args - CliScanArgs instance initialized with all the required parameters for submitting a scan
     * @param base64ZipFile - Temp file used instead of LocalCodeContainer.zippedFile attribute, should
     *                      contain zipped sources encoded with base 64 encoding
     * @return CxWSResponseRunID object which is similar to the return value of scan web service method
     * @throws AbortException
     */

    public CxWSResponseRunID scanStreaming(final CliScanArgs args, final FilePath base64ZipFile) throws AbortException
    {
        assert sessionId!=null;

        try {

            Pair<byte[],byte[]> soapMessage = createScanSoapMessage(args);

            // Create HTTP connection

            final HttpURLConnection streamingUrlConnection = (HttpURLConnection)webServiceUrl.openConnection();
            streamingUrlConnection.addRequestProperty("Content-Type","text/xml; charset=utf-8");
            streamingUrlConnection.addRequestProperty("SOAPAction","\"http://Checkmarx.com/v7/Scan\"");
            streamingUrlConnection.setDoOutput(true);
            // Calculate the length of the soap message
            final long length = soapMessage.getLeft().length + soapMessage.getRight().length + base64ZipFile.length();
            streamingUrlConnection.setFixedLengthStreamingMode((int)length);
            streamingUrlConnection.connect();
            final OutputStream os = streamingUrlConnection.getOutputStream();

            logger.info("Uploading sources to Checkmarx server");
            os.write(soapMessage.getLeft());
            final InputStream fis = base64ZipFile.read();
            org.apache.commons.io.IOUtils.copyLarge(fis, os);

            os.write(soapMessage.getRight());
            os.close();
            fis.close();
            logger.info("Finished uploading sources to Checkmarx server");


            CxWSResponseRunID cxWSResponseRunID = parseXmlResponse(streamingUrlConnection.getInputStream());

            if (!cxWSResponseRunID.isIsSuccesfull())
            {
                String message = "Submission of sources for scan failed: \n" + cxWSResponseRunID.getErrorMessage();
                logger.error(message);
                throw new AbortException(message);
            }

            return cxWSResponseRunID;

        } catch (IOException e) {
            logger.error(e.getMessage(),e);
            throw new AbortException(e.getMessage());
        } catch (JAXBException e) {
            logger.error(e.getMessage(), e);
            throw new AbortException(e.getMessage());
        } catch (XMLStreamException e) {
            logger.error(e.getMessage(), e);
            throw new AbortException(e.getMessage());
        } catch (InterruptedException e) {
            logger.error(e.getMessage(), e);
            throw new AbortException(e.getMessage());
        }
    }


    public boolean isLoggedIn()
    {
        return this.sessionId!=null;
    }

}
