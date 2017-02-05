package com.checkmarx.jenkins;

import com.checkmarx.jenkins.logger.CxPluginLogger;
import com.checkmarx.jenkins.xmlresponseparser.CreateAndRunProjectXmlResponseParser;
import com.checkmarx.jenkins.xmlresponseparser.RunIncrementalScanXmlResponseParser;
import com.checkmarx.jenkins.xmlresponseparser.RunScanAndAddToProjectXmlResponseParser;
import com.checkmarx.jenkins.xmlresponseparser.XmlResponseParser;
import com.checkmarx.ws.CxJenkinsWebService.*;
import com.checkmarx.ws.CxWSResolver.CxClientType;
import com.checkmarx.ws.CxWSResolver.CxWSResolver;
import com.checkmarx.ws.CxWSResolver.CxWSResolverSoap;
import com.checkmarx.ws.CxWSResolver.CxWSResponseDiscovery;
import hudson.AbortException;
import hudson.FilePath;
import jenkins.model.Jenkins;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.stream.XMLStreamException;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.WebServiceException;
import java.io.*;
import java.net.HttpRetryException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;

import static ch.lambdaj.Lambda.*;

/**
 * Wraps all Web services invocations
 *
 * @author denis
 * @since 13/11/2013
 */
public class CxWebService {

    private transient CxPluginLogger logger;

    private static final String CHECKMARX_SERVER_WAS_NOT_FOUND_ON_THE_SPECIFIED_ADRESS = "Checkmarx server was not found on the specified adress";
    private static final int WEBSERVICE_API_VERSION = 1;
    private static final String CXWSRESOLVER_PATH = "/cxwebinterface/cxwsresolver.asmx";
    private static final int LCID = 1033; // English
    private static final int MILISECONDS_IN_MINUTE = 1000 * 60;

	private String sessionId;
	private CxJenkinsWebServiceSoap cxJenkinsWebServiceSoap;
	private final URL webServiceUrl;

    public CxWebService(@NotNull final String serverUrl, CxPluginLogger cxPluginLogger) throws MalformedURLException, AbortException {
        this.logger = cxPluginLogger;

        disableCertificateValidation();

        validateServerUrl(serverUrl);

        CxWSResolverSoap cxWSResolverSoap = getCxWSResolverSoap(serverUrl);
        webServiceUrl = getWebServiceUrl(cxWSResolverSoap);

        CxJenkinsWebService cxJenkinsWebService = new CxJenkinsWebService(webServiceUrl);
        cxJenkinsWebServiceSoap = getJenkinsWebServiceSoap(cxJenkinsWebService);
    }

    private CxJenkinsWebServiceSoap getJenkinsWebServiceSoap(CxJenkinsWebService cxJenkinsWebService) {
        CxJenkinsWebServiceSoap jenkinsWebServiceSoap = cxJenkinsWebService.getCxJenkinsWebServiceSoap();
        setClientTimeout((BindingProvider) jenkinsWebServiceSoap, CxConfig.getRequestTimeOutDuration());
        return jenkinsWebServiceSoap;
    }

    private URL getWebServiceUrl(CxWSResolverSoap cxWSResolverSoap) throws AbortException, MalformedURLException {
        CxWSResponseDiscovery cxWSResponseDiscovery = cxWSResolverSoap.getWebServiceUrl(CxClientType.JENKINS,
                WEBSERVICE_API_VERSION);
        if (!cxWSResponseDiscovery.isIsSuccesfull()) {
            String message = "Failed to resolve Checkmarx webservice url: \n" + cxWSResponseDiscovery.getErrorMessage();
            logger.error(message);
            throw new AbortException(message);
        }
        URL webServiceUrl = new URL(cxWSResponseDiscovery.getServiceURL());
        logger.info("Webservice url: " + webServiceUrl);
        return webServiceUrl;
    }

