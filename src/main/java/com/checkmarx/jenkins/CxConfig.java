package com.checkmarx.jenkins;

import java.io.InputStream;
import java.util.Properties;

/**
 * Configuration class that loads its values from cxconfig.xml file located in the classpath
 */
public class CxConfig {

    private static Properties configuration;
    private static final String CONFIGURATION_MAX_ZIP_SIZE_KEY = "MaxZipSizeBytes";
    private static final String CONFIGURATION_DEFAULT_FILTER_PATTERN_KEY = "DefaultFilterPattern";
    private static final String CONFIGURATION_PLUGIN_VERSION_KEY = "PluginVersion";

    static {
        configuration = new Properties();
        try {
            InputStream inputStream = CxConfig.class.getResourceAsStream("cxconfig.xml");
            configuration.loadFromXML(inputStream);
            inputStream.close();
        } catch (Exception e)
        {
            configuration.setProperty(CONFIGURATION_MAX_ZIP_SIZE_KEY,"209715200");
        }
    }

    public static long maxZipSize()
    {
        return Integer.getInteger(configuration.getProperty(CONFIGURATION_MAX_ZIP_SIZE_KEY));
    }

    public static String defaultFilterPattern()
    {
        return configuration.getProperty(CONFIGURATION_DEFAULT_FILTER_PATTERN_KEY);
    }

    public static String version()
    {
        return configuration.getProperty(CONFIGURATION_PLUGIN_VERSION_KEY);
    }

}
