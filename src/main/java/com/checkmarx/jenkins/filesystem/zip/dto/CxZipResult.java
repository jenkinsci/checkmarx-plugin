package com.checkmarx.jenkins.filesystem.zip.dto;

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
    @NotNull
    private final ZippingDetails zippingDetails;

    public CxZipResult(@NotNull FilePath tempFile, ZippingDetails zippingDetails) {
        this.tempFile = tempFile;
        this.zippingDetails = zippingDetails;
    }

    @NotNull
    public FilePath getTempFile() {
        return tempFile;
    }

    @NotNull
    public ZippingDetails getZippingDetails() {
        return zippingDetails;
    }
}
