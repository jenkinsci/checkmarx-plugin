package com.checkmarx.jenkins;

/**
 * This class represents the severity of a result in a scan report
 */
public enum  CxResultSeverity {
    HIGH("High","High"),
    MEDIUM("Medium","Medium"),
    LOW("Low","Low"),
    INFO("Info","Information");


    private final String displayString; // This value is used for displaying a legend in a graph, and similar display uses
    public final String xmlParseString; // This value is used for detecting result severity while parsing results xml

    private CxResultSeverity(String displayString, String xmlParseString)
    {
        this.displayString = displayString;
        this.xmlParseString = xmlParseString;
    }

    @Override
    public String toString()
    {
        return displayString;
    }
}