    private CxWSResolverSoap getCxWSResolverSoap(@NotNull String serverUrl) throws MalformedURLException, AbortException {
        URL resolverUrl = new URL(serverUrl + CXWSRESOLVER_PATH);

        checkServerConnectivity(resolverUrl);

       logger.info("Resolver url: " + resolverUrl);
        CxWSResolver cxWSResolver;
        try {
            cxWSResolver = new CxWSResolver(resolverUrl);
        } catch (WebServiceException e) {
            logger.error("Failed to resolve Checkmarx webservice url with resolver at: " + resolverUrl, e);
            throw new AbortException("Checkmarx server was not found on url: " + serverUrl);
        }
        CxWSResolverSoap resolverSoap = cxWSResolver.getCxWSResolverSoap();
        setClientTimeout((BindingProvider) resolverSoap, CxConfig.getRequestTimeOutDuration());
        return resolverSoap;
    }

    private void validateServerUrl(@NotNull final String serverUrl) throws AbortException, MalformedURLException {
        logger.info("Establishing connection with Checkmarx server at: " + serverUrl);
        UrlValidations urlValidations = new UrlValidations();
        if (urlValidations.urlHasPaths(serverUrl)) {
            String message = "Checkmarx server url must not contain path: " + serverUrl;
            logger.info(message);
            throw new AbortException(message);
        }
    }

    private void disableCertificateValidation() {
        @Nullable
        CxScanBuilder.DescriptorImpl descriptor = (CxScanBuilder.DescriptorImpl) Jenkins.getInstance().getDescriptor(
                CxScanBuilder.class);
        if (descriptor != null && !descriptor.isEnableCertificateValidation()) {
            logger.info("SSL/TLS Certificate Validation Disabled");
            CxSSLUtility.disableSSLCertificateVerification(logger);
        }
    }

    private void checkServerConnectivity(URL url) throws AbortException {
        int seconds = CxConfig.getRequestTimeOutDuration();
        int milliseconds = seconds * 1000;

        try {
            HttpURLConnection urlConn;
            urlConn = (HttpURLConnection) url.openConnection();
            urlConn.setConnectTimeout(milliseconds);
            urlConn.setReadTimeout(milliseconds);
            urlConn.connect();
            if (urlConn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new AbortException(CHECKMARX_SERVER_WAS_NOT_FOUND_ON_THE_SPECIFIED_ADRESS);
            }
        } catch (IOException e) {
            logger.error(CHECKMARX_SERVER_WAS_NOT_FOUND_ON_THE_SPECIFIED_ADRESS, e);
            throw new AbortException(CHECKMARX_SERVER_WAS_NOT_FOUND_ON_THE_SPECIFIED_ADRESS);
        }
    }

    private void setClientTimeout(BindingProvider provider, int seconds) {
        logger.info("Setting connection timeout to " + seconds + " seconds");
        int milliseconds = seconds * 1000;
        Map<String, Object> requestContext = provider.getRequestContext();
        // see https://java.net/jira/browse/JAX_WS-1166
        requestContext.put("com.sun.xml.internal.ws.connect.timeout", milliseconds);
        requestContext.put("com.sun.xml.internal.ws.request.timeout", milliseconds);
        requestContext.put("com.sun.xml.ws.request.timeout", milliseconds);
        requestContext.put("com.sun.xml.ws.connect.timeout", milliseconds);
        requestContext.put("javax.xml.ws.client.connectionTimeout", milliseconds);
        requestContext.put("javax.xml.ws.client.receiveTimeout", milliseconds);
        requestContext.put("timeout", milliseconds); // IBM
    }

    public void login(@Nullable String username, @Nullable String password) throws AbortException {
        sessionId = null;
        Credentials credentials = new Credentials();
        credentials.setUser(username);
        credentials.setPass(password);
        CxWSResponseLoginData cxWSResponseLoginData = cxJenkinsWebServiceSoap.login(credentials, LCID);

        if (!cxWSResponseLoginData.isIsSuccesfull()) {
            logger.error("Login to Checkmarx server failed:");
            logger.error(cxWSResponseLoginData.getErrorMessage());
            throw new AbortException(cxWSResponseLoginData.getErrorMessage());
        }

        sessionId = cxWSResponseLoginData.getSessionId();
        logger.info("Login successful, sessionId: " + sessionId);
    }

