package com.checkmarx.jenkins;

public enum ScaReportFormat {
	PDF("PDF"), XML("XML"), CSV("CSV"), JSON("JSON"), cyclonedxjson("cyclonedxjson"), cyclonedxxml("cyclonedxxml");

	private final String displayNames;

	ScaReportFormat(String displayNames) {
		this.displayNames = displayNames;
	}

	public String getDisplayNames() {
		return displayNames;
	}
}