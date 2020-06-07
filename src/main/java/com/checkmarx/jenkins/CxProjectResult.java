package com.checkmarx.jenkins;

import hudson.Extension;
import hudson.Functions;
import hudson.PluginWrapper;
import hudson.model.*;
import hudson.util.*;
import jenkins.model.Jenkins;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.StackedAreaRenderer;
import org.jfree.data.category.CategoryDataset;
import org.jfree.ui.RectangleInsets;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;

/**
 * @author denis
 * @since 25/11/2013
 */
public class CxProjectResult implements Action {

    private AbstractProject owner;

    public CxProjectResult(AbstractProject owner) {
        assert owner != null : "owner must not be null";
        this.owner = owner;
    }

    public CxScanResult getLastSynchronousBuildAction() {
        AbstractBuild<?, ?> r = this.owner.getLastBuild();
        if(r != null) {
            return r.getAction(CxScanResult.class);
        }

        return null;
    }

    public List<CxScanResult> getLastSynchronousBuildActions() {
        AbstractBuild<?, ?> r = this.owner.getLastBuild();
        if(r != null) {
            return r.getActions(CxScanResult.class);
        }
        return null;
    }

    public CxScanResult getLastSynchronousSASTBuildAction() {

        List<CxScanResult> lastSynchronousBuildActions = getLastSynchronousBuildActions();
        if(lastSynchronousBuildActions != null) {
            for (CxScanResult res : lastSynchronousBuildActions) {
                if(res.getSastEnabled() == null) {//case the build is before plugin version 8.80.0
                    return res;
                }

                else if(res.getSastEnabled()) {
                    return res;
                }
            }
        }
        return null;
    }

    @Override
    public String getUrlName() {
        if (isShowResults()) {
            return "checkmarx";
        } else {
            return null;
        }
    }

    @Override
    public String getDisplayName() {
      return null;
    }

    @Override
    public String getIconFileName() {
        return null;
    }

    @NotNull
    public String getIconPath() {
        return Optional.ofNullable(Jenkins.getInstance())
                .map(Jenkins::getPluginManager)
                .map(pm -> pm.getPlugin("checkmarx"))
                .map(PluginWrapper::getShortName)
                .map(shortName -> "/plugin/" + shortName + "/")
                .orElse("");
    }

    public boolean isShowResults() {
        @Nullable
        CxScanBuilder.DescriptorImpl descriptor = (CxScanBuilder.DescriptorImpl) Jenkins.getInstance().getDescriptor(CxScanBuilder.class);
        return descriptor != null && !descriptor.isHideResults();
    }

    public boolean isResultAvailable() {
        return getLastSynchronousBuildAction() != null;
    }

    private CxScanResult getLastBuildAction() {
        AbstractBuild<?, ?> r = this.owner.getLastBuild();
        return r != null ? r.getAction(CxScanResult.class) : null;
    }

    public String getProjectStateUrl() {
        CxScanResult action = getLastBuildAction();
        if (action == null) return "";
        return action.getProjectStateUrl();
    }