    private CxWSResponseScanStatus getScanStatus(CxWSResponseRunID cxWSResponseRunID) throws AbortException {
        assert sessionId != null : "Trying to get scan status before login";
        CxWSResponseScanStatus cxWSResponseScanStatus = cxJenkinsWebServiceSoap.getStatusOfSingleScan(sessionId,
                cxWSResponseRunID.getRunId());
        if (!cxWSResponseScanStatus.isIsSuccesfull()) {
            String message = "Error received from Checkmarx server: " + cxWSResponseScanStatus.getErrorMessage();
            logger.error(message);
            throw new AbortException(message);
        }
        return cxWSResponseScanStatus;
    }

    /**
     * Simple convenience method to avoid spamming the console with duplicate messages.
     * This method will only log a new message if it does not equal the previous message.
     * This method is not responsible for tracking the messages, and expects them to
     * be passed in as parameters.
     *
     * @param prevMsg - The message to compare the new message against.
     * @param newMsg  - The message to log, if new
     * @return - When new, the message that was just logged.  Otherwise, the single message is returned (new=old)
     */
    private String cleanLogger(String prevMsg, String newMsg) {
        //only log if new != old
        if (!newMsg.equals(prevMsg)) {
            logger.info(newMsg);
        }
        //if new, return the message logged
        //otherwise, just returns the msg (because new=old)
        return newMsg;
    }

    public long trackScanProgress(final CxWSResponseRunID cxWSResponseRunID, final String username,
                                  final String password, final boolean scanTimeOutEnabled, final long scanTimeoutDuration)
            throws AbortException, InterruptedException {
        assert sessionId != null : "Trying to track scan progress before login";

        final long jobStartTime = System.currentTimeMillis();
        int retryAttempts = CxConfig.getServerCallRetryNumber();

        boolean locReported = false;
        String previousMessage = "";
        while (true) {
            String newMessage = "";
            try {
                Thread.sleep(10L * 1000);

                if (scanTimeOutEnabled
                        && jobStartTime + scanTimeoutDuration * MILISECONDS_IN_MINUTE < System.currentTimeMillis()) {
                    logger.info("Scan duration exceeded timeout threshold");
                    return 0;
                }

                CxWSResponseScanStatus status = this.getScanStatus(cxWSResponseRunID);

                switch (status.getCurrentStatus()) {
                    // In progress states
                    case WAITING_TO_PROCESS:
                        newMessage = "Scan job waiting for processing";
                        previousMessage = cleanLogger(previousMessage, newMessage);
                        break;

                    case QUEUED:
                        if (!locReported) {
                            logger.info("Source contains: " + status.getLOC() + " lines of code.");
                            locReported = true;
                        }
                        newMessage = "Scan job queued at position: " + status.getQueuePosition();
                        previousMessage = cleanLogger(previousMessage, newMessage);
                        break;

                    case UNZIPPING:
                        logger.info("Unzipping: " + status.getCurrentStagePercent() + "% finished");
                        logger.info("LOC: " + status.getLOC());
                        logger.info("StageMessage: " + status.getStageMessage());
                        logger.info("StepMessage: " + status.getStepMessage());
                        logger.info("StepDetails: " + status.getStepDetails());
                        break;

                    case WORKING:

                        newMessage = "Scanning: " + status.getStageMessage() + " " + status.getStepDetails()
                                + " (Current stage progress: " + status.getCurrentStagePercent() + "%, Total progress: "
                                + status.getTotalPercent() + "%)";

                        previousMessage = cleanLogger(previousMessage, newMessage);
                        break;

                    // End of progress states
                    case FINISHED:
                        logger.info("Scan Finished Successfully -  RunID: " + status.getRunId() + " ScanID:"
                                + status.getScanId());
                        return status.getScanId();

                    case FAILED:
                    case DELETED:
                    case UNKNOWN:
                    case CANCELED:
                        String message = "Scan " + status.getStageName() + " -  RunID: " + status.getRunId() + " ScanID: "
                                + status.getScanId() + " Server scan status: " + status.getStageMessage();
                        logger.info(message);
                        throw new AbortException(message);
                }

            } catch (AbortException | WebServiceException e) {
                if (e.getMessage().contains("Unauthorized") || e.getMessage().contains("ReConnect")) {
                    RestoreSession(username, password);
                } else if (retryAttempts > 0) {
                    retryAttempts--;
                } else {
                    throw e;
                }
            }
        }
    }

    private void RestoreSession(String username, String password) throws AbortException {
        logger.info("Session was rejected by the Checkmarx server, trying to re-login");
        this.login(username, password);
    }

