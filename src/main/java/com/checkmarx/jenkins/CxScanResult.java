package com.checkmarx.jenkins;

import hudson.PluginWrapper;
import hudson.model.*;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

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

    private static final Logger logger = Logger.getLogger(CxScanResult.class);

    public final AbstractBuild<?,?> owner;

    private int highCount;
    private int mediumCount;
    private int lowCount;
    private int infoCount;
    private LinkedList<QueryResult> highQueryResultList;
    private LinkedList<QueryResult> mediumQueryResultList;
    private LinkedList<QueryResult> lowQueryResultList;
    private LinkedList<QueryResult> infoQueryResultList;

    private String resultDeepLink;

    private String scanStart;
    private String scanTime;
    private String linesOfCodeScanned;
    private String filesScanned;
    private String scanType;

    private boolean resultIsValid;
    private String errorMessage;


    public CxScanResult(AbstractBuild owner)
    {
        this.owner = owner;
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

    public String getResultDeepLink() {
        return resultDeepLink;
    }

    public String getScanStart() {
        return scanStart;
    }

    public String getScanTime() {
        return scanTime;
    }

    public String getLinesOfCodeScanned() {
        return linesOfCodeScanned;
    }

    public String getFilesScanned() {
        return filesScanned;
    }

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

        private String currentQueryName;
        private String currentQuerySeverity;
        private int    currentQueryNumOfResults;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            super.startElement(uri, localName, qName, attributes);

            if ("Result".equals(qName))
            {
                currentQueryNumOfResults++;
                String severity = attributes.getValue("Severity");
                if (severity.equals(CxResultSeverity.HIGH.xmlParseString))
                {
                    CxScanResult.this.highCount++;

                } else if (severity.equals(CxResultSeverity.MEDIUM.xmlParseString))
                {
                    CxScanResult.this.mediumCount++;

                } else if (severity.equals(CxResultSeverity.LOW.xmlParseString))
                {
                    CxScanResult.this.lowCount++;
                } else if (severity.equals(CxResultSeverity.INFO.xmlParseString))
                {
                    CxScanResult.this.infoCount++;
                }
            } else if ("Query".equals(qName)) {  //TODO: Validate that the added else does not ruin correctness
                currentQueryName = attributes.getValue("name");
                currentQuerySeverity = attributes.getValue("Severity");
                currentQueryNumOfResults = 0;


            } else {
                if ("CxXMLResults".equals(qName))
                {
                    CxScanResult.this.resultDeepLink    = attributes.getValue("DeepLink");
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

                if (StringUtils.containsIgnoreCase(qr.getSeverity(),CxResultSeverity.HIGH.xmlParseString))
                {
                    CxScanResult.this.highQueryResultList.add(qr);
                } else if (StringUtils.containsIgnoreCase(qr.getSeverity(),CxResultSeverity.MEDIUM.xmlParseString))
                {
                    CxScanResult.this.mediumQueryResultList.add(qr);
                } else if(StringUtils.containsIgnoreCase(qr.getSeverity(),CxResultSeverity.LOW.xmlParseString))
                {
                    CxScanResult.this.lowQueryResultList.add(qr);
                } else if (StringUtils.containsIgnoreCase(qr.getSeverity(),CxResultSeverity.INFO.xmlParseString))
                {
                    CxScanResult.this.infoQueryResultList.add(qr);
                } else {
                    logger.warn("Encountered a result query with unknown severity: " + qr.getSeverity());
                };
            }
        }
    }

    public static class QueryResult {
        private String name;
        private String severity;
        private int count;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getSeverity() {
            return severity;
        }

        public void setSeverity(String severity) {
            this.severity = severity;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public String getPrettyName()
        {
            return this.name.replace('_',' ');
        }
    }

}
