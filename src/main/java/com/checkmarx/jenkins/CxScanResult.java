package com.checkmarx.jenkins;

import com.thoughtworks.xstream.annotations.XStreamOmitField;
import hudson.PluginWrapper;
import hudson.model.*;
import hudson.util.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.servlet.ServletOutputStream;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.IOException;
import java.util.LinkedList;

/**
 * Created with IntelliJ IDEA.
 * User: denis
 * Date: 3/11/13
 * Time: 11:47
 * Description:
 */

// One option is to inherit from AbstractTestResultAction<CxScanResult>


public class CxScanResult implements Action {

    @XStreamOmitField
    private final Logger logger;

    public final AbstractBuild<?,?> owner;
    private String serverUrl;

    private int highCount;
    private int mediumCount;
    private int lowCount;
    private int infoCount;
    private LinkedList<QueryResult> highQueryResultList;
    private LinkedList<QueryResult> mediumQueryResultList;
    private LinkedList<QueryResult> lowQueryResultList;
    private LinkedList<QueryResult> infoQueryResultList;

    @NotNull
    private String resultDeepLink;
    private File pdfReport;

    public final static String PDF_REPORT_NAME = "ScanReport.pdf";
    @Nullable
    private String scanStart;
    @Nullable
    private String scanTime;
    @Nullable
    private String linesOfCodeScanned;
    @Nullable
    private String filesScanned;
    @Nullable
    private String scanType;

    private boolean resultIsValid;
    private String errorMessage;


    public CxScanResult(final AbstractBuild owner, final String loggerSuffix, String serverUrl)
    {
        logger = CxLogUtils.loggerWithSuffix(getClass(),loggerSuffix);
        this.owner = owner;
        this.serverUrl = serverUrl;
        this.resultIsValid=false;
        this.errorMessage = "No Scan Results"; // error message to appear if results were not parsed
        this.highQueryResultList   = new LinkedList<QueryResult>();
        this.mediumQueryResultList = new LinkedList<QueryResult>();
        this.lowQueryResultList    = new LinkedList<QueryResult>();
        this.infoQueryResultList   = new LinkedList<QueryResult>();
    }


    @Override
    public String getIconFileName() {
        return getIconPath() + "CxIcon24x24.png";
    }

    @Override
    public String getDisplayName() {
        return "Checkmarx Scan Results";
    }

    @Override
    public String getUrlName() {
        return "checkmarx";
    }

    public String getIconPath() {
        PluginWrapper wrapper = Hudson.getInstance().getPluginManager().getPlugin(CxPlugin.class);
        return "/plugin/"+ wrapper.getShortName()+"/";
    }

    public int getHighCount()
    {
        return highCount;
    }

    public int getMediumCount()
    {
        return mediumCount;
    }

    public int getLowCount()
    {
        return lowCount;
    }

    public int getInfoCount()
    {
        return infoCount;
    }

