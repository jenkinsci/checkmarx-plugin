package com.checkmarx.jenkins;

import static ch.lambdaj.Lambda.filter;
import static ch.lambdaj.Lambda.having;
import static ch.lambdaj.Lambda.on;
import hudson.AbortException;
import hudson.FilePath;
import hudson.util.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpRetryException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.stream.XMLStreamException;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.WebServiceException;

import jenkins.model.Jenkins;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.Logger;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.checkmarx.jenkins.xmlresponseparser.CreateAndRunProjectXmlResponseParser;
import com.checkmarx.jenkins.xmlresponseparser.RunIncrementalScanXmlResponseParser;
import com.checkmarx.jenkins.xmlresponseparser.RunScanAndAddToProjectXmlResponseParser;
import com.checkmarx.jenkins.xmlresponseparser.XmlResponseParser;
import com.checkmarx.ws.CxJenkinsWebService.ConfigurationSet;
import com.checkmarx.ws.CxJenkinsWebService.CreateAndRunProject;
import com.checkmarx.ws.CxJenkinsWebService.Credentials;
import com.checkmarx.ws.CxJenkinsWebService.CxJenkinsWebService;
import com.checkmarx.ws.CxJenkinsWebService.CxJenkinsWebServiceSoap;
import com.checkmarx.ws.CxJenkinsWebService.CxWSBasicRepsonse;
import com.checkmarx.ws.CxJenkinsWebService.CxWSCreateReportResponse;
import com.checkmarx.ws.CxJenkinsWebService.CxWSReportRequest;
import com.checkmarx.ws.CxJenkinsWebService.CxWSReportStatusResponse;
import com.checkmarx.ws.CxJenkinsWebService.CxWSReportType;
import com.checkmarx.ws.CxJenkinsWebService.CxWSResponseConfigSetList;
import com.checkmarx.ws.CxJenkinsWebService.CxWSResponseGroupList;
import com.checkmarx.ws.CxJenkinsWebService.CxWSResponseLoginData;
import com.checkmarx.ws.CxJenkinsWebService.CxWSResponsePresetList;
import com.checkmarx.ws.CxJenkinsWebService.CxWSResponseProjectsDisplayData;
import com.checkmarx.ws.CxJenkinsWebService.CxWSResponseRunID;
import com.checkmarx.ws.CxJenkinsWebService.CxWSResponseScanResults;
import com.checkmarx.ws.CxJenkinsWebService.CxWSResponseScanStatus;
import com.checkmarx.ws.CxJenkinsWebService.Group;
import com.checkmarx.ws.CxJenkinsWebService.LocalCodeContainer;
import com.checkmarx.ws.CxJenkinsWebService.Preset;
import com.checkmarx.ws.CxJenkinsWebService.ProjectDisplayData;
import com.checkmarx.ws.CxJenkinsWebService.ProjectSettings;
import com.checkmarx.ws.CxJenkinsWebService.RunIncrementalScan;
import com.checkmarx.ws.CxJenkinsWebService.RunScanAndAddToProject;
import com.checkmarx.ws.CxWSResolver.CxClientType;
import com.checkmarx.ws.CxWSResolver.CxWSResolver;
import com.checkmarx.ws.CxWSResolver.CxWSResolverSoap;
import com.checkmarx.ws.CxWSResolver.CxWSResponseDiscovery;

/**
 * Wraps all Web services invocations
 *
 * @author denis
 * @since 13/11/2013
 */
public class CxWebService {

	private static final int WEBSERVICE_API_VERSION = 1;
	private static final String CXWSRESOLVER_PATH = "/cxwebinterface/cxwsresolver.asmx";
	private static final int LCID = 1033; // English
	private static final int MILISEOUNDS_IN_HOUR = 1000 * 60 * 60;

	private final Logger logger;
	private String sessionId;
	private CxJenkinsWebServiceSoap cxJenkinsWebServiceSoap;
	private final URL webServiceUrl;

	public CxWebService(String serverUrl) throws MalformedURLException, AbortException {
		this(serverUrl, null);
	}

	public CxWebService(String serverUrl, int requestTimeoutDuration) throws MalformedURLException, AbortException {
		this(serverUrl, requestTimeoutDuration, null);
	}
	
	public CxWebService(String serverUrl, int requestTimeoutDuration, final String loggerSuffix) throws MalformedURLException, AbortException {
		this(serverUrl, loggerSuffix);
		setClientTimeout(requestTimeoutDuration);
	}

