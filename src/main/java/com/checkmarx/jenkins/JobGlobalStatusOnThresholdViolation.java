package com.checkmarx.jenkins;

public enum JobGlobalStatusOnThresholdViolation {

	FAILURE("Failure"), UNSTABLE("Unstable");

	private final String displayName;

	JobGlobalStatusOnThresholdViolation(String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return displayName;
	}
}