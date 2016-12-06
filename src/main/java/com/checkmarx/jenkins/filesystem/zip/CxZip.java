package com.checkmarx.jenkins.filesystem.zip;

import hudson.AbortException;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

import java.io.IOException;

/**
 * Created by tsahib on 7/5/2016.
 */
public class CxZip {
    private Logger logger;
    private Run<?, ?> build;
    private FilePath workspace;
    private TaskListener  listener;

    public CxZip(Logger logger, final Run<?, ?> build, FilePath workspace, final TaskListener  listener) {
        this.logger = logger;
        this.build = build;
        this.workspace = workspace;
        this.listener = listener;
    }

    public FilePath ZipWorkspaceFolder(String filterPattern) throws IOException, InterruptedException {
        if (this.workspace == null) {
            throw new AbortException(
                    "Checkmarx Scan Failed: cannot acquire Jenkins workspace location. It can be due to workspace residing on a disconnected slave.");
        }

        logger.info("Started zipping the workspace");

        CxZipperCallable zipperCallable = new CxZipperCallable(filterPattern);

        final CxZipResult zipResult = this.workspace.act(zipperCallable);
        logger.info(zipResult.getLogMessage());
        final FilePath tempFile = zipResult.getTempFile();
        final int numOfZippedFiles = zipResult.getNumOfZippedFiles();

        logger.info("Zipping complete with " + numOfZippedFiles + " files, total compressed size: " +
                FileUtils.byteCountToDisplaySize(tempFile.length() / 8 * 6)); // We print here the size of compressed sources before encoding to base 64
        logger.info("Temporary file with zipped and base64 encoded sources was created at: " + tempFile.getRemote());
        listener.getLogger().flush();

        return tempFile;
    }

    public FilePath zipSourceCode(String filterPattern) throws IOException, InterruptedException {
        ZipperCallable zipperCallable = new ZipperCallable(filterPattern);
        final CxZipResult zipResult = this.workspace.act(zipperCallable);
        final FilePath tempFile = zipResult.getTempFile();
        return tempFile;
    }
}