    public CxWSCreateReportResponse generateScanReport(long scanId, CxWSReportType reportType) throws AbortException {
        assert sessionId != null : "Trying to retrieve scan report before login";

        CxWSReportRequest cxWSReportRequest = new CxWSReportRequest();
        cxWSReportRequest.setScanID(scanId);
        cxWSReportRequest.setType(reportType);
        logger.info("Requesting " + reportType.toString().toUpperCase() + " Scan Report Generation");

        int retryAttempts = CxConfig.getServerCallRetryNumber();
        CxWSCreateReportResponse cxWSCreateReportResponse;
        do {
            cxWSCreateReportResponse = cxJenkinsWebServiceSoap.createScanReport(sessionId, cxWSReportRequest);
            if (!cxWSCreateReportResponse.isIsSuccesfull()) {
                retryAttempts--;
                logger.error("Error requesting scan report generation: " + cxWSCreateReportResponse.getErrorMessage());
            }
        } while (!cxWSCreateReportResponse.isIsSuccesfull() && retryAttempts > 0);

        if (!cxWSCreateReportResponse.isIsSuccesfull()) {
            String message = "Error requesting scan report generation: " + cxWSCreateReportResponse.getErrorMessage();
            logger.error(message);
            throw new AbortException(message);
        }

        return cxWSCreateReportResponse;
    }

    public void retrieveScanReport(long reportId, File reportFile, CxWSReportType reportType) throws AbortException,
            InterruptedException {
        // Wait for the report to become ready

        String previousMessage = "";
        while (true) {
            CxWSReportStatusResponse cxWSReportStatusResponse = cxJenkinsWebServiceSoap.getScanReportStatus(sessionId,
                    reportId);
            if (!cxWSReportStatusResponse.isIsSuccesfull()) {
                String message = "Error retrieving scan report status: " + cxWSReportStatusResponse.getErrorMessage();
                logger.error(message);
                throw new AbortException(message);
            }
            if (cxWSReportStatusResponse.isIsFailed()) {
                String message = "Failed to create scan report";
                logger.error("Web method getScanReportStatus returned status response with isFailed field set to true");
                logger. error(message);
                throw new AbortException(message);
            }

            if (cxWSReportStatusResponse.isIsReady()) {
                logger.info("Scan report generated on Checkmarx server");
                break;
            }

            previousMessage = cleanLogger(previousMessage, reportType.toString().toUpperCase() + " Report generation in progress");

            Thread.sleep(5L * 1000);
        }

        CxWSResponseScanResults cxWSResponseScanResults = cxJenkinsWebServiceSoap.getScanReport(sessionId, reportId);
        if (!cxWSResponseScanResults.isIsSuccesfull()) {
            String message = "Error retrieving scan report: " + cxWSResponseScanResults.getErrorMessage();
            logger.error(message);
            throw new AbortException(message);
        }

		// Save results on disk
		try {
			FileOutputStream fileOutputStream = new FileOutputStream(reportFile);
			IOUtils.write(cxWSResponseScanResults.getScanResults(), fileOutputStream);
			fileOutputStream.close();

        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            String message = "Can't create report file: " + reportFile.getAbsolutePath();
            logger.error(message);
            throw new AbortException(message);
        }
        logger.info("Scan report written to: " + reportFile.getAbsolutePath());
    }

    public List<ProjectDisplayData> getProjectsDisplayData() throws AbortException {
        assert sessionId != null : "Trying to retrieve projects display data before login";

        CxWSResponseProjectsDisplayData cxWSResponseProjectsDisplayData = this.cxJenkinsWebServiceSoap
                .getProjectsDisplayData(this.sessionId);
        if (!cxWSResponseProjectsDisplayData.isIsSuccesfull()) {
            String message = "Error retrieving projects display data from server: "
                    + cxWSResponseProjectsDisplayData.getErrorMessage();
            logger.error(message);
            throw new AbortException(message);
        }

        return cxWSResponseProjectsDisplayData.getProjectList().getProjectDisplayData();
    }

