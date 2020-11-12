package com.checkmarx.jenkins;

import java.util.List;

public class CxResultsInfo {

    private List<String> logFileLines;
    private String totalFiles;
    private String goodFiles;
    private String partiallyGoodFiles;
    private String badFiles;
    private String parsedLoc;
    private String goodLoc;
    private String badLoc;
    private String numberOfDomObjects;
    private String scanCoverage;
    private String scanCoverageLoc;
    private String flowDurationHours;
    private String parseDurationHours;
    private String queryDurationHours;
    private String scanDurationPercentage;
    private String resolverDurationHours;
    private String absIntDurationHours;
    private String slowestQueryName;
    private String slowestQueryTime;

    public CxResultsInfo(List<String> logFileLines) {
        this.logFileLines = logFileLines;
    }

    public String getTotalFiles() {
        return totalFiles;
    }

    public String getGoodFiles() {
        return goodFiles;
    }

    public String getPartiallyGoodFiles() {
        return partiallyGoodFiles;
    }

    public String getBadFiles() {
        return badFiles;
    }

    public String getParsedLoc() {
        return parsedLoc;
    }

    public String getGoodLoc() {
        return goodLoc;
    }

    public String getBadLoc() {
        return badLoc;
    }

    public String getNumberOfDomObjects() {
        return numberOfDomObjects;
    }

    public String getScanCoverage() {
        return scanCoverage;
    }

    public String getScanCoverageLoc() {
        return scanCoverageLoc;
    }

    public String getFlowDurationHours() {
        return flowDurationHours;
    }

    public String getParseDurationHours() {
        return parseDurationHours;
    }

    public String getQueryDurationHours() {
        return queryDurationHours;
    }

    public String getScanDurationPercentage() {
        return scanDurationPercentage;
    }

    public String getResolverDurationHours() {
        return resolverDurationHours;
    }

    public String getAbsIntDurationHours() {
        return absIntDurationHours;
    }

    public String getSlowestQueryName() {
        return slowestQueryName;
    }

    public String getSlowestQueryTime() {
        return slowestQueryTime;
    }

