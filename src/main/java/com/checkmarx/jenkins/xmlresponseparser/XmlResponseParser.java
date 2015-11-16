package com.checkmarx.jenkins.xmlresponseparser;

import com.checkmarx.ws.CxJenkinsWebService.CxWSResponseRunID;

import javax.xml.bind.JAXBException;
import javax.xml.stream.XMLStreamException;
import java.io.InputStream;

/**
 * Created by ehuds on 15/11/2015.
 */
public interface XmlResponseParser {
    public CxWSResponseRunID parse(InputStream inputStream) throws XMLStreamException, JAXBException;
}