    @NotNull
    public String getResultDeepLink() {
        return resultDeepLink;
    }
    @Nullable
    public String getScanStart() {
        return scanStart;
    }
    @Nullable
    public String getScanTime() {
        return scanTime;
    }
    @Nullable
    public String getLinesOfCodeScanned() {
        return linesOfCodeScanned;
    }
    @Nullable
    public String getFilesScanned() {
        return filesScanned;
    }
    @Nullable
    public String getScanType() {
        return scanType;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean isResultIsValid() {
        return resultIsValid;
    }

    public LinkedList<QueryResult> getHighQueryResultList() {
        return highQueryResultList;
    }

    public LinkedList<QueryResult> getMediumQueryResultList() {
        return mediumQueryResultList;
    }

    public LinkedList<QueryResult> getLowQueryResultList() {
        return lowQueryResultList;
    }

    public LinkedList<QueryResult> getInfoQueryResultList() {
        return infoQueryResultList;
    }

    public boolean isPdfReportReady() {
        File buildDirectory = owner.getRootDir();
        pdfReport = new File(buildDirectory, "/checkmarx/" + PDF_REPORT_NAME);
        return pdfReport.exists();
    }

    public String getPdfReportUrl(){
        return "/pdfReport";
    }

    public void doPdfReport(StaplerRequest req, StaplerResponse rsp) throws IOException {
        rsp.setContentType("application/pdf");
        ServletOutputStream outputStream = rsp.getOutputStream();
        IOUtils.copy(pdfReport, outputStream);

        outputStream.flush();
        outputStream.close();
    }

    /**
     * Gets the test result of the previous build, if it's recorded, or null.
     */

    public CxScanResult getPreviousResult() {
        AbstractBuild<?,?> b = owner;
        while(true) {
            b = b.getPreviousBuild();
            if(b==null)
                return null;
            CxScanResult r = b.getAction(CxScanResult.class);
            if(r!=null)
                return r;
        }
    }

    public void readScanXMLReport(File scanXMLReport)
    {
        ResultsParseHandler handler = new ResultsParseHandler();

        try {
            SAXParser saxParser = SAXParserFactory.newInstance().newSAXParser();

            CxScanResult.this.highCount=0;
            CxScanResult.this.mediumCount=0;
            CxScanResult.this.lowCount=0;
            CxScanResult.this.infoCount=0;

            saxParser.parse(scanXMLReport,handler);

            CxScanResult.this.resultIsValid = true;
            CxScanResult.this.errorMessage=null;

        } catch (ParserConfigurationException e)
        {
            logger.fatal(e);
        } catch (SAXException e)
        {
            CxScanResult.this.resultIsValid = false;
            CxScanResult.this.errorMessage = e.getMessage();
            logger.warn(e);
        } catch (IOException e)
        {
            CxScanResult.this.resultIsValid = false;
            CxScanResult.this.errorMessage = e.getMessage();
            logger.warn(e);
        }
    }

    private class ResultsParseHandler extends DefaultHandler {

        @Nullable
        private String currentQueryName;
        @Nullable
        private String currentQuerySeverity;
        private int    currentQueryNumOfResults;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            super.startElement(uri, localName, qName, attributes);

            if ("Result".equals(qName))
            {
                @Nullable
                String falsePositive = attributes.getValue("FalsePositive");
                if (!"True".equals(falsePositive))
                {
                    currentQueryNumOfResults++;
                    @Nullable
                    String severity = attributes.getValue("SeverityIndex");
                    if (severity!=null) {
                        if (severity.equals(CxResultSeverity.HIGH.xmlParseString)) {
                            CxScanResult.this.highCount++;

                        } else if (severity.equals(CxResultSeverity.MEDIUM.xmlParseString)) {
                            CxScanResult.this.mediumCount++;

                        } else if (severity.equals(CxResultSeverity.LOW.xmlParseString)) {
                            CxScanResult.this.lowCount++;

                        } else if (severity.equals(CxResultSeverity.INFO.xmlParseString)) {
                            CxScanResult.this.infoCount++;
                        }
                    } else {
                        logger.warn("\"SeverityIndex\" attribute was not found in element \"Result\" in XML report. " +
                                "Make sure you are working with Checkmarx server version 7.1.6 HF3 or above.");
                    }
                }
            } else if ("Query".equals(qName)) {
                currentQueryName = attributes.getValue("name");
                if (currentQueryName==null)
                {
                    logger.warn("\"name\" attribute was not found in element \"Query\" in XML report");
                }
                currentQuerySeverity = attributes.getValue("SeverityIndex");
                if (currentQuerySeverity==null)
                {
                    logger.warn("\"SeverityIndex\" attribute was not found in element \"Query\" in XML report. " +
                            "Make sure you are working with Checkmarx server version 7.1.6 HF3 or above.");
                }
                currentQueryNumOfResults = 0;


            } else {
                if ("CxXMLResults".equals(qName))
                {
                    CxScanResult.this.resultDeepLink    = constructDeepLink(attributes.getValue("DeepLink"));
                    CxScanResult.this.scanStart         = attributes.getValue("ScanStart");
                    CxScanResult.this.scanTime          = attributes.getValue("ScanTime");
                    CxScanResult.this.linesOfCodeScanned= attributes.getValue("LinesOfCodeScanned");
                    CxScanResult.this.filesScanned      = attributes.getValue("FilesScanned");
                    CxScanResult.this.scanType          = attributes.getValue("ScanType");
                }
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

                if (StringUtils.equals(qr.getSeverity(),CxResultSeverity.HIGH.xmlParseString))
                {
                    CxScanResult.this.highQueryResultList.add(qr);
                } else if(StringUtils.equals(qr.getSeverity(),CxResultSeverity.MEDIUM.xmlParseString))
                {
                    CxScanResult.this.mediumQueryResultList.add(qr);
                } else if(StringUtils.equals(qr.getSeverity(),CxResultSeverity.LOW.xmlParseString))
                {
                    CxScanResult.this.lowQueryResultList.add(qr);
                } else if(StringUtils.equals(qr.getSeverity(),CxResultSeverity.INFO.xmlParseString))
                {
                    CxScanResult.this.infoQueryResultList.add(qr);
                } else {
                    logger.warn("Encountered a result query with unknown severity: " + qr.getSeverity());
                }
            }
        }

        @NotNull
        private String constructDeepLink(@Nullable String rawDeepLink){
            if (rawDeepLink==null)
            {
                logger.warn("\"DeepLink\" attribute was not found in element \"CxXMLResults\" in XML report");
                return "";
            }
            String token = "CxWebClient";
            String [] tokens = rawDeepLink.split(token);
            if (tokens.length < 1)
            {
                logger.warn("DeepLink value found in XML report is of unexpected format: " + rawDeepLink + "\n"
                + "\"Open Code Viewer\" button will not be functional");
            }
            return CxScanResult.this.serverUrl + "/" + token + tokens[1];
        }
    }

    public static class QueryResult {
        @Nullable
        private String name;
        @Nullable
        private String severity;
        private int count;
        @Nullable
        public String getName() {
            return name;
        }

        public void setName(@Nullable String name) {
            this.name = name;
        }
        @Nullable
        public String getSeverity() {
            return severity;
        }

        public void setSeverity(@Nullable String severity) {
            this.severity = severity;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        @NotNull
        public String getPrettyName()
        {
            if (this.name!=null) {
                return this.name.replace('_', ' ');
            } else {
                return "";
            }
        }
    }
}
