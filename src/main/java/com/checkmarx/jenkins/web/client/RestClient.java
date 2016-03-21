package com.checkmarx.jenkins.web.client;

import java.io.Closeable;
import java.rmi.ConnectException;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.*;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;

import com.checkmarx.jenkins.web.model.AnalyzeRequest;
import com.checkmarx.jenkins.web.model.AuthenticationRequest;
import com.checkmarx.jenkins.web.model.CxException;
import com.checkmarx.jenkins.web.model.GetOpenSourceSummaryRequest;
import com.checkmarx.jenkins.web.model.GetOpenSourceSummaryResponse;
import com.sun.mail.iap.ConnectionException;


/**
 * @author tsahi
 * @since 02/02/16
 */
public class RestClient implements Closeable {
    private static final String ROOT_PATH = "CxRestAPI/api";
    private static final String AUTHENTICATION_PATH = "auth/login";
    private static final String ANALYZE_SUMMARY_PATH = "projects/{projectId}/opensourceanalysis/summaryresults";
    private static final String ANALYZE_PATH = "projects/{projectId}/opensourcesummary";
    private static final String FAILED_TO_CONNECT_CX_SERVER_ERROR = "connection to checkmarx server failed";

    private AuthenticationRequest authenticationRequest;
    private Client client;
    private WebTarget root;

    public RestClient(String serverUri, AuthenticationRequest authenticationRequest) {
        this.authenticationRequest = authenticationRequest;
        client = ClientBuilder.newClient();
        root = client.target(serverUri).path(ROOT_PATH);
    }

    public void analyzeOpenSources(AnalyzeRequest request) {
        Cookie cookie = authenticate();
        Invocation invocation = root.path(ANALYZE_PATH)
                .resolveTemplate("projectId", request.getProjectId())
                .request()
                .cookie(cookie)
                .buildPost(Entity.entity(request, MediaType.APPLICATION_JSON));
        Response response = invokeRequet(invocation);
        validateResponse(response);
    }

    public GetOpenSourceSummaryResponse getOpenSourceSummary(GetOpenSourceSummaryRequest request) {
        Cookie cookie = authenticate();
        Invocation invocation = root.path(ANALYZE_SUMMARY_PATH)
                .resolveTemplate("projectId", request.getProjectId())
                .request()
                .cookie(cookie)
                .buildGet();
        Response response = invokeRequet(invocation);
        validateResponse(response);
        return response.readEntity(GetOpenSourceSummaryResponse.class);
    }

    private Cookie authenticate() {
        Invocation invocation = root.path(AUTHENTICATION_PATH)
                .request()
                .buildPost(Entity.entity(authenticationRequest, MediaType.APPLICATION_JSON));
        Response response = invokeRequet(invocation);
        validateResponse(response);

        Map<String, NewCookie> cookiesMap = response.getCookies();
        @SuppressWarnings("unchecked")
        NewCookie cookieEntry = cookiesMap.get("cxCookie");
        return cookieEntry;
    }

    private Response invokeRequet(Invocation invocation) {
        try {
            return invocation.invoke();
        } catch (ProcessingException exc) {
            return ThrowFailedToConnectCxServerError();
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
        throw new WebApplicationException(cxException.getMessage() + "\n" + cxException.getMessageDetails(), response);
    }

    @Override
    public void close() {
        client.close();
    }
}
