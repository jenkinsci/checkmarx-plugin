package com.checkmarx.jenkins;

import java.io.InputStream;
import java.util.Properties;

import jenkins.model.Jenkins;

/**
 * Configuration class that loads its values from cxconfig.xml file located in
 * the classpath
 */
public class CxConfig {

    private static Properties configuration;
    private static final String CONFIGURATION_DEFAULT_FILTER_PATTERN_KEY = "DefaultFilterPattern";
    private static final String DEFAULT_OSA_ARCHIVE_INCLUDE_PATTERNS = "DefaultOSAArchiveIncludePatterns";

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
        return Jenkins.getInstance().getPluginManager().getPlugin("checkmarx").getVersion();
    }

    public static String getDefaultOsaArchiveIncludePatterns() {
        return configuration.getProperty(DEFAULT_OSA_ARCHIVE_INCLUDE_PATTERNS);
    }
}
