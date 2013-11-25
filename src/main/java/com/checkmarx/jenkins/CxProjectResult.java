package com.checkmarx.jenkins;

import hudson.PluginWrapper;
import hudson.model.*;

/**
 * Created with IntelliJ IDEA.
 * User: denis
 * Date: 25/11/2013
 * Time: 12:07
 * Description:
 */
public class CxProjectResult implements HealthReportingAction {

    private AbstractProject owner;

    public CxProjectResult(AbstractProject owner)
    {
        assert owner != null : "owner must not be null";
        this.owner = owner;
    }

    public HealthReportingAction getLastBuildAction()
    {
        AbstractBuild<?, ?> r = this.owner.getLastBuild();
        while (r != null)
        {

            HealthReportingAction a = r.getAction(CxScanResult.class);
            if (a!=null)
            {
                return a;
            }
            r = r.getPreviousBuild();
        }
        return null;
    }

    @Override
    public String getUrlName() {
        return "checkmarx";
    }

    @Override
    public String getDisplayName() {
        return "Checkmarx Scan Results";
    }

    @Override
    public String getIconFileName() {
        return getIconPath() + "CxIcon24x24.png";
    }

    public String getIconPath() {
        PluginWrapper wrapper = Hudson.getInstance().getPluginManager().getPlugin(CxPlugin.class);
        return "/plugin/"+ wrapper.getShortName()+"/";
    }

    @Override
    public HealthReport getBuildHealth() {
        HealthReportingAction a = getLastBuildAction();
        if (a!=null)
        {
            return a.getBuildHealth();
        }
        return null;
    }

    public boolean isResultAvailable()
    {
        return getLastBuildAction() != null;
    }



}
