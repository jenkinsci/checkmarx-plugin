package com.checkmarx.jenkins.xmlresponseparser;

import com.checkmarx.ws.CxJenkinsWebService.CreateAndRunProjectResponse;
import com.checkmarx.ws.CxJenkinsWebService.CxWSResponseRunID;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;

/**
 * Created by ehuds on 15/11/2015.
 */
public class CreateAndRunProjectXmlResponseParser implements XmlResponseParser {
    @Override
    public CxWSResponseRunID parse(InputStream inputStream) throws XMLStreamException, JAXBException {
        XMLInputFactory xif = XMLInputFactory.newFactory();
        XMLStreamReader xsr = xif.createXMLStreamReader(inputStream);
        xsr.nextTag();
        // We now consume all tags before the first occurrence of ScanResponse,
        // which constitute the soap message envelope header
        while(!xsr.getLocalName().equals("CreateAndRunProjectResponse")) {
            xsr.nextTag();
        }

        final JAXBContext context = JAXBContext.newInstance(CreateAndRunProjectResponse.class);
        final Unmarshaller unmarshaller = context.createUnmarshaller();
        final CreateAndRunProjectResponse scanResponse = (CreateAndRunProjectResponse)unmarshaller.unmarshal(xsr);
        // We neglect the consumption of soap envelope tail, since it is not used anywhere
        xsr.close();

        return scanResponse.getCreateAndRunProjectResult();
    }
}
