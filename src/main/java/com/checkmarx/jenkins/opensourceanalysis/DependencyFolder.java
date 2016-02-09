package com.checkmarx.jenkins.opensourceanalysis;

/**
 * Created by tsahib on 03/02/2016.
 */
public class DependencyFolder {
    private String include;
    private String exclude;

    public DependencyFolder(String include, String exclude){
        this.include = include;
        this.exclude = exclude;
    }

    public String getInclude(){
        return include;
    }

    public String getExclude(){
        return exclude;
    }
}
