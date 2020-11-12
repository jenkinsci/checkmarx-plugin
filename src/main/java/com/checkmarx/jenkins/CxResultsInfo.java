package com.checkmarx.jenkins;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private String maxMemoryAfter;
    private String averageMaxMemoryAfter;
    private String totalExceptions;
    private String totalAntlrExcpetions;
    private String totalResults;
    private String totalInfoMessages;
    private String totalWarnMessages;
    private String totalDebugMessages;
    private String totalErrorMessages;
    private String totalQueries;
    private String totalGeneralQueries;

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

    public String getMaxMemoryAfter() {
        return maxMemoryAfter;
    }

    public String getAverageMaxMemoryAfter() {
        return averageMaxMemoryAfter;
    }

    public String getTotalExceptions() {
        return totalExceptions;
    }

    public String getTotalAntlrExcpetions() {
        return totalAntlrExcpetions;
    }

    public String getTotalResults() {
        return totalResults;
    }

    public String getTotalInfoMessages() {
        return totalInfoMessages;
    }

    public String getTotalWarnMessages() {
        return totalWarnMessages;
    }

    public String getTotalDebugMessages() {
        return totalDebugMessages;
    }

    public String getTotalErrorMessages() {
        return totalErrorMessages;
    }

    public String getTotalQueries() {
        return totalQueries;
    }

    public String getTotalGeneralQueries() {
        return totalGeneralQueries;
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
        int scanDurationSecs = scanDurationRegex.size() > 0 ? Utils.calcSecFromDateTimeString(scanDurationRegex.get(scanDurationRegex.size() - 1)) : 0;
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
        this.maxMemoryAfter = "" + GetMaxMemoryUsed(logFileLines);
        this.averageMaxMemoryAfter = "" + GetMemoryUsedAverage(logFileLines);
        this.totalExceptions = "" + GetSystemExceptions(logFileLines);
        this.totalAntlrExcpetions = "" + GetAntlrExceptions(logFileLines);
        this.totalResults = "" + GetResults(logFileLines);
        this.totalInfoMessages = "" + GetInfoMessages(logFileLines);
        this.totalWarnMessages = "" + GetWarnMessages(logFileLines);
        this.totalDebugMessages = "" + GetDebugMessages(logFileLines);
        this.totalErrorMessages = "" + GetErrorMessages(logFileLines);
        this.totalQueries = "" + GetTotalQueries(logFileLines);
        this.totalGeneralQueries = "" + GetTotalGeneralQueries(logFileLines);
    }

    public static int GetMaxMemoryUsed(List<String> log)
    {
        List<Integer> memoryUsage = GetMemoryUsedList(log);
        if (memoryUsage != null && memoryUsage.size()>0)
        {
            return Collections.max(memoryUsage);

        }
        else
        {
            return 0;
        }
    }


    public static int GetMemoryUsedAverage(List<String> log)
    {

        List<Integer> memoryUsage = GetMemoryUsedList(log);
        if (memoryUsage != null && memoryUsage.size()>0)
        {

            int count = 0;
            int total = 0;

            for (int value: memoryUsage) {

                total += value;
                count++;

            }

            return total/count;

        }
        else
        {
            return 0;
        }
    }





    private static List<Integer> GetMemoryUsedList(List<String> log)
    {
        try
        {
            List<Integer> memoryUsed = new ArrayList<Integer>();
            List<String> logWithoutQueries = GetLogFileWithoutQueries(log);
            String pattern1 = "Used memory:\\s*(?<number>(\\d*))";


            for (String line : logWithoutQueries)
            {

                Pattern p1 = Pattern.compile(".+" + pattern1 + ".+",Pattern.CASE_INSENSITIVE);
                Matcher m1 = p1.matcher(line);
                boolean b1 = m1.matches();


                if (b1)
                {
                    memoryUsed.add(Integer.parseInt(m1.group(1)));
                }
            }

            return memoryUsed;
        }

        catch (Exception ex)
        {

            return null;
        }
    }


    private static int GetSystemExceptions(List<String> log)
    {
        try
        {
            int count = 0;
            String pattern1 = "System.*Exception:";


            for (String line : log)
            {
                Pattern p1 = Pattern.compile(".+" + pattern1 + ".+", Pattern.CASE_INSENSITIVE);
                Matcher m1 = p1.matcher(line);
                boolean b1 = m1.lookingAt();


                if (b1)
                {
                    count++;
                }
            }

            return count;
        }

        catch (Exception ex)
        {

            return 0;
        }
    }


    private static int GetInfoMessages(List<String> log)
    {
        try
        {
            int count = 0;
            String pattern1 = "info  available";


            for (String line : log)
            {

                Pattern p1 = Pattern.compile(".+" + pattern1 + ".+",Pattern.CASE_INSENSITIVE);
                Matcher m1 = p1.matcher(line);
                boolean b1 = m1.matches();


                if (b1)
                {
                    count++;
                }
            }

            return count;
        }

        catch (Exception ex)
        {

            return 0;
        }
    }

    private static int GetTotalQueries(List<String> log)
    {
        try
        {
            int count = 0;

            String pattern1 = "^(.*?)Severity";

            for (String line : log)
            {


                Pattern p1 = Pattern.compile(pattern1,Pattern.CASE_INSENSITIVE);
                Matcher m1 = p1.matcher(line);
                boolean b1 = m1.lookingAt();


                if (b1)
                {
                    count++;
                }
            }

            return count;
        }

        catch (Exception ex)
        {

            return 0;
        }
    }

    private static int GetTotalGeneralQueries(List<String> log)
    {
        try
        {
            int count = 0;

            String pattern1 = "^[A-Z].+?(?=_\\d+)(?!^Severity$)";
            String pattern2 = "^[A-Z].+?(?=_-\\d+)(?!^Severity$)";




            for (String line : log)
            {


                Pattern p1 = Pattern.compile(pattern1,Pattern.CASE_INSENSITIVE);
                Pattern p2 = Pattern.compile(pattern2,Pattern.CASE_INSENSITIVE);
                Matcher m1 = p1.matcher(line);
                Matcher m2 = p2.matcher(line);
                boolean b1 = m1.lookingAt();
                boolean b2 = m2.lookingAt();


                if (b1 || b2)
                {



                    count++;
                }
            }



            return count;
        }

        catch (Exception ex)
        {

            return 0;
        }
    }



    private static int GetWarnMessages(List<String> log)
    {
        try
        {
            int count = 0;
            String pattern1 = "warn  available";


            for (String line : log)
            {

                Pattern p1 = Pattern.compile(".+" + pattern1 + ".+",Pattern.CASE_INSENSITIVE);
                Matcher m1 = p1.matcher(line);
                boolean b1 = m1.matches();


                if (b1)
                {
                    count++;
                }
            }

            return count;
        }

        catch (Exception ex)
        {

            return 0;
        }
    }

    private static int GetDebugMessages(List<String> log)
    {
        try
        {
            int count = 0;
            String pattern1 = "debug  available";


            for (String line : log)
            {

                Pattern p1 = Pattern.compile(".+" + pattern1 + ".+",Pattern.CASE_INSENSITIVE);
                Matcher m1 = p1.matcher(line);
                boolean b1 = m1.matches();


                if (b1)
                {
                    count++;
                }
            }

            return count;
        }

        catch (Exception ex)
        {

            return 0;
        }
    }


    private static int GetErrorMessages(List<String> log)
    {
        try
        {
            int count = 0;
            String pattern1 = "error  available";


            for (String line : log)
            {

                Pattern p1 = Pattern.compile(".+" + pattern1 + ".+",Pattern.CASE_INSENSITIVE);
                Matcher m1 = p1.matcher(line);
                boolean b1 = m1.matches();


                if (b1)
                {
                    count++;
                }
            }

            return count;
        }

        catch (Exception ex)
        {

            return 0;
        }
    }

    private static int GetAntlrExceptions(List<String> log)
    {
        try
        {
            int count = 0;
            String pattern1 = "antlr4.*exception";


            for (String line : log)
            {

                Pattern p1 = Pattern.compile(".+" + pattern1 + ".+",Pattern.CASE_INSENSITIVE);
                Matcher m1 = p1.matcher(line);
                boolean b1 = m1.matches();


                if (b1)
                {
                    count++;
                }
            }

            return count;
        }

        catch (Exception ex)
        {

            return 0;
        }
    }


    private static List<String> GetLogFileWithoutQueries(List<String> log)
    {
        List<String> newLog = new ArrayList<String>();
        String pattern = "Finish analyzing sources of the project";

        for ( String  line : log)
        {

            Pattern p = Pattern.compile(pattern);//. represents single character
            Matcher m = p.matcher(line);
            boolean b = m.matches();


            if (!b)
            {
                newLog.add(line);
            }
        }

        return newLog;
    }


    public static List<String> Readfile(String filePath) throws FileNotFoundException {
        List<String> lines = new ArrayList<String>();
        File file = new File(filePath);

        if (file.exists())
        {

            Scanner reader = new Scanner(file);
            while (reader.hasNextLine()){

                String line = reader.nextLine();
                lines.add(line);

            }

            reader.close();
            return lines;
        }

        else
        {
            throw new FileNotFoundException("Cannot");
        }
    }


    public static String GetResults(List<String> log)
    {
        String pattern = "(?<=\\[Queries\\] - There are )\\s*(?<number>(\\d*))(?= results found)";
        String value = "";


        if (pattern != null && !pattern.isEmpty())
        {
            for (String line: log)
            {

                Pattern p = Pattern.compile(".+" + pattern + ".+",Pattern.CASE_INSENSITIVE);
                Matcher m = p.matcher(line);
                boolean b = m.matches();

                if (b) {

                    value = m.group(1);
                    return value;
                }
            }
        }

        return "N/A";
    }
}
