package com.checkmarx.jenkins;

import org.apache.commons.lang.exception.ExceptionUtils;

import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * Created by: dorg.
 * Date: 04/03/2018.
 */
public class ComponentScanFormatter extends Formatter {

    public static final String LINE_SEPARATOR = System.lineSeparator();

    @Override
    public String format(LogRecord record) {
        if (record.getSourceClassName() != null && record.getSourceClassName().startsWith("org.whitesource")) {
            String message = formatMessage(record);
            String throwable = "";
            if (record.getThrown() != null) {
                throwable = ExceptionUtils.getStackTrace(record.getThrown());
            }

            return String.format("[OSA ComponentScan-%s]: %s %s" + LINE_SEPARATOR,
                    record.getLevel(),
                    message,
                    throwable);
        }
        return "";
    }
}
