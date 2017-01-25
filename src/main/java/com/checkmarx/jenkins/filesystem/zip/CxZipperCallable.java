package com.checkmarx.jenkins.filesystem.zip;

import com.checkmarx.components.zipper.ZipListener;
import com.checkmarx.components.zipper.Zipper;
import com.checkmarx.jenkins.CxConfig;
import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import org.apache.commons.codec.binary.Base64OutputStream;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * Creates zip file with source files.
 *
 * @author yevgenib
 * @since 11/03/14
 */
public class CxZipperCallable implements FilePath.FileCallable<CxZipResult> {

    private static final long serialVersionUID = 1L;

    @NotNull
    private final String combinedFilterPattern;
    private int numOfZippedFiles;

    private static final Logger LOGGER = LogManager.getLogManager().getLogger("hudson.WebAppMain");

    public CxZipperCallable(@NotNull String combinedFilterPattern) {
        this.combinedFilterPattern = combinedFilterPattern;
        this.numOfZippedFiles = 0;
    }

    @Override
    public CxZipResult invoke(final File file, final VirtualChannel channel) throws IOException, InterruptedException {
        ZipListener zipListener = new ZipListener() {
            @Override
            public void updateProgress(String fileName, long size) {
                numOfZippedFiles++;
                LOGGER.info("Zipping (" + FileUtils.byteCountToDisplaySize(size) + "): " + fileName + "\n");
            }
        };

        final File tempFile = File.createTempFile("base64ZippedSource", ".bin");
        final OutputStream fileOutputStream = new FileOutputStream(tempFile);
        final Base64OutputStream base64FileOutputStream = new Base64OutputStream(fileOutputStream, true, 0, null);

        try {
            new Zipper().zip(file, combinedFilterPattern, base64FileOutputStream, CxConfig.maxZipSize(), zipListener);
        } finally {
            fileOutputStream.close();
        }
        final FilePath remoteTempFile = new FilePath(tempFile);
        return new CxZipResult(remoteTempFile, numOfZippedFiles);
    }
}
