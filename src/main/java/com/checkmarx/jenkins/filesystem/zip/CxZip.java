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

    private static String CANNOT_FIND_WORKSPACE = "Cannot acquire Jenkins workspace location. It can be due to workspace residing on a disconnected slave.";

    private AbstractBuild<?, ?> build;

    public CxZip(final AbstractBuild<?, ?> build, final BuildListener listener) {
        this.build = build;
        LOGGER = new CxPluginLogger(listener);
    }

    public FilePath ZipWorkspaceFolder(String filterPattern) throws IOException, InterruptedException {
        FilePath baseDir = build.getWorkspace();
        if (baseDir == null) {
            throw new AbortException(
                    "Checkmarx Scan Failed: "+CANNOT_FIND_WORKSPACE);
        }

        LOGGER.info("Started zipping the workspace, this may take a while.");

        SastZipperCallable sastZipperCallable = new SastZipperCallable(filterPattern);
        final CxZipResult zipResult = zipFileAndGetResult(baseDir, sastZipperCallable);

        logZippingCompletionSummery(zipResult, "Temporary file with zipped and base64 encoded sources", 64);

        return zipResult.getTempFile();
    }

    public FilePath zipSourceCode(String filterPattern) throws Exception {
        FilePath baseDir = build.getWorkspace();
        if (baseDir == null) {
            throw new Exception(CANNOT_FIND_WORKSPACE);
        }

        LOGGER.info("Started zipping files for OSA, this may take a while.");

        OsaZipperCallable osaZipperCallable = new OsaZipperCallable(filterPattern);
        final CxZipResult zipResult = zipFileAndGetResult(baseDir, osaZipperCallable);

        logZippingCompletionSummery(zipResult, "Temporary zip file", 1);

        return zipResult.getTempFile();
    }

    private CxZipResult zipFileAndGetResult(FilePath baseDir, FilePath.FileCallable<CxZipResult> callable) throws InterruptedException, IOException {
        try {
            return baseDir.act(callable);
        //Handles the case where "act" method works on a remote system catches the ZipperException and make it's own IOException
        }catch (IOException e){
            if(e.getCause() != null && e.getCause().getClass().isInstance(Zipper.MaxZipSizeReached.class)) {
                throw (Zipper.MaxZipSizeReached) e.getCause();
            }
            if(e.getCause() != null && e.getCause().getClass().isInstance(Zipper.ZipperException.class)){
                throw (Zipper.ZipperException)e.getCause();
            } else{
                throw e;
            }
        }
    }

    private void logZippingCompletionSummery(CxZipResult zipResult, String tempFileDescription, int base) throws IOException, InterruptedException {
        LOGGER.info("Zipping complete with " + zipResult.getZippingDetails().getNumOfZippedFiles() + " files, total compressed size: " +
                FileUtils.byteCountToDisplaySize(zipResult.getTempFile().length() / base)); // We print here the size of compressed sources before encoding to base
        LOGGER.info(tempFileDescription+" was created at: " + zipResult.getTempFile().getRemote());
    }
}
