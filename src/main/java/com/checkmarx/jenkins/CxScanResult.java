package com.checkmarx.jenkins;

import static com.checkmarx.jenkins.CxResultSeverity.HIGH;
import static com.checkmarx.jenkins.CxResultSeverity.INFO;
import static com.checkmarx.jenkins.CxResultSeverity.LOW;
import static com.checkmarx.jenkins.CxResultSeverity.MEDIUM;
import hudson.PluginWrapper;
import hudson.model.Action;
import hudson.model.AbstractBuild;
import hudson.model.Hudson;
import hudson.util.IOUtils;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import javax.servlet.ServletOutputStream;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import jenkins.model.Jenkins;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.thoughtworks.xstream.annotations.XStreamOmitField;

/**
 * @author denis
 * @since 3/11/13
 */
public class CxScanResult implements Action {

	@XStreamOmitField
	private final Logger logger;

	public final AbstractBuild<?, ?> owner;
	private String serverUrl;

	private int highCount;
	private int mediumCount;
	private int lowCount;
	private int infoCount;
	private LinkedList<QueryResult> highQueryResultList;
	private LinkedList<QueryResult> mediumQueryResultList;
	private LinkedList<QueryResult> lowQueryResultList;
	private LinkedList<QueryResult> infoQueryResultList;

	@NotNull
	private String resultDeepLink;
	private File pdfReport;

	public static final String PDF_REPORT_NAME = "ScanReport.pdf";
	@Nullable
	private String scanStart;
	@Nullable
	private String scanTime;
	@Nullable
	private String linesOfCodeScanned;
	@Nullable
	private String filesScanned;
	@Nullable
	private String scanType;

	private boolean resultIsValid;
	private String errorMessage;

	public CxScanResult(final AbstractBuild owner, final String loggerSuffix, String serverUrl) {
		logger = CxLogUtils.loggerWithSuffix(getClass(), loggerSuffix);
		this.owner = owner;
		this.serverUrl = serverUrl;
		this.resultIsValid = false;
		this.errorMessage = "No Scan Results"; // error message to appear if results were not parsed
		this.highQueryResultList = new LinkedList<>();
		this.mediumQueryResultList = new LinkedList<>();
		this.lowQueryResultList = new LinkedList<>();
		this.infoQueryResultList = new LinkedList<>();
	}

	@Override
	public String getIconFileName() {
		if (isShowResults()) {
			return getIconPath() + "CxIcon24x24.png";
		} else {
			return null;
		}
	}

	@Override
	public String getDisplayName() {
		if (isShowResults()) {
			return "Checkmarx Scan Results";
		} else {
			return null;
		}
	}

	@Override
	public String getUrlName() {
		if (isShowResults()) {
			return "checkmarx";
		} else {
			return null;
		}
	}

	@NotNull
	public String getIconPath() {
		PluginWrapper wrapper = Hudson.getInstance().getPluginManager().getPlugin(CxPlugin.class);
		return "/plugin/" + wrapper.getShortName() + "/";

	}

	public boolean isShowResults() {
		@Nullable
		CxScanBuilder.DescriptorImpl descriptor = (CxScanBuilder.DescriptorImpl) Jenkins.getInstance().getDescriptor(CxScanBuilder.class);
		return descriptor != null && !descriptor.isHideResults();
	}

	public int getHighCount() {
		return highCount;
	}

	public int getMediumCount() {
		return mediumCount;
	}

	public int getLowCount() {
		return lowCount;
	}

	public int getInfoCount() {
		return infoCount;
	}

	@NotNull
	public String getResultDeepLink() {
		return resultDeepLink;
	}

	@Nullable
	public String getScanStart() {
		return scanStart;
	}

	@Nullable
	public String getScanTime() {
		return scanTime;
	}

	@Nullable
	public String getLinesOfCodeScanned() {
		return linesOfCodeScanned;
	}

	@Nullable
	public String getFilesScanned() {
		return filesScanned;
	}

