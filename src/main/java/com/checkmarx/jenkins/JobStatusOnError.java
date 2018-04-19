package com.checkmarx.jenkins;

public enum JobStatusOnError {
	GLOBAL("Use Global Settings"), FAILURE("Failure"), UNSTABLE("Unstable") SUCCESS("Success");

	private final String displayName;

	JobStatusOnError(String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return displayName;
	}
}
