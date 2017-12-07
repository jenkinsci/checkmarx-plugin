package com.checkmarx.jenkins.filesystem;

import com.checkmarx.jenkins.logger.CxPluginLogger;
import hudson.EnvVars;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Created by tsahib on 7/5/2016.
 */
public class FolderPattern {

    private transient CxPluginLogger logger;

    private Run<?, ?> run;
    private TaskListener listener;

    public FolderPattern(final Run<?, ?> run, final TaskListener listener) {
        this.run = run;
        this.listener = listener;
        this.logger = new CxPluginLogger(listener);
    }

    public String generatePattern(String filterPattern, String excludeFolders) throws IOException, InterruptedException {
        EnvVars env = run.getEnvironment(listener);
        filterPattern = env.expand(filterPattern);
        excludeFolders = processExcludeFolders(env.expand(excludeFolders));
        if (!StringUtils.isEmpty(filterPattern) && !StringUtils.isEmpty(excludeFolders)){
            return filterPattern + "," + excludeFolders;
        }

        return filterPattern + excludeFolders;
    }

    @NotNull
    private String processExcludeFolders(String excludeFolders) {
        if (excludeFolders == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        String[] patterns = StringUtils.split(excludeFolders, ",\n");
        String prefix = "";

        for (String p : patterns) {
            p = p.trim();
            if (p.length() > 0) {
                result.append(prefix);
                result.append("!**/");
                result.append(p);
                result.append("/**");
                prefix = ", ";
            }
        }


        logger.info("Excluded folders converted to: " + result.toString());
        return result.toString();
    }
}
