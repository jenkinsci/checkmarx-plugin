package com.checkmarx.jenkins.logger;

/**
 * Created by: zoharby.
 * Date: 31/01/2017.
 */
public interface LoggingDevice {

    public void info(String message);

    public void error(String message);

    public void error(String message, Throwable t);
}