    public List<Preset> getPresets() throws AbortException {
        assert sessionId != null : "Trying to retrieve presetes before login";
        CxWSResponsePresetList cxWSResponsePresetList = this.cxJenkinsWebServiceSoap.getPresetList(this.sessionId);
        if (!cxWSResponsePresetList.isIsSuccesfull()) {
            String message = "Error retrieving presets from server: " + cxWSResponsePresetList.getErrorMessage();
            logger.error(message);
            throw new AbortException(message);
        }
        return cxWSResponsePresetList.getPresetList().getPreset();
    }

    // Source encoding is called "configuration" in server terms
    public List<ConfigurationSet> getSourceEncodings() throws AbortException {
        assert sessionId != null : "Trying to retrieve configurations before login";
        CxWSResponseConfigSetList cxWSResponseConfigSetList = this.cxJenkinsWebServiceSoap
                .getConfigurationSetList(sessionId);
        if (!cxWSResponseConfigSetList.isIsSuccesfull()) {
            String message = "Error retrieving configurations from server: "
                    + cxWSResponseConfigSetList.getErrorMessage();
            logger.error(message);
            throw new AbortException(message);
        }
        return cxWSResponseConfigSetList.getConfigSetList().getConfigurationSet();
    }

    public CxWSBasicRepsonse validateProjectName(String cxProjectName, String groupId) {
        assert sessionId != null : "Trying to validate project name before login";
        return this.cxJenkinsWebServiceSoap.isValidProjectName(sessionId, cxProjectName, groupId);
    }

    private Pair<byte[], byte[]> createScanSoapMessage(Object request, Class inputType,
                                                       ProjectSettings projectSettings, LocalCodeContainer localCodeContainer, boolean visibleToOtherUsers,
                                                       boolean isPublicScan) {
        final String soapMessageHead = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<soap:Envelope xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                + "xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" "
                + "xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" + "  <soap:Body>\n";

        final String soapMessageTail = "\n  </soap:Body>\n</soap:Envelope>";
        final String zippedFileOpenTag = "<ZippedFile>";
        final String zippedFileCloseTag = "</ZippedFile>";

        try {
            final JAXBContext context = JAXBContext.newInstance(inputType);
            final Marshaller marshaller = context.createMarshaller();

            StringWriter scanMessage = new StringWriter();
            scanMessage.write(soapMessageHead);

            // Nullify the zippedFile field, and save its old value for
            // restoring later
            final byte[] oldZippedFileValue = localCodeContainer.getZippedFile();
            localCodeContainer.setZippedFile(new byte[]{});

            marshaller.setProperty(Marshaller.JAXB_FRAGMENT, true);
            marshaller.marshal(request, scanMessage);
            localCodeContainer.setZippedFile(oldZippedFileValue); // Restore the
            // old value

            scanMessage.write(soapMessageTail);
            // Here we split the message around <ZippedFile></ZippedFile>
            // substring. We know that the opening
            // and closing tag are adjacent because the zippedFile property was
            // set to empty byte array
            final String[] parts = scanMessage.toString().split(zippedFileOpenTag + zippedFileCloseTag);
            assert parts.length == 2;
            final String startPart = parts[0] + zippedFileOpenTag;
            final String endPart = zippedFileCloseTag + parts[1];

            return Pair.of(startPart.getBytes("UTF-8"), endPart.getBytes("UTF-8"));
        } catch (JAXBException | UnsupportedEncodingException e) {

            // Getting here indicates a bug
            logger.error(e.getMessage(), e);
            throw new RuntimeException("Eror creating SOAP message", e);
        }
    }

    public List<Group> getAssociatedGroups() throws AbortException {
        assert sessionId != null : "Trying to retrieve teams before login";

        final CxWSResponseGroupList associatedGroupsList = this.cxJenkinsWebServiceSoap
                .getAssociatedGroupsList(sessionId);
        if (!associatedGroupsList.isIsSuccesfull()) {
            String message = "Error retrieving associated groups (teams) from server: "
                    + associatedGroupsList.getErrorMessage();
            logger.error(message);
            throw new AbortException(message);
        }

        return associatedGroupsList.getGroupList().getGroup();
    }

