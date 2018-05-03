package com.checkmarx.jenkins;

import org.apache.commons.lang.exception.ExceptionUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * Created by: dorg.
 * Date: 04/03/2018.
 */
public class ComponentScanFormatter extends Formatter {

    public static final String lineSeparator = System.lineSeparator();

    @Override
    public String format(LogRecord record) {
        if (record.getSourceClassName() != null && record.getSourceClassName().startsWith("org.whitesource")) {
            String message = formatMessage(record);
            String throwable = "";
            if (record.getThrown() != null) {
                throwable = ExceptionUtils.getStackTrace(record.getThrown());
            }

            return String.format("[OSA ComponentScan-%s]: %s %s" + lineSeparator,
                    record.getLevel(),
                    message,
                    throwable);
        }
        return "";
    }
}
