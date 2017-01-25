package com.checkmarx.jenkins.filesystem.zip;

import hudson.AbortException;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

import java.io.IOException;

/**
 * Created by tsahib on 7/5/2016.
 */
public class CxZip {

    private static Logger log;

    private AbstractBuild<?, ?> build;
    private BuildListener listener;

    public CxZip(Logger logger, final AbstractBuild<?, ?> build, final BuildListener listener) {
        log = logger;
        this.build = build;
        this.listener = listener;
    }

    public FilePath ZipWorkspaceFolder(String filterPattern) throws IOException, InterruptedException {
        FilePath baseDir = build.getWorkspace();
        if (baseDir == null) {
            throw new AbortException(
                    "Checkmarx Scan Failed: cannot acquire Jenkins workspace location. It can be due to workspace residing on a disconnected slave.");
        }

        log.info("Started zipping the workspace");

        CxZipperCallable zipperCallable = new CxZipperCallable(filterPattern, log);

        final CxZipResult zipResult = baseDir.act(zipperCallable);

        final FilePath tempFile = zipResult.getTempFile();
        final int numOfZippedFiles = zipResult.getNumOfZippedFiles();

        log.info("Zipping complete with " + numOfZippedFiles + " files, total compressed size: " +
                FileUtils.byteCountToDisplaySize(tempFile.length() / 8 * 6)); // We print here the size of compressed sources before encoding to base 64
        log.info("Temporary file with zipped and base64 encoded sources was created at: " + tempFile.getRemote());
        listener.getLogger().flush();

        return tempFile;
    }

    public FilePath zipSourceCode(String filterPattern) throws IOException, InterruptedException {
        ZipperCallable zipperCallable = new ZipperCallable(filterPattern);
        FilePath baseDir = build.getWorkspace();
        final CxZipResult zipResult = baseDir.act(zipperCallable);
        final FilePath tempFile = zipResult.getTempFile();
        return tempFile;
    }
}