    public CxWSResponseRunID runScanAndAddToProject(ProjectSettings projectSettings,
                                                    LocalCodeContainer localCodeContainer, boolean visibleToOtherUsers, boolean isPublicScan,
                                                    final FilePath base64ZipFile, String comment) throws AbortException {
        assert sessionId != null;

        RunScanAndAddToProject scan = new RunScanAndAddToProject();
        scan.setLocalCodeContainer(localCodeContainer);
        scan.setSessionId(sessionId);
        scan.setProjectSettings(projectSettings);
        scan.setVisibleToUtherUsers(visibleToOtherUsers);
        scan.setIsPublicScan(isPublicScan);
        scan.setComment(comment);

        Pair<byte[], byte[]> soapMeassage = createScanSoapMessage(scan, RunScanAndAddToProject.class, projectSettings,
                localCodeContainer, visibleToOtherUsers, isPublicScan);

        return scan(localCodeContainer, visibleToOtherUsers, isPublicScan, base64ZipFile, "RunScanAndAddToProject",
                soapMeassage, new RunScanAndAddToProjectXmlResponseParser());
    }

    public CxWSResponseRunID runIncrementalScan(ProjectSettings projectSettings, LocalCodeContainer localCodeContainer,
                                                boolean visibleToOtherUsers, boolean isPublicScan, final FilePath base64ZipFile, String comment) throws AbortException {
        assert sessionId != null;

        RunIncrementalScan scan = new RunIncrementalScan();
        scan.setLocalCodeContainer(localCodeContainer);
        scan.setSessionId(sessionId);
        scan.setProjectSettings(projectSettings);
        scan.setVisibleToUtherUsers(visibleToOtherUsers);
        scan.setIsPublicScan(isPublicScan);
        scan.setComment(comment);

        Pair<byte[], byte[]> soapMeassage = createScanSoapMessage(scan, RunIncrementalScan.class, projectSettings,
                localCodeContainer, visibleToOtherUsers, isPublicScan);

        return scan(localCodeContainer, visibleToOtherUsers, isPublicScan, base64ZipFile, "RunIncrementalScan",
                soapMeassage, new RunIncrementalScanXmlResponseParser());
    }

    public CxWSResponseRunID createAndRunProject(ProjectSettings projectSettings,
                                                 LocalCodeContainer localCodeContainer, boolean visibleToOtherUsers, boolean isPublicScan,
                                                 final FilePath base64ZipFile, String comment) throws AbortException {
        assert sessionId != null;

        CreateAndRunProject scan = new CreateAndRunProject();
        scan.setLocalCodeContainer(localCodeContainer);
        scan.setSessionID(sessionId);
        scan.setProjectSettings(projectSettings);
        scan.setVisibleToOtherUsers(visibleToOtherUsers);
        scan.setIsPublicScan(isPublicScan);
        scan.setComment(comment);

        Pair<byte[], byte[]> soapMessage = createScanSoapMessage(scan, CreateAndRunProject.class, projectSettings,
                localCodeContainer, visibleToOtherUsers, isPublicScan);

        return scan(localCodeContainer, visibleToOtherUsers, isPublicScan, base64ZipFile, "CreateAndRunProject",
                soapMessage, new CreateAndRunProjectXmlResponseParser());
    }

    public Boolean isOsaLicenseValid() {
        CxWSResponseServerLicenseData response = cxJenkinsWebServiceSoap.getServerLicenseData(sessionId);
        return response.isIsOsaEnabled();
    }

    public long getProjectId(ProjectSettings projectSettings) throws AbortException {
        CxWSResponseProjectsDisplayData projects = cxJenkinsWebServiceSoap.getProjectsDisplayData(sessionId);

        final String groupId = projectSettings.getAssociatedGroupID();
        final List<Group> groups = getAssociatedGroups();
        final List<Group> selected = filter(having(on(Group.class).getID(), Matchers.equalTo(groupId)), groups);

        if (selected.isEmpty()) {
            final String message = "Could not translate group (team) id: " + groupId + " to group name\n"
                    + "Open the Job configuration page, and select a team.\n";
            logger.error(message);
            throw new AbortException(message);

        } else if (selected.size() > 1) {
            logger.error("Server returned more than one group with id: " + groupId);
            for (Group g : selected) {
                logger.error("Group Id: " + g.getID() + " groupName: " + g.getGroupName());
            }
        }

        long projectId = 0;
        if (projects != null && projects.isIsSuccesfull()) {
            for (ProjectDisplayData projectDisplayData : projects.getProjectList().getProjectDisplayData()) {
                if (projectDisplayData.getProjectName().equals(projectSettings.getProjectName())
                        && projectDisplayData.getGroup().equals(selected.get(0).getGroupName())) {
                    projectId = projectDisplayData.getProjectID();
                    break;
                }
            }
        }

        if (projectId == 0) {
            throw new AbortException("Can't find exsiting project to scan");
        }

        return projectId;
    }

