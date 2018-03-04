package com.checkmarx.jenkins.logger;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * Created by: dorg.
 * Date: 04/03/2018.
 */
public class CxFormatter extends Formatter {

    public static final String lineSeparator =  System.lineSeparator();

    @Override
    public String format(LogRecord record) {

        String message = formatMessage(record);
        String throwable = "";
        if (record.getThrown() != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            pw.println();
            record.getThrown().printStackTrace(pw);
            pw.close();
            throwable = sw.toString();
        }

        return String.format("[Checkmarx] - [%s] - %s %s" + lineSeparator,
                record.getLevel(),
                message,
                throwable);


    }
}
