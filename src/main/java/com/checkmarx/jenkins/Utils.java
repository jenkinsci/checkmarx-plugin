package com.checkmarx.jenkins;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {

    public enum DOMStatiscs
    {
        TotalFiles,
        GoodFiles,
        PartiallyGoodFiles,
        BadFiles,
        ParsedLoc,
        GoodLoc,
        BadLoc,
        NumberOfDomObjects,
        ScanCoverage,
        ScanCoverageLoc,
        FlowStartTime,
        FlowEndTime,
        ParseStartTime,
        ParseEndTime,
        QueryStartTime,
        QueryEndTime,
        ScanDuration,
        ResolverStartTime,
        ResolverEndTime,
        AbsIntStartTime,
        AbsIntEndTime
    }

    public static List<String> getDOMStatistics(List<String> log, DOMStatiscs type)
    {
        List<String> listOutput = new ArrayList<>();
        String pattern;

        switch (type)
        {
            case TotalFiles:
                pattern = "Total files\\s*(?<number>(\\d*))";
                break;
            case GoodFiles:
                pattern = "Good files:\\s*(?<number>(\\d*))";
                break;
            case PartiallyGoodFiles:
                pattern = "Partially good files:\\s*(?<number>(\\d*))";
                break;
            case BadFiles:
                pattern = "Bad files:\\s*(?<number>(\\d*))";
                break;
            case ParsedLoc:
                pattern = "Parsed LOC:\\s*(?<number>(\\d*))";
                break;
            case GoodLoc:
                pattern = "Good LOC:\\s*(?<number>(\\d*))";
                break;
            case BadLoc:
                pattern = "Bad LOC:\\s*(?<number>(\\d*))";
                break;
            case NumberOfDomObjects:
                pattern = "Number of DOM Objects:\\s*(?<number>(\\d*))";
                break;
            case ScanCoverage:
                pattern = "Scan coverage:\\s*(?<number>(\\d.*))";
                break;
            case ScanCoverageLoc:
                pattern = "Scan coverage LOC:\\s*(?<number>(\\d.*))";
                break;
            case FlowStartTime:
                pattern = "((?:\\d\\.)?\\d{2}:\\d{2}:\\d{2})(?=(?:\\.\\d*)? \\[Resolving\\] - Engine Phase \\(Start\\): Expanding graph from History)";
                break;
            case FlowEndTime:
                pattern = "((?:\\d\\.)?\\d{2}:\\d{2}:\\d{2})(?=(?:\\.\\d*)? \\[Resolving\\] - Engine Phase \\( End \\): Expanding graph from History)";
                break;
            case ParseStartTime:
                pattern = "((?:\\d\\.)?\\d{2}:\\d{2}:\\d{2})(?=(?:\\.\\d*)? \\[Unspecified\\] - Engine Phase \\(Start\\): Parsing)";
                break;
            case ParseEndTime:
                pattern = "((?:\\d\\.)?\\d{2}:\\d{2}:\\d{2})(?=(?:\\.\\d*)? \\[Unspecified\\] - Engine Phase \\( End \\): Parsing)";
                break;
            case QueryStartTime:
                pattern = "((?:\\d\\.)?\\d{2}:\\d{2}:\\d{2})(?=(?:\\.\\d*)? \\[Unspecified\\] - Engine Phase \\(Start\\): Querying)";
                break;
            case QueryEndTime:
                pattern = "((?:\\d\\.)?\\d{2}:\\d{2}:\\d{2})(?=(?:\\.\\d*)? \\[Unspecified\\] - Engine Phase \\( End \\): Querying)";
                break;
            case ScanDuration:
                pattern = "(?<=Elapsed Time: )((?:\\d\\.)?[0-9:]{8})";
                break;
            case ResolverStartTime:
                pattern = "((?:\\d\\.)?\\d{2}:\\d{2}:\\d{2})(?=(?:\\.\\d*)? \\[Resolving\\] - Engine Phase \\(Start\\): Resolver)";
                break;
            case ResolverEndTime:
                pattern = "((?:\\d\\.)?\\d{2}:\\d{2}:\\d{2})(?=(?:\\.\\d*)? \\[Resolving\\] - Engine Phase \\( End \\): Resolver)";
                break;
            case AbsIntStartTime:
                pattern = "((?:\\d\\.)?\\d{2}:\\d{2}:\\d{2})(?=(?:\\.\\d*)? \\[Resolving\\] - Engine Phase \\(Start\\): AbsInt Resolver)";
                break;
            case AbsIntEndTime:
                pattern = "((?:\\d\\.)?\\d{2}:\\d{2}:\\d{2})(?=(?:\\.\\d*)? \\[Resolving\\] - Engine Phase \\( End \\): AbsInt Resolver)";
                break;
            default:
                pattern = "";
                break;
        }

        if (!pattern.isEmpty())
        {
            for (String line : log)
            {
                Pattern p = Pattern.compile(pattern);
                Matcher m = p.matcher(line);
                boolean isMatch = m.find();

                if (isMatch)
                {
                    listOutput.add(m.group(1));
                }
            }
        }

        return listOutput;
    }

    public static List<String> getSlowestQueryInfo(List<String> log)
    {
        List<String> listOutput = new ArrayList<>();
        String pattern = "(?<=Query - )[aA-zZ._0-9]+";
        if (!pattern.isEmpty())
        {
            String slowestQueryName = "";
            String slowestQueryTime = "";
            int slowestQuerySecs = -1;

            for (String line : log)
            {
                Pattern p = Pattern.compile(pattern);
                Matcher m = p.matcher(line);

                boolean isMatch = m.find();
                if (isMatch)
                {
                    String queryTime = getQueryTime(line);
                    if (queryTime != null && !queryTime.equals("Failed"))
                    {
                        int secs = calcSecFromDateTimeString(queryTime);
                        if (secs > slowestQuerySecs)
                        {
                            slowestQueryName = m.group(0);
                            slowestQueryTime = queryTime;
                            slowestQuerySecs = secs;
                        }
                    }
                }
            }

            listOutput.add(slowestQueryName);
            listOutput.add(slowestQueryTime);
        }

        return listOutput;
    }

    public static int calcSecFromDateTimeString(List<String> startRegexList, List<String> endRegexList)
    {
        if (startRegexList.size() != endRegexList.size()) {
            return -1;
        }

        int totalDiff = 0;
        int size = startRegexList.size();

        for (int i = 0; i < size; i++) {
            totalDiff += calcSecFromDateTimeString(endRegexList.get(i)) - calcSecFromDateTimeString(startRegexList.get(i));
        }

        return totalDiff;
    }

    public static int calcSecFromDateTimeString(String text)
    {
        if (text.contains(".")) {

            String[] firstParts = text.split(":");
            String[] secondParts = firstParts[0].split(".");

            return (Integer.parseInt(secondParts[0]) * 24 * 60 * 60) + (Integer.parseInt(secondParts[1]) * 60 * 60) + (Integer.parseInt(firstParts[1]) * 60) + Integer.parseInt(firstParts[2]);
        }

        String[] firstParts = text.split(":");
        return (Integer.parseInt(firstParts[0]) * 60 * 60) + (Integer.parseInt(firstParts[1]) * 60) + Integer.parseInt(firstParts[2]);
    }

    public static String secAsTimeString(int sec)
    {
        int days = (int)Math.floor(sec / 86400);
        int hours = (int)Math.floor((sec - (days * 86400)) / 3600);
        int minutes = (int)Math.floor((sec - (days * 86400) - (hours * 3600)) / 60);
        int seconds = sec - (days * 86400) - (hours * 3600) - (minutes * 60);

        String str = "";
        if (days > 0 && days < 10){
            str = String.format("%s0%d days, ", str, days);
        }

        if (days > 9) {
            str = String.format("%s%d days, ", str, days);
        }

        if (hours < 10) {
            str = String.format("%s0%d:", str, hours);
        }

        if (hours > 9) {
            str = String.format("%s%d:", str, hours);
        }

        if (minutes < 10) {
            str = String.format("%s0%d:", str, minutes);
        }

        if (minutes > 9) {
            str = String.format("%s%d:", str, minutes);
        }

        if (seconds < 10) {
            str = String.format("%s0%d", str, seconds);
        }

        if (seconds > 9) {
            str = String.format("%s%d", str, seconds);
        }

        return str;
    }

    private static String getQueryTime(String line)
    {
        Pattern p = Pattern.compile("(?<=Duration = )[0-9:]+");
        Matcher m = p.matcher(line);

        boolean isMatch = m.find();
        if (isMatch) {
            if (!verifyIfQueryFailed(line)) {
                return m.group(0);
            }
            else {
                return "Failed";
            }
        }
        else {
            return null;
        }
    }

    private static boolean verifyIfQueryFailed(String line)
    {
        Pattern p = Pattern.compile("Failed!");
        Matcher m = p.matcher(line);

        boolean isMatch = m.find();
        if (isMatch) {
            return true;
        }
        else {
            return false;
        }
    }
}

