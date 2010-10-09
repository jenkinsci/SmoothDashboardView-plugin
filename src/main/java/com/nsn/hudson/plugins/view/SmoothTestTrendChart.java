package com.nsn.hudson.plugins.view;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.Run;
import hudson.plugins.view.dashboard.DashboardPortlet;
import hudson.plugins.view.dashboard.test.TestResult;
import hudson.plugins.view.dashboard.test.TestUtil;
import hudson.tasks.junit.TestResultAction;
import hudson.util.ColorPalette;
import hudson.util.DataSetBuilder;
import hudson.util.Graph;
import hudson.util.ShiftedCategoryAxis;
import hudson.util.StackedAreaRenderer2;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.StackedAreaRenderer;
import org.jfree.data.category.CategoryDataset;
import org.jfree.ui.RectangleInsets;
import org.joda.time.LocalDate;
import org.kohsuke.stapler.DataBoundConstructor;

public class SmoothTestTrendChart extends DashboardPortlet {

	@DataBoundConstructor
	public SmoothTestTrendChart(String name) {
		super(name);
	}
	
	/**
	 * Graph of duration of tests over time.
	 */
	public Graph getSummaryGraph() {		
		java.util.List<TestJobInfo> jobInfos = new ArrayList<TestJobInfo>();
		
		// hold onto summaries
		final Map<LocalDate, TestResultSummary> summaries = new HashMap<LocalDate, TestResultSummary>();

		
		for (Job job : getDashboard().getJobs()) {
			TestJobInfo jobInfo = new TestJobInfo(job);
			//if(job.getFirstBuild()==null||job.getLastBuild().getAction(TestResultAction.class)==null){
			if(job.getFirstBuild()==null){
				continue;
			}
			jobInfo.setStartRunTime(new LocalDate(job.getFirstBuild().getTimestamp()));
			jobInfo.setEndRunTime(new LocalDate(job.getLastBuild().getTimestamp()));
			jobInfos.add(jobInfo);
		}
		
		// get the latest endRunDay of jobs
		LocalDate endRunDay = new LocalDate(2000, 1, 1);
		for (TestJobInfo jobInfo:jobInfos){
			if(endRunDay.isBefore(jobInfo.getEndRunTime())){
				endRunDay=jobInfo.getEndRunTime();
			}
		}
		
        for(TestJobInfo jobInfo:jobInfos){
			
			for(LocalDate startRunDay =jobInfo.getStartRunTime();(startRunDay.isBefore(jobInfo.getEndRunTime())||startRunDay.isEqual(jobInfo.getEndRunTime()));startRunDay=startRunDay.plusDays(1)){
				
				Run run = jobInfo.getJob().getFirstBuild();
			    Run currentRun  = null;
			    Run lastRun = null;
			    
				while (run != null) {
					Run nextRun = run.getNextBuild();
					
					LocalDate runDay = new LocalDate(run.getTimestamp());
					
					if(startRunDay.isEqual(runDay) && run.getAction(TestResultAction.class)!=null){
						currentRun = run;
					}
					if(startRunDay.isBefore(runDay)){
						lastRun = getPreviousBuildWithTestResultAction(run);
						break;
					}
					
					run =  nextRun;
				}
				
                if(currentRun!=null){
					summarize(summaries, currentRun, startRunDay, startRunDay);	
				    currentRun = null;
				}else if(lastRun!=null){
				    summarize(summaries,lastRun,startRunDay,startRunDay);
				    lastRun =null;
				}
			}
			
			//makeup to summarize the job statics data from lastRunDay + 1 of this job to latest endRunDay of all jobs
			for(LocalDate startRunDay=jobInfo.getEndRunTime().plusDays(1);startRunDay.isBefore(endRunDay)||startRunDay.isEqual(endRunDay);startRunDay=startRunDay.plusDays(1)){
				summarize(summaries,jobInfo.getJob().getLastBuild(),startRunDay,startRunDay);
			}
		}
		
		return new Graph(-1, 300, 220) {

			@Override
			protected JFreeChart createGraph() {
				final JFreeChart chart = ChartFactory.createStackedAreaChart(
		            null,                   // chart title
		            "date",                   // unused
		            "count",                  // range axis label
		            buildDataSet(summaries), // data
		            PlotOrientation.VERTICAL, // orientation
		            false,                     // include legend
		            false,                     // tooltips
		            false                     // urls
		        );

		        chart.setBackgroundPaint(Color.white);

		        final CategoryPlot plot = chart.getCategoryPlot();

		        plot.setBackgroundPaint(Color.WHITE);
		        plot.setOutlinePaint(null);
		        plot.setForegroundAlpha(0.8f);
		        plot.setRangeGridlinesVisible(true);
		        plot.setRangeGridlinePaint(Color.black);

		        CategoryAxis domainAxis = new ShiftedCategoryAxis(null);
		        plot.setDomainAxis(domainAxis);
		        domainAxis.setCategoryLabelPositions(CategoryLabelPositions.UP_90);
		        domainAxis.setLowerMargin(0.0);
		        domainAxis.setUpperMargin(0.0);
		        domainAxis.setCategoryMargin(0.0);

		        final NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
		        rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());

		        StackedAreaRenderer ar = new StackedAreaRenderer2();
		        plot.setRenderer(ar);
		        ar.setSeriesPaint(0,ColorPalette.RED); // Failures.
		        ar.setSeriesPaint(1,ColorPalette.YELLOW); // Skips.
		        ar.setSeriesPaint(2,ColorPalette.BLUE); // Total.

		        // crop extra space around the graph
		        plot.setInsets(new RectangleInsets(0,0,0,5.0));
		        
				return chart;
			}

		};
	}
	
	private CategoryDataset buildDataSet(Map<LocalDate, TestResultSummary> summaries) {
        DataSetBuilder<String,LocalDate> dsb = new DataSetBuilder<String,LocalDate>();

        for (Map.Entry<LocalDate, TestResultSummary> entry : summaries.entrySet()) {
            dsb.add( entry.getValue().getFailed(), "failed", entry.getKey());
            dsb.add( entry.getValue().getSkipped(), "skipped", entry.getKey());
            dsb.add( entry.getValue().getSuccess(), "total", entry.getKey());
        }
        return dsb.build();
    }
	
	private void summarize(Map<LocalDate, TestResultSummary> summaries,
			Run run, LocalDate firstDay, LocalDate lastDay) {
		
		if(run.getAction(TestResultAction.class)==null){
			return;
		}
		
		TestResult testResult = TestUtil.getTestResult(run);
		
		// for every day between first day and last day inclusive
		for (LocalDate curr = firstDay; curr.compareTo(lastDay) <= 0; curr = curr.plusDays(1)) {
			TestResultSummary trs = summaries.get(curr);
			if (trs == null) {
				    trs = new TestResultSummary();
					summaries.put(curr, trs);
			}
			trs.addTestResult(testResult);
		}
		
	}
	
	private Run getPreviousBuildWithTestResultAction(Run run){
	    if(run ==null){
	    	return null;
	    }
		else if(run.getAction(TestResultAction.class)!=null){
			return run;
		}else {
			return getPreviousBuildWithTestResultAction(run.getPreviousBuild());
		}
	}

	@Extension
    public static class DescriptorImpl extends Descriptor<DashboardPortlet> {

		@Override
		public String getDisplayName() {
			return "Smooth Test Trend Chart";
		}
	}
	
	public class TestJobInfo {
	    public final Job job;
		
		public LocalDate startRunTime;
		
		public LocalDate endRunTime;
		
		public TestJobInfo(Job job){
			this.job  = job;
		}

		public Job getJob() {
			return job;
		}

		public LocalDate getStartRunTime() {
			return startRunTime;
		}

		public void setStartRunTime(LocalDate startRunTime) {
			this.startRunTime = startRunTime;
		}

		public LocalDate getEndRunTime() {
			return endRunTime;
		}

		public void setEndRunTime(LocalDate endRunTime) {
			this.endRunTime = endRunTime;
		}
		
	}
}
