package com.nsn.hudson.plugins.view;

import hudson.plugins.view.dashboard.test.TestResult;

import java.util.ArrayList;
import java.util.List;



/**
 * @author tang
 */

public class TestResultSummary extends TestResult {
	private List<TestResult> testResults = new ArrayList<TestResult>();

	public TestResultSummary() {
		super(null, 0, 0, 0);
	}
	
	public TestResultSummary addTestResult(TestResult testResult) {
		testResults.add(testResult);
		
		tests += testResult.getTests();
		success += testResult.getSuccess();
		failed += testResult.getFailed();
		skipped += testResult.getSkipped();
		
		return this;
	}
	
	public List<TestResult> getTestResults() {
		return testResults;
	}
	
}
