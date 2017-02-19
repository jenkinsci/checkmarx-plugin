package com.checkmarx.jenkins.web.client;

import com.checkmarx.jenkins.web.model.*;
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
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Map;


/**
 * @author tsahi
 * @since 02/02/16
 */
public class ScanClient implements Closeable {
    private static final String ROOT_PATH = "CxRestAPI/";
    private static final String AUTHENTICATION_PATH = "auth/login";
    private static final String ANALYZE_SUMMARY_PATH = "projects/{projectId}/summaryresults";
    public static final String OSA_SCAN_HTML_PATH = "projects/{projectId}/opensourceanalysis/htmlresults";
    public static final String OSA_SCAN_PDF_PATH = "projects/{projectId}/opensourceanalysis/pdfresults";
    private static final String ANALYZE_PATH = "projects/{projectId}/scans";
    private static final String FAILED_TO_CONNECT_CX_SERVER_ERROR = "connection to checkmarx server failed";
    private static final String CX_COOKIE = "cxCookie";
    private static final String CSRF_COOKIE = "CXCSRFToken";
    private static final String OSA_ZIPPED_FILE_KEY_NAME = "OSAZippedSourceCode";
    private AuthenticationRequest authenticationRequest;
    private Client client;
    private WebTarget root;

    public ScanClient(String serverUri, AuthenticationRequest authenticationRequest) {
        this.authenticationRequest = authenticationRequest;
        client = ClientBuilder.newBuilder().register(MultiPartFeature.class).build();
        root = client.target(serverUri.trim()).path(ROOT_PATH);
    }

    public URI createScan(CreateScanRequest request) throws IOException {
        final MultiPart multipart = createScanMultiPartRequest(request);
        Map<String, NewCookie> cookies = authenticate();
        Invocation invocation = root.path(ANALYZE_PATH)
                .resolveTemplate("projectId", request.getProjectId())
                .request()
                .cookie(cookies.get(CX_COOKIE))
                .cookie(CSRF_COOKIE, cookies.get(CSRF_COOKIE).getValue())
                .header(CSRF_COOKIE, cookies.get(CSRF_COOKIE).getValue())
                .buildPost(Entity.entity(multipart, multipart.getMediaType()));
        Response response = invokeRequet(invocation);
        validateResponse(response);
        CreateScanResponse scanResponse = response.readEntity(CreateScanResponse.class);
        return scanResponse.getLink();
    }

    public URI createScanLargeFileWorkaround(CreateScanRequest request) throws IOException {

        //create httpclient
        CookieStore cookieStore = new BasicCookieStore();
        HttpClient apacheClient = HttpClientBuilder.create().setDefaultCookieStore(cookieStore).build();

        //create login request
        HttpPost loginPost = new HttpPost(root.getUri() + AUTHENTICATION_PATH);
        String json = "{\"username\":\"" + authenticationRequest.getUsername() + "\", \"password\": \"" + authenticationRequest.getPassword() + "\"}";
        StringEntity requestEntity = new StringEntity(json, ContentType.APPLICATION_JSON);
        loginPost.setEntity(requestEntity);

        //send login request
        HttpResponse loginResponse = apacheClient.execute(loginPost);

        //validate login response
        String loginMessageBody = IOUtils.toString(loginResponse.getEntity().getContent());
        validateApacheHttpClientResponse(loginResponse, 200, "Fail to authenticate", loginMessageBody);


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

        //sent scan request
        HttpResponse response = apacheClient.execute(post);

        //verify scan request
        String createScanResponseBody = IOUtils.toString(response.getEntity().getContent(), Charset.defaultCharset());
        validateApacheHttpClientResponse(response, 202, "Fail to create OSA scan", createScanResponseBody);

        //extract response as object and return the link
        ObjectMapper mapper = new ObjectMapper();
        CreateScanResponse createScanResponse = mapper.readValue(createScanResponseBody, CreateScanResponse.class);
        return createScanResponse.getLink();
    }


    public String getOSAScanHtmlResults(long projectId) {
        Map<String, NewCookie> cookies = authenticate();
        Response response = root.path(OSA_SCAN_HTML_PATH).resolveTemplate("projectId", projectId).request().cookie(cookies.get(CX_COOKIE))
                .cookie(CSRF_COOKIE, cookies.get(CSRF_COOKIE).getValue())
                .header(CSRF_COOKIE, cookies.get(CSRF_COOKIE).getValue()).get();
        validateResponse(response);
        return response.readEntity(String.class);
    }