	public CxWebService(@NotNull final String serverUrl, @Nullable final String loggerSuffix)
			throws MalformedURLException, AbortException {
		logger = CxLogUtils.loggerWithSuffix(getClass(), loggerSuffix);

		@Nullable
		CxScanBuilder.DescriptorImpl descriptor = (CxScanBuilder.DescriptorImpl) Jenkins.getInstance().getDescriptor(
				CxScanBuilder.class);
		if (descriptor != null && !descriptor.isEnableCertificateValidation()) {
			logger.info("SSL/TLS Certificate Validation Disabled");
			CxSSLUtility.disableSSLCertificateVerification();
		}

		logger.info("Establishing connection with Checkmarx server at: " + serverUrl);
		URL serverUrlUrl = new URL(serverUrl);
		if (serverUrlUrl.getPath().length() > 0) {
			String message = "Checkmarx server url must not contain path: " + serverUrl;
			logger.debug(message);
			throw new AbortException(message);
		}
		URL resolverUrl = new URL(serverUrl + CXWSRESOLVER_PATH);

		logger.debug("Resolver url: " + resolverUrl);
		CxWSResolver cxWSResolver;
		try {
			cxWSResolver = new CxWSResolver(resolverUrl);
		} catch (javax.xml.ws.WebServiceException e) {
			logger.error("Failed to resolve Checkmarx webservice url with resolver at: " + resolverUrl, e);
			throw new AbortException("Checkmarx server was not found on url: " + serverUrl);
		}
		CxWSResolverSoap cxWSResolverSoap = cxWSResolver.getCxWSResolverSoap();
		CxWSResponseDiscovery cxWSResponseDiscovery = cxWSResolverSoap.getWebServiceUrl(CxClientType.JENKINS,
				WEBSERVICE_API_VERSION);
		if (!cxWSResponseDiscovery.isIsSuccesfull()) {
			String message = "Failed to resolve Checkmarx webservice url: \n" + cxWSResponseDiscovery.getErrorMessage();
			logger.error(message);
			throw new AbortException(message);
		}

		webServiceUrl = new URL(cxWSResponseDiscovery.getServiceURL());
		logger.debug("Webservice url: " + webServiceUrl);
		CxJenkinsWebService cxJenkinsWebService = new CxJenkinsWebService(webServiceUrl);
		
		cxJenkinsWebServiceSoap = cxJenkinsWebService.getCxJenkinsWebServiceSoap();
	}

