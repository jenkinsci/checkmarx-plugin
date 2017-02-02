package com.checkmarx.jenkins.filesystem.zip.callable;

import com.checkmarx.jenkins.CxConfig;
import com.checkmarx.jenkins.filesystem.zip.Zipper;
import com.checkmarx.jenkins.filesystem.zip.dto.CxZipResult;
import com.checkmarx.jenkins.filesystem.zip.dto.ZippingDetails;
import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import org.apache.commons.codec.binary.Base64OutputStream;
import org.jetbrains.annotations.NotNull;

import java.io.*;

/**
 * Creates zip file with source files.
 *
 * @author yevgenib
 * @since 11/03/14
 */
public class SastZipperCallable implements FilePath.FileCallable<CxZipResult>, Serializable{

    private static final long serialVersionUID = 1L;

    @NotNull
    private final String combinedFilterPattern;


    public SastZipperCallable(@NotNull String combinedFilterPattern) {
        this.combinedFilterPattern = combinedFilterPattern;
    }


    @Override
    public CxZipResult invoke(final File file, final VirtualChannel channel) throws IOException, InterruptedException {

        final File tempFile = File.createTempFile("base64ZippedSource", ".bin");
        final OutputStream fileOutputStream = new FileOutputStream(tempFile);
        final Base64OutputStream base64FileOutputStream = new Base64OutputStream(fileOutputStream, true, 0, null);

        ZippingDetails zippingDetails;
        try {
            zippingDetails = new Zipper().zip(file, combinedFilterPattern, base64FileOutputStream, CxConfig.maxZipSize());
        } finally {
            fileOutputStream.close();
        }
        final FilePath remoteTempFile = new FilePath(tempFile);

        return new CxZipResult(remoteTempFile, zippingDetails);
    }
}
