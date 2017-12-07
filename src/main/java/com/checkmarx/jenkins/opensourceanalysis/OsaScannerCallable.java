package com.checkmarx.jenkins.opensourceanalysis;

import com.checkmarx.jenkins.exception.CxOSAException;
import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.util.List;


public class OsaScannerCallable implements FilePath.FileCallable<List<OSAFile>>, Serializable {

    private static final long serialVersionUID = 1L;

    private OSAScanner osaScanner;

    private TaskListener listener;

    public OsaScannerCallable(OSAScanner osaScanner, TaskListener listener){
        this.osaScanner = osaScanner;
        this.listener = listener;
    }

    @Override
    public List<OSAFile> invoke(File file, VirtualChannel channel) throws IOException, InterruptedException {
        final File tempFile = Files.createTempDirectory("OSAUnzip").toFile();

        try {
            return osaScanner.scanFiles(file, tempFile, 4);
        } catch (CxOSAException e) {
            throw new IOException(e);
        } finally {
            try {
                FileUtils.deleteDirectory(tempFile);
            } catch (Exception e) {
                listener.getLogger().println("Failed to delete temp directory ["+tempFile.getAbsolutePath()+"]");
            }
        }
    }
}