	@Nullable
	public String getScanType() {
		return scanType;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public boolean isResultIsValid() {
		return resultIsValid;
	}

	public List<QueryResult> getHighQueryResultList() {
		return highQueryResultList;
	}

	public List<QueryResult> getMediumQueryResultList() {
		return mediumQueryResultList;
	}

	public List<QueryResult> getLowQueryResultList() {
		return lowQueryResultList;
	}

	public List<QueryResult> getInfoQueryResultList() {
		return infoQueryResultList;
	}

	public boolean isPdfReportReady() {
		File buildDirectory = owner.getRootDir();
		pdfReport = new File(buildDirectory, "/checkmarx/" + PDF_REPORT_NAME);
		return pdfReport.exists();
	}

	public String getPdfReportUrl() {
		return "/pdfReport";
	}

	public void doPdfReport(StaplerRequest req, StaplerResponse rsp) throws IOException {
		rsp.setContentType("application/pdf");
		ServletOutputStream outputStream = rsp.getOutputStream();
		IOUtils.copy(pdfReport, outputStream);

		outputStream.flush();
		outputStream.close();
	}

	/**
	 * Gets the test result of the previous build, if it's recorded, or null.
	 */

	public CxScanResult getPreviousResult() {
		AbstractBuild<?, ?> b = owner;
		while (true) {
			b = b.getPreviousBuild();
			if (b == null) {
				return null;
			}
			CxScanResult r = b.getAction(CxScanResult.class);
			if (r != null) {
				return r;
			}
		}
	}

	public void readScanXMLReport(File scanXMLReport) {
		ResultsParseHandler handler = new ResultsParseHandler();

		try {
			SAXParser saxParser = SAXParserFactory.newInstance().newSAXParser();

			highCount = 0;
			mediumCount = 0;
			lowCount = 0;
			infoCount = 0;

			saxParser.parse(scanXMLReport, handler);

			resultIsValid = true;
			errorMessage = null;

		} catch (ParserConfigurationException e) {
			logger.fatal(e);
		} catch (SAXException | IOException e) {
			resultIsValid = false;
			errorMessage = e.getMessage();
			logger.warn(e);
		}
	}

	private class ResultsParseHandler extends DefaultHandler {

		@Nullable
		private String currentQueryName;
		@Nullable
		private String currentQuerySeverity;
		private int currentQueryNumOfResults;

		@Override
		public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
			super.startElement(uri, localName, qName, attributes);

			switch (qName) {
			case "Result":
				@Nullable
				String falsePositive = attributes.getValue("FalsePositive");
				if (!"True".equals(falsePositive)) {
					currentQueryNumOfResults++;
					@Nullable
					String severity = attributes.getValue("SeverityIndex");
					if (severity != null) {
						if (severity.equals(HIGH.xmlParseString)) {
							highCount++;

						} else if (severity.equals(MEDIUM.xmlParseString)) {
							mediumCount++;

						} else if (severity.equals(LOW.xmlParseString)) {
							lowCount++;

						} else if (severity.equals(INFO.xmlParseString)) {
							infoCount++;
						}
					} else {
						logger.warn("\"SeverityIndex\" attribute was not found in element \"Result\" in XML report. "
								+ "Make sure you are working with Checkmarx server version 7.1.6 HF3 or above.");
					}
				}
				break;
			case "Query":
				currentQueryName = attributes.getValue("name");
				if (currentQueryName == null) {
					logger.warn("\"name\" attribute was not found in element \"Query\" in XML report");
				}
				currentQuerySeverity = attributes.getValue("SeverityIndex");
				if (currentQuerySeverity == null) {
					logger.warn("\"SeverityIndex\" attribute was not found in element \"Query\" in XML report. "
							+ "Make sure you are working with Checkmarx server version 7.1.6 HF3 or above.");
				}
				currentQueryNumOfResults = 0;

				break;
			default:
				if ("CxXMLResults".equals(qName)) {
					resultDeepLink = constructDeepLink(attributes.getValue("DeepLink"));
					scanStart = attributes.getValue("ScanStart");
					scanTime = attributes.getValue("ScanTime");
					linesOfCodeScanned = attributes.getValue("LinesOfCodeScanned");
					filesScanned = attributes.getValue("FilesScanned");
					scanType = attributes.getValue("ScanType");
				}
				break;
			}
		}

		@Override
		public void endElement(String uri, String localName, String qName) throws SAXException {
			super.endElement(uri, localName, qName);
			if ("Query".equals(qName)) {
				QueryResult qr = new QueryResult();
				qr.setName(currentQueryName);
				qr.setSeverity(currentQuerySeverity);
				qr.setCount(currentQueryNumOfResults);

				if (StringUtils.equals(qr.getSeverity(), HIGH.xmlParseString)) {
					highQueryResultList.add(qr);
				} else if (StringUtils.equals(qr.getSeverity(), MEDIUM.xmlParseString)) {
					mediumQueryResultList.add(qr);
				} else if (StringUtils.equals(qr.getSeverity(), LOW.xmlParseString)) {
					lowQueryResultList.add(qr);
				} else if (StringUtils.equals(qr.getSeverity(), INFO.xmlParseString)) {
					infoQueryResultList.add(qr);
				} else {
					logger.warn("Encountered a result query with unknown severity: " + qr.getSeverity());
				}
			}
		}

		@NotNull
		private String constructDeepLink(@Nullable String rawDeepLink) {
			if (rawDeepLink == null) {
				logger.warn("\"DeepLink\" attribute was not found in element \"CxXMLResults\" in XML report");
				return "";
			}
			String token = "CxWebClient";
			String[] tokens = rawDeepLink.split(token);
			if (tokens.length < 1) {
				logger.warn("DeepLink value found in XML report is of unexpected format: " + rawDeepLink + "\n"
						+ "\"Open Code Viewer\" button will not be functional");
			}
			return serverUrl + "/" + token + tokens[1];
		}
	}

	public static class QueryResult {
		@Nullable
		private String name;
		@Nullable
		private String severity;
		private int count;

		@Nullable
		public String getName() {
			return name;
		}

		public void setName(@Nullable String name) {
			this.name = name;
		}

		@Nullable
		public String getSeverity() {
			return severity;
		}

		public void setSeverity(@Nullable String severity) {
			this.severity = severity;
		}

		public int getCount() {
			return count;
		}

		public void setCount(int count) {
			this.count = count;
		}

		@NotNull
		public String getPrettyName() {
			if (this.name != null) {
				return this.name.replace('_', ' ');
			} else {
				return "";
			}
		}
	}
}
