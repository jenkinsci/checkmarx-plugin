package com.checkmarx.jenkins;


import com.checkmarx.ws.CxCLIWebService.*;
import com.checkmarx.ws.CxWSResolver.*;
import com.sun.xml.internal.ws.wsdl.parser.InaccessibleWSDLException;
import hudson.AbortException;
import hudson.model.BuildListener;
import org.apache.log4j.Logger;

import javax.xml.namespace.QName;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Created with IntelliJ IDEA.
 * User: denis
 * Date: 13/11/2013
 * Time: 18:12
 * Description:
 */
public class CxWebService {

    private final static Logger logger = Logger.getLogger(CxWebService.class);
    private final static QName CXWSRESOLVER_QNAME = new QName("http://Checkmarx.com", "CxWSResolver");
    private final static QName CXCLIWEBSERVICE_QNAME = new QName("http://Checkmarx.com/v7", "CxCLIWebService");
    private final static int WEBSERVICE_API_VERSION = 1;
    private final static String CXWSRESOLVER_PATH = "/cxwebinterface/cxwsresolver.asmx";
    private final static int LCID = 1033; // English

    private String sessionId;
    private CxCLIWebServiceSoap cxCLIWebServiceSoap;

    public CxWebService(String serverUrl) throws MalformedURLException, AbortException
    {
        logger.info("Establishing connection with Checkmarx server at: " + serverUrl);
        URL resolverUrl = new URL(serverUrl + CXWSRESOLVER_PATH);
        logger.debug("Resolver url: " + resolverUrl);
        CxWSResolver cxWSResolver;
        try {
            cxWSResolver = new CxWSResolver(resolverUrl,CXWSRESOLVER_QNAME);
        } catch (InaccessibleWSDLException e){
            logger.error("Failed to resolve Checkmarx webservice url with resolver at: " + resolverUrl);
            logger.error(e);
            throw new AbortException("Checkmarx server was not found on url: " + serverUrl);
        }
        CxWSResolverSoap cxWSResolverSoap =  cxWSResolver.getCxWSResolverSoap();
        CxWSResponseDiscovery cxWSResponseDiscovery = cxWSResolverSoap.getWebServiceUrl(CxClientType.CLI,WEBSERVICE_API_VERSION); // TODO: Replace CLI with Jenkins
        if (!cxWSResponseDiscovery.isIsSuccesfull())
        {
            String message = "Failed to resolve Checkmarx webservice url: \n" + cxWSResponseDiscovery.getErrorMessage();
            logger.error(message);
            throw new AbortException(message);
        }

        URL webServiceUrl = new URL(cxWSResponseDiscovery.getServiceURL());
        logger.debug("Webservice url: " + webServiceUrl);
        CxCLIWebService cxCLIWebService = new CxCLIWebService(webServiceUrl,CXCLIWEBSERVICE_QNAME);
        cxCLIWebServiceSoap = cxCLIWebService.getCxCLIWebServiceSoap();

    }

    public void login(String username, String password) throws AbortException
    {
        Credentials credentials = new Credentials();
        credentials.setUser(username);
        credentials.setPass(password);
        CxWSResponseLoginData cxWSResponseLoginData = cxCLIWebServiceSoap.login(credentials,LCID);

        if (!cxWSResponseLoginData.isIsSuccesfull())
        {
            String message = "Login to Checkmarx server failed: " +  cxWSResponseLoginData.getErrorMessage();
            logger.error(message);
            throw new AbortException(message);
        }

        sessionId = cxWSResponseLoginData.getSessionId();
        logger.debug("Login successful");
    }

    public CxWSResponseRunID scan(CliScanArgs args) throws AbortException
    {
        assert sessionId!=null : "Trying to scan before login";

        CxWSResponseRunID cxWSResponseRunID = cxCLIWebServiceSoap.scan(sessionId,args);
        if (!cxWSResponseRunID.isIsSuccesfull())
        {
            String message = "Submission of sources for scan failed: \n" + cxWSResponseRunID.getErrorMessage();
            logger.error(message);
            throw new AbortException(message);
        }

        return cxWSResponseRunID;
    }


    private CxWSResponseScanStatus getScanStatus(CxWSResponseRunID cxWSResponseRunID) throws AbortException
    {
        assert sessionId!=null : "Trying to get scan status before login";
        CxWSResponseScanStatus cxWSResponseScanStatus = cxCLIWebServiceSoap.getStatusOfSingleScan(sessionId,cxWSResponseRunID.getRunId());
        if (!cxWSResponseScanStatus.isIsSuccesfull())
        {
            String message = "Error communicating with Checkmarx server: \n" + cxWSResponseScanStatus.getErrorMessage();
            logger.error(message);
            throw new AbortException(message);
        }
        return cxWSResponseScanStatus;
    }



    public void trackScanProgress(CxWSResponseRunID cxWSResponseRunID, BuildListener listener) throws AbortException
    {

        while (true)
        {
            CxWSResponseScanStatus status = this.getScanStatus(cxWSResponseRunID);
            String message;
                switch (status.getCurrentStatus())
            {
                // In progress states
                case QUEUED:
                case WORKING:
                case UNZIPPING:
                case WAITING_TO_PROCESS:

                // End of progress states
                case FINISHED:
                case FAILED:
                case DELETED:
                case UNKNOWN:
                case CANCELED:
            }

            try {
                Thread.currentThread().wait(10);
            } catch (InterruptedException e)
            {
                String err = "Process interrupted while waiting for scan results";
                logger.error(err);
                logger.error(e);
                throw new AbortException(err);
            }
        }



    }

}
