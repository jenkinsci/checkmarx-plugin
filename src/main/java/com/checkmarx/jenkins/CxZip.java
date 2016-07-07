package com.checkmarx.jenkins;

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
    private Logger logger;
    private AbstractBuild<?, ?> build;
    private BuildListener listener;

    public CxZip(Logger logger, final AbstractBuild<?, ?> build, final BuildListener listener) {
        this.logger = logger;
        this.build = build;
        this.listener = listener;
    }

    public FilePath ZipWorkspaceFolder(String filterPattern) throws IOException, InterruptedException {
        FilePath baseDir = build.getWorkspace();
        if (baseDir == null) {
            throw new AbortException(
                    "Checkmarx Scan Failed: cannot acquire Jenkins workspace location. It can be due to workspace residing on a disconnected slave.");
        }

        logger.info("Started zipping the workspace");

        CxZipperCallable zipperCallable = new CxZipperCallable(filterPattern);

        final CxZipResult zipResult = baseDir.act(zipperCallable);
        logger.info(zipResult.getLogMessage());
        final FilePath tempFile = zipResult.getTempFile();
        final int numOfZippedFiles = zipResult.getNumOfZippedFiles();

        logger.info("Zipping complete with " + numOfZippedFiles + " files, total compressed size: " +
                FileUtils.byteCountToDisplaySize(tempFile.length() / 8 * 6)); // We print here the size of compressed sources before encoding to base 64
        logger.info("Temporary file with zipped and base64 encoded sources was created at: " + tempFile.getRemote());
        listener.getLogger().flush();

        return tempFile;
    }
}
