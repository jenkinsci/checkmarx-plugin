package com.checkmarx.jenkins.opensourceanalysis;

import com.checkmarx.jenkins.CxWebService;
import com.checkmarx.jenkins.cryptography.CryptographicCallable;
import com.checkmarx.jenkins.folder.FoldersScanner;
import com.checkmarx.jenkins.web.client.RestClient;
import com.checkmarx.jenkins.web.model.AnalyzeRequest;
import com.checkmarx.jenkins.web.model.CxException;
import com.checkmarx.jenkins.web.model.GetOpenSourceSummaryRequest;
import com.checkmarx.jenkins.web.model.GetOpenSourceSummaryResponse;
import hudson.model.AbstractBuild;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import com.checkmarx.jenkins.cryptography.CryptographicCallable;
import com.checkmarx.jenkins.folder.FoldersScanner;
import com.checkmarx.jenkins.web.client.RestClient;
import com.checkmarx.jenkins.web.model.AnalyzeRequest;
import com.checkmarx.jenkins.web.model.GetOpenSourceSummaryRequest;
import com.checkmarx.jenkins.web.model.GetOpenSourceSummaryResponse;

/**
 * @author tsahi
 * @since 02/02/16
 */
public class OpenSourceAnalyzerService {

    private static final String OSA_RUN_STARTED="OSA (open source analysis) Run has started";
    private static final String OSA_RUN_ENDED="OSA (open source analysis) Run has finished successfully";
    private DependencyFolder dependencyFolder;
    private AbstractBuild<?, ?> build;
    private RestClient restClient;
    private long projectId;
    private transient Logger logger;
    private static final Pattern PARAM_LIST_SPLIT_PATTERN = Pattern.compile(",|$| ", Pattern.MULTILINE);
    private CxWebService webServiceClient;
    public static final String NO_LICENSE_ERROR = "Open Source Analysis License is not enabled for this project.Please contact your CxSAST Administrator";

    public OpenSourceAnalyzerService(final AbstractBuild<?, ?> build, DependencyFolder dependencyFolder, RestClient restClient, long projectId, Logger logger, CxWebService webServiceClient) {
        this.dependencyFolder = dependencyFolder;
        this.build = build;
        this.restClient = restClient;
        this.projectId = projectId;
        this.logger = logger;
        this.webServiceClient = webServiceClient;
    }

    public void analyze() throws IOException, InterruptedException {
        try{
            if (!isOsaConfigured()) {
                return;
            }

            if (!validLicense()){
                logger.error(NO_LICENSE_ERROR);
                return;
            }

            logger.info(OSA_RUN_STARTED);
            Collection<DependencyInfo> dependencies = getDependenciesFromFolders();
            if (dependencies.isEmpty()){
                logger.info("No dependencies found");
                return;
            }

            List<String> hashValues = calculateHash(dependencies);
            callAnalyzeApi(hashValues);
            GetOpenSourceSummaryResponse summaryResponse = getOpenSourceSummary();
            printResultsToOutput(summaryResponse);
            logger.info(OSA_RUN_ENDED);
        }
        catch (Exception e){
            logger.error("Open Source Analysis failed:", e);
        }
    }

    private boolean isOsaConfigured() {
        return ! StringUtils.isEmpty(dependencyFolder.getInclude());
    }

    private boolean validLicense() {
        return webServiceClient.isOsaLicenseValid();
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

    private void callAnalyzeApi(List<String> hashValues) throws Exception {
        AnalyzeRequest anaReq = new AnalyzeRequest(projectId, hashValues);
        restClient.analyzeOpenSources(anaReq);
    }

    private GetOpenSourceSummaryResponse getOpenSourceSummary() throws Exception {
        GetOpenSourceSummaryRequest summaryRequest = new GetOpenSourceSummaryRequest(projectId);
        return restClient.getOpenSourceSummary(summaryRequest);
    }

    private void printResultsToOutput(GetOpenSourceSummaryResponse results) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n").append("open source libraries: ").append(results.getTotal()).append("\n");
        sb.append("vulnerable and outdated: ").append(results.getVulnerableAndOutdated()).append("\n");
        sb.append("vulnerable and updated: ").append(results.getVulnerableAndUpdate()).append("\n");
        sb.append("with no known vulnerabilities: ").append(results.getNoKnownVulnerabilities()).append("\n");
        sb.append("vulnerability score: ").append(results.getVulnerabilityScore()).append("\n");
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
