package com.checkmarx.jenkins;

import hudson.Functions;
import hudson.PluginWrapper;
import hudson.model.*;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.test.*;
import hudson.util.*;
import org.apache.log4j.Logger;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.StackedAreaRenderer;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.ui.RectangleInsets;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created with IntelliJ IDEA.
 * User: denis
 * Date: 3/11/13
 * Time: 11:47
 * Description:
 */

// One option is to inherit from AbstractTestResultAction<CxScanResult>


public class CxScanResult implements Action {



    public final AbstractBuild<?,?> owner;

    private int highCount;
    private int mediumCount;
    private int lowCount;
    private int infoCount;
    private LinkedList<QueryResult> queryResultList;
    private String resultDeepLink;
    private boolean resultIsValid;
    private String errorMessage;


    public CxScanResult(AbstractBuild owner)
    {
        this.owner = owner;
        this.resultIsValid=false;
        this.errorMessage = "No Scan Results"; // error message to appear if results were not parsed
        this.queryResultList = new LinkedList<QueryResult>();
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

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean isResultIsValid() {
        return resultIsValid;
    }

    public LinkedList<QueryResult> getQueryResultList() {
        return queryResultList;
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
        Logger logger = Logger.getLogger(CxScanResult.class);
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
                if (severity.equals("High"))
                {
                    CxScanResult.this.highCount++;

                } else if (severity.equals("Medium"))
                {
                    CxScanResult.this.mediumCount++;

                } else if (severity.equals("Low"))
                {
                    CxScanResult.this.lowCount++;
                } else if (severity.equals("Information"))
                {
                    CxScanResult.this.infoCount++;
                }
            } if ("Query".equals(qName)) {
                currentQueryName = attributes.getValue("name");
                currentQuerySeverity = attributes.getValue("Severity");
                currentQueryNumOfResults = 0;


            } else {
                if ("CxXMLResults".equals(qName))
                {
                    CxScanResult.this.resultDeepLink = attributes.getValue("DeepLink");
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
                CxScanResult.this.queryResultList.add(qr);

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