    public byte[] getOSAScanPdfResults(long projectId) {

        Map<String, NewCookie> cookies = authenticate();
        Response response = root.path(OSA_SCAN_PDF_PATH).resolveTemplate("projectId", projectId).request().cookie(cookies.get(CX_COOKIE))
                .cookie(CSRF_COOKIE, cookies.get(CSRF_COOKIE).getValue())
                .header(CSRF_COOKIE, cookies.get(CSRF_COOKIE).getValue()).get();
        validateResponse(response);
        return response.readEntity(byte[].class);

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

    public void waitForScanToFinish(URI uri) throws InterruptedException {
        Map<String, NewCookie> cookies = authenticate();
        Invocation invocation = root.path(uri.toString())
                .request()
                .cookie(cookies.get(CX_COOKIE))
                .cookie(CSRF_COOKIE, cookies.get(CSRF_COOKIE).getValue())
                .header(CSRF_COOKIE, cookies.get(CSRF_COOKIE).getValue())
                .buildGet();

        sampleScan(invocation);
    }

    private void sampleScan(Invocation invocation) throws InterruptedException {
        Boolean scanFinished = false;
        while (!scanFinished) {
            Response response = invokeRequet(invocation);
            validateResponse(response);
            if (scanFinished(response)) {
                scanFinished = true;
            } else {
                Thread.sleep(5L * 1000);
            }
        }
    }

    private boolean scanFinished(Response response) {
        GetScanStatusResponse scanStatusResponse = response.readEntity(GetScanStatusResponse.class);
        ScanStatus scanStatus = ScanStatus.fromId(scanStatusResponse.getStatus());
        switch (scanStatus) {
            case InProgress:
                return false;
            case Finished:
                return true;
            case Failed:
                throw new WebApplicationException(scanStatusResponse.getMessage());
            default:
                throw new WebApplicationException("Scan Status invalid: " + scanStatus);
        }
    }

    public GetOpenSourceSummaryResponse getOpenSourceSummary(GetOpenSourceSummaryRequest request) {
        Map<String, NewCookie> cookies = authenticate();
        Invocation invocation = root.path(ANALYZE_SUMMARY_PATH)
                .resolveTemplate("projectId", request.getProjectId())
                .request()
                .cookie(cookies.get(CX_COOKIE))
                .cookie(CSRF_COOKIE, cookies.get(CSRF_COOKIE).getValue())
                .header(CSRF_COOKIE, cookies.get(CSRF_COOKIE).getValue())
                .buildGet();
        Response response = invokeRequet(invocation);
        validateResponse(response);
        return response.readEntity(GetOpenSourceSummaryResponse.class);
    }

    private Map<String, NewCookie> authenticate() {
        Invocation invocation = root.path(AUTHENTICATION_PATH)
                .request()
                .buildPost(Entity.entity(authenticationRequest, MediaType.APPLICATION_JSON));
        Response response = invokeRequet(invocation);
        validateResponse(response);

        return response.getCookies();
    }

    private Response invokeRequet(Invocation invocation) {
        try {
            return invocation.invoke();
        } catch (ProcessingException exc) {
            return ThrowFailedToConnectCxServerError();
        }
    }

    private void validateApacheHttpClientResponse(HttpResponse response, int status, String message, String messageBody) {
        if (response.getStatusLine().getStatusCode() != status) {
            throw new WebApplicationException(message + ": " + "status code: " + response.getStatusLine().getStatusCode() + ". reason phrase: " + response.getStatusLine().getReasonPhrase() + ". message body: " + messageBody);
        }
    }

    private void validateResponse(Response response) {
        int httpStatus = response.getStatus();
        if (httpStatus < 400) return;
        if (httpStatus == Response.Status.SERVICE_UNAVAILABLE.getStatusCode())
            ThrowFailedToConnectCxServerError();
        else
            ThrowCxException(response);
    }

    private Response ThrowFailedToConnectCxServerError() {
        throw new WebApplicationException(FAILED_TO_CONNECT_CX_SERVER_ERROR);
    }

    private void ThrowCxException(Response response) {
        CxException cxException = response.readEntity(CxException.class);
        if (cxException != null) {
            throw new WebApplicationException(cxException.getMessageCode() + "\n" + cxException.getMessageDetails(), response);
        } else {
            throw new WebApplicationException(response);
        }
    }

    @Override
    public void close() {
        client.close();
    }
}
