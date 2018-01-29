package com.checkmarx.jenkins.opensourceanalysis;

import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import org.whitesource.fs.ComponentScan;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Properties;


public class OsaScannerCallable implements FilePath.FileCallable<String>, Serializable {

    private static final long serialVersionUID = 1L;

    private Properties scannerProperties;

    public OsaScannerCallable(Properties scannerProperties){
        this.scannerProperties = scannerProperties;}

    @Override
    public String invoke(File file, VirtualChannel channel) throws IOException, InterruptedException {
            scannerProperties.put("d", file.getAbsolutePath());
            ComponentScan componentScan = new ComponentScan(scannerProperties);
            return componentScan.scan();
    }
}
