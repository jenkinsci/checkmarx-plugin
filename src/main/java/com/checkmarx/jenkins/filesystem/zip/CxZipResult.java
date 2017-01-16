package com.checkmarx.jenkins.filesystem.zip;

import hudson.FilePath;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

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


    public CxZipResult(@NotNull FilePath tempFile, int numOfZippedFiles) {
        this.tempFile = tempFile;
        this.numOfZippedFiles = numOfZippedFiles;
    }

    @NotNull
    public FilePath getTempFile() {
        return tempFile;
    }

    public int getNumOfZippedFiles(){
        return numOfZippedFiles;
    }

}
