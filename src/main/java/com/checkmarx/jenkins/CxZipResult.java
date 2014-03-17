package com.checkmarx.jenkins;

import hudson.FilePath;

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

    private final FilePath tempFile;
    private final int numOfZippedFiles;

    public CxZipResult(FilePath tempFile, int numOfZippedFiles) {
        this.tempFile = tempFile;
        this.numOfZippedFiles = numOfZippedFiles;
    }

    public FilePath getTempFile() {
        return tempFile;
    }

    public int getNumOfZippedFiles(){
        return numOfZippedFiles;
    }
}
