package com.checkmarx.jenkins.opensourceanalysis;

/**
 * Created by tsahib on 03/02/2016.
 */
public class DependencyFolder {
    private String include;
    private String exclude;
    private String archiveIncludePatterns;

    public DependencyFolder(String include, String exclude, String osaArchiveIncludePatterns){
        this.include = include;
        this.exclude = exclude;
        this.archiveIncludePatterns = osaArchiveIncludePatterns;
    }

    public String getInclude(){
        return include;
    }

    public String getExclude(){
        return exclude;
    }

    public String getArchiveIncludePatterns() {
        return archiveIncludePatterns;
    }
}
