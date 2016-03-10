package com.checkmarx.jenkins;

import hudson.model.Result;

public class ThresholdConfig {
    private int highSeverity;
    private int mediumSeverity;
    private int lowSeverity;
    private Result buildStatus;
    
	public int getHighSeverity() {
		return highSeverity;
	}
	public void setHighSeverity(int highSeverity) {
		this.highSeverity = highSeverity;
	}
	public int getMediumSeverity() {
		return mediumSeverity;
	}
	public void setMediumSeverity(int mediumSeverity) {
		this.mediumSeverity = mediumSeverity;
	}
	public int getLowSeverity() {
		return lowSeverity;
	}
	public void setLowSeverity(int lowSeverity) {
		this.lowSeverity = lowSeverity;
	}
	public Result getBuildStatus() {
		return buildStatus;
	}
	public void setBuildStatus(Result buildStatus) {
		this.buildStatus = buildStatus;
	}
}
