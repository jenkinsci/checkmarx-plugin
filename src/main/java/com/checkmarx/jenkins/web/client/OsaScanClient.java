package com.checkmarx.jenkins.web.client;

import com.checkmarx.jenkins.logger.CxPluginLogger;
import com.checkmarx.jenkins.web.model.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.HttpClientBuilder;
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
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Created by zoharby on 09/01/2017.
 */
public class OsaScanClient implements Closeable {

    private transient CxPluginLogger logger = new CxPluginLogger();

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

    private ObjectMapper mapper = new ObjectMapper();

    Map<String, NewCookie> cookies;

    public OsaScanClient(String hostname, AuthenticationRequest authenticationRequest) {
        this.authenticationRequest = authenticationRequest;
        client = ClientBuilder.newBuilder().register(MultiPartFeature.class).build();
        root = client.target(hostname.trim()).path(ROOT_PATH);
        cookies = login();
    }

    public CreateScanResponse createScanLargeFileWorkaround(CreateScanRequest request) throws IOException {

        //create httpclient
        CookieStore cookieStore = new BasicCookieStore();
        HttpClient apacheClient = HttpClientBuilder.create().setDefaultCookieStore(cookieStore).build();

        //create login request
        HttpPost loginPost = new HttpPost(root.getUri() + AUTHENTICATION_PATH);
        String json = mapper.writeValueAsString(authenticationRequest);
        StringEntity requestEntity = new StringEntity(json, ContentType.APPLICATION_JSON);
        loginPost.setEntity(requestEntity);

        //send login request
        HttpResponse loginResponse = apacheClient.execute(loginPost);

        //validate login response
        validateApacheHttpClientResponse(loginResponse, 200, "Failed to authenticate");


        //create OSA scan request
        HttpPost post = new HttpPost(root.getUri() + ANALYZE_PATH.replace("{projectId}", String.valueOf(request.getProjectId())));
        InputStreamBody streamBody = new InputStreamBody(request.getZipFile().read(), ContentType.APPLICATION_OCTET_STREAM, OSA_ZIPPED_FILE_KEY_NAME);
        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
        builder.addPart(OSA_ZIPPED_FILE_KEY_NAME, streamBody);
        HttpEntity entity = builder.build();
        post.setEntity(entity);

        //set csrf header and cookies
        for (org.apache.http.cookie.Cookie c : cookieStore.getCookies()) {
            if (CSRF_COOKIE.equals(c.getName())) {
                post.addHeader(CSRF_COOKIE, c.getValue());
            }
        }
        Header[] setCookies = loginResponse.getHeaders("Set-Cookie");
        StringBuilder cookies = new StringBuilder();
        for (Header h : setCookies) {
            cookies.append(h.getValue()).append(";");
        }
        post.addHeader("cookie", cookies.toString());

        //send scan request
        HttpResponse response = apacheClient.execute(post);

        //verify scan request
        validateApacheHttpClientResponse(response, 202, "Failed to create OSA scan");

        //extract response as object and return the link
        String createScanResponseBody = IOUtils.toString(response.getEntity().getContent(), Charset.defaultCharset());
        CreateScanResponse createScanResponse = mapper.readValue(createScanResponseBody, CreateScanResponse.class);
        return createScanResponse;
    }

    private void validateApacheHttpClientResponse(HttpResponse response, int status, String message) {
        if (response.getStatusLine().getStatusCode() != status) {
            String responseBody = extractResponseBody(response);
            responseBody = responseBody.replace("{", "").replace("}", "").replace(System.lineSeparator(), " ").replace("  ", "");
            throw new WebApplicationException(message + ": " + "status code: " + response.getStatusLine().getStatusCode() + ". error:" + responseBody);
        }
    }


    public CreateScanResponse createScan(CreateScanRequest request) throws IOException {
        final MultiPart multipart = createScanMultiPartRequest(request);
        logger.info("sending request for osa scan");
        Invocation invocation = root.path(ANALYZE_PATH)
                .resolveTemplate("projectId", request.getProjectId())
                .request()
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

    private MultiPart createScanMultiPartRequest(CreateScanRequest request) throws IOException {
        InputStream read = request.getZipFile().read();

        final StreamDataBodyPart filePart = new StreamDataBodyPart(OSA_ZIPPED_FILE_KEY_NAME, read);
        return new FormDataMultiPart()
                .bodyPart(new FormDataBodyPart("origin", Integer.toString(CreateScanRequest.JENKINS_ORIGIN)))
                .bodyPart(filePart);

    }

    @NotNull
    private File getFileFromRequest(CreateScanRequest request) {
        return new File(request.getZipFile().getRemote());
    }

    //todo: make wait handler part of scan sender and move waiting logic out of client
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
            return ThrowFailedToConnectCxServerError();
        }
    }

    private void validateResponse(Response response, Response.Status expectedStatus, String message) throws WebApplicationException {
        if (response.getStatus() == Response.Status.SERVICE_UNAVAILABLE.getStatusCode())
            ThrowFailedToConnectCxServerError();
        if (response.getStatus() != expectedStatus.getStatusCode()) {
            String responseBody = response.readEntity(String.class);
            responseBody = responseBody.replace("{", "").replace("}", "").replace(System.lineSeparator(), " ").replace("  ", "");
            throw new WebApplicationException(message + ": " + "status code: " + response.getStatus() + ". error:" + responseBody);
        }
    }

    private Response ThrowFailedToConnectCxServerError() {
        throw new WebApplicationException(FAILED_TO_CONNECT_CX_SERVER_ERROR);
    }

    private String extractResponseBody(HttpResponse response) {
        try {
            return IOUtils.toString(response.getEntity().getContent(), Charset.defaultCharset());
        } catch (IOException e) {
            return "";
        }
    }

    @Override
    public void close() {
        client.close();
    }
}
