package com.checkmarx.jenkins;

import hudson.model.Action;

/**
 * Created with IntelliJ IDEA.
 * User: denis
 * Date: 3/11/13
 * Time: 11:47
 * Description:
 */
public class CxScanResult implements Action {

    private String myString = "moia stroka";

    @Override
    public String getIconFileName() {
        return "/plugin/checkmarx/CxIcon24x24.png";
    }

    @Override
    public String getDisplayName() {
        return "Checkmarx Scan Results";
    }

    @Override
    public String getUrlName() {
        return "checkmarx";
    }

    public String getMyString() {
        return myString;
    }

    public void setMyString(String myString) {
        this.myString = myString;
    }
}
