package com.checkmarx.jenkins;


import hudson.WebAppMain;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.slf4j.Marker;

import java.util.logging.Logger;


public class JenkinsServerLogger implements org.slf4j.Logger {

    private final Logger log;

    public JenkinsServerLogger() {
        this.log = Logger.getLogger(WebAppMain.class.getName());
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
        log.info(s);
    }

    public void debug(String s, Throwable throwable) {
        log.info(s);
        if(throwable != null) {
            log.info(ExceptionUtils.getStackTrace(throwable));
        }
    }

    public void info(String s) {
        log.info(s);
    }

    public void info(String s, Throwable throwable) {
        log.info(s);
        if(throwable != null) {
            log.info(ExceptionUtils.getStackTrace(throwable));
        }
    }

    public void warn(String s) {
        log.warning(s);

    }

    public void warn(String s, Throwable throwable) {
        log.warning(s);
        if(throwable != null) {
            log.warning(ExceptionUtils.getStackTrace(throwable));
        }
    }

    public void error(String s) {
        log.severe(s);
    }

    public void error(String s, Throwable throwable) {
        log.severe(s);
        if(throwable != null) {
            log.severe(ExceptionUtils.getStackTrace(throwable));
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
        this.trace((String)format, (Object)arg1, (Object)arg2);
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
        this.debug((String)format, (Object)arg1, (Object)arg2);
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
        this.info((String)format, (Object)arg1, (Object)arg2);
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
        this.warn((String)format, (Object)arg1, (Object)arg2);
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
        this.error((String)format, (Object)arg1, (Object)arg2);
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
    }

    public void trace(String s, Object o) {
    }

    public void trace(String s, Object o, Object o1) {
    }

    public void trace(String s, Object... objects) {
    }

    public void trace(String s, Throwable throwable) {
    }

    public void debug(String s, Object o) {

    }

    public void debug(String s, Object o, Object o1) {
    }

    public void debug(String s, Object... objects) {

    }

    @Override
    public void info(String format, Object arg) {

    }

    @Override
    public void info(String format, Object arg1, Object arg2) {

    }

    @Override
    public void info(String format, Object... arguments) {

    }

    @Override
    public void warn(String format, Object arg) {

    }

    @Override
    public void warn(String format, Object... arguments) {

    }

    @Override
    public void warn(String format, Object arg1, Object arg2) {

    }


    @Override
    public void error(String format, Object arg) {

    }

    @Override
    public void error(String format, Object arg1, Object arg2) {

    }

    @Override
    public void error(String format, Object... arguments) {

    }
}
