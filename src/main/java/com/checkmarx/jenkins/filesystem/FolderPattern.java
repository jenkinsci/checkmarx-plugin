package com.checkmarx.jenkins.filesystem;

import hudson.EnvVars;
import hudson.model.BuildListener;
import hudson.model.Run;
import hudson.model.TaskListener;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Created by tsahib on 7/5/2016.
 */
public class FolderPattern {

    private Logger logger;
    private Run<?, ?> build;
    private TaskListener listener;

    public FolderPattern(Logger logger, final Run<?, ?> build, final TaskListener listener) {
        this.logger = logger;
        this.build = build;
        this.listener = listener;
    }

    public String generatePattern(String filterPattern, String excludeFolders) throws IOException, InterruptedException {
        EnvVars env = build.getEnvironment(listener);
        return env.expand(filterPattern) + "," + processExcludeFolders(env.expand(excludeFolders));
    }

    @NotNull
    private String processExcludeFolders(String excludeFolders) {
        if (excludeFolders == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        String[] patterns = StringUtils.split(excludeFolders, ",\n");
        for (String p : patterns) {
            p = p.trim();
            if (p.length() > 0) {
                result.append("!**/");
                result.append(p);
                result.append("/**/*, ");
            }
        }
        logger.debug("Exclude folders converted to: " + result.toString());
        return result.toString();
    }
}
