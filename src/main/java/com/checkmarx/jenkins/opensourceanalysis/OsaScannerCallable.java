package com.checkmarx.jenkins.opensourceanalysis;

import com.checkmarx.jenkins.CxScanBuilder;
import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import org.apache.commons.io.FileUtils;
import org.whitesource.fs.ComponentScan;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Properties;


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
        String dependenciesJson = componentScan.scan();
        File dependenciesFile = new File(file.getAbsolutePath(), CxScanBuilder.REPORTS_FOLDER + "/OSADependencies.json");
        try {
            FileUtils.writeStringToFile(dependenciesFile, dependenciesJson, Charset.defaultCharset());
            listener.getLogger().println("OSA dependencies saved to file: ["+dependenciesFile.getAbsolutePath()+"]");
        } catch (Exception e) {
            listener.getLogger().println("Failed to write osa dependencies json to file: " + e.getMessage());
        }

        return dependenciesJson;
    }
}
