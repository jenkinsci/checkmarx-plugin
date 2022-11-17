package com.checkmarx.jenkins;

public enum JobGlobalStatusOnError {

	FAILURE("Failure"), UNSTABLE("Unstable"), ABORTED("ABORTED");

	private final String displayName;

	JobGlobalStatusOnError(String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return displayName;
	}
}