	public void setClientTimeout(int milliseconds) {
		Map<String, Object> requestContext = ((BindingProvider)cxJenkinsWebServiceSoap).getRequestContext();
		requestContext.put("com.sun.xml.internal.ws.connect.timeout", milliseconds);
		requestContext.put("com.sun.xml.internal.ws.request.timeout", milliseconds);
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
		logger.debug("Login successful, sessionId: " + sessionId);
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

	public long trackScanProgress(final CxWSResponseRunID cxWSResponseRunID, final String username,
			final String password, final boolean scanTimeOutEnabled, final long scanTimeoutDuration)
			throws AbortException, InterruptedException {
		assert sessionId != null : "Trying to track scan progress before login";

		final long jobStartTime = System.currentTimeMillis();
		int retryAttempts = CxConfig.getServerCallRetryNumber();

		boolean locReported = false;
		while (true) {
			try {
				Thread.sleep(10L * 1000);

				if (scanTimeOutEnabled
						&& jobStartTime + scanTimeoutDuration * MILISEOUNDS_IN_HOUR < System.currentTimeMillis()) {
					logger.info("Scan duration exceeded timeout threshold");
					return 0;
				}

				CxWSResponseScanStatus status = this.getScanStatus(cxWSResponseRunID);

				switch (status.getCurrentStatus()) {
				// In progress states
				case WAITING_TO_PROCESS:
					logger.info("Scan job waiting for processing");
					break;

				case QUEUED:
					if (!locReported) {
						logger.info("Source contains: " + status.getLOC() + " lines of code.");
						locReported = true;
					}
					logger.info("Scan job queued at position: " + status.getQueuePosition());
					break;

				case UNZIPPING:
					logger.info("Unzipping: " + status.getCurrentStagePercent() + "% finished");
					logger.info("LOC: " + status.getLOC());
					logger.info("StageMessage: " + status.getStageMessage());
					logger.info("StepMessage: " + status.getStepMessage());
					logger.info("StepDetails: " + status.getStepDetails());
					break;

				case WORKING:
					logger.info("Scanning: " + status.getStageMessage() + " " + status.getStepDetails()
							+ " (Current stage progress: " + status.getCurrentStagePercent() + "%, Total progress: "
							+ status.getTotalPercent() + "%)");
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

				// Here we handle a case where the sessionId was timed out in
				// the server
				// and we need to re-login to continue working. The default
				// sessionId
				// timeout in the server is 24 hours.
				if (e.getMessage().contains("Unauthorized")) {
					logger.info("Session was rejected by the Checkmarx server, trying to re-login");
					this.login(username, password);
					continue;
				} else if (retryAttempts > 0) {
					retryAttempts--;
				} else {
					throw e;
				}
			}
		}
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
				logger.warn("Error requesting scan report generation: " + cxWSCreateReportResponse.getErrorMessage());
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
				logger.error(message);
				throw new AbortException(message);
			}

			if (cxWSReportStatusResponse.isIsReady()) {
				logger.info("Scan report generated on Checkmarx server");
				break;
			}

			logger.info(reportType.toString().toUpperCase() + " Report generation in progress");

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
			logger.debug(e);
			String message = "Can't create report file: " + reportFile.getAbsolutePath();
			logger.info(message);
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
			localCodeContainer.setZippedFile(new byte[] {});

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
			final FilePath base64ZipFile) throws AbortException {
		assert sessionId != null;

		RunScanAndAddToProject scan = new RunScanAndAddToProject();
		scan.setLocalCodeContainer(localCodeContainer);
		scan.setSessionId(sessionId);
		scan.setProjectSettings(projectSettings);
		scan.setVisibleToUtherUsers(visibleToOtherUsers);
		scan.setIsPublicScan(isPublicScan);

		Pair<byte[], byte[]> soapMeassage = createScanSoapMessage(scan, RunScanAndAddToProject.class, projectSettings,
				localCodeContainer, visibleToOtherUsers, isPublicScan);

		return scan(localCodeContainer, visibleToOtherUsers, isPublicScan, base64ZipFile, "RunScanAndAddToProject",
				soapMeassage, new RunScanAndAddToProjectXmlResponseParser());
	}

	public CxWSResponseRunID runIncrementalScan(ProjectSettings projectSettings, LocalCodeContainer localCodeContainer,
			boolean visibleToOtherUsers, boolean isPublicScan, final FilePath base64ZipFile) throws AbortException {
		assert sessionId != null;

		RunIncrementalScan scan = new RunIncrementalScan();
		scan.setLocalCodeContainer(localCodeContainer);
		scan.setSessionId(sessionId);
		scan.setProjectSettings(projectSettings);
		scan.setVisibleToUtherUsers(visibleToOtherUsers);
		scan.setIsPublicScan(isPublicScan);

		Pair<byte[], byte[]> soapMeassage = createScanSoapMessage(scan, RunIncrementalScan.class, projectSettings,
				localCodeContainer, visibleToOtherUsers, isPublicScan);

		return scan(localCodeContainer, visibleToOtherUsers, isPublicScan, base64ZipFile, "RunIncrementalScan",
				soapMeassage, new RunIncrementalScanXmlResponseParser());
	}

	public CxWSResponseRunID createAndRunProject(ProjectSettings projectSettings,
			LocalCodeContainer localCodeContainer, boolean visibleToOtherUsers, boolean isPublicScan,
			final FilePath base64ZipFile) throws AbortException {
		assert sessionId != null;

		CreateAndRunProject scan = new CreateAndRunProject();
		scan.setLocalCodeContainer(localCodeContainer);
		scan.setSessionID(sessionId);
		scan.setProjectSettings(projectSettings);
		scan.setVisibleToOtherUsers(visibleToOtherUsers);
		scan.setIsPublicScan(isPublicScan);

		Pair<byte[], byte[]> soapMessage = createScanSoapMessage(scan, CreateAndRunProject.class, projectSettings,
				localCodeContainer, visibleToOtherUsers, isPublicScan);

		return scan(localCodeContainer, visibleToOtherUsers, isPublicScan, base64ZipFile, "CreateAndRunProject",
				soapMessage, new CreateAndRunProjectXmlResponseParser());
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
			logger.warn("Server returned more than one group with id: " + groupId);
			for (Group g : selected) {
				logger.warn("Group Id: " + g.getID() + " groupName: " + g.getGroupName());
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
	 * @param base64ZipFile
	 *            - Temp file used instead of LocalCodeContainer.zippedFile
	 *            attribute, should contain zipped sources encoded with base 64
	 *            encoding
	 * @return object which is similar to the return value of scan web service
	 *         method
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
	 * @param runId
	 *            run ID of the scan
	 * @return server response
	 */
	public CxWSBasicRepsonse cancelScan(String runId) {
		return cxJenkinsWebServiceSoap.cancelScan(sessionId, runId);
	}

	/**
	 * Cancel report generation on Checkmarx server
	 *
	 * @param reportId
	 *            ID of the report
	 * @return server response
	 */
	public CxWSBasicRepsonse cancelScanReport(long reportId) {
		return cxJenkinsWebServiceSoap.cancelScanReport(sessionId, reportId);
	}
}
