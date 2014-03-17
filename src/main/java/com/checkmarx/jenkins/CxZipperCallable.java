package com.checkmarx.jenkins;

import com.checkmarx.components.zipper.ZipListener;
import com.checkmarx.components.zipper.Zipper;
import com.checkmarx.ws.CxJenkinsWebService.CliScanArgs;
import com.checkmarx.ws.CxJenkinsWebService.CxWSResponseRunID;
import hudson.AbortException;
import hudson.FilePath;
import hudson.remoting.Callable;
import hudson.remoting.VirtualChannel;
import org.apache.commons.codec.binary.Base64OutputStream;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;

import java.io.*;

/**
 * Created with IntelliJ IDEA.
 * User: yevgenib
 * Date: 11/03/14
 * Time: 11:46
 * To change this template use File | Settings | File Templates.
 */
public class CxZipperCallable implements FilePath.FileCallable<CxZipResult> {
    private static final long serialVersionUID = 1L;

    @NotNull
    private final String combinedFilterPattern;
    private int numOfZippedFiles;
    @NotNull
    private final StringBuffer logMessage = new StringBuffer();

    public CxZipperCallable(@NotNull String combinedFilterPattern){
        this.combinedFilterPattern = combinedFilterPattern;
        this.numOfZippedFiles = 0;
    }

    @Override
    public CxZipResult invoke(final File file, final VirtualChannel channel) throws IOException, InterruptedException {

        ZipListener zipListener = new ZipListener() {
            @Override
            public void updateProgress(String fileName, long size) {
                numOfZippedFiles++;
                logMessage.append("Zipping (" + FileUtils.byteCountToDisplaySize(size) + "): " + fileName + "\n");
            }
        };

        final File tempFile = File.createTempFile("base64ZippedSource", ".bin");
        final OutputStream fileOutputStream = new FileOutputStream(tempFile);
        final Base64OutputStream base64FileOutputStream = new Base64OutputStream(fileOutputStream,true,0,null);

        new Zipper().zip(file, combinedFilterPattern, base64FileOutputStream, CxConfig.maxZipSize(), zipListener);
        fileOutputStream.close();

        final FilePath remoteTempFile = new FilePath(tempFile);
        return new CxZipResult(remoteTempFile, numOfZippedFiles, logMessage.toString());
    }
}
