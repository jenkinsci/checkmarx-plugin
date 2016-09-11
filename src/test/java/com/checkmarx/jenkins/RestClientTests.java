package com.checkmarx.jenkins;

import com.checkmarx.jenkins.web.client.RestClient;
import com.checkmarx.jenkins.web.model.ScanRequest;
import com.checkmarx.jenkins.web.model.AuthenticationRequest;
import com.checkmarx.jenkins.web.model.CxException;
import com.google.common.collect.ImmutableMap;
import mockit.Mock;
import mockit.MockUp;
import org.glassfish.jersey.media.multipart.file.FileDataBodyPart;
import org.junit.Test;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.*;
import java.io.File;
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
            @Mock
            File getFileFromRequest(ScanRequest request) {
                return null;
            }
        };
        new MockUp<FileDataBodyPart>(){
            @Mock
            void $init(final String name, final File fileEntity) { }
        };

        RestClient client = new RestClient("", new AuthenticationRequest("", ""));
        client.createScan(new ScanRequest(0, null));
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
            @Mock
            File getFileFromRequest(ScanRequest request) {
                return null;
            }
        };
        new MockUp<FileDataBodyPart>(){
            @Mock
            void $init(final String name, final File fileEntity) { }
        };

        RestClient client = new RestClient("", new AuthenticationRequest("", ""));
        client.createScan(new ScanRequest(0, null));
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
            @Mock
            File getFileFromRequest(ScanRequest request) {
                return null;
            }
        };
        new MockUp<FileDataBodyPart>(){
            @Mock
            void $init(final String name, final File fileEntity) { }
        };

        RestClient client = new RestClient("", new AuthenticationRequest("", ""));
        try {
            client.createScan(new ScanRequest(0, null));
        }
        catch (Exception ex)
        {
            assertEquals(ex.getMessage(),"connection to checkmarx server failed");
        }
    }
}