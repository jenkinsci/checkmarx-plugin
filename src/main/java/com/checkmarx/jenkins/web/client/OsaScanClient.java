package com.checkmarx.jenkins.web.client;

import com.checkmarx.jenkins.logger.CxPluginLogger;
import com.checkmarx.jenkins.opensourceanalysis.OSAFile;
import com.checkmarx.jenkins.web.model.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import java.io.Closeable;
import java.io.IOException;
import java.util.*;

/**
 * Created by zoharby on 09/01/2017.
 */
public class OsaScanClient implements Closeable {

    private transient CxPluginLogger logger = new CxPluginLogger();

    private static final String ROOT_PATH = "cxrestapi/";
    private static final String AUTHENTICATION_PATH = "auth/login";
    private static final String CREATE_SCAN_PATH = "osa/scans";

    private static final String ANALYZE_SUMMARY_PATH = "osa/reports";
    private static final String LATEST_SCAN_RESULTS = "osa/scans";
    private static final String SCAN_STATUS_PATH = "osa/scans/{scanId}";
    private static final String LIBRARIES_PATH = "osa/libraries";
    private static final String CVEs_PATH = "osa/vulnerabilities";
    private static final String FAILED_TO_CONNECT_CX_SERVER_ERROR = "connection to checkmarx server failed";
    private static final String CX_COOKIE = "cxCookie";
    private static final String CSRF_COOKIE = "CXCSRFToken";
    private static final String ACCEPT_HEADER = "Accept";
    private static final String CX_ORIGIN_HEADER = "cxOrigin";
    private static final String CX_ORIGIN_VALUE = "Jenkins";

    private static final int ITEMS_PER_PAGE = 10;
    private AuthenticationRequest authenticationRequest;
    private Client client;
    private WebTarget root;

    private ObjectMapper mapper = new ObjectMapper();

    private Map<String, NewCookie> cookies;

    public OsaScanClient(String hostname, AuthenticationRequest authenticationRequest) {
        this.authenticationRequest = authenticationRequest;
        client = ClientBuilder.newBuilder().build();
        root = client.target(hostname.trim()).path(ROOT_PATH);
        cookies = login();
    }

    public CreateScanResponse createScan(long projectId, List<OSAFile> osaFileList) {

        CreateOSAScanRequest req = new CreateOSAScanRequest(projectId, CX_ORIGIN_VALUE, osaFileList);
        logger.info("sending request for osa scan");
        Invocation invocation = root.path(CREATE_SCAN_PATH)
                .request()
                .cookie(cookies.get(CX_COOKIE))
                .cookie(CSRF_COOKIE, cookies.get(CSRF_COOKIE).getValue())
                .header(CSRF_COOKIE, cookies.get(CSRF_COOKIE).getValue())
                .buildPost(Entity.entity(req, MediaType.APPLICATION_JSON));
        Response response = invokeRequest(invocation);
        validateResponse(response, Response.Status.ACCEPTED, "fail create OSA scan");
        return response.readEntity(CreateScanResponse.class);
    }

    public GetOpenSourceSummaryResponse getOpenSourceSummary(String scanId) throws IOException {
        Invocation invocation = getSummeryByAcceptHeaderInvocation(scanId, "application/json");
        logger.info("sending request for HTML report");
        Response response = invokeRequest(invocation);
        validateResponse(response, Response.Status.OK, "fail get OSA scan summary results");
        return response.readEntity(GetOpenSourceSummaryResponse.class);
    }

    public String getOSAScanHtmlResults(String scanId) {
        Invocation invocation = getSummeryByAcceptHeaderInvocation(scanId, "text/html");
        logger.info("sending request for JSON report");
        Response response = invokeRequest(invocation);
        validateResponse(response, Response.Status.OK, "fail get OSA scan html results");
        return response.readEntity(String.class);
    }

