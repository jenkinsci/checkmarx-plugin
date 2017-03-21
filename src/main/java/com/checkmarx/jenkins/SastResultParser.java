package com.checkmarx.jenkins;

import com.checkmarx.jenkins.logger.CxPluginLogger;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.checkmarx.jenkins.CxResultSeverity.*;

/**
 * Created by zoharby on 22/01/2017.
 */
public class SastResultParser {

    private CxPluginLogger logger;
    private String serverUrl;

    private SastScanResult sastScanResult;

    private static ObjectMapper mapper = new ObjectMapper();

    public SastResultParser(CxPluginLogger logger, String serverUrl) {
        this.logger = logger;
        this.serverUrl = serverUrl;
    }

    public SastScanResult readScanXMLReport(File scanXMLReport) {
        SastResultParser.ResultsParseHandler handler = new SastResultParser.ResultsParseHandler();
        sastScanResult = new SastScanResult();

        try {
            SAXParser saxParser = SAXParserFactory.newInstance().newSAXParser();

            sastScanResult.setHighCount(0);
            sastScanResult.setMediumCount(0);
            sastScanResult.setLowCount(0);
            sastScanResult.setInfoCount(0);

            saxParser.parse(scanXMLReport, handler);

            sastScanResult.setResultIsValid(true);
            setQueriesAsJson();

        } catch (ParserConfigurationException e) {
            logger.error(e.getMessage());
        } catch (SAXException | IOException e) {
            sastScanResult.setResultIsValid(false);
            sastScanResult.setErrorMessage(e.getMessage());
            logger.error(e.getMessage());
        }
        return sastScanResult;
    }


    private class ResultsParseHandler extends DefaultHandler {

        @Nullable
        private String currentQueryName;
        @Nullable
        private String currentQuerySeverity;
        private int currentQueryNumOfResults;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            super.startElement(uri, localName, qName, attributes);

            switch (qName) {
                case "Result":
                    @Nullable
                    String falsePositive = attributes.getValue("FalsePositive");
                    if (!"True".equals(falsePositive)) {
                        currentQueryNumOfResults++;
                        @Nullable
                        String severity = attributes.getValue("SeverityIndex");
                        if (severity != null) {
                            if (severity.equals(HIGH.xmlParseString)) {
                                sastScanResult.setHighCount(sastScanResult.getHighCount()+1);

                            } else if (severity.equals(MEDIUM.xmlParseString)) {
                                sastScanResult.setMediumCount(sastScanResult.getMediumCount()+1);

                            } else if (severity.equals(LOW.xmlParseString)) {
                                sastScanResult.setLowCount(sastScanResult.getLowCount()+1);

                            } else if (severity.equals(INFO.xmlParseString)) {
                                sastScanResult.setInfoCount(sastScanResult.getInfoCount()+1);
                            }
                        } else {
                            logger.error("\"SeverityIndex\" attribute was not found in element \"Result\" in XML report. "
                                    + "Make sure you are working with Checkmarx server version 7.1.6 HF3 or above.");
                        }
                    }
                    break;
                case "Query":
                    currentQueryName = attributes.getValue("name");
                    if (currentQueryName == null) {
                        logger.error("\"name\" attribute was not found in element \"Query\" in XML report");
                    }
                    currentQuerySeverity = attributes.getValue("SeverityIndex");
                    if (currentQuerySeverity == null) {
                        logger.error("\"SeverityIndex\" attribute was not found in element \"Query\" in XML report. "
                                + "Make sure you are working with Checkmarx server version 7.1.6 HF3 or above.");
                    }
                    currentQueryNumOfResults = 0;

                    break;
                default:
                    if ("CxXMLResults".equals(qName)) {
                        sastScanResult.setResultDeepLink(constructDeepLink(attributes.getValue("DeepLink")));
                        sastScanResult.setScanStart(attributes.getValue("ScanStart"));
                        sastScanResult.setScanTime(attributes.getValue("ScanTime"));
                        sastScanResult.setLinesOfCodeScanned(attributes.getValue("LinesOfCodeScanned"));
                        sastScanResult.setFilesScanned(attributes.getValue("FilesScanned"));
                        sastScanResult.setScanType(attributes.getValue("ScanType"));
                    }
                    break;
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            super.endElement(uri, localName, qName);
            if ("Query".equals(qName)) {
                QueryResult qr = new QueryResult();
                qr.setName(currentQueryName);
                qr.setSeverity(currentQuerySeverity);
                qr.setCount(currentQueryNumOfResults);

                if (StringUtils.equals(qr.getSeverity(), HIGH.xmlParseString)) {
                    sastScanResult.getHighQueryResultList().add(qr);
                } else if (StringUtils.equals(qr.getSeverity(), MEDIUM.xmlParseString)) {
                    sastScanResult.getMediumQueryResultList().add(qr);
                } else if (StringUtils.equals(qr.getSeverity(), LOW.xmlParseString)) {
                    sastScanResult.getLowQueryResultList().add(qr);
                } else if (StringUtils.equals(qr.getSeverity(), INFO.xmlParseString)) {
                    sastScanResult.getInfoQueryResultList().add(qr);
                } else {
                    logger.error("Encountered a result query with unknown severity: " + qr.getSeverity());
                }
            }
        }

        @NotNull
        private String constructDeepLink(@Nullable String rawDeepLink) {
            if (rawDeepLink == null) {
                logger.error("\"DeepLink\" attribute was not found in element \"CxXMLResults\" in XML report");
                return "";
            }
            String token = "CxWebClient";
            String[] tokens = rawDeepLink.split(token);
            if (tokens.length < 1) {
                logger.error("DeepLink value found in XML report is of unexpected format: " + rawDeepLink + "\n"
                        + "\"Open Code Viewer\" button will not be functional");
            }
            return serverUrl + "/" + token + tokens[1];
        }
    }

        private void setQueriesAsJson(){
            sastScanResult.setHighQueryResultsJson(transformQueryListToJson(sastScanResult.getHighQueryResultList()));
            sastScanResult.setMediumQueryResultsJson(transformQueryListToJson(sastScanResult.getMediumQueryResultList()));
            sastScanResult.setLowQueryResultsJson(transformQueryListToJson(sastScanResult.getLowQueryResultList()));
            sastScanResult.setInfoQueryResultsJson(transformQueryListToJson(sastScanResult.getInfoQueryResultList()));
        }

        private String transformQueryListToJson(List<QueryResult> queryResults){
            try {
               if(queryResults != null) {
                   return mapper.writeValueAsString(queryResults);
               }
            } catch (JsonProcessingException e) {
                logger.error("Could not parse result to Json \n ", e);
            }
            return "[]";
        }

}
