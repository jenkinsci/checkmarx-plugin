package com.checkmarx.jenkins.legacy8_7;

import hudson.model.Result;

public class ThresholdConfig {
    private Integer highSeverity;
    private Integer mediumSeverity;
    private Integer lowSeverity;
    private Result buildStatus;
    
	public Integer getHighSeverity() {
		return highSeverity;
	}
	public void setHighSeverity(Integer highSeverity) {
		this.highSeverity = highSeverity;
	}
	public Integer getMediumSeverity() {
		return mediumSeverity;
	}
	public void setMediumSeverity(Integer mediumSeverity) {
		this.mediumSeverity = mediumSeverity;
	}
	public Integer getLowSeverity() {
		return lowSeverity;
	}
	public void setLowSeverity(Integer lowSeverity) {
		this.lowSeverity = lowSeverity;
	}
	public Result getBuildStatus() {
		return buildStatus;
	}
	public void setBuildStatus(Result buildStatus) {
		this.buildStatus = buildStatus;
	}
}