    public byte[] getOSAScanPdfResults(String scanId) {
        Invocation invocation = getSummeryByAcceptHeaderInvocation(scanId, "application/pdf");
        logger.info("sending request for PDF report");
        Response response = invokeRequest(invocation);
        validateResponse(response, Response.Status.OK, "fail get OSA scan pdf results");
        return response.readEntity(byte[].class);
    }

    public List<Library> getScanResultLibraries(String scanId) {
        List<Library> libraryList = new LinkedList<>();
        int lastListSize = ITEMS_PER_PAGE;
        int currentPage = 1;
        while (lastListSize == ITEMS_PER_PAGE) {
            Invocation invocation = getPageRequestInvocation(LIBRARIES_PATH, currentPage, scanId);
            logger.info("sending request for libraries page number " + currentPage);
            Response response = invokeRequest(invocation);
            validateResponse(response, Response.Status.OK, "fail get OSA scan libraries");
            try {
                List<Library> libraryPage = mapper.readValue(response.readEntity(String.class), new TypeReference<List<Library>>() {
                });
                if (libraryPage != null) {
                    libraryList.addAll(libraryPage);
                    lastListSize = libraryPage.size();
                } else {
                    break;
                }
            } catch (IOException e) {
                logger.error("failed to parse Libraries: "+e.getMessage(), e);
                lastListSize = 0;
            }
            ++currentPage;
        }
        return libraryList;
    }

    public List<CVE> getScanResultCVEs(String scanId) {
        List<CVE> cvesList = new LinkedList<>();
        int lastListSize = ITEMS_PER_PAGE;
        int currentPage = 1;
        while (lastListSize == ITEMS_PER_PAGE) {
            Invocation invocation = getPageRequestInvocation(CVEs_PATH, currentPage, scanId);
            logger.info("sending request for CVE's page number " + currentPage);
            Response response = invokeRequest(invocation);
            validateResponse(response, Response.Status.OK, "fail get OSA scan CVE's");
            try {
                List<CVE> cvePage = mapper.readValue(response.readEntity(String.class), new TypeReference<List<CVE>>() {
                });
                if (cvePage != null) {
                    lastListSize = cvePage.size();
                    cvesList.addAll(cvePage);
                } else {
                    break;
                }
            } catch (IOException e) {
                logger.error("failed to parse CVE's", e);
                lastListSize = 0;
            }
            ++currentPage;
        }
        return cvesList;
    }


    private Invocation getSummeryByAcceptHeaderInvocation(String scanId, String acceptHeaderValue) {
        return root.path(ANALYZE_SUMMARY_PATH).queryParam("scanId", scanId).request()
                .header(CX_ORIGIN_HEADER, CX_ORIGIN_VALUE)
                .cookie(cookies.get(CX_COOKIE))
                .header(ACCEPT_HEADER, acceptHeaderValue)
                .cookie(CSRF_COOKIE, cookies.get(CSRF_COOKIE).getValue())
                .header(CSRF_COOKIE, cookies.get(CSRF_COOKIE).getValue()).buildGet();
    }

    private Invocation getPageRequestInvocation(String path, int pageNumber, String scanId) {
        return root.path(path).queryParam("scanId", scanId)
                .queryParam("page", pageNumber).queryParam("itemsPerPage", ITEMS_PER_PAGE).request()
                .header(CX_ORIGIN_HEADER, CX_ORIGIN_VALUE)
                .cookie(cookies.get(CX_COOKIE))
                .cookie(CSRF_COOKIE, cookies.get(CSRF_COOKIE).getValue())
                .header(CSRF_COOKIE, cookies.get(CSRF_COOKIE).getValue()).buildGet();
    }

    public ScanDetails waitForScanToFinish(String scanId) throws InterruptedException {
        Map<String, NewCookie> cookies = login();
        Invocation invocation = root.path(SCAN_STATUS_PATH).resolveTemplate("scanId", scanId)
                .request()
                .header(CX_ORIGIN_HEADER, CX_ORIGIN_VALUE)
                .cookie(cookies.get(CX_COOKIE))
                .cookie(CSRF_COOKIE, cookies.get(CSRF_COOKIE).getValue())
                .header(CSRF_COOKIE, cookies.get(CSRF_COOKIE).getValue())
                .buildGet();
        return sampleScanAndGetScanDetailsWhenScanSucceed(invocation);
    }

