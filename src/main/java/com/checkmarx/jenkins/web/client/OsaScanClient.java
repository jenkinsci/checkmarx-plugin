package com.checkmarx.jenkins.web.client;

import com.checkmarx.jenkins.web.model.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.log4j.Logger;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.MultiPart;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.media.multipart.file.StreamDataBodyPart;
import org.jetbrains.annotations.NotNull;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Created by zoharby on 09/01/2017.
 */
public class OsaScanClient implements Closeable {

    private static final String ROOT_PATH = "CxRestAPI/";
    private static final String AUTHENTICATION_PATH = "auth/login";
    private static final String ANALYZE_SUMMARY_PATH = "osa/reports";
    private static final String ANALYZE_PATH = "projects/{projectId}/scans";
    private static final String SCAN_STATUS_PATH = "osa/scans/{scanId}";
    private static final String LIBRARIES_PATH = "osa/libraries";
    private static final String CVEs_PATH = "osa/vulnerabilities";
    private static final String FAILED_TO_CONNECT_CX_SERVER_ERROR = "connection to checkmarx server failed";
    private static final String CX_COOKIE = "cxCookie";
    private static final String CSRF_COOKIE = "CXCSRFToken";
    private static final String ACCEPT_HEADER = "Accept";
    private static final String CX_ORIGIN_HEADER = "cxOrigin";
    private static final String CX_ORIGIN_VALUE = "Jenkins";
    private static final String OSA_ZIPPED_FILE_KEY_NAME = "OSAZippedSourceCode";

    private static final int ITEMS_PER_PAGE = 10;

    private AuthenticationRequest authenticationRequest;
    private Client client;
    private WebTarget root;
    private static Logger logger;

    private ObjectMapper mapper = new ObjectMapper();

    Map<String, NewCookie> cookies;

    public OsaScanClient(String hostname, AuthenticationRequest authenticationRequest, Logger logger) {
        this.authenticationRequest = authenticationRequest;
        client = ClientBuilder.newBuilder().register(MultiPartFeature.class).build();
        root = client.target(hostname.trim()).path(ROOT_PATH);
        this.logger = logger;
        cookies = login();
    }


    public CreateScanResponse createScan(CreateScanRequest request) throws IOException {
        final MultiPart multipart = createScanMultiPartRequest(request);
        logger.debug("sending request for osa scan");
        Invocation invocation = root.path(ANALYZE_PATH)
                .resolveTemplate("projectId", request.getProjectId())
                .request()
                .header(CX_ORIGIN_HEADER, CX_ORIGIN_VALUE)
                .cookie(cookies.get(CX_COOKIE))
                .cookie(CSRF_COOKIE, cookies.get(CSRF_COOKIE).getValue())
                .header(CSRF_COOKIE, cookies.get(CSRF_COOKIE).getValue())
                .buildPost(Entity.entity(multipart, multipart.getMediaType()));
        Response response = invokeRequest(invocation);
        validateResponse(response, Response.Status.ACCEPTED, "fail create OSA scan");
        return response.readEntity(CreateScanResponse.class);
    }

    public GetOpenSourceSummaryResponse getOpenSourceSummary(String scanId) throws IOException {
        Invocation invocation = getSummeryByAcceptHeaderInvocation(scanId, "application/json");
        logger.debug("sending request for HTML report");
        Response response = invokeRequest(invocation);
        validateResponse(response, Response.Status.OK, "fail get OSA scan summary results");
        return  response.readEntity(GetOpenSourceSummaryResponse.class);
    }

    public String getOSAScanHtmlResults(String scanId) {
        Invocation invocation = getSummeryByAcceptHeaderInvocation(scanId, "text/html");
        logger.debug("sending request for JSON report");
        Response response = invokeRequest(invocation);
        validateResponse(response, Response.Status.OK, "fail get OSA scan html results");
        return response.readEntity(String.class);
    }

    public byte[] getOSAScanPdfResults(String scanId) {
        Invocation invocation = getSummeryByAcceptHeaderInvocation(scanId, "application/pdf");
        logger.debug("sending request for PDF report");
        Response response = invokeRequest(invocation);
        validateResponse(response, Response.Status.OK, "fail get OSA scan pdf results");
        return response.readEntity(byte[].class);
    }

    public List<Library> getScanResultLibraries(String scanId){
        List<Library> libraryList = new LinkedList<>();
        int lastListSize = ITEMS_PER_PAGE;
        int currentPage = 1;
        while (lastListSize == ITEMS_PER_PAGE) {
            Invocation invocation = getPageRequestInvocation(LIBRARIES_PATH, currentPage, scanId);
            logger.debug("sending request for libraries page number "+ currentPage);
            Response response = invokeRequest(invocation);
            validateResponse(response, Response.Status.OK, "fail get OSA scan libraries");
            try {
                List<Library> libraryPage = mapper.readValue(response.readEntity(String.class), new TypeReference<List<Library>>() {
                });
                if(libraryPage != null) {
                    libraryList.addAll(libraryPage);
                    lastListSize = libraryPage.size();
                }else {
                    break;
                }
            } catch (IOException e) {
                logger.info("failed to parse Libraries", e);
                lastListSize = 0;
            }
            ++currentPage;
        }
        return libraryList;
    }

