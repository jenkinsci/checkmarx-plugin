package com.checkmarx.jenkins.filesystem.zip.dto;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.List;

/**
 * Created by: zoharby.
 * Date: 30/01/2017.
 */
public class ZippingDetails implements Serializable{

    private static final long serialVersionUID = 1L;

    @NotNull
    private final int numOfZippedFiles;
    @NotNull
    private final List<String> zippingLog;

    public ZippingDetails(int numOfZippedFiles, List<String> zippingLog) {
        this.numOfZippedFiles = numOfZippedFiles;
        this.zippingLog = zippingLog;
    }

    @NotNull
    public int getNumOfZippedFiles() {
        return numOfZippedFiles;
    }

    public List<String> getZippingLog() {
        return zippingLog;
    }
}