    private ScanDetails sampleScanAndGetScanDetailsWhenScanSucceed(Invocation invocation) throws InterruptedException {
        ScanDetails scanStatusResponse = null;
        Boolean scanFinished = false;
        while (!scanFinished){
            Response response = invokeRequest(invocation);
            validateResponse(response, Response.Status.OK, "error occured while waiting for scan to finish");
            scanStatusResponse = response.readEntity(ScanDetails.class);
            if (isScanFinished(scanStatusResponse)){
                scanFinished = true;
            }else {
                Thread.sleep(5L * 1000);
            }
        }
        return scanStatusResponse;
    }

    private boolean isScanFinished(ScanDetails scanStatusResponse) {
        ScanStatus scanStatus = ScanStatus.fromId(scanStatusResponse.getState().getId());
        switch (scanStatus) {
            case NotStarted:
                return false;
            case InProgress:
                return false;
            case Finished:
                return true;
            case Failed:
                throw new WebApplicationException("Scan was unsuccessful: "+ scanStatusResponse.getState().getFailureReason());
            default:
                throw new WebApplicationException("Scan Status invalid: " + scanStatus);
        }
    }


    private Map<String, NewCookie> login() {
        Invocation invocation = root.path(AUTHENTICATION_PATH)
                .request()
                .header(CX_ORIGIN_HEADER, CX_ORIGIN_VALUE)
                .buildPost(Entity.entity(authenticationRequest, MediaType.APPLICATION_JSON));
        logger.info("Authenticating client");
        Response response = invokeRequest(invocation);
        validateResponse(response, Response.Status.OK, "fail to perform login");

        return response.getCookies();
    }

    private Response invokeRequest(Invocation invocation) {
        try {
            return invocation.invoke();
        } catch (ProcessingException exc) {
            return ThrowFailedToConnectCxServerError(exc);
        }
    }

    private void validateResponse(Response response, Response.Status expectedStatus, String message) throws WebApplicationException {
        if (response.getStatus() != expectedStatus.getStatusCode()) {
            String responseBody = response.readEntity(String.class);
            responseBody = responseBody.replace("{", "").replace("}", "").replace(System.lineSeparator(), " ").replace("  ", "");
            throw new WebApplicationException(message + ": " + "status code: " + response.getStatus() + ". error:" + responseBody);
        }
    }

    private Response ThrowFailedToConnectCxServerError(Exception e) {
        throw new WebApplicationException(FAILED_TO_CONNECT_CX_SERVER_ERROR + ": " + e.toString());
    }

    @Override
    public void close() {
        client.close();
    }

    public ScanDetails getLatestScanId(long projectId) {
        Invocation invocation = root.path(LATEST_SCAN_RESULTS).queryParam("projectId", projectId).request()
                .header(CX_ORIGIN_HEADER, CX_ORIGIN_VALUE)
                .cookie(cookies.get(CX_COOKIE))
                .cookie(CSRF_COOKIE, cookies.get(CSRF_COOKIE).getValue())
                .header(CSRF_COOKIE, cookies.get(CSRF_COOKIE).getValue()).buildGet();

        Response response = invocation.invoke();
        validateResponse(response, Response.Status.OK, "Failed to get OSA latest scan list");

        try {
            List<ScanDetails> scanDetails = mapper.readValue(response.readEntity(String.class), new TypeReference<List<ScanDetails>>(){});
            for (ScanDetails sd: scanDetails) {
                if("Succeeded".equals(sd.getState().getName())) {
                    return sd;
                }
            }

        } catch (IOException e) {
            return null;
        }

        return null;

    }
}
