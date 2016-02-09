package com.checkmarx.jenkins.cryptography;

import hudson.FilePath;
import hudson.remoting.VirtualChannel;

import java.io.File;
import java.io.IOException;

/**
 * @author tsahi
 * @since 02/02/16
 */

public class CryptographicCallable implements FilePath.FileCallable<String> {
    @Override
    public String invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
        CryptographicService cryptographicService = new CryptographicService();
        return cryptographicService.calculateSHA1(f);
    }
}
