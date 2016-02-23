package com.checkmarx.jenkins.web.client;

import java.io.Closeable;
import java.util.Map;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.checkmarx.jenkins.web.model.AnalyzeRequest;
import com.checkmarx.jenkins.web.model.AuthenticationRequest;
import com.checkmarx.jenkins.web.model.CxException;
import com.checkmarx.jenkins.web.model.GetOpenSourceSummaryRequest;
import com.checkmarx.jenkins.web.model.GetOpenSourceSummaryResponse;


/**
 * @author tsahi
 * @since 02/02/16
 */
public class RestClient implements Closeable {
	private static final String ROOT_PATH = "CxRestAPI/api";
	private static final String AUTHENTICATION_PATH = "auth/login";
	private static final String ANALYZE_SUMMARY_PATH = "projects/{projectId}/opensourceanalysis/summaryresults";
	private static final String ANALYZE_PATH = "projects/opensourcesummary";

    private AuthenticationRequest authenticationRequest;
	private Client client;
	private WebTarget root;

    public RestClient(String serverUri, AuthenticationRequest authenticationRequest) {
        this.authenticationRequest = authenticationRequest;
		client = ClientBuilder.newClient();
		root = client.target(serverUri).path(ROOT_PATH);
    }

    public void analyzeOpenSources(AnalyzeRequest request) throws Exception {
        Cookie cookie = authenticate();
		Response response = root.path(ANALYZE_PATH)
                .request()
                .cookie(cookie)
                .post(Entity.entity(request, MediaType.APPLICATION_JSON));

        validateResponse(response);
    }

	public GetOpenSourceSummaryResponse getOpenSourceSummary(GetOpenSourceSummaryRequest request) throws Exception {
        Cookie cookie = authenticate();
		return root.path(ANALYZE_SUMMARY_PATH)
				.resolveTemplate("projectId", request.getProjectId())
                .request()
                .cookie(cookie)
                .get(GetOpenSourceSummaryResponse.class);
    }

    private Cookie authenticate() throws Exception {
		Response response = root.path(AUTHENTICATION_PATH)
                .request()
                .post(Entity.entity(authenticationRequest, MediaType.APPLICATION_JSON));

        validateResponse(response);

        Map<String, NewCookie> cookiesMap = response.getCookies();
		@SuppressWarnings("unchecked")
		Map.Entry<String, NewCookie> cookieEntry = (Map.Entry<String, NewCookie>) cookiesMap.entrySet().toArray()[0];
        return cookieEntry.getValue();
    }

    private void validateResponse(Response response) throws Exception {
		if (response.getStatus() >= Status.BAD_REQUEST.getStatusCode()) {
            CxException cxException = response.readEntity(CxException.class);
            throw new Exception(cxException.getMessage() + "\n" + cxException.getMessageDetails());
        }
    }

	@Override
	public void close() {
		client.close();
	}
}