    /**
     * Same as "scan" method, but works by streaming the
     * LocalCodeContainer.zippedFile contents. NOTE: The attribute
     * LocalCodeContainer.zippedFile inside args is REPLACED by empty byte
     * array, and base64ZipFile temp file is used instead.
     *
     * @param base64ZipFile - Temp file used instead of LocalCodeContainer.zippedFile
     *                      attribute, should contain zipped sources encoded with base 64
     *                      encoding
     * @return object which is similar to the return value of scan web service
     * method
     * @throws AbortException
     */
    private CxWSResponseRunID scan(LocalCodeContainer localCodeContainer, boolean visibleToOtherUsers,
                                   boolean isPublicScan, final FilePath base64ZipFile, String soapActionName,
                                   Pair<byte[], byte[]> soapMessage, XmlResponseParser xmlResponseParser) throws AbortException {
        assert sessionId != null;
        int retryAttemptsLeft = CxConfig.getServerCallRetryNumber();

        while (true) {
            try {
                return sendScanRequest(base64ZipFile, soapActionName, soapMessage, xmlResponseParser);
            } catch (AbortException abort) {
                if (retryAttemptsLeft > 0) {
                    retryAttemptsLeft--;
                } else {
                    throw abort;
                }

            }
        }
    }

    private CxWSResponseRunID sendScanRequest(final FilePath base64ZipFile, String soapActionName,
                                              Pair<byte[], byte[]> soapMessage, XmlResponseParser xmlResponseParser) throws AbortException {
        try {

            // Create HTTP connection

            final HttpURLConnection streamingUrlConnection = (HttpURLConnection) webServiceUrl.openConnection();
            streamingUrlConnection.addRequestProperty("Content-Type", "text/xml; charset=utf-8");
            streamingUrlConnection.addRequestProperty("SOAPAction",
                    String.format("\"http://Checkmarx.com/v7/%s\"", soapActionName));
            streamingUrlConnection.setDoOutput(true);
            // Calculate the length of the soap message
            final long length = soapMessage.getLeft().length + soapMessage.getRight().length + base64ZipFile.length();
            streamingUrlConnection.setFixedLengthStreamingMode((int) length);
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

            CxWSResponseRunID cxWSResponseRunID = xmlResponseParser.parse(streamingUrlConnection.getInputStream());

            if (!cxWSResponseRunID.isIsSuccesfull()) {
                String message = "Submission of sources for scan failed: \n" + cxWSResponseRunID.getErrorMessage();
                throw new AbortException(message);
            }

            return cxWSResponseRunID;

        } catch (HttpRetryException e) {
            String consoleMessage = "\nCheckmarx plugin for Jenkins does not support Single sign-on authentication."
                    + "\nPlease, configure Checkmarx server to work in Anonymous authentication mode.\n";
            logger.error(consoleMessage);
            throw new AbortException(e.getMessage());
        } catch (IOException | JAXBException | XMLStreamException | InterruptedException e) {
            logger.error(e.getMessage(), e);
            throw new AbortException(e.getMessage());
        }
    }

    /**
     * Cancel scan on Checkmarx server
     *
     * @param runId run ID of the scan
     * @return server response
     */
    public CxWSBasicRepsonse cancelScan(String runId) {
        return cxJenkinsWebServiceSoap.cancelScan(sessionId, runId);
    }

    /**
     * Cancel report generation on Checkmarx server
     *
     * @param reportId ID of the report
     * @return server response
     */
    public CxWSBasicRepsonse cancelScanReport(long reportId) {
        return cxJenkinsWebServiceSoap.cancelScanReport(sessionId, reportId);
    }

    public CxWSResponseScanStatusArray getQueuedScans() {
        return cxJenkinsWebServiceSoap.getScansStatuses(sessionId);
    }
}
