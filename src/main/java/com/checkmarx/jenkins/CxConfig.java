package com.checkmarx.jenkins;

import java.io.InputStream;
import java.util.Properties;

import jenkins.model.Jenkins;

/**
 * Configuration class that loads its values from cxconfig.xml file located in
 * the classpath
 */
public class CxConfig {

	private static final String REQUEST_TIME_OUT_DURATION_SEC = "RequestTimeOutDurationSec";
	private static final String SERVER_CALL_RETRY_NUMBER = "ServerCallRetryNumber";
	private static Properties configuration;
	private static final String CONFIGURATION_MAX_ZIP_SIZE_KEY = "MaxZipSizeBytes";
	private static final String CONFIGURATION_MAX_OSA_ZIP_SIZE_KEY = "MaxOSAZipSizeBytes";
	private static final String CONFIGURATION_DEFAULT_FILTER_PATTERN_KEY = "DefaultFilterPattern";

	static {
		configuration = new Properties();
		try {
			InputStream inputStream = CxConfig.class.getResourceAsStream("cxconfig.xml");
			configuration.loadFromXML(inputStream);
			inputStream.close();
		} catch (Exception e) {
			configuration.setProperty(CONFIGURATION_MAX_ZIP_SIZE_KEY, "209715200");
			configuration.setProperty(CONFIGURATION_MAX_OSA_ZIP_SIZE_KEY, "2146483647");
		}
	}

	private CxConfig() {
		// Hides default constructor
	}

	public static long maxZipSize() {
		return Integer.parseInt(configuration.getProperty(CONFIGURATION_MAX_ZIP_SIZE_KEY));
	}

	public static long maxOSAZipSize() {
		return Integer.parseInt(configuration.getProperty(CONFIGURATION_MAX_OSA_ZIP_SIZE_KEY));
	}

	public static String defaultFilterPattern() {
		return configuration.getProperty(CONFIGURATION_DEFAULT_FILTER_PATTERN_KEY);
	}

	public static String version() {
		return Jenkins.getInstance().getPluginManager().getPlugin("checkmarx").getVersion();
	}

	public static int getServerCallRetryNumber() {
		return Integer.parseInt(configuration.getProperty(SERVER_CALL_RETRY_NUMBER));
	}

	public static int getRequestTimeOutDuration() {
		return Integer.parseInt(configuration.getProperty(REQUEST_TIME_OUT_DURATION_SEC));
	}
}
