package com.checkmarx.jenkins;

import static org.junit.Assert.assertEquals;
import hudson.util.Secret;
import mockit.Expectations;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import mockit.integration.junit4.JMockit;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Sergey Kadaner
 * @since 21/04/2015
 */

@RunWith(JMockit.class)
public class CxScanBuilderTests {

	@Mocked
	Secret secret;

	@Test
	public void getProjectName_projectNameSet_projectNameNotEmpty() {
		String projectName = "projectName";
		String buildStep = null;

		CxScanBuilder cxScanBuilder = createCxScanBuilder(projectName, buildStep, null);
		assertEquals(projectName, cxScanBuilder.getProjectName());
	}

	@Test
	public void getBuildStep_projectNameSet_buildStepNotEmpty() {
		String projectName = "projectName";
		String buildStep = null;

		CxScanBuilder cxScanBuilder = createCxScanBuilder(projectName, buildStep, null);

		assertEquals(projectName, cxScanBuilder.getBuildStep());
	}

	@Test
	public void getProjectName_buildStepSet_projectNotEmpty() {
		String projectName = null;
		String buildStep = "projectName";

		CxScanBuilder cxScanBuilder = createCxScanBuilder(projectName, buildStep, null);
		assertEquals(buildStep, cxScanBuilder.getProjectName());
	}

	@Test
	public void getBuildStep_buildStepSet_buildStepNotEmpty() {
		String projectName = null;
		String buildStep = "projectName";

		CxScanBuilder cxScanBuilder = createCxScanBuilder(projectName, buildStep, null);

		assertEquals(buildStep, cxScanBuilder.getBuildStep());
	}

	@Test
	public void getPassword_validPasswordEntered_returnPasswordFromPassword(@Mocked final Secret mySecret) {
		final String password = "password";

		new Expectations() {
			{
				mySecret.getPlainText();
				result = password;
			}
		};

		CxScanBuilder cxScanBuilder = createCxScanBuilder(null, null, password);

		assertEquals(password, cxScanBuilder.getPassword());
	}

	@NotNull
	private CxScanBuilder createCxScanBuilder(String projectName, String buildStep, String password) {
		new MockUp<CxScanBuilder>() {
			@Mock
			void init() {
			}
		};
		return new CxScanBuilder(false, null, null, password, projectName, 0, buildStep, null, null, null, false, null, null, false, false, 0, null,
				null, false, false, false, 0, 0, 0, false, null, null);
	}
}