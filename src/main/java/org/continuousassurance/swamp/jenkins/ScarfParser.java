package org.continuousassurance.swamp.jenkins;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.continuousassurance.scarf.ScarfInterface;
import org.continuousassurance.scarf.ScarfXmlReader;
import org.continuousassurance.scarf.datastructures.*;

public class ScarfParser implements ScarfInterface {
	/**
	 * Initial info from SCARF file
	 */
	private InitialInfo info;
	/**
	 * List of bugs in the SCARF file
	 */
	private List<BugInstance> bugs;
	/**
	 * List of metrics in the SCARF file
	 */
	private List<Metric> metrics;
	/**
	 * List of metric summaries in the SCARF file
	 */
	private List<MetricSummary> metricSummaries;
	/**
	 * List of bug summaries in the SCARF file
	 */
	private List<BugSummary> bugSummaries;
	/**
	 * Name of tool that ran assessment
	 */
	private String tool;
	/**
	 * Name of platform on which assessment was run
	 */
	private String platform;
	/**
	 * Map from source file name to a list of bugs found in that file
	 */
	private Map<String, List<BugInstance>> fileBugs;
	
	/**
	 * Constructor for ResultsParser
	 * @param f SCARF file being read
	 */
	public ScarfParser(File f) {
		ScarfXmlReader reader = new ScarfXmlReader(this);
		bugs = new ArrayList<>();
		metrics = new ArrayList<>();
		metricSummaries = new ArrayList<>();
		bugSummaries = new ArrayList<>();
		fileBugs = new HashMap<>();
		reader.parseFromFile(f);
	}
	
	@Override
	/**
	 * Callback for initial info in SCARF
	 * @param initial InitialInfo object
	 */
	public void initialCallback(InitialInfo initial) {
		info = initial;
		tool = info.getToolName() + " " + info.getToolVersion();
		platform = "?"; // TODO: Get the actual Platform once SCARF is updated
		System.out.println("Tool = " + tool);
	}
	
	@Override
	/**
	 * Callback for metric in SCARF
	 * @param metric Metric that was just parsed from SCARF file
	 */
	public void metricCallback(Metric metric) {
		metrics.add(metric);
	}
	
	@Override
	/**
	 * Callback for metric summary in SCARF
	 * @param summary MetricSummary that was just parsed from SCARF file
	 */
	public void metricSummaryCallback(MetricSummary summary) {
		metricSummaries.add(summary);
	}

	@Override
	/**
	 * Callback for bug in SCARF
	 * @param bug BugInstance that was just parsed from SCARF file
	 */
	public void bugCallback(BugInstance bug) {
		for (Location l : bug.getLocations()) {
			if (l.isPrimary()) {
				String filename = l.getSourceFile();
				if (fileBugs.containsKey(filename)) {
					bugs = fileBugs.get(filename);
				}
				else {
					bugs = new ArrayList<>();
					//System.out.println("Filename used: " + filename);
				}
				bugs.add(bug);
				fileBugs.put(filename, bugs);
				break;
			}
		}
	}
	
	@Override
	/**
	 * Callback for bug summaries in SCARF
	 * @param summary BugSummary that was just parsed from the SCARF file
	 */
	public void bugSummaryCallback(BugSummary summary) {
		bugSummaries.add(summary);
	}
	
	/**
	 * Given the name of a file, returns the bugs found in that file
	 * @param filename source code file name
	 * @return list of bugs found in that file
	 */
	public List<BugInstance> getFileBugs(String filename) {
		if (fileBugs.containsKey(filename)) {
			return fileBugs.get(filename);
		}
		else {
			return new ArrayList<BugInstance>();
		}
	}
	
	/**
	 * Getter for tool name
	 * @return name of tool that ran the assessment
	 */
	public String getToolName() {
		return tool;
	}
	
	/**
	 * Getter for platform name
	 * @return name of platform assessment was run on
	 */
	public String getPlatformName() {
		return platform;
	}

	/**
	 * Getter for list of bugs
	 * @return a list of BugInstance objects
	 */
	public List<BugInstance> getAllBugInstances() {
		return bugs;
	}
}
