package com.checkmarx.jenkins.web.model;

import java.util.List;


/**
 *
 * @author tsahi
 * @since 02/02/16
 */
public class AnalyzeRequest {

    private long projectId;
    private List<String> hashValues;
    private static final int ORIGIN = 1;

    public AnalyzeRequest(long projectId, List<String> hashValues){
        this.projectId = projectId;
        this.hashValues = hashValues;
    }

    public long getProjectId(){
        return this.projectId;
    }

    public List<String> getHashValues(){
        return this.hashValues;
    }
}
