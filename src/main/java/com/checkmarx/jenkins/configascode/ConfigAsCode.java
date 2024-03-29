package com.checkmarx.jenkins.configascode;

import com.typesafe.config.Optional;

public class ConfigAsCode {
    @Optional
    private ProjectConfig project;
    @Optional
    private String team;
    @Optional
    private SastConfig sast;
    @Optional
    private ScaConfig sca;

    public ConfigAsCode() {
    }

    public String getTeam() {
        return team;
    }

    public void setTeam(String team) {
        this.team = team;
    }

    public SastConfig getSast() {
        return sast;
    }

    public void setSast(SastConfig sast) {
        this.sast = sast;
    }

    public ProjectConfig getProject() {
        return project;
    }

    public void setProject(ProjectConfig project) {
        this.project = project;
    }

    public ScaConfig getSca() {
        return sca;
    }

    public void setSca(ScaConfig sca) {
        this.sca = sca;
    }
}
