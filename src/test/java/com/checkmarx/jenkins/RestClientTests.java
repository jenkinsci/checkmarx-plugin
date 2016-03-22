package com.checkmarx.jenkins;

import com.checkmarx.jenkins.web.client.RestClient;
import com.checkmarx.jenkins.web.model.AnalyzeRequest;
import com.checkmarx.jenkins.web.model.AuthenticationRequest;
import com.checkmarx.jenkins.web.model.CxException;
import com.google.common.collect.ImmutableMap;
import mockit.Mock;
import mockit.MockUp;
import org.junit.Test;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by eranb on 10/03/2016.
 */
public class RestClientTests {

    @Test
    public void analyzeOpenSources_andReturnStatusCodeIsLowerThen400_noExceptionShouldBeThrown() {
        final MockUp<Response> responseMockUp = new MockUp<Response>() {
            @Mock
            int getStatus() {
                return 200;
            }
            @Mock
            Map<String, NewCookie> getCookies(){
                return ImmutableMap.of("cxCookie",new NewCookie("",""), "CXCSRFToken",new NewCookie("",""));
            }

        };
        new MockUp<RestClient>() {
            @Mock
            Response invokeRequet(Invocation invocation) {
                return responseMockUp.getMockInstance();
            }
        };
    RestClient client = new RestClient("", new AuthenticationRequest("", ""));
    client.analyzeOpenSources(new AnalyzeRequest(0,Collections.EMPTY_LIST));
    }

    @Test(expected=WebApplicationException.class)
    public void analyzeOpenSources_andReturnStatusCodeIsGraterThen400_ThrowException() {
        final MockUp<Response> responseMockUp = new MockUp<Response>() {
            @Mock
            int getStatus() {
                return 401;
            }
            @Mock
            Map<String, NewCookie> getCookies(){
                return ImmutableMap.of("cxCookie",new NewCookie("",""));
            }
            @Mock
            <T> T readEntity(Class<T> entityType)
            {
              return (T)new CxException();
            };
        };
        new MockUp<RestClient>() {
            @Mock
            Response invokeRequet(Invocation invocation) {
                return responseMockUp.getMockInstance();
            }
        };
        RestClient client = new RestClient("", new AuthenticationRequest("", ""));
        client.analyzeOpenSources(new AnalyzeRequest(0,Collections.EMPTY_LIST));
    }

    @Test
    public void analyzeOpenSources_andReturnStatusCodeIs503_ThrowConnectionFailedException() {
        final MockUp<Response> responseMockUp = new MockUp<Response>() {
            @Mock
            int getStatus() {
                return 503;
            }
            @Mock
            Map<String, NewCookie> getCookies(){
                return ImmutableMap.of("cxCookie",new NewCookie("",""));
            }
            @Mock
            <T> T readEntity(Class<T> entityType)
            {
                return (T)new CxException();
            };
        };
        new MockUp<RestClient>() {
            @Mock
            Response invokeRequet(Invocation invocation) {
                return responseMockUp.getMockInstance();
            }
        };
        RestClient client = new RestClient("", new AuthenticationRequest("", ""));
        try {
            client.analyzeOpenSources(new AnalyzeRequest(0, Collections.EMPTY_LIST));
        }
        catch (Exception ex)
        {
            assertEquals(ex.getMessage(),"connection to checkmarx server failed");
        }
    }
}