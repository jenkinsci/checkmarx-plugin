package com.checkmarx.jenkins.filesystem;

import com.checkmarx.jenkins.logger.CxPluginLogger;
import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Created by tsahib on 7/5/2016.
 */
public class FolderPattern {

    private static CxPluginLogger LOGGER;
    private AbstractBuild<?, ?> build;
    private BuildListener listener;

    public FolderPattern(final AbstractBuild<?, ?> build, final BuildListener listener) {
        this.build = build;
        this.listener = listener;
        LOGGER = new CxPluginLogger(listener);
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
        LOGGER.debug("Exclude folders converted to: " + result.toString());
        return result.toString();
    }
}
