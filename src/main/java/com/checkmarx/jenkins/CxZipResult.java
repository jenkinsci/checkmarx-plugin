package com.checkmarx.jenkins;

import hudson.FilePath;

import java.io.Serializable;

import org.jetbrains.annotations.NotNull;

/**
 * Stores zip file location.
 * 
 * @author yevgenib
 * @since 11/03/14
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