    public String getOsaProjectStateUrl() {
        CxScanResult action = getLastBuildAction();
        if (action == null) return "";
        return action.getOsaProjectStateUrl();
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////
    // Graph generation logic
    // /////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Generates a PNG image for the test result trend.
     */
    public void doGraph(StaplerRequest req, StaplerResponse rsp) throws IOException {
        if (ChartUtil.awtProblemCause != null) {
            // not available. send out error message
            rsp.sendRedirect2(req.getContextPath() + "/images/headless.png");
            return;
        }

        CxScanResult cxScanResult = getLastSynchronousBuildAction();
        if (cxScanResult != null && req.checkIfModified(cxScanResult.owner.getTimestamp(), rsp)) {
            return;
        }

        ChartUtil.generateGraph(req, rsp, createChart(req, buildDataSet(req)), calcDefaultSize());
    }

    /**
     * Generates a clickable map HTML for {@link #doGraph(StaplerRequest, StaplerResponse)}.
     */
    public void doGraphMap(StaplerRequest req, StaplerResponse rsp) throws IOException {
        CxScanResult cxScanResult = getLastSynchronousBuildAction();
        if (cxScanResult != null && req.checkIfModified(cxScanResult.owner.getTimestamp(), rsp)) {
            return;
        }
        ChartUtil.generateClickableMap(req, rsp, createChart(req, buildDataSet(req)), calcDefaultSize());
    }

    /**
     * Determines the default size of the trend graph.
     * <p>
     * This is default because the query parameter can choose arbitrary size. If the screen resolution is too low, use a
     * smaller size.
     */
    private Area calcDefaultSize() {
        Area res = Functions.getScreenResolution();
        if (res != null && res.width <= 800) {
            return new Area(250, 100);
        } else {
            return new Area(500, 200);
        }
    }

    private CategoryDataset buildDataSet(StaplerRequest req) {

        CxScanResult lastBuildAction = getLastSynchronousSASTBuildAction();
        if (lastBuildAction == null) {
            // We get here is there are no builds with scan results.
            // In this case we generate an empty graph
            DataSetBuilder<CxResultSeverity, String> dsb = new DataSetBuilder<CxResultSeverity, String>();

            dsb.add(0, CxResultSeverity.HIGH, "0");
            dsb.add(0, CxResultSeverity.MEDIUM, "0");
            dsb.add(0, CxResultSeverity.LOW, "0");
            return dsb.build();

        } else {
            DataSetBuilder<CxResultSeverity, ChartUtil.NumberOnlyBuildLabel> dsb = new DataSetBuilder<CxResultSeverity, ChartUtil.NumberOnlyBuildLabel>();

            for (CxScanResult a = lastBuildAction; a != null; a = a.getPreviousResult()) {
                dsb.add(a.getHighCount(), CxResultSeverity.HIGH, new ChartUtil.NumberOnlyBuildLabel((Run<?, ?>) a.owner));
                dsb.add(a.getMediumCount(), CxResultSeverity.MEDIUM, new ChartUtil.NumberOnlyBuildLabel((Run<?, ?>) a.owner));
                dsb.add(a.getLowCount(), CxResultSeverity.LOW, new ChartUtil.NumberOnlyBuildLabel((Run<?, ?>) a.owner));
            }
            return dsb.build();
        }

    }

    private JFreeChart createChart(StaplerRequest req, CategoryDataset dataset) {

        final String relPath = getRelPath(req);

        final JFreeChart chart = ChartFactory.createStackedAreaChart(null, // chart title
                null, // unused
                "count", // range axis label
                dataset, // data
                PlotOrientation.VERTICAL, // orientation
                true, // include legend
                true, // tooltips
                false // urls
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
                return relPath + label.build.getNumber() + "/testReport/"; // TODO: Check if need to change testReport
                // to checkmarx
            }
        };
        plot.setRenderer(ar);
        ar.setSeriesPaint(0, new Color(246, 0, 22)); // high.
        ar.setSeriesPaint(1, new Color(249, 167, 16)); // medium.
        ar.setSeriesPaint(2, new Color(254, 255, 3)); // low.

        // crop extra space around the graph
        plot.setInsets(new RectangleInsets(0, 0, 0, 5.0));

        return chart;
    }

    private String getRelPath(StaplerRequest req) {
        String relPath = req.getParameter("rel");
        if (relPath == null) {
            return "";
        }
        return relPath;
    }

    @Extension
    public static class Factory extends TransientProjectActionFactory {

        /**
         * This factory method is called by Jenkins to create instances of CxProjectResult for every project in the
         * system.
         */
        @Override
        public Collection<? extends Action> createFor(AbstractProject project) {
            // We don't want to add the CxProjectResult action to MatrixProject (appears as Multi-Configuration in GUI),
            // since it does not make sense to present our vulnerability graph in this level.

            if (project instanceof Project) {
                if (((Project) project).getBuildersList().get(CxScanBuilder.class) != null) {
                    LinkedList<Action> list = new LinkedList<Action>();
                    list.add(new CxProjectResult(project));
                    return list;
                }
            }

            if (isMavenPluginActive()) {
                MavenProjectResult mavenProjectResult = new MavenProjectResult(project);
                LinkedList<Action> list = mavenProjectResult.getMavenProjectResult();
                if (list != null) {
                    return list;
                }
            }
            return null;
        }

        private boolean isMavenPluginActive() {
            PluginWrapper mavenPlugin = Jenkins.getInstance().getPluginManager().getPlugin("maven-plugin");
            return mavenPlugin != null && mavenPlugin.isActive();
        }
    }
}
