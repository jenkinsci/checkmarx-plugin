package com.checkmarx.jenkins.filesystem.zip;

import com.checkmarx.components.zipper.Zipper;
import com.checkmarx.jenkins.CxConfig;
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
public class ZipperCallable implements FilePath.FileCallable<CxZipResult>  {

    @NotNull
    private final String combinedFilterPattern;

    public ZipperCallable(@NotNull String combinedFilterPattern){
        this.combinedFilterPattern = combinedFilterPattern;
    }

    @Override
    public CxZipResult invoke(File file, VirtualChannel channel) throws IOException, InterruptedException {
        final File tempFile = File.createTempFile("ZippedSourceCode", ".zip");
        OutputStream fileOutputStream = null;
        try{
            fileOutputStream = new FileOutputStream(tempFile);
            new Zipper().zip(file, combinedFilterPattern, fileOutputStream, CxConfig.maxZipSize(), null);
            final FilePath remoteTempFile = new FilePath(tempFile);
            return new CxZipResult(remoteTempFile, 0);
        }
        finally {
            if(fileOutputStream != null) {
                fileOutputStream.close();
            }
        }
    }
}
