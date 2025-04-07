package com.checkmarx.jenkins;

/**
 * This class represents the severity of a result in a scan report
 */
public enum  CxResultSeverity {
	CRITICAL("Critical","4"),
	HIGH("High","3"),
    MEDIUM("Medium","2"),
    LOW("Low","1"),
    INFO("Info","0");


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