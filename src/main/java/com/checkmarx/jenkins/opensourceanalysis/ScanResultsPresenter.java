package com.checkmarx.jenkins.opensourceanalysis;

import com.checkmarx.jenkins.web.model.GetOpenSourceSummaryResponse;
import org.apache.log4j.Logger;

/**
 * Created by tsahib on 9/13/2016.
 */
public class ScanResultsPresenter {
    private transient Logger logger;

    public ScanResultsPresenter(Logger logger) {
        this.logger = logger;
    }

    public void printResultsToOutput(GetOpenSourceSummaryResponse results) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n").append("open source libraries: ").append(results.getTotal()).append("\n");
        sb.append("vulnerable and outdated: ").append(results.getVulnerableAndOutdated()).append("\n");
        sb.append("vulnerable and updated: ").append(results.getVulnerableAndUpdate()).append("\n");
        sb.append("high vulnerabilities: ").append(results.getHighVulnerabilities()).append("\n");
        sb.append("medium vulnerabilities: ").append(results.getMediumVulnerabilities()).append("\n");
        sb.append("low vulnerabilities: ").append(results.getLowVulnerabilities()).append("\n");
        sb.append("with no known vulnerabilities: ").append(results.getNoKnownVulnerabilities()).append("\n");
        sb.append("vulnerability score: ").append(results.getVulnerabilityScore()).append("\n");
        logger.info(sb.toString());
    }
}
