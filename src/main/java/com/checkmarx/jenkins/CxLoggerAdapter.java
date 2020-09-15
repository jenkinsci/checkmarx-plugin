package com.checkmarx.jenkins;


import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.helpers.MessageFormatter;

import java.io.PrintStream;

public class CxLoggerAdapter implements Logger {

    private static final String INFO_PREFIX = "[Cx-Info]: ";
    private static final String DEBUG_PREFIX = "[Cx-Debug]: ";
    private static final String ERROR_PREFIX = "[Cx-Error]: ";
    private static final String WARN_PREFIX = "[Cx-Warning]: ";
    private static final String TRACE_PREFIX = "[Cx-Trace]: ";

    private final PrintStream log;

    public CxLoggerAdapter(PrintStream log) {
        this.log = log;
    }

    @Override
    public String getName() {
        return "Checkmarx";
    }


    public boolean isDebugEnabled() {
        return true;
    }

    @Override
    public boolean isInfoEnabled() {
        return true;
    }

    @Override
    public boolean isWarnEnabled() {
        return true;
    }

    @Override
    public boolean isErrorEnabled() {
        return true;
    }

    public void debug(String s) {
        log.println(DEBUG_PREFIX + s);
    }

    public void debug(String s, Throwable throwable) {
        debug(s);
        if (throwable != null) {
            throwable.printStackTrace(log);
        }
    }

    public void info(String s) {
        log.println(INFO_PREFIX + s);
    }

    public void info(String s, Throwable throwable) {
        info(s);
        if (throwable != null) {
            throwable.printStackTrace(log);
        }
    }

    public void warn(String s) {
        log.println(WARN_PREFIX + s);
    }

    public void warn(String s, Throwable throwable) {
        warn(s);
        if (throwable != null) {
            throwable.printStackTrace(log);
        }
    }

    public void error(String s) {
        if (this.isErrorEnabled())
            log.println(ERROR_PREFIX + s);
    }

    public void error(String s, Throwable throwable) {
        log.println(ERROR_PREFIX + s);
        if (throwable != null) {
            throwable.printStackTrace(log);
        }
    }

    public String toString() {
        return this.getClass().getName() + "(" + this.getName() + ")";
    }


    //--- ignoring ---
    public boolean isTraceEnabled(Marker marker) {
        return this.isTraceEnabled();
    }

    public void trace(Marker marker, String msg) {
        this.trace(msg);
    }

    public void trace(Marker marker, String format, Object arg) {
        this.trace(format, arg);
    }

    public void trace(Marker marker, String format, Object arg1, Object arg2) {
        this.trace(format, arg1, arg2);
    }

    public void trace(Marker marker, String format, Object... arguments) {
        this.trace(format, arguments);
    }

    public void trace(Marker marker, String msg, Throwable t) {
        this.trace(msg, t);
    }

    public boolean isDebugEnabled(Marker marker) {
        return this.isDebugEnabled();
    }

    public void debug(Marker marker, String msg) {
        this.debug(msg);
    }

    public void debug(Marker marker, String format, Object arg) {
        this.debug(format, arg);
    }

    public void debug(Marker marker, String format, Object arg1, Object arg2) {
        this.debug(format, arg1, arg2);
    }

    public void debug(Marker marker, String format, Object... arguments) {
        this.debug(format, arguments);
    }

    public void debug(Marker marker, String msg, Throwable t) {
        this.debug(msg, t);
    }

    public boolean isInfoEnabled(Marker marker) {
        return this.isInfoEnabled();
    }

    public void info(Marker marker, String msg) {
        this.info(msg);
    }

    public void info(Marker marker, String format, Object arg) {
        this.info(format, arg);
    }

    public void info(Marker marker, String format, Object arg1, Object arg2) {
        this.info(format, arg1, arg2);
    }

    public void info(Marker marker, String format, Object... arguments) {
        this.info(format, arguments);
    }

    public void info(Marker marker, String msg, Throwable t) {
        this.info(msg, t);
    }