    public List<CVE> getScanResultCVEs(String scanId){
        List<CVE> cvesList = new LinkedList<>();
        int lastListSize = ITEMS_PER_PAGE;
        int currentPage = 1;
        while (lastListSize == ITEMS_PER_PAGE) {
            Invocation invocation = getPageRequestInvocation(CVEs_PATH, currentPage, scanId);
            logger.debug("sending request for CVE's page number "+ currentPage);
            Response response = invokeRequest(invocation);
            validateResponse(response, Response.Status.OK, "fail get OSA scan CVE's");
            try {
                List<CVE> cvePage = mapper.readValue(response.readEntity(String.class), new TypeReference<List<CVE>>() {
                });
                if(cvePage != null) {
                    lastListSize = cvePage.size();
                    cvesList.addAll(cvePage);
                }else {
                    break;
                }
            } catch (IOException e) {
                logger.info("failed to parse CVE's", e);
                lastListSize = 0;
            }
            ++currentPage;
        }
        return cvesList;
    }


    private Invocation getSummeryByAcceptHeaderInvocation(String scanId, String acceptHeaderValue){
        return  root.path(ANALYZE_SUMMARY_PATH).queryParam("scanId", scanId).request()
                .header(CX_ORIGIN_HEADER, CX_ORIGIN_VALUE)
                .cookie(cookies.get(CX_COOKIE))
                .header(ACCEPT_HEADER, acceptHeaderValue)
                .cookie(CSRF_COOKIE, cookies.get(CSRF_COOKIE).getValue())
                .header(CSRF_COOKIE, cookies.get(CSRF_COOKIE).getValue()).buildGet();
    }

    private Invocation getPageRequestInvocation(String path, int pageNumber, String scanId){
       return root.path(path).queryParam("scanId", scanId)
                .queryParam("page", pageNumber).queryParam("itemsPerPage", ITEMS_PER_PAGE).request()
                .header(CX_ORIGIN_HEADER, CX_ORIGIN_VALUE)
                .cookie(cookies.get(CX_COOKIE))
                .cookie(CSRF_COOKIE, cookies.get(CSRF_COOKIE).getValue())
                .header(CSRF_COOKIE, cookies.get(CSRF_COOKIE).getValue()).buildGet();
    }

    private MultiPart createScanMultiPartRequest(CreateScanRequest request) throws IOException {
        InputStream read = request.getZipFile().read();

        final StreamDataBodyPart filePart = new StreamDataBodyPart (OSA_ZIPPED_FILE_KEY_NAME, read);
        return new FormDataMultiPart()
                .bodyPart(new FormDataBodyPart("origin", Integer.toString(CreateScanRequest.JENKINS_ORIGIN)))
                .bodyPart(filePart);

    }

    @NotNull
    private File getFileFromRequest(CreateScanRequest request) {
        return new File(request.getZipFile().getRemote());
    }

    public void waitForScanToFinish(String scanId) throws InterruptedException {
        Map<String, NewCookie> cookies = login();
        Invocation invocation = root.path(SCAN_STATUS_PATH).resolveTemplate("scanId", scanId)
                .request()
                .header(CX_ORIGIN_HEADER, CX_ORIGIN_VALUE)
                .cookie(cookies.get(CX_COOKIE))
                .cookie(CSRF_COOKIE, cookies.get(CSRF_COOKIE).getValue())
                .header(CSRF_COOKIE, cookies.get(CSRF_COOKIE).getValue())
                .buildGet();
        sampleScan(invocation);
    }

    private void sampleScan(Invocation invocation) throws InterruptedException {
        Boolean scanFinished = false;
        while (!scanFinished){
            Response response = invokeRequest(invocation);
            validateResponse(response, Response.Status.OK, "error occured while waiting for scan to finish");
            if (scanFinished(response)){
                scanFinished = true;
            }else {
                Thread.sleep(5L * 1000);
            }
        }
    }

    private boolean scanFinished(Response response) {
        ScanDetails scanStatusResponse = response.readEntity(ScanDetails.class);
        ScanStatus scanStatus = ScanStatus.fromId(scanStatusResponse.getState().getId());
        switch (scanStatus){
            case NotStarted:
                return false;
            case InProgress:
                return false;
            case Finished:
                return true;
            case Failed:
                throw new WebApplicationException("Scan was unsuccessful");
            default:
                throw new WebApplicationException("Scan Status invalid: " + scanStatus);
        }
    }


    private Map<String, NewCookie> login() {
        Invocation invocation = root.path(AUTHENTICATION_PATH)
                .request()
                .header(CX_ORIGIN_HEADER, CX_ORIGIN_VALUE)
                .buildPost(Entity.entity(authenticationRequest, MediaType.APPLICATION_JSON));
        logger.debug("Authenticating client");
        Response response = invokeRequest(invocation);
        validateResponse(response, Response.Status.OK, "fail to perform login");

        return response.getCookies();
    }

    private Response invokeRequest(Invocation invocation) {
        try {
            return invocation.invoke();
        } catch (ProcessingException exc) {
            return ThrowFailedToConnectCxServerError();
        }
    }

    private void validateResponse(Response response, Response.Status expectedStatus, String message) throws WebApplicationException {
        if (response.getStatus() == Response.Status.SERVICE_UNAVAILABLE.getStatusCode())
            ThrowFailedToConnectCxServerError();
        if (response.getStatus() != expectedStatus.getStatusCode()) {
            throw new WebApplicationException(message + ": " + response.getStatusInfo().toString());
        }
    }

    private Response ThrowFailedToConnectCxServerError() {
        throw new WebApplicationException(FAILED_TO_CONNECT_CX_SERVER_ERROR);
    }

    @Override
    public void close() {
        client.close();
    }
}