    public void generateResults()
    {
        List<String> totalFilesRegex = Utils.getDOMStatistics(logFileLines, Utils.DOMStatiscs.TotalFiles);
        List<String> goodFilesRegex = Utils.getDOMStatistics(logFileLines, Utils.DOMStatiscs.GoodFiles);
        List<String> partiallyGoodFilesRegex = Utils.getDOMStatistics(logFileLines, Utils.DOMStatiscs.PartiallyGoodFiles);
        List<String> badFilesRegex = Utils.getDOMStatistics(logFileLines, Utils.DOMStatiscs.BadFiles);
        List<String> parsedLocRegex = Utils.getDOMStatistics(logFileLines, Utils.DOMStatiscs.ParsedLoc);
        List<String> goodLocRegex = Utils.getDOMStatistics(logFileLines, Utils.DOMStatiscs.GoodLoc);
        List<String> badLocRegex = Utils.getDOMStatistics(logFileLines, Utils.DOMStatiscs.BadLoc);
        List<String> numberOfDomObjectsRegex = Utils.getDOMStatistics(logFileLines, Utils.DOMStatiscs.NumberOfDomObjects);
        List<String> scanCoverageRegex = Utils.getDOMStatistics(logFileLines, Utils.DOMStatiscs.ScanCoverage);
        List<String> scanCoverageLocRegex = Utils.getDOMStatistics(logFileLines, Utils.DOMStatiscs.ScanCoverageLoc);

        List<String> flowStartRegex = Utils.getDOMStatistics(logFileLines, Utils.DOMStatiscs.FlowStartTime);
        List<String> flowEndRegex = Utils.getDOMStatistics(logFileLines, Utils.DOMStatiscs.FlowEndTime);

        List<String> parseStartRegex = Utils.getDOMStatistics(logFileLines, Utils.DOMStatiscs.ParseStartTime);
        List<String> parseEndRegex = Utils.getDOMStatistics(logFileLines, Utils.DOMStatiscs.ParseEndTime);

        List<String> queryStartRegex = Utils.getDOMStatistics(logFileLines, Utils.DOMStatiscs.QueryStartTime);
        List<String> queryEndRegex = Utils.getDOMStatistics(logFileLines, Utils.DOMStatiscs.QueryEndTime);

        List<String> scanDurationRegex = Utils.getDOMStatistics(logFileLines, Utils.DOMStatiscs.ScanDuration);

        List<String> resolverStartRegex = Utils.getDOMStatistics(logFileLines, Utils.DOMStatiscs.ResolverStartTime);
        List<String> resolverEndRegex = Utils.getDOMStatistics(logFileLines, Utils.DOMStatiscs.ResolverEndTime);

        List<String> absIntStartRegex = Utils.getDOMStatistics(logFileLines, Utils.DOMStatiscs.AbsIntStartTime);
        List<String> absIntEndRegex = Utils.getDOMStatistics(logFileLines, Utils.DOMStatiscs.AbsIntEndTime);

        List<String> slowestQueryInfoRegex = Utils.getSlowestQueryInfo(logFileLines);

        int flowDurationSecs = Utils.calcSecFromDateTimeString(flowStartRegex, flowEndRegex);
        int parseDurationSecs = Utils.calcSecFromDateTimeString(parseStartRegex, parseEndRegex);
        int queryDurationSecs = Utils.calcSecFromDateTimeString(queryStartRegex, queryEndRegex);
        int scanDurationSecs = Utils.calcSecFromDateTimeString(scanDurationRegex.get(scanDurationRegex.size() - 1));
        int resolverDurationSecs = Utils.calcSecFromDateTimeString(resolverStartRegex, resolverEndRegex);
        int absIntDurationSecs = Utils.calcSecFromDateTimeString(absIntStartRegex, absIntEndRegex);

        this.totalFiles = totalFilesRegex.size() > 0 ? totalFilesRegex.get(0) : "0";
        this.goodFiles = goodFilesRegex.size() > 0 ? goodFilesRegex.get(0) : "0";
        this.partiallyGoodFiles = partiallyGoodFilesRegex.size() > 0 ? partiallyGoodFilesRegex.get(0) : "0";
        this.badFiles = badFilesRegex.size() > 0 ? badFilesRegex.get(0) : "0";
        this.parsedLoc = parsedLocRegex.size() > 0 ? parsedLocRegex.get(0) : "0";
        this.goodLoc = goodLocRegex.size() > 0 ? goodLocRegex.get(0) : "0";
        this.badLoc = badLocRegex.size() > 0 ? badLocRegex.get(0) : "0";
        this.numberOfDomObjects = numberOfDomObjectsRegex.size() > 0 ? numberOfDomObjectsRegex.get(0) : "0";
        this.scanCoverage = scanCoverageRegex.size() > 0 ? scanCoverageRegex.get(0) : "0";
        this.scanCoverageLoc = scanCoverageLocRegex.size() > 0 ? scanCoverageLocRegex.get(0) : "0";
        this.flowDurationHours = Utils.secAsTimeString(flowDurationSecs);
        this.parseDurationHours = Utils.secAsTimeString(parseDurationSecs);
        this.queryDurationHours = Utils.secAsTimeString(queryDurationSecs);
        this.scanDurationPercentage = String.format("%.2f%%", ((float)queryDurationSecs / (float)scanDurationSecs) * 100);
        this.resolverDurationHours = Utils.secAsTimeString(resolverDurationSecs);
        this.absIntDurationHours = Utils.secAsTimeString(absIntDurationSecs);
        this.slowestQueryName = slowestQueryInfoRegex.size() > 0 ? slowestQueryInfoRegex.get(0) : "N/A";
        this.slowestQueryTime = slowestQueryInfoRegex.size() > 1 ? slowestQueryInfoRegex.get(1) : "N/A";
    }
}
