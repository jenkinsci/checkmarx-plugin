package com.checkmarx.jenkins;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

/**
 * Creates a logger with the following name: "com.checkmarx.<suffix>.<className>"
 */
public class CxLogUtils {
    public static Logger loggerWithSuffix(@NotNull final Class clazz,@Nullable final String loggerSuffix)
    {
        if (loggerSuffix!=null)
        {
            return Logger.getLogger("com.checkmarx." + loggerSuffix + "." + clazz.getName());
        } else {
            return Logger.getLogger("com.checkmarx." + clazz.getName());
        }
    }

    public static Logger parentLoggerWithSuffix(@NotNull final String loggerSuffix)
    {
        return Logger.getLogger("com.checkmarx." + loggerSuffix);
    }
}
