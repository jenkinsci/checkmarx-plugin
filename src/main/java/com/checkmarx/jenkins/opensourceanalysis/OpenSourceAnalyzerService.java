package com.checkmarx.jenkins.opensourceanalysis;

import com.checkmarx.jenkins.cryptography.CryptographicCallable;
import com.checkmarx.jenkins.folder.FoldersScanner;
import com.checkmarx.jenkins.web.client.RestClient;
import com.checkmarx.jenkins.web.model.AnalyzeRequest;
import com.checkmarx.jenkins.web.model.AnalyzeResponse;
import hudson.model.AbstractBuild;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author tsahi
 * @since 02/02/16
 */
public class OpenSourceAnalyzerService {

    private DependencyFolder dependencyFolder;
    private AbstractBuild<?, ?> build;
    private RestClient restClient;
    private long projectId;
    private transient Logger logger;
    private static final Pattern PARAM_LIST_SPLIT_PATTERN = Pattern.compile(",|$", Pattern.MULTILINE);

    public OpenSourceAnalyzerService(final AbstractBuild<?, ?> build, DependencyFolder dependencyFolder, RestClient restClient, long projectId, Logger logger) {
        this.dependencyFolder = dependencyFolder;
        this.build = build;
        this.restClient = restClient;
        this.projectId = projectId;
        this.logger = logger;
    }

    public void analyze() throws IOException, InterruptedException {
        try{
            if (dependencyFolder.getInclude().isEmpty()) {
                return;
            }

            Collection<DependencyInfo> dependencies = getDependenciesFromFolders();
            if (dependencies.isEmpty()){
                logger.info("No dependencies found");
                return;
            }

            List<String> hashValues = calculateHash(dependencies);
            AnalyzeResponse anaResponse = callAnalyzeApi(hashValues);
            printResultsToOutput(anaResponse);

        }catch (Exception e){
            logger.error("Open Source Analysis failed:", e);
        }
    }

    private List<String> calculateHash(Collection<DependencyInfo> dependencies) throws IOException, InterruptedException {
        List<String> hashValues = new ArrayList<>();
        for (DependencyInfo dependency : dependencies) {
            hashValues.add(dependency.getFilePath().act(new CryptographicCallable()));
        }
        return hashValues;
    }

    private Collection<DependencyInfo> getDependenciesFromFolders() throws IOException, InterruptedException {
        FoldersScanner foldersScanner = new FoldersScanner(splitParameters(dependencyFolder.getInclude()), splitParameters(dependencyFolder.getExclude()));
        return build.getWorkspace().act(foldersScanner);
    }

    private AnalyzeResponse callAnalyzeApi(List<String> hashValues) {
        AnalyzeRequest anaReq = new AnalyzeRequest(projectId, hashValues);
        return restClient.analyzeOpenSources(anaReq);
    }

    private void printResultsToOutput(AnalyzeResponse results) {
        StringBuilder sb = new StringBuilder();
        sb.append("open source libraries: ").append(results.getTotal()).append("\n");
        sb.append("vulnerable and outdated: ").append(results.getVulnerableAndOutdated()).append("\n");
        sb.append("vulnerable and updated: ").append(results.getVulnerableAndUpdate()).append("\n");
        sb.append("with no known vulnerabilities: ").append(results.getNoKnownVulnerabilities()).append("\n");
        logger.info(sb.toString());
    }

    private List<String> splitParameters(String paramList) {
        List<String> params = new ArrayList<>();
        String[] split = PARAM_LIST_SPLIT_PATTERN.split(paramList);

        if (paramList == null || split == null) {
            return params;
        }

        for (String param : split) {
            if (StringUtils.isNotBlank(param)) {
                params.add(param.trim());
            }
        }

        return params;
    }


}
