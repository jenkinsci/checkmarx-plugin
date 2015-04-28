package com.checkmarx.jenkins;

import hudson.util.Secret;
import mockit.Expectations;
import mockit.Mocked;
import mockit.integration.junit4.JMockit;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;


/**
 * @author Sergey Kadaner
 * @since 21/04/2015
 */

@RunWith(JMockit.class)
public class CxScanBuilderTests {

    @Mocked
    Secret secret;

    @Test
    public void projectSetAndNotEmpty() {
        String projectName = "projectName";
        String buildStep = null;

        CxScanBuilder cxScanBuilder = createCxScanBuilder(projectName, buildStep, null);
        assertEquals(cxScanBuilder.getProjectName(), projectName);
    }

    @Test
    public void projectSetAndBuildStepNotEmpty() {
        String projectName = "projectName";
        String buildStep = null;

        CxScanBuilder cxScanBuilder = createCxScanBuilder(projectName, buildStep, null);

        assertEquals(cxScanBuilder.getBuildStep(), projectName);
    }

    @Test
    public void buildStepSetAndProjectNotEmpty() {
        String projectName = null;
        String buildStep = "projectName";

        CxScanBuilder cxScanBuilder = createCxScanBuilder(projectName, buildStep, null);
        assertEquals(cxScanBuilder.getProjectName(), buildStep);
    }

    @Test
    public void buildStepSetAndNotEmpty() {
        String projectName = null;
        String buildStep = "projectName";

        CxScanBuilder cxScanBuilder = createCxScanBuilder(projectName, buildStep, null);

        assertEquals(cxScanBuilder.getBuildStep(), buildStep);
    }

    @Test
    public void passwordUnchanged(@Mocked final Secret secret) {
        final String password = "password";

        new Expectations() {{
            secret.getPlainText();
            result = password;
        }};


        CxScanBuilder cxScanBuilder = createCxScanBuilder(null, null, password);

        assertEquals(password, cxScanBuilder.getPassword());
    }

    @NotNull
    private CxScanBuilder createCxScanBuilder(String projectName, String buildStep, String password) {
        return new CxScanBuilder(false, null, null, password,
                projectName,
                buildStep,
                null, null, false, null, null, false, false, 0, null, null, false, false, false, 0, 0, 0, false);
    }
}