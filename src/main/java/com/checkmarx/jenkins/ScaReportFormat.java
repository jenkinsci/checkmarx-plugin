package com.checkmarx.jenkins;

public enum ScaReportFormat {
	PDF("PDF"), XML("XML"), CSV("CSV"), JSON("JSON"), cyclonedxjson("cyclonedxjson"), cyclonedxxml("cyclonedxxml");

	private final String displayName;

	ScaReportFormat(String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return displayName;
	}
}