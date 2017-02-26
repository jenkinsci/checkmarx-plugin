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
import java.util.logging.Logger;

/**
 * Created by tsahib on 9/8/2016.
 */
public class OsaZipperCallable implements FilePath.FileCallable<CxZipResult>  {

    private static Logger LOGGER = Logger.getLogger(OsaZipperCallable.class.getName());


    @NotNull
    private final String combinedFilterPattern;

    public OsaZipperCallable(@NotNull String combinedFilterPattern){
        this.combinedFilterPattern = combinedFilterPattern;
    }

    @Override
    public CxZipResult invoke(File file, VirtualChannel channel) throws IOException, InterruptedException {
        final File tempFile = File.createTempFile("ZippedSourceCode", ".zip");
        FilePath remoteTempFile = new FilePath(tempFile);
        OutputStream fileOutputStream = null;
        try{
            fileOutputStream = new FileOutputStream(tempFile);
            ZippingDetails zippingDetails = new Zipper().zip(file, combinedFilterPattern, fileOutputStream, CxConfig.maxOSAZipSize());
            return new CxZipResult(remoteTempFile, zippingDetails);
        } catch (Exception e){
            deleteTempFile(remoteTempFile);
            throw e;
        }
        finally {
            if(fileOutputStream != null) {
                fileOutputStream.close();
            }
        }
    }


    public void deleteTempFile(FilePath tempFileToDelete) {

        if(tempFileToDelete != null) {
            try {
                if(tempFileToDelete.exists()) {
                    if(!tempFileToDelete.delete()) {
                        LOGGER.warning("Fail to delete temp file");
                    }
                }
            } catch (Exception e) {
                LOGGER.warning("Fail to delete temp file: " + e.getMessage());
            }
        }
    }
}
