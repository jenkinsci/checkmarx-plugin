package com.checkmarx.jenkins;

import hudson.maven.MavenModuleSet;
import hudson.model.AbstractProject;
import hudson.model.Action;

import java.util.LinkedList;

/**
 * @author tsahi
 * @since 02/06/16
 */
public class MavenProjectResult {
    private AbstractProject project;

    public MavenProjectResult(AbstractProject project) {
        this.project = project;
    }

    public LinkedList<Action> getMavenProjectResult(){
        if (project instanceof MavenModuleSet) {
            MavenModuleSet mavenProject = (MavenModuleSet) project;
            if (mavenProject.getPrebuilders().get(CxScanBuilder.class) != null
                    || mavenProject.getPostbuilders().get(CxScanBuilder.class) != null) {
                LinkedList<Action> list = new LinkedList<Action>();
                list.add(new CxProjectResult(project));
                return list;
            }
        }
        return null;
    }
}
