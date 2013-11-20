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


public class CxScanResult implements HealthReportingAction {



    public final AbstractBuild<?,?> owner;
    private Map<String,String> descriptions = new ConcurrentHashMap<String, String>();

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
     * Gets the diff string of failures.
     */
    public final String getFailureDiffString() {
        CxScanResult prev = getPreviousResult();
        if(prev==null)  return "";  // no record

        return " / "+ Functions.getDiffString(this.getHighCount() - prev.getHighCount());
    }

    public HealthReport getBuildHealth() {
        final int highCount = getHighCount();
        int score = highCount; // TODO: implement real health calculation
        String description = "Number of high severity vulnerabilities: " + Integer.toString(highCount);
        return new HealthReport(score, description);
    }

    /**
     * Gets the test result of the previous build, if it's recorded, or null.
     */

    private CxScanResult getPreviousResult() {
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


    /**
     * Generates a PNG image for the test result trend.
     */
    public void doGraph( StaplerRequest req, StaplerResponse rsp) throws IOException {
        if(ChartUtil.awtProblemCause!=null) {
            // not available. send out error message
            rsp.sendRedirect2(req.getContextPath()+"/images/headless.png");
            return;
        }

        if(req.checkIfModified(owner.getTimestamp(),rsp))
        {
            return; // TODO: check why the timestamp does not refresh
        }

        ChartUtil.generateGraph(req,rsp,createChart(req,buildDataSet(req)),calcDefaultSize());
    }

    /**
     * Generates a clickable map HTML for {@link #doGraph(StaplerRequest, StaplerResponse)}.
     */
    public void doGraphMap( StaplerRequest req, StaplerResponse rsp) throws IOException {
        if(req.checkIfModified(owner.getTimestamp(),rsp))
            return;
        ChartUtil.generateClickableMap(req,rsp,createChart(req,buildDataSet(req)),calcDefaultSize());
    }

    /**
     * Returns a full path down to a test result
     */
    public String getTestResultPath(TestResult it) {
        return getUrlName() + "/" + it.getRelativePathFrom(null);
    }

    /**
     * Determines the default size of the trend graph.
     *
     * This is default because the query parameter can choose arbitrary size.
     * If the screen resolution is too low, use a smaller size.
     */
    private Area calcDefaultSize() {
        Area res = Functions.getScreenResolution();
        if(res!=null && res.width<=800)
            return new Area(250,100);
        else
            return new Area(500,200);
    }

    private enum Severity {
        High,
        Medium,
        Low;
    }

    private CategoryDataset buildDataSet(StaplerRequest req) {
        boolean highOnly = Boolean.valueOf(req.getParameter("highOnly"));

        DataSetBuilder<Severity,ChartUtil.NumberOnlyBuildLabel> dsb = new DataSetBuilder<Severity,ChartUtil.NumberOnlyBuildLabel>();

        for( CxScanResult a=this; a!=null; a=a.getPreviousResult() ) {
            dsb.add( a.getHighCount(), Severity.High, new ChartUtil.NumberOnlyBuildLabel(a.owner));
            dsb.add( a.getMediumCount(), Severity.Medium, new ChartUtil.NumberOnlyBuildLabel(a.owner));
            dsb.add( a.getLowCount(),Severity.Low, new ChartUtil.NumberOnlyBuildLabel(a.owner));
        }
        return dsb.build();
    }

    private JFreeChart createChart(StaplerRequest req,CategoryDataset dataset) {

        final String relPath = getRelPath(req);

        final JFreeChart chart = ChartFactory.createStackedAreaChart(
                null,                   // chart title
                null,                   // unused
                "count",                  // range axis label
                dataset,                  // data
                PlotOrientation.VERTICAL, // orientation
                true,                     // include legend
                true,                     // tooltips
                false                     // urls
        );

        // NOW DO SOME OPTIONAL CUSTOMISATION OF THE CHART...

        // set the background color for the chart...

        chart.setBackgroundPaint(Color.white);

        final CategoryPlot plot = chart.getCategoryPlot();

        plot.setBackgroundPaint(Color.WHITE);
        plot.setOutlinePaint(null);
        plot.setForegroundAlpha(0.8f);
        plot.setRangeGridlinesVisible(true);
        plot.setRangeGridlinePaint(Color.black);

        CategoryAxis domainAxis = new ShiftedCategoryAxis(null);
        plot.setDomainAxis(domainAxis);
        domainAxis.setCategoryLabelPositions(CategoryLabelPositions.UP_90);
        domainAxis.setLowerMargin(0.0);
        domainAxis.setUpperMargin(0.0);
        domainAxis.setCategoryMargin(0.0);

        final NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());

        StackedAreaRenderer ar = new StackedAreaRenderer2() {
            @Override
            public String generateURL(CategoryDataset dataset, int row, int column) {
                ChartUtil.NumberOnlyBuildLabel label = (ChartUtil.NumberOnlyBuildLabel) dataset.getColumnKey(column);
                return relPath+label.build.getNumber()+"/testReport/";  //TODO: Check if need to change testReport to checkmarx
            }

            @Override
            public String generateToolTip(CategoryDataset dataset, int row, int column) {
                ChartUtil.NumberOnlyBuildLabel label = (ChartUtil.NumberOnlyBuildLabel) dataset.getColumnKey(column);
                AbstractTestResultAction a = label.build.getAction(AbstractTestResultAction.class);
                switch (row) {
                    case 0:
                        return String.valueOf(hudson.tasks.test.Messages.AbstractTestResultAction_fail(label.build.getDisplayName(), a.getFailCount()));
                    case 1:
                        return String.valueOf(hudson.tasks.test.Messages.AbstractTestResultAction_skip(label.build.getDisplayName(), a.getSkipCount()));
                    default:
                        return String.valueOf(hudson.tasks.test.Messages.AbstractTestResultAction_test(label.build.getDisplayName(), a.getTotalCount()));
                }
            }
        };
        plot.setRenderer(ar);
        ar.setSeriesPaint(0,new Color(246,0,22)); // high.
        ar.setSeriesPaint(1,new Color(249,167,16)); // medium.
        ar.setSeriesPaint(2,new Color(254,255,3)); // low.

        // crop extra space around the graph
        plot.setInsets(new RectangleInsets(0,0,0,5.0));

        return chart;
    }

    private String getRelPath(StaplerRequest req) {
        String relPath = req.getParameter("rel");
        if(relPath==null)   return "";
        return relPath;
    }

    /**
     * {@link TestObject}s do not have their own persistence mechanism, so updatable data of {@link TestObject}s
     * need to be persisted by the owning {@link AbstractTestResultAction}, and this method and
     * {@link #setDescription(TestObject, String)} provides that logic.
     *
     * <p>
     * The default implementation stores information in the 'this' object.
     *
     * @see TestObject#getDescription()
     */
    protected String getDescription(TestObject object) {
        return descriptions.get(object.getId());
    }

    protected void setDescription(TestObject object, String description) {
        descriptions.put(object.getId(), description);
    }

    public Object readResolve() {
        if (descriptions == null) {
            descriptions = new ConcurrentHashMap<String, String>();
        }

        return this;
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
