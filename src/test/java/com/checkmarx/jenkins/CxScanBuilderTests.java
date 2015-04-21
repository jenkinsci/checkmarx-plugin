package com.checkmarx.jenkins;

import org.junit.Test;

import static org.junit.Assert.assertEquals;


/**
 *
 * @author Sergey Kadaner
 * @since 21/04/2015
 */

public class CxScanBuilderTests {

    @Test
    public void projectSetAndNotEmpty()  {
        String projectName = "projectName";
        String buildStep = null;

        CxScanBuilder cxScanBuilder = new CxScanBuilder(false, null, null, null,
                projectName,
                buildStep,
                null, null, false, null, null, false, false, 0, null, null, false, false, false, 0, 0, 0, false);
        assertEquals(cxScanBuilder.getProjectName(), projectName);
    }

    @Test
    public void projectSetAndBuildStepNotEmpty()  {
        String projectName = "projectName";
        String buildStep = null;

        CxScanBuilder cxScanBuilder = new CxScanBuilder(false, null, null, null,
                projectName,
                buildStep,
                null, null, false, null, null, false, false, 0, null, null, false, false, false, 0, 0, 0, false);

        assertEquals(cxScanBuilder.getBuildStep(), projectName);
    }

    @Test
    public void buildStepSetAndProjectNotEmpty()  {
        String projectName = null;
        String buildStep = "projectName";;

        CxScanBuilder cxScanBuilder = new CxScanBuilder(false, null, null, null,
                projectName,
                buildStep,
                null, null, false, null, null, false, false, 0, null, null, false, false, false, 0, 0, 0, false);
        assertEquals(cxScanBuilder.getProjectName(), buildStep);
    }

    @Test
    public void buildStepSetAndNotEmpty()  {
        String projectName = null;
        String buildStep = "projectName";

        CxScanBuilder cxScanBuilder = new CxScanBuilder(false, null, null, null,
                projectName,
                buildStep,
                null, null, false, null, null, false, false, 0, null, null, false, false, false, 0, 0, 0, false);

        assertEquals(cxScanBuilder.getBuildStep(), buildStep);
    }

}