package com.checkmarx.jenkins.opensourceanalysis;

import com.checkmarx.jenkins.CxScanBuilder;
import com.checkmarx.jenkins.logger.CxFormatter;
import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import org.apache.commons.io.FileUtils;
import org.whitesource.fs.ComponentScan;

import java.io.*;
import java.nio.charset.Charset;
import java.util.Properties;
import java.util.logging.*;
import java.util.logging.Level;
import java.util.logging.Logger;


public class OsaScannerCallable implements FilePath.FileCallable<String>, Serializable {

    private static final long serialVersionUID = 1L;

    private Properties scannerProperties;
    private  TaskListener listener;

    public OsaScannerCallable(Properties scannerProperties, TaskListener listener){
        this.scannerProperties = scannerProperties;
        this.listener = listener;
    }

    @Override
    public String invoke(File file, VirtualChannel channel) throws IOException, InterruptedException {
        scannerProperties.put("d", file.getAbsolutePath());
        ComponentScan componentScan = new ComponentScan(scannerProperties);

        //we do this in order to redirect the logs from the filesystem agent component to the build console
        Logger rootLog = Logger.getLogger("");
        StreamHandler handler = new StreamHandler(listener.getLogger(), new CxFormatter());
        handler.setLevel(Level.ALL);
        rootLog.addHandler(handler);
        String dependenciesJson;
        try {
            dependenciesJson = componentScan.scan();
        } finally {
            handler.flush();
            rootLog.removeHandler(handler);
        }

        File dependenciesFile = new File(file.getAbsolutePath(), CxScanBuilder.REPORTS_FOLDER + "/OSADependencies.json");
        try {
            FileUtils.writeStringToFile(dependenciesFile, dependenciesJson, Charset.defaultCharset());
            listener.getLogger().println("[Checkmarx] - [info] - OSA dependencies saved to file: ["+dependenciesFile.getAbsolutePath()+"]");
        } catch (Exception e) {
            listener.getLogger().println("[Checkmarx] - [warning] - Failed to write osa dependencies json to file: " + e.getMessage());
        }

        return dependenciesJson;
    }
}
