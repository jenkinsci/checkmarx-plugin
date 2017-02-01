package com.checkmarx.jenkins.filesystem.zip;

import com.checkmarx.jenkins.filesystem.zip.callable.OsaZipperCallable;
import com.checkmarx.jenkins.filesystem.zip.callable.SastZipperCallable;
import com.checkmarx.jenkins.filesystem.zip.dto.CxZipResult;
import com.checkmarx.jenkins.logger.CxPluginLogger;
import hudson.AbortException;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.io.Serializable;

/**
 * Created by tsahib on 7/5/2016.
 */
public class CxZip implements Serializable {

    private static final long serialVersionUID = 1L;

    private static CxPluginLogger LOGGER;

    private AbstractBuild<?, ?> build;

    public CxZip(final AbstractBuild<?, ?> build, final BuildListener listener) {
        this.build = build;
        LOGGER = new CxPluginLogger(listener);
    }

    public FilePath ZipWorkspaceFolder(String filterPattern) throws IOException, InterruptedException {
        FilePath baseDir = build.getWorkspace();
        if (baseDir == null) {
            throw new AbortException(
                    "Checkmarx Scan Failed: cannot acquire Jenkins workspace location. It can be due to workspace residing on a disconnected slave.");
        }

        LOGGER.info("Started zipping the workspace, this may take a while.");

        SastZipperCallable zipperCallable = new SastZipperCallable(filterPattern);
        final CxZipResult zipResult = baseDir.act(zipperCallable);
        final FilePath tempFile = zipResult.getTempFile();

        LOGGER.info("Zipping complete with " + zipResult.getZippingDetails().getNumOfZippedFiles() + " files, total compressed size: " +
                FileUtils.byteCountToDisplaySize(tempFile.length() / 8 * 6)); // We print here the size of compressed sources before encoding to base 64
        LOGGER.info("Temporary file with zipped and base64 encoded sources was created at: " + tempFile.getRemote());

        return tempFile;
    }

    public FilePath zipSourceCode(String filterPattern) throws IOException, InterruptedException {
        LOGGER.info("Started zipping files for OSA, this may take a while.");

        OsaZipperCallable zipperCallable = new OsaZipperCallable(filterPattern);
        FilePath baseDir = build.getWorkspace();
        final CxZipResult zipResult = baseDir.act(zipperCallable);
        final FilePath tempFile = zipResult.getTempFile();
        LOGGER.info("Zipping complete with " + zipResult.getZippingDetails().getNumOfZippedFiles() + " files, total compressed size: " +
                FileUtils.byteCountToDisplaySize(tempFile.length() / 8 * 6));

        return tempFile;
    }
}