    public boolean isWarnEnabled(Marker marker) {
        return this.isWarnEnabled();
    }

    public void warn(Marker marker, String msg) {
        this.warn(msg);
    }

    public void warn(Marker marker, String format, Object arg) {
        this.warn(format, arg);
    }

    public void warn(Marker marker, String format, Object arg1, Object arg2) {
        this.warn(format, arg1, arg2);
    }

    public void warn(Marker marker, String format, Object... arguments) {
        this.warn(format, arguments);
    }

    public void warn(Marker marker, String msg, Throwable t) {
        this.warn(msg, t);
    }

    public boolean isErrorEnabled(Marker marker) {
        return this.isErrorEnabled();
    }

    public void error(Marker marker, String msg) {
        this.error(msg);
    }

    public void error(Marker marker, String format, Object arg) {
        this.error(format, arg);
    }

    public void error(Marker marker, String format, Object arg1, Object arg2) {
        this.error(format, arg1, arg2);
    }

    public void error(Marker marker, String format, Object... arguments) {
        this.error(format, arguments);
    }

    public void error(Marker marker, String msg, Throwable t) {
        this.error(msg, t);
    }

    public boolean isTraceEnabled() {
        return false;
    }

    public void trace(String s) {
        log.println(TRACE_PREFIX + s);
    }

    public void trace(String s, Object o) {
        if (this.isTraceEnabled())
            this.trace(MessageFormatter.format(s, o).getMessage());
    }

    public void trace(String s, Object o, Object o1) {
        if (this.isTraceEnabled())
            this.trace(MessageFormatter.format(s, o, o1).getMessage());
    }

    public void trace(String s, Object... objects) {
        if (this.isTraceEnabled())
            this.trace(MessageFormatter.arrayFormat(s, objects).getMessage());
    }

    public void trace(String s, Throwable throwable) {
        trace(s);
        if (throwable != null) {
            throwable.printStackTrace(log);
        }
    }

    public void debug(String s, Object o) {
        if (this.isDebugEnabled())
            this.debug(MessageFormatter.format(s, o).getMessage());
    }

    public void debug(String s, Object o, Object o1) {
        if (this.isDebugEnabled())
            this.debug(MessageFormatter.format(s, o, o1).getMessage());
    }

    public void debug(String s, Object... objects) {
        if (this.isDebugEnabled())
            this.debug(MessageFormatter.arrayFormat(s, objects).getMessage());
    }

    @Override
    public void info(String format, Object arg) {
        if (this.isInfoEnabled())
            this.info(MessageFormatter.format(format, arg).getMessage());
    }

    @Override
    public void info(String format, Object arg1, Object arg2) {
        if (this.isInfoEnabled())
            this.info(MessageFormatter.format(format, arg1, arg2).getMessage());
    }

    @Override
    public void info(String format, Object... arguments) {
        if (this.isInfoEnabled())
            this.info(MessageFormatter.arrayFormat(format, arguments).getMessage());
    }

    @Override
    public void warn(String format, Object arg) {
        if (this.isWarnEnabled())
            this.warn(MessageFormatter.format(format, arg).getMessage());
    }

    @Override
    public void warn(String format, Object... arguments) {
        if (this.isWarnEnabled())
            this.warn(MessageFormatter.arrayFormat(format, arguments).getMessage());
    }

    @Override
    public void warn(String format, Object arg1, Object arg2) {
        if (this.isWarnEnabled())
            this.warn(MessageFormatter.format(format, arg1, arg2).getMessage());
    }

    @Override
    public void error(String format, Object arg) {
        if (this.isErrorEnabled())
            this.error(MessageFormatter.format(format, arg).getMessage());
    }

    @Override
    public void error(String format, Object arg1, Object arg2) {
        if (this.isErrorEnabled())
            this.error(MessageFormatter.format(format, arg1, arg2).getMessage());
    }

    @Override
    public void error(String format, Object... arguments) {
        if (this.isErrorEnabled())
            this.error(MessageFormatter.arrayFormat(format, arguments).getMessage());
    }
}
