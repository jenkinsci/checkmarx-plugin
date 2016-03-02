package com.checkmarx.jenkins;

public enum JobStatusOnError {
	GLOBAL("Use Global Settings"), FAILURE("Failure"), UNSTABLE("Unstable");

	private final String displayName;

	JobStatusOnError(String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return displayName;
	}
}