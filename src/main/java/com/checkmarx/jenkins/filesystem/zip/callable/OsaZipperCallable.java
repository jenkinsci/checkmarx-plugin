package com.checkmarx.jenkins.filesystem.zip.callable;

import com.checkmarx.jenkins.CxConfig;
import com.checkmarx.jenkins.filesystem.zip.Zipper;
import com.checkmarx.jenkins.filesystem.zip.dto.CxZipResult;
import com.checkmarx.jenkins.filesystem.zip.dto.ZippingDetails;
import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Created by tsahib on 9/8/2016.
 */
public class OsaZipperCallable implements FilePath.FileCallable<CxZipResult>  {

    @NotNull
    private final String combinedFilterPattern;

    public OsaZipperCallable(@NotNull String combinedFilterPattern){
        this.combinedFilterPattern = combinedFilterPattern;
    }

    @Override
    public CxZipResult invoke(File file, VirtualChannel channel) throws IOException, InterruptedException {
        final File tempFile = File.createTempFile("ZippedSourceCode", ".zip");
        OutputStream fileOutputStream = null;
        try{
            fileOutputStream = new FileOutputStream(tempFile);
            ZippingDetails zippingDetails = new Zipper().zip(file, combinedFilterPattern, fileOutputStream, CxConfig.maxOSAZipSize());
            final FilePath remoteTempFile = new FilePath(tempFile);
            return new CxZipResult(remoteTempFile, zippingDetails);
        }
        finally {
            if(fileOutputStream != null) {
                fileOutputStream.close();
            }
        }
    }
}
