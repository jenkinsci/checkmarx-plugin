package com.checkmarx.jenkins;

import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;

import hudson.PluginWrapper;
import jenkins.model.Jenkins;

/**
 * Configuration class that loads its values from cxconfig.xml file located in
 * the classpath
 */
public class CxConfig {

	private static Properties configuration;
	private static final String CONFIGURATION_DEFAULT_FILTER_PATTERN_KEY = "DefaultFilterPattern";
	private static final String DEFAULT_OSA_ARCHIVE_INCLUDE_PATTERNS= "DefaultOSAArchiveIncludePatterns";
	private static final String DEFAULT_SCA_SERVER_URL = "DefaultSCAServerURL";
	private static final String DEFAULT_SCA_ACCESS_CONTROL_URL = "DefaultSCAAccessControlUrl";
	private static final String DEFAULT_SCA_WEB_APP_URL = "DefaultSCAWebAppUrl";
	private static final String CHARSET_NAME = "CharsetName";
	private static final String ALGORITHM = "Algorithm";
	private static final String AES = "AES";
	private static final String LENGTH = "Length";

	static {
		configuration = new Properties();
		try {
			InputStream inputStream = CxConfig.class.getResourceAsStream("cxconfig.xml");
			configuration.loadFromXML(inputStream);
			inputStream.close();
		} catch (Exception e) {
		}
	}

	private CxConfig() {
		// Hides default constructor
	}

	public static String defaultFilterPattern() {
		return configuration.getProperty(CONFIGURATION_DEFAULT_FILTER_PATTERN_KEY);
	}
	public static String version() {
		return Optional.ofNullable(Jenkins.getInstance())
				.map(Jenkins::getPluginManager)
				.map(pm -> pm.getPlugin("checkmarx"))
				.map(PluginWrapper::getVersion)
				.orElse("");
	}

	public static String getDefaultOsaArchiveIncludePatterns() {
		return configuration.getProperty(DEFAULT_OSA_ARCHIVE_INCLUDE_PATTERNS.trim());
	}

	public static String getDefaultScaServerUrl() {
		return configuration.getProperty(DEFAULT_SCA_SERVER_URL);
	}

	public static String getDefaultScaAccessControlUrl() {
		return configuration.getProperty(DEFAULT_SCA_ACCESS_CONTROL_URL);
	}

	public static String getDefaultScaWebAppUrl() {
		return configuration.getProperty(DEFAULT_SCA_WEB_APP_URL);
	}

	public static String getCharsetName() {
		return configuration.getProperty(CHARSET_NAME);
	}

	public static String getAlgorithm() {
		return configuration.getProperty(ALGORITHM);
	}

	public static String getAes() {
		return configuration.getProperty(AES);
	}

	public static int getLength() {
		return Integer.parseInt(configuration.getProperty(LENGTH));
	}
}
