package com.checkmarx.jenkins;

import hudson.FilePath;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.Serializable;

/**
 * Created with IntelliJ IDEA.
 * User: yevgenib
 * Date: 11/03/14
 * Time: 11:47
 * To change this template use File | Settings | File Templates.
 */
public class CxZipResult implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotNull
    private final FilePath tempFile;
    private final int numOfZippedFiles;
    @NotNull
    private final String logMessage;

    public CxZipResult(@NotNull FilePath tempFile, int numOfZippedFiles, @NotNull String logMessage) {
        this.tempFile = tempFile;
        this.numOfZippedFiles = numOfZippedFiles;
        this.logMessage = logMessage;
    }

    @NotNull
    public FilePath getTempFile() {
        return tempFile;
    }

    public int getNumOfZippedFiles(){
        return numOfZippedFiles;
    }

    @NotNull
    public String getLogMessage() {
        return logMessage;
    }
}